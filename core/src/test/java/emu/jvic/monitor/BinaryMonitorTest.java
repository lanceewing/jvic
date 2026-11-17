package emu.jvic.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import emu.jvic.MachineType;
import emu.jvic.memory.RamType;
import emu.jvic.testutil.MachineTestHarness;

/**
 * Tests for the binary monitor protocol, driving it as the desktop transport does.
 * Expected bytes were captured from xvic 3.10's binary monitor, except where jvic's
 * values differ (registers, palette).
 */
public class BinaryMonitorTest {

    private MachineTestHarness h;
    private MonitorSession session;
    private BinaryMonitor monitor;
    private final List<byte[]> sent = new ArrayList<>();
    private int quits;

    @BeforeEach
    void setUp() {
        h = MachineTestHarness.withSyntheticRoms(MachineType.PAL, RamType.RAM_UNEXPANDED);
        session = new MonitorSession(new MonitorSession.Platform() {
            @Override
            public void resumeEmulation() {
            }

            @Override
            public void quit() {
                quits++;
            }

            @Override
            public boolean isWarp() {
                return false;
            }

            @Override
            public void setWarp(boolean warp) {
            }
        });
        session.attach(h.machine);
        monitor = new BinaryMonitor(session, sent::add);
    }

    /** The runner's loop: run frames, and let the session see a stop. */
    private void loop(int frames) {
        for (int i = 0; i < frames; i++) {
            if (h.machine.isDebuggerStopped()) {
                session.onMachineStopped();
            } else {
                h.machine.update();
                if (h.machine.isDebuggerStopped()) {
                    session.onMachineStopped();
                }
            }
        }
    }

    private static byte[] command(int type, int requestId, int... body) {
        ByteBuffer buffer = ByteBuffer.allocate(11 + body.length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put((byte) 2).put((byte) 2).putInt(body.length).putInt(requestId).put((byte) type);
        for (int b : body) {
            buffer.put((byte) b);
        }
        return buffer.array();
    }

    private List<byte[]> take() {
        List<byte[]> packets = new ArrayList<>(sent);
        sent.clear();
        return packets;
    }

    private static int type(byte[] packet) {
        return packet[6] & 0xFF;
    }

    private static int error(byte[] packet) {
        return packet[7] & 0xFF;
    }

    private static boolean isEvent(byte[] packet) {
        return (packet[8] == -1) && (packet[9] == -1) && (packet[10] == -1) && (packet[11] == -1);
    }

    private static String body(byte[] packet) {
        StringBuilder hex = new StringBuilder();
        for (int i = 12; i < packet.length; i++) {
            hex.append((hex.length() > 0) ? " " : "").append(String.format("%02x", packet[i] & 0xFF));
        }
        return hex.toString();
    }

    @Test
    void aCommandStopsTheRunningMachineFirstAsVice() {
        monitor.handle(command(0x81, 5));
        assertTrue(take().isEmpty(), "nothing until the machine has stopped");
        loop(3);
        List<byte[]> packets = take();
        assertEquals(3, packets.size());
        assertEquals(0x31, type(packets.get(0)));
        assertTrue(isEvent(packets.get(0)));
        assertEquals(0x62, type(packets.get(1)));
        assertEquals(0x81, type(packets.get(2)));
        assertEquals(5, packets.get(2)[8]);
        // Once stopped, commands are answered at once.
        monitor.handle(command(0x81, 6));
        assertEquals(1, take().size());
    }

    @Test
    void fixedResponsesMatchVice() {
        loop(0);
        h.machine.getDebugger().requestStop();
        loop(3);
        take();
        monitor.handle(command(0x83, 1, 0));
        assertEquals("0a 00 05 03 10 02 50 43 04 00 08 01 41 04 01 08 01 58 04 02 08 01 59 05 04 08 02 53 50 "
                + "05 37 08 02 30 30 05 38 08 02 30 31 05 05 08 02 46 4c 06 35 10 03 4c 49 4e 06 36 10 03 43 59 43",
                body(take().get(0)));
        monitor.handle(command(0x83, 1, 1));
        assertEquals("08 00 05 03 10 02 50 43 04 00 08 01 41 04 01 08 01 58 04 02 08 01 59 05 04 08 02 53 50 "
                + "05 05 08 02 46 4c 06 35 10 03 4c 49 4e 06 36 10 03 43 59 43", body(take().get(0)));
        monitor.handle(command(0x82, 1));
        assertEquals("02 00 0a 00 00 07 64 65 66 61 75 6c 74 06 00 00 03 63 70 75", body(take().get(0)));
        monitor.handle(command(0x85, 1));
        assertEquals("04 03 0a 00 00 04 00 00 00 00", body(take().get(0)));
        // Errors: an unknown command, a memspace that doesn't exist, a missing checkpoint.
        byte[] bad = take(0x77);
        assertEquals(0x00, type(bad));
        assertEquals(0x83, error(bad));
        monitor.handle(command(0x01, 1, 0, 0, 0, 3, 0, 7, 0, 0));
        assertEquals(0x02, error(take().get(0)));
        monitor.handle(command(0x11, 1, 9, 0, 0, 0));
        assertEquals(0x01, error(take().get(0)));
    }

    private byte[] take(int commandType) {
        monitor.handle(command(commandType, 1));
        return take().get(0);
    }

    @Test
    void memoryAndRegisters() {
        h.machine.getDebugger().requestStop();
        loop(3);
        take();
        monitor.handle(command(0x02, 1, 0, 0x00, 0x10, 0x02, 0x10, 0, 0, 0, 0xA9, 0x01, 0x60));
        assertEquals(0x02, type(take().get(0)));
        monitor.handle(command(0x01, 1, 0, 0x00, 0x10, 0x02, 0x10, 0, 0, 0));
        assertEquals("03 00 a9 01 60", body(take().get(0)));
        // A=42, then PC=1000: the reply is the registers.
        monitor.handle(command(0x32, 1, 0, 2, 0, 3, 0, 0x42, 0, 3, 3, 0x00, 0x10));
        String registers = body(take().get(0));
        assertTrue(registers.startsWith("0a 00 03 03 00 10 03 00 42 00"), registers);
        monitor.handle(command(0x31, 1, 0));
        assertEquals(registers, body(take().get(0)));
    }

    @Test
    void checkpointsAndEventsMatchVice() {
        h.machine.getDebugger().requestStop();
        loop(3);
        take();
        // Set an exec checkpoint at $E001 (the synthetic KERNAL's loop).
        monitor.handle(command(0x12, 1, 0x01, 0xE0, 0x01, 0xE0, 1, 1, 4, 0));
        assertEquals("01 00 00 00 00 01 e0 01 e0 01 01 04 00 00 00 00 00 00 00 00 00 00 00", body(take().get(0)));
        monitor.handle(command(0x14, 2));
        List<byte[]> list = take();
        assertEquals(2, list.size());
        assertEquals("01 00 00 00", body(list.get(1)));
        // Exit: the reply, then resumed; on the hit, checkpoint, registers and stopped.
        monitor.handle(command(0xAA, 3));
        List<byte[]> exit = take();
        assertEquals(0xAA, type(exit.get(0)));
        assertEquals(0x63, type(exit.get(1)));
        loop(3);
        List<byte[]> hit = take();
        assertEquals(3, hit.size());
        assertEquals(0x11, type(hit.get(0)));
        assertEquals("01 00 00 00 01 01 e0 01 e0 01 01 04 00 01 00 00 00 00 00 00 00 00 00", body(hit.get(0)));
        assertEquals(0x31, type(hit.get(1)));
        assertEquals("01 e0", body(hit.get(2)));
        // Toggle, condition, delete.
        monitor.handle(command(0x15, 4, 1, 0, 0, 0, 0));
        assertEquals(0x15, type(take().get(0)));
        int[] condition = { 1, 0, 0, 0, 6, 'A', ' ', '=', '=', ' ', '0' };
        monitor.handle(command(0x22, 5, condition));
        assertEquals(0x22, type(take().get(0)));
        monitor.handle(command(0x13, 6, 1, 0, 0, 0));
        assertEquals(0x13, type(take().get(0)));
    }

    @Test
    void advanceStepsAndReportsTheStop() {
        h.machine.getDebugger().requestStop();
        loop(3);
        take();
        monitor.handle(command(0x71, 1, 0, 2, 0));
        List<byte[]> packets = take();
        assertEquals(0x71, type(packets.get(0)));
        assertEquals(0x63, type(packets.get(1)));
        loop(3);
        packets = take();
        assertEquals(0x31, type(packets.get(0)));
        assertEquals(0x62, type(packets.get(1)));
    }

    @Test
    void dumpUndumpResourcesAndQuit() {
        h.machine.getDebugger().requestStop();
        loop(3);
        take();
        byte[] name = "s.jvs".getBytes(StandardCharsets.ISO_8859_1);
        int[] dump = new int[3 + name.length];
        dump[2] = name.length;
        for (int i = 0; i < name.length; i++) {
            dump[3 + i] = name[i];
        }
        monitor.handle(command(0x41, 1, dump));
        assertEquals(0x41, type(take().get(0)));
        int[] undump = new int[1 + name.length];
        undump[0] = name.length;
        System.arraycopy(dump, 3, undump, 1, name.length);
        monitor.handle(command(0x42, 2, undump));
        byte[] undumped = take().get(0);
        assertEquals(0x42, type(undumped));
        assertEquals(2, undumped.length - 12);

        byte[] resource = "MachineVideoStandard".getBytes(StandardCharsets.ISO_8859_1);
        int[] get = new int[1 + resource.length];
        get[0] = resource.length;
        for (int i = 0; i < resource.length; i++) {
            get[1 + i] = resource[i];
        }
        monitor.handle(command(0x51, 3, get));
        assertEquals("01 04 01 00 00 00", body(take().get(0)));

        monitor.handle(command(0xBB, 4));
        assertEquals(0xBB, type(take().get(0)));
        assertEquals(1, quits);
    }
}
