package emu.jvic.monitor;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import emu.jvic.MachineType;
import emu.jvic.PixelData;
import emu.jvic.cpu.Cpu6502;
import emu.jvic.debug.Debugger;
import emu.jvic.debug.StateReader;
import emu.jvic.video.Vic;

/**
 * VICE's binary monitor protocol (API version 2), as used by IDE debuggers: framed
 * commands in, framed responses and events out. This class handles whole packets on
 * the emulation thread; a transport (the desktop's TCP server on port 6502) splits
 * the byte stream into packets and sends the responses.
 *
 * <p>As in VICE, any command stops the machine first (with a registers event and a
 * stopped event), and the machine sends a checkpoint event (if a checkpoint stopped
 * it), registers and stopped events whenever it stops, and a resumed event whenever
 * it resumes. The byte layouts follow the VICE 3.10 manual, chapter 13, and were
 * checked against xvic 3.10.
 */
public final class BinaryMonitor implements MonitorSession.EventListener {

    /** Sends a response or event packet to the client. */
    public interface Sink {
        void send(byte[] packet);
    }

    static final int API_VERSION = 2;
    static final int EVENT_ID = 0xFFFFFFFF;

    static final int ERROR_OK = 0x00;
    static final int ERROR_NOT_FOUND = 0x01;
    static final int ERROR_MEMSPACE = 0x02;
    static final int ERROR_LENGTH = 0x80;
    static final int ERROR_PARAMETER = 0x81;
    static final int ERROR_API_VERSION = 0x82;
    static final int ERROR_COMMAND = 0x83;
    static final int ERROR_FAILED = 0x8F;

    // Register IDs, as xvic 3.10 numbers them.
    static final int REG_A = 0x00;
    static final int REG_X = 0x01;
    static final int REG_Y = 0x02;
    static final int REG_PC = 0x03;
    static final int REG_SP = 0x04;
    static final int REG_FL = 0x05;
    static final int REG_LIN = 0x35;
    static final int REG_CYC = 0x36;
    static final int REG_00 = 0x37;
    static final int REG_01 = 0x38;

    private static final int[] MAIN_REGISTERS = { REG_PC, REG_A, REG_X, REG_Y, REG_SP, REG_00, REG_01, REG_FL,
        REG_LIN, REG_CYC };
    private static final int[] DRIVE_REGISTERS = { REG_PC, REG_A, REG_X, REG_Y, REG_SP, REG_FL, REG_LIN, REG_CYC };

    private final MonitorSession session;
    private final Sink sink;

    public BinaryMonitor(MonitorSession session, Sink sink) {
        this.session = session;
        this.sink = sink;
        session.addEventListener(this);
    }

    /** Stops sending events. */
    public void close() {
        session.removeEventListener(this);
    }

    // ------------------------------------------------------------------------------
    // Packets.
    // ------------------------------------------------------------------------------

    /** The length of a command header: STX, API, length (4), request ID (4), command. */
    public static final int HEADER_LENGTH = 11;

    /** The body length given in a command header. */
    public static int bodyLength(byte[] header) {
        return littleInt(header, 2);
    }

    private static int littleInt(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8) | ((data[offset + 2] & 0xFF) << 16)
                | ((data[offset + 3] & 0xFF) << 24);
    }

    private static int littleShort(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    /** A response packet. */
    static byte[] packet(int type, int error, int requestId, byte[] body) {
        byte[] packet = new byte[12 + body.length];
        packet[0] = 0x02;
        packet[1] = API_VERSION;
        putInt(packet, 2, body.length);
        packet[6] = (byte) type;
        packet[7] = (byte) error;
        putInt(packet, 8, requestId);
        System.arraycopy(body, 0, packet, 12, body.length);
        return packet;
    }

    private static void putInt(byte[] data, int offset, int value) {
        data[offset] = (byte) value;
        data[offset + 1] = (byte) (value >> 8);
        data[offset + 2] = (byte) (value >> 16);
        data[offset + 3] = (byte) (value >> 24);
    }

    /** A little-endian body builder. */
    private static final class Body {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        Body b(int value) {
            out.write(value & 0xFF);
            return this;
        }

        Body w(int value) {
            return b(value).b(value >> 8);
        }

        Body i(int value) {
            return w(value).w(value >>> 16);
        }

        Body l(long value) {
            return i((int) value).i((int) (value >>> 32));
        }

        Body bytes(byte[] data) {
            out.write(data, 0, data.length);
            return this;
        }

        byte[] get() {
            return out.toByteArray();
        }
    }

    private void respond(int type, int requestId, byte[] body) {
        sink.send(packet(type, ERROR_OK, requestId, body));
    }

    private void error(int error, int requestId) {
        sink.send(packet(0x00, error, requestId, new byte[0]));
    }

    /**
     * Handles one command packet (header and body). Call on the emulation thread. As
     * in VICE, the machine is stopped first if it is running.
     */
    public void handle(byte[] packet) {
        int requestId = littleInt(packet, 6);
        if ((packet[0] != 0x02) || ((packet[1] & 0xFF) != API_VERSION)) {
            error(ERROR_API_VERSION, requestId);
            return;
        }
        int command = packet[10] & 0xFF;
        byte[] body = new byte[packet.length - HEADER_LENGTH];
        System.arraycopy(packet, HEADER_LENGTH, body, 0, body.length);
        if (session.getEngine() == null) {
            error(ERROR_FAILED, requestId);
            return;
        }
        session.runWhenStopped(() -> {
            try {
                process(command, requestId, body);
            } catch (ArrayIndexOutOfBoundsException e) {
                error(ERROR_LENGTH, requestId);
            } catch (RuntimeException e) {
                error(ERROR_FAILED, requestId);
            }
        });
    }

    private AddressSpace memspace(MonitorEngine engine, int memspace) {
        switch (memspace) {
            case 0: return engine.getMainSpace();
            case 1: return engine.getDriveSpace();
            default: return null;
        }
    }

    private void process(int command, int id, byte[] body) {
        MonitorEngine engine = session.getEngine();
        switch (command) {
            case 0x01: { // Memory get
                AddressSpace space = memspace(engine, body[5] & 0xFF);
                if (space == null) {
                    error(ERROR_MEMSPACE, id);
                    return;
                }
                boolean sideEffects = body[0] != 0;
                int start = littleShort(body, 1);
                int length = ((littleShort(body, 3) - start) & 0xFFFF) + 1;
                Body out = new Body().w(length);
                for (int i = 0; i < length; i++) {
                    out.b(space.read((start + i) & 0xFFFF, sideEffects));
                }
                respond(0x01, id, out.get());
                return;
            }
            case 0x02: { // Memory set
                AddressSpace space = memspace(engine, body[5] & 0xFF);
                if (space == null) {
                    error(ERROR_MEMSPACE, id);
                    return;
                }
                int start = littleShort(body, 1);
                int length = ((littleShort(body, 3) - start) & 0xFFFF) + 1;
                if (body.length < 8 + length) {
                    error(ERROR_LENGTH, id);
                    return;
                }
                for (int i = 0; i < length; i++) {
                    space.write((start + i) & 0xFFFF, body[8 + i] & 0xFF);
                }
                respond(0x02, id, new byte[0]);
                return;
            }
            case 0x11: { // Checkpoint get
                Checkpoint checkpoint = engine.getCheckpoints().find(littleInt(body, 0));
                if (checkpoint == null) {
                    error(ERROR_NOT_FOUND, id);
                    return;
                }
                sink.send(checkpointInfo(checkpoint, false, id));
                return;
            }
            case 0x12: { // Checkpoint set
                AddressSpace space = (body.length > 8) ? memspace(engine, body[8] & 0xFF) : engine.getMainSpace();
                if (space == null) {
                    error(ERROR_MEMSPACE, id);
                    return;
                }
                int operations = body[6] & (Checkpoint.LOAD | Checkpoint.STORE | Checkpoint.EXEC);
                if (operations == 0) {
                    error(ERROR_PARAMETER, id);
                    return;
                }
                Checkpoint checkpoint = engine.getCheckpoints().add(space, littleShort(body, 0), littleShort(body, 2),
                        operations, body[4] != 0, body[7] != 0);
                if (body[5] == 0) {
                    checkpoint.setEnabled(false);
                    engine.getCheckpoints().refresh();
                }
                sink.send(checkpointInfo(checkpoint, false, id));
                return;
            }
            case 0x13: { // Checkpoint delete
                Checkpoint checkpoint = engine.getCheckpoints().find(littleInt(body, 0));
                if (checkpoint == null) {
                    error(ERROR_NOT_FOUND, id);
                    return;
                }
                engine.getCheckpoints().remove(checkpoint);
                respond(0x13, id, new byte[0]);
                return;
            }
            case 0x14: { // Checkpoint list
                List<Checkpoint> all = engine.getCheckpoints().getAll();
                for (Checkpoint checkpoint : all) {
                    sink.send(checkpointInfo(checkpoint, false, id));
                }
                respond(0x14, id, new Body().i(all.size()).get());
                return;
            }
            case 0x15: { // Checkpoint toggle
                Checkpoint checkpoint = engine.getCheckpoints().find(littleInt(body, 0));
                if (checkpoint == null) {
                    error(ERROR_NOT_FOUND, id);
                    return;
                }
                checkpoint.setEnabled(body[4] != 0);
                engine.getCheckpoints().refresh();
                respond(0x15, id, new byte[0]);
                return;
            }
            case 0x22: { // Condition set
                Checkpoint checkpoint = engine.getCheckpoints().find(littleInt(body, 0));
                if (checkpoint == null) {
                    error(ERROR_NOT_FOUND, id);
                    return;
                }
                int length = body[4] & 0xFF;
                String text = new String(body, 5, length, StandardCharsets.ISO_8859_1);
                try {
                    Parser parser = new Parser(text, 0, engine.getRadix(), engine);
                    checkpoint.setCondition(Condition.parse(parser));
                } catch (MonitorException e) {
                    error(ERROR_FAILED, id);
                    return;
                }
                respond(0x22, id, new byte[0]);
                return;
            }
            case 0x31: { // Registers get
                AddressSpace space = memspace(engine, body[0] & 0xFF);
                if (space == null) {
                    error(ERROR_MEMSPACE, id);
                    return;
                }
                sink.send(registersInfo(space, id));
                return;
            }
            case 0x32: { // Registers set
                AddressSpace space = memspace(engine, body[0] & 0xFF);
                if (space == null) {
                    error(ERROR_MEMSPACE, id);
                    return;
                }
                int count = littleShort(body, 1);
                int p = 3;
                for (int n = 0; n < count; n++) {
                    int size = body[p] & 0xFF;
                    setRegister(space, body[p + 1] & 0xFF, littleShort(body, p + 2));
                    p += size + 1;
                }
                sink.send(registersInfo(space, id));
                return;
            }
            case 0x41: { // Dump
                int length = body[2] & 0xFF;
                String name = new String(body, 3, length, StandardCharsets.ISO_8859_1);
                try {
                    engine.getFileSystem().write(name, StateCommands.save(engine));
                } catch (MonitorFileException e) {
                    error(ERROR_FAILED, id);
                    return;
                }
                respond(0x41, id, new byte[0]);
                return;
            }
            case 0x42: { // Undump
                int length = body[0] & 0xFF;
                String name = new String(body, 1, length, StandardCharsets.ISO_8859_1);
                try {
                    StateCommands.load(engine, engine.getFileSystem().read(name));
                } catch (MonitorFileException | StateReader.StateException e) {
                    error(ERROR_FAILED, id);
                    return;
                }
                respond(0x42, id, new Body().w(engine.getMainSpace().getDisplayPc()).get());
                return;
            }
            case 0x51: { // Resource get
                String name = new String(body, 1, body[0] & 0xFF, StandardCharsets.ISO_8859_1);
                String value = MediaCommands.getResource(engine, name);
                if (value == null) {
                    error(ERROR_NOT_FOUND, id);
                    return;
                }
                if (value.startsWith("\"")) {
                    byte[] text = value.substring(1, value.length() - 1).getBytes(StandardCharsets.ISO_8859_1);
                    respond(0x51, id, new Body().b(0).b(text.length).bytes(text).get());
                } else {
                    respond(0x51, id, new Body().b(1).b(4).i(Integer.parseInt(value)).get());
                }
                return;
            }
            case 0x52: { // Resource set
                int type = body[0] & 0xFF;
                int nameLength = body[1] & 0xFF;
                String name = new String(body, 2, nameLength, StandardCharsets.ISO_8859_1);
                int valueLength = body[2 + nameLength] & 0xFF;
                int valueStart = 3 + nameLength;
                String value;
                if (type == 1) {
                    int number = 0;
                    for (int i = valueLength - 1; i >= 0; i--) {
                        number = (number << 8) | (body[valueStart + i] & 0xFF);
                    }
                    value = Integer.toString(number);
                } else {
                    value = new String(body, valueStart, valueLength, StandardCharsets.ISO_8859_1);
                }
                if (MediaCommands.getResource(engine, name) == null) {
                    error(ERROR_NOT_FOUND, id);
                    return;
                }
                if (MediaCommands.setResource(engine, name, value) != null) {
                    error(ERROR_FAILED, id);
                    return;
                }
                respond(0x52, id, new byte[0]);
                return;
            }
            case 0x71: { // Advance instructions
                boolean over = body[0] != 0;
                int count = littleShort(body, 1);
                respond(0x71, id, new byte[0]);
                engine.startStep(over, Math.max(1, count));
                return;
            }
            case 0x72: { // Keyboard feed
                int length = body[0] & 0xFF;
                StringBuilder keys = new StringBuilder();
                for (int i = 0; i < length; i++) {
                    keys.append((char) (body[1 + i] & 0xFF));
                }
                engine.queueKeys(keys.toString());
                respond(0x72, id, new byte[0]);
                return;
            }
            case 0x73: // Execute until return
                respond(0x73, id, new byte[0]);
                engine.startReturn();
                return;
            case 0x81: // Ping
                respond(0x81, id, new byte[0]);
                return;
            case 0x82: { // Banks available (as xvic: two names, both bank 0)
                byte[] name1 = "default".getBytes(StandardCharsets.ISO_8859_1);
                byte[] name2 = "cpu".getBytes(StandardCharsets.ISO_8859_1);
                respond(0x82, id, new Body().w(2).b(3 + name1.length).w(0).b(name1.length).bytes(name1)
                        .b(3 + name2.length).w(0).b(name2.length).bytes(name2).get());
                return;
            }
            case 0x83: { // Registers available
                AddressSpace space = memspace(engine, body[0] & 0xFF);
                if (space == null) {
                    error(ERROR_MEMSPACE, id);
                    return;
                }
                int[] ids = (space == engine.getMainSpace()) ? MAIN_REGISTERS : DRIVE_REGISTERS;
                Body out = new Body().w(ids.length);
                for (int register : ids) {
                    byte[] name = registerName(register).getBytes(StandardCharsets.ISO_8859_1);
                    int bits = ((register == REG_PC) || (register == REG_LIN) || (register == REG_CYC)) ? 16 : 8;
                    out.b(3 + name.length).b(register).b(bits).b(name.length).bytes(name);
                }
                respond(0x83, id, out.get());
                return;
            }
            case 0x84: // Display get
                respond(0x84, id, display(engine));
                return;
            case 0x85: // VICE info: the VICE version whose protocol jvic follows (3.10.0.0).
                respond(0x85, id, new Body().b(4).b(3).b(10).b(0).b(0).b(4).i(0).get());
                return;
            case 0x86: { // CPU history
                AddressSpace space = memspace(engine, body[0] & 0xFF);
                if (space == null) {
                    error(ERROR_MEMSPACE, id);
                    return;
                }
                respond(0x86, id, history(space, littleInt(body, 1)));
                return;
            }
            case 0x91: { // Palette get
                int[] palette = engine.getMachine().getVic().getPaletteColours();
                Body out = new Body().w(palette.length);
                for (int colour : palette) {
                    out.b(3).b(colour >>> 24).b(colour >> 16).b(colour >> 8);
                }
                respond(0x91, id, out.get());
                return;
            }
            case 0xA2: { // Joyport set
                int port = littleShort(body, 0);
                if ((port != 0) && (port != 1)) {
                    error(ERROR_PARAMETER, id);
                    return;
                }
                engine.execute("jpdb " + port + " $" + Integer.toHexString(littleShort(body, 2) & 0xFF), text -> { });
                respond(0xA2, id, new byte[0]);
                return;
            }
            case 0xB2: // Userport set: no user port devices in jvic.
                respond(0xB2, id, new byte[0]);
                return;
            case 0xAA: // Exit
                respond(0xAA, id, new byte[0]);
                engine.resume();
                return;
            case 0xBB: // Quit
                respond(0xBB, id, new byte[0]);
                engine.flushLog();
                engine.getHost().quit();
                return;
            case 0xCC: { // Reset
                int what = body[0] & 0xFF;
                if ((what != 0) && (what != 1) && (what != 8)) {
                    error(ERROR_PARAMETER, id);
                    return;
                }
                respond(0xCC, id, new byte[0]);
                engine.execute("reset " + what, text -> { });
                return;
            }
            case 0xDD: { // Autostart / autoload
                boolean run = body[0] != 0;
                int index = littleShort(body, 1);
                String name = new String(body, 4, body[3] & 0xFF, StandardCharsets.ISO_8859_1);
                if (!engine.getFileSystem().exists(name)) {
                    error(ERROR_FAILED, id);
                    return;
                }
                respond(0xDD, id, new byte[0]);
                engine.execute((run ? "autostart" : "autoload") + " \"" + name + "\" +" + index, text -> { });
                return;
            }
            default:
                error(ERROR_COMMAND, id);
        }
    }

    // ------------------------------------------------------------------------------
    // Registers, checkpoints, display, history.
    // ------------------------------------------------------------------------------

    private static String registerName(int register) {
        switch (register) {
            case REG_A: return "A";
            case REG_X: return "X";
            case REG_Y: return "Y";
            case REG_PC: return "PC";
            case REG_SP: return "SP";
            case REG_FL: return "FL";
            case REG_LIN: return "LIN";
            case REG_CYC: return "CYC";
            case REG_00: return "00";
            default: return "01";
        }
    }

    private int registerValue(MonitorEngine engine, AddressSpace space, int register) {
        Cpu6502 cpu = space.getCpu();
        Vic vic = engine.getMainSpace().getVic();
        switch (register) {
            case REG_A: return cpu.getAccumulator();
            case REG_X: return cpu.getIndexRegisterX();
            case REG_Y: return cpu.getIndexRegisterY();
            case REG_PC: return space.getDisplayPc();
            case REG_SP: return cpu.getStackPointer();
            case REG_FL: return cpu.getProcessorStatus();
            case REG_LIN: return vic.getRasterLine();
            case REG_CYC: return vic.getRasterCycle();
            case REG_00: return space.read(0, false);
            default: return space.read(1, false);
        }
    }

    private static void setRegister(AddressSpace space, int register, int value) {
        Cpu6502 cpu = space.getCpu();
        switch (register) {
            case REG_A: cpu.setAccumulator(value & 0xFF); break;
            case REG_X: cpu.setIndexRegisterX(value & 0xFF); break;
            case REG_Y: cpu.setIndexRegisterY(value & 0xFF); break;
            case REG_PC: space.setPc(value & 0xFFFF); break;
            case REG_SP: cpu.setStackPointer(value & 0xFF); break;
            case REG_FL: cpu.setProcessorStatus(value & 0xFF); break;
            case REG_00: space.write(0, value & 0xFF); break;
            case REG_01: space.write(1, value & 0xFF); break;
            default: break;
        }
    }

    private byte[] registersInfo(AddressSpace space, int id) {
        MonitorEngine engine = session.getEngine();
        int[] ids = (space == engine.getMainSpace()) ? MAIN_REGISTERS : DRIVE_REGISTERS;
        Body out = new Body().w(ids.length);
        for (int register : ids) {
            out.b(3).b(register).w(registerValue(engine, space, register));
        }
        return packet(0x31, ERROR_OK, id, out.get());
    }

    private static byte[] checkpointInfo(Checkpoint checkpoint, boolean hit, int id) {
        int operations = (checkpoint.has(Checkpoint.LOAD) ? Checkpoint.LOAD : 0)
                | (checkpoint.has(Checkpoint.STORE) ? Checkpoint.STORE : 0)
                | (checkpoint.has(Checkpoint.EXEC) ? Checkpoint.EXEC : 0);
        int start = checkpoint.getStart();
        Body out = new Body().i(checkpoint.getNumber()).b(hit ? 1 : 0).w(start)
                .w((start + checkpoint.getLength() - 1) & 0xFFFF).b(checkpoint.isStop() ? 1 : 0)
                .b(checkpoint.isEnabled() ? 1 : 0).b(operations).b(checkpoint.isTemporary() ? 1 : 0)
                .i(checkpoint.getHitCount()).i(checkpoint.getIgnoreCount())
                .b((checkpoint.getCondition() != null) ? 1 : 0)
                .b("8".equals(checkpoint.getSpace().getName()) ? 1 : 0);
        return packet(0x11, ERROR_OK, id, out.get());
    }

    /** The screen as 8-bit colour numbers, with xvic's header layout. */
    private static byte[] display(MonitorEngine engine) {
        MachineType type = engine.getMachine().getMachineType();
        PixelData pixels = engine.getMachine().getPixelData();
        int[][] palettes = engine.getMachine().getVic().getAllPalettes();
        int width = type.getTotalScreenWidth();
        int height = type.getTotalScreenHeight();
        java.util.Map<Integer, Integer> colours = new java.util.HashMap<>();
        for (int[] palette : palettes) {
            for (int i = 0; i < palette.length; i++) {
                if (!colours.containsKey(palette[i])) {
                    colours.put(palette[i], i);
                }
            }
        }
        Body out = new Body().i(13).w(width).w(height).w(type.getHorizontalOffset()).w(type.getVerticalOffset())
                .w(type.getVisibleScreenWidth()).w(type.getVisibleScreenHeight()).b(8).i(width * height);
        for (int i = 0; i < width * height; i++) {
            Integer colour = colours.get(pixels.getPixel(i));
            out.b((colour != null) ? colour : 0);
        }
        return out.get();
    }

    private static byte[] history(AddressSpace space, int count) {
        CpuHistory history = space.getHistory();
        int size = (history != null) ? history.size() : 0;
        int first = Math.max(0, size - Math.max(0, count));
        Body out = new Body().i(size - first);
        for (int n = first; n < size; n++) {
            int registers = history.getRegisters(n);
            int bytes = history.getBytes(n);
            out.b(47).w(8);
            out.b(3).b(REG_PC).w(history.getAddress(n));
            out.b(3).b(REG_A).w(registers & 0xFF);
            out.b(3).b(REG_X).w((registers >> 8) & 0xFF);
            out.b(3).b(REG_Y).w((registers >> 16) & 0xFF);
            out.b(3).b(REG_SP).w(registers >>> 24);
            out.b(3).b(REG_FL).w(history.getStatus(n));
            out.b(3).b(REG_LIN).w(0xFFFF);
            out.b(3).b(REG_CYC).w(0xFFFF);
            out.l(history.getCycle(n));
            out.b(4).b(bytes).b(bytes >> 8).b(bytes >> 16).b(0xFF);
        }
        return out.get();
    }

    // ------------------------------------------------------------------------------
    // Events.
    // ------------------------------------------------------------------------------

    @Override
    public void onStopped() {
        MonitorEngine engine = session.getEngine();
        if (engine == null) {
            return;
        }
        Checkpoint checkpoint = engine.getStopCheckpoint();
        if (checkpoint != null) {
            sink.send(checkpointInfo(checkpoint, true, EVENT_ID));
        }
        sink.send(registersInfo(engine.getMainSpace(), EVENT_ID));
        int pc = engine.getMainSpace().getDisplayPc();
        boolean jam = engine.getDebugger().getStopReason() == Debugger.STOP_JAM;
        sink.send(packet(jam ? 0x61 : 0x62, ERROR_OK, EVENT_ID, new Body().w(pc).get()));
    }

    @Override
    public void onResumed() {
        MonitorEngine engine = session.getEngine();
        if (engine != null) {
            sink.send(packet(0x63, ERROR_OK, EVENT_ID, new Body().w(engine.getMainSpace().getDisplayPc()).get()));
        }
    }
}
