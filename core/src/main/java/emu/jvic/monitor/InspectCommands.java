package emu.jvic.monitor;

import java.util.ArrayList;
import java.util.List;

import emu.jvic.Machine;
import emu.jvic.debug.WatchProxyChip;
import emu.jvic.io.Via6522;
import emu.jvic.memory.MemoryMappedChip;
import emu.jvic.memory.RamChip;
import emu.jvic.memory.RomChip;

/**
 * The phase 2 commands that inspect or control the machine: a, command, dummy, io,
 * screen, memsprite, backtrace, cpuhistory, keybuf, reset and export. Output matches
 * VICE 3.10's where the concept exists in jvic. The syntax lines are VICE's; the help
 * descriptions are jvic's own.
 */
final class InspectCommands {

    private InspectCommands() {
    }

    /** Reads on, off or toggle; returns the new setting. */
    static boolean parseOnOff(Parser args, boolean current) {
        int position = args.position();
        String word = args.readWord().toLowerCase();
        args.expectEnd();
        switch (word) {
            case "on": return true;
            case "off": return false;
            case "toggle": return !current;
            default: throw new MonitorException(MonitorException.UNEXPECTED_TOKEN, position);
        }
    }

    // ------------------------------------------------------------------------------
    // a
    // ------------------------------------------------------------------------------

    static final class Assemble extends MonitorCommand {

        Assemble() {
            super("a", null, "a <address> [ <instruction> [: <instruction>]* ]",
                    "Assembles instructions, separated by `:', starting at the address. With\n"
                    + "one instruction or none, enters assembly mode: each line is then an\n"
                    + "instruction for the next address, until an empty line. Labels can be\n"
                    + "used as operands; a lone `A' is the accumulator.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            if (args.atEnd()) {
                throw args.wrongSyntax();
            }
            int position = args.position();
            int address = args.parseAddress();
            AddressSpace space = engine.getSpace(args.getLastMemspace(), position);
            space.setDot(address);
            args.skipSpaces();
            int column = args.position();
            String instructions = args.getLine().substring(column).trim();
            if (instructions.isEmpty()) {
                engine.startAssemblyMode(space);
                return;
            }
            engine.assemble(space, instructions, column, out);
            if (instructions.indexOf(':') < 0) {
                // As in VICE, one instruction (even an invalid one) enters assembly mode.
                engine.startAssemblyMode(space);
            }
        }
    }

    // ------------------------------------------------------------------------------
    // command and dummy
    // ------------------------------------------------------------------------------

    static final class Command extends MonitorCommand {

        Command() {
            super("command", null, "command <checknum> \"<Command>\"",
                    "Sets a monitor command to run when the checkpoint is hit. For a\n"
                    + "breakpoint or watchpoint, it runs once the machine has stopped (so `x'\n"
                    + "resumes it); for a tracepoint, it runs straight away.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            int number = CheckpointCommands.parseCheckpointNumber(args);
            String command = FileCommands.parseFilename(args);
            args.expectEnd();
            Checkpoint checkpoint = engine.getCheckpoints().find(number);
            if (checkpoint == null) {
                out.print("#" + number + " not a valid checkpoint\n");
                return;
            }
            checkpoint.setCommand(command);
            out.print("Setting checkpoint " + number + " command to: " + command + "\n");
        }
    }

    static final class Dummy extends MonitorCommand {

        Dummy() {
            super("dummy", null, "dummy [on|off|toggle]",
                    "Sets whether watchpoints trigger on the CPU's dummy accesses: reads\n"
                    + "whose value is thrown away, and the extra write of read-modify-write\n"
                    + "instructions. The default is off. With no argument, shows the setting.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            if (args.atEnd()) {
                out.print("Checkpoints will " + (engine.isDummyAccesses() ? "" : "not ")
                        + "trigger on dummy accesses.\n");
                return;
            }
            engine.setDummyAccesses(parseOnOff(args, engine.isDummyAccesses()));
        }
    }

    // ------------------------------------------------------------------------------
    // io
    // ------------------------------------------------------------------------------

    static final class Io extends MonitorCommand {

        Io() {
            super("io", null, "io <address>",
                    "With no address, shows the registers of the I/O chips: the VIC and both\n"
                    + "VIAs (or the drive's two VIAs on 8:). With the address of a chip, also\n"
                    + "decodes its state.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            AddressSpace space = engine.getDefaultSpace();
            int address = -1;
            if (!args.atEnd()) {
                int position = args.position();
                address = args.parseAddress();
                space = engine.getSpace(args.getLastMemspace(), position);
                args.expectEnd();
            }
            boolean drive = (space == engine.getDriveSpace());
            Machine machine = engine.getMachine();
            StringBuilder text = new StringBuilder();
            if (drive) {
                Via6522 via1 = machine.getC1541Drive().getVia1();
                Via6522 via2 = machine.getC1541Drive().getVia2();
                if (address < 0) {
                    chip(text, space, "VIA1", 0x1800, null, null);
                    chip(text, space, "VIA2", 0x1C00, null, null);
                } else if ((address >= 0x1800) && (address < 0x1C00)) {
                    chip(text, space, "VIA1", 0x1800, via1, null);
                } else if ((address >= 0x1C00) && (address < 0x2000)) {
                    chip(text, space, "VIA2", 0x1C00, via2, null);
                }
            } else if (address < 0) {
                chip(text, space, "VIC", 0x9000, null, null);
                chip(text, space, "VIA1", 0x9110, null, null);
                chip(text, space, "VIA2", 0x9120, null, null);
            } else if ((address >= 0x9000) && (address < 0x9100)) {
                chip(text, space, "VIC", 0x9000, null, engine);
            } else if ((address >= 0x9110) && (address < 0x9120)) {
                chip(text, space, "VIA1", 0x9110, machine.getVia1(), null);
            } else if ((address >= 0x9120) && (address < 0x9130)) {
                chip(text, space, "VIA2", 0x9120, machine.getVia2(), null);
            }
            out.print(text.toString());
        }

        /**
         * One chip: its name, its 16 registers as mem shows them, and, if a VIA or the
         * engine (for the VIC) is given, a blank line and its decoded state.
         */
        private static void chip(StringBuilder text, AddressSpace space, String name, int base, Via6522 via,
                MonitorEngine vicEngine) {
            text.append(name).append(":\n");
            text.append(MemoryCommands.Mem.display(space, Radix.HEXADECIMAL, base, 16, false));
            if (via != null) {
                text.append('\n').append(describeVia(space, base, via));
            } else if (vicEngine != null) {
                text.append('\n').append(describeVic(space, vicEngine));
            }
        }

        /** VICE's VIA layout, without VICE's internal alarm details. */
        static String describeVia(AddressSpace space, int base, Via6522 via) {
            int[] r = new int[16];
            for (int i = 0; i < 16; i++) {
                r[i] = space.read(base + i, false);
            }
            StringBuilder text = new StringBuilder();
            text.append("Port A: ").append(Format.hex2(r[1])).append(" DDR: ").append(Format.hex2(r[3]))
                    .append(" no HS: ").append(Format.hex2(r[15])).append('\n');
            text.append("Port B: ").append(Format.hex2(r[0])).append(" DDR: ").append(Format.hex2(r[2])).append('\n');
            text.append("Timer 1: ").append(Format.hex4(r[4] | (r[5] << 8))).append(" Latch: ")
                    .append(Format.hex4(r[6] | (r[7] << 8))).append('\n');
            text.append("Timer 2: ").append(Format.hex4(r[8] | (r[9] << 8))).append(" Latch: ")
                    .append(Format.pad(Integer.toHexString(via.getTimer2Latch() & 0xFF), 4, ' ')).append('\n');
            text.append("Aux. control: ").append(Format.hex2(r[11])).append('\n');
            text.append("Per. control: ").append(Format.hex2(r[12])).append('\n');
            text.append("IRQ flags: ").append(Format.hex2(r[13])).append('\n');
            text.append("IRQ enable: ").append(Format.hex2(r[14])).append('\n');
            text.append('\n');
            text.append("Shift Register: ").append(Format.hex2(r[10])).append('\n');
            return text.toString();
        }

        /** The VIC's state, decoded from its registers (jvic's own layout). */
        static String describeVic(AddressSpace space, MonitorEngine engine) {
            int[] r = new int[16];
            for (int i = 0; i < 16; i++) {
                r[i] = space.read(0x9000 + i, false);
            }
            StringBuilder text = new StringBuilder();
            text.append("Raster cycle/line: ").append(space.getVic().getRasterCycle()).append('/')
                    .append(space.getVic().getRasterLine()).append('\n');
            text.append("Matrix: $").append(Format.hex4(matrixAddress(r))).append(", Char: $")
                    .append(Format.hex4(cpuAddress((r[5] & 0x0F) << 10))).append(", Colour: $")
                    .append(((r[2] & 0x80) != 0) ? "9600" : "9400").append('\n');
            text.append("Size: ").append(r[2] & 0x7F).append(" columns x ").append((r[3] >> 1) & 0x3F)
                    .append(" rows, char height ").append(((r[3] & 1) != 0) ? 16 : 8).append('\n');
            text.append("Origin: X ").append(r[0] & 0x7F).append(", Y ").append(r[1])
                    .append(((r[0] & 0x80) != 0) ? ", interlace" : "").append('\n');
            text.append("Colours: screen ").append(r[15] >> 4).append(", border ").append(r[15] & 7)
                    .append(", auxiliary ").append(r[14] >> 4).append(((r[15] & 8) == 0) ? ", reversed" : "")
                    .append('\n');
            text.append("Sound: ").append(Format.hex2(r[10])).append(' ').append(Format.hex2(r[11])).append(' ')
                    .append(Format.hex2(r[12])).append(' ').append(Format.hex2(r[13])).append(", volume ")
                    .append(r[14] & 0x0F).append('\n');
            return text.toString();
        }
    }

    /** Converts a 14-bit VIC address to the CPU address the VIC sees there. */
    static int cpuAddress(int vicAddress) {
        return ((vicAddress & 0x2000) != 0) ? (vicAddress & 0x1FFF) : (0x8000 | vicAddress);
    }

    /** The CPU address of the screen matrix, from VIC registers 0-15. */
    static int matrixAddress(int[] vicRegisters) {
        return cpuAddress(((vicRegisters[5] & 0xF0) << 6) | ((vicRegisters[2] & 0x80) << 2));
    }

    // ------------------------------------------------------------------------------
    // screen and memsprite
    // ------------------------------------------------------------------------------

    static final class Screen extends MonitorCommand {

        Screen() {
            super("screen", "sc", "screen ",
                    "Shows the text on the screen, using the screen size and address from the\n"
                    + "VIC's registers.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            args.expectEnd();
            AddressSpace space = engine.getMainSpace();
            int[] r = new int[16];
            for (int i = 0; i < 16; i++) {
                r[i] = space.read(0x9000 + i, false);
            }
            int columns = r[2] & 0x7F;
            int rows = (r[3] >> 1) & 0x3F;
            int matrix = matrixAddress(r);
            StringBuilder text = new StringBuilder();
            text.append("Displaying ").append(columns).append('x').append(rows).append(" screen at $")
                    .append(Format.hex4(matrix)).append(":\n");
            for (int row = 0; (columns > 0) && (row < rows); row++) {
                int address = (matrix + (row * columns)) & 0xFFFF;
                text.append("*C:").append(Format.hex4(address)).append("  ");
                for (int column = 0; column < columns; column++) {
                    char c = MemoryCommands.Text.screenCodeToAscii(space.read(address + column, false));
                    text.append(Character.toLowerCase(c));
                }
                text.append('\n');
            }
            out.print(text.toString());
        }
    }

    static final class MemSprite extends MonitorCommand {

        static final int SPRITE_BYTES = 63;

        MemSprite() {
            super("memsprite", "ms", "memsprite [<data_type>] [<address_opt_range>]",
                    "Shows memory as 24x21 sprites, one row of three bytes per line. The\n"
                    + "VIC-20 has no sprites, but the layout suits other 24-pixel-wide data.\n"
                    + "With one address, shows one sprite.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            Object[] range = MemoryCommands.parseOptionalRange(args, engine);
            AddressSpace space = (AddressSpace) range[0];
            int start = (Integer) range[1];
            int end = (Integer) range[2];
            int count = (end < 0) ? SPRITE_BYTES : ((end - start) & 0xFFFF) + 1;
            int sprites = (count + SPRITE_BYTES - 1) / SPRITE_BYTES;
            StringBuilder text = new StringBuilder();
            int address = start & 0xFFFF;
            for (int sprite = 0; sprite < sprites; sprite++) {
                for (int row = 0; row < 21; row++) {
                    int[] b = new int[3];
                    for (int i = 0; i < 3; i++) {
                        b[i] = space.read(address + i, engine.isSideEffects());
                    }
                    text.append('>').append(space.getName()).append(':').append(Format.hex4(address)).append(' ');
                    for (int i = 0; i < 3; i++) {
                        for (int bit = 7; bit >= 0; bit--) {
                            text.append(((b[i] >> bit) & 1) != 0 ? '#' : '.');
                        }
                    }
                    text.append(' ').append(Format.hex2(b[0])).append(' ').append(Format.hex2(b[1])).append(' ')
                            .append(Format.hex2(b[2])).append('\n');
                    address = (address + 3) & 0xFFFF;
                }
                text.append('\n');
            }
            out.print(text.toString());
            space.setDot(address);
        }
    }

    // ------------------------------------------------------------------------------
    // backtrace and cpuhistory
    // ------------------------------------------------------------------------------

    static final class Backtrace extends MonitorCommand {

        Backtrace() {
            super("backtrace", "bt", "backtrace ",
                    "Shows the chain of JSR calls, most recent first, found by looking for\n"
                    + "return addresses on the stack. The offset of each from SP+1 is in\n"
                    + "brackets. This is a best guess: other data on the stack can look like a\n"
                    + "return address.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            args.expectEnd();
            AddressSpace space = engine.getDefaultSpace();
            StringBuilder text = new StringBuilder();
            text.append("             PC        ").append(Disassembler6502.backtraceLine(space, space.getDisplayPc()));
            int sp = space.getCpu().getStackPointer();
            for (int i = sp + 1; i < 0xFF;) {
                int lo = space.read(0x100 + i, false);
                int hi = space.read(0x100 + i + 1, false);
                int jsr = (((hi << 8) | lo) - 2) & 0xFFFF;
                if (space.read(jsr, false) == 0x20) {
                    int target = space.read(jsr + 1, false) | (space.read(jsr + 2, false) << 8);
                    text.append(Format.hex4(jsr)).append(" -> ").append(Format.hex4(target)).append(" [SP +")
                            .append(Format.decimal(i - sp, 3)).append("] ")
                            .append(Disassembler6502.backtraceLine(space, jsr));
                    i += 2;
                } else {
                    i++;
                }
            }
            out.print(text.toString());
        }
    }

    static final class CpuHistoryCommand extends MonitorCommand {

        /** How many instructions chis shows when not given a count: all, as in xvic. */
        static final int DEFAULT_COUNT = CpuHistory.SIZE;

        CpuHistoryCommand() {
            super("cpuhistory", "chis", "cpuhistory [<count>] [c:] [8:] [9:] [10:] [11:]",
                    "Shows the last instructions executed, oldest first, with the registers as\n"
                    + "each started and the CPU cycle. With several memspaces, merges them in\n"
                    + "cycle order. jvic records history only while the instruction hook is on:\n"
                    + "`chis on' turns recording on (it slows emulation slightly), `chis off'\n"
                    + "turns it off. Checkpoints and stepping also record it.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            int count = DEFAULT_COUNT;
            List<AddressSpace> spaces = new ArrayList<>();
            if (!args.atEnd() && Character.isLetter(args.peek())) {
                int position = args.position();
                if (args.tryMemspace() == null) {
                    engine.setHistoryEnabled(parseOnOff(args, engine.isHistoryEnabled()));
                    return;
                }
                args.rewind(position);
            }
            while (!args.atEnd()) {
                int position = args.position();
                String memspace = args.tryMemspace();
                if (memspace != null) {
                    spaces.add(engine.getSpace(memspace, position));
                } else {
                    count = args.parseExpression();
                }
            }
            if (spaces.isEmpty()) {
                spaces.add(engine.getDefaultSpace());
            }
            // Take each memspace's last entries, then merge them by cycle.
            List<long[]> entries = new ArrayList<>();
            for (int s = 0; s < spaces.size(); s++) {
                CpuHistory history = spaces.get(s).getHistory();
                if (history == null) {
                    continue;
                }
                for (int n = Math.max(0, history.size() - count); n < history.size(); n++) {
                    entries.add(new long[] { history.getCycle(n), s, n });
                }
            }
            if (entries.isEmpty() && !engine.isHistoryEnabled()) {
                out.print("No CPU history has been recorded. Use `chis on' to record it.\n");
                return;
            }
            entries.sort((a, b) -> Long.compare(a[0], b[0]));
            StringBuilder text = new StringBuilder();
            for (int i = Math.max(0, entries.size() - count); i < entries.size(); i++) {
                AddressSpace space = spaces.get((int) entries.get(i)[1]);
                text.append(space.getHistory().line(space, (int) entries.get(i)[2]));
            }
            out.print(text.toString());
        }
    }

    // ------------------------------------------------------------------------------
    // keybuf, reset and export
    // ------------------------------------------------------------------------------

    static final class Keybuf extends MonitorCommand {

        Keybuf() {
            super("keybuf", null, "keybuf <string>",
                    "Types the string into the KERNAL's keyboard buffer, ten characters at a\n"
                    + "time as the buffer empties. Lower case letters type unshifted letters.\n"
                    + "\\n or \\x0d is RETURN; \\xNN is any PETSCII code.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            String text = args.tryString();
            if (text == null) {
                if (args.atEnd()) {
                    throw args.wrongSyntax();
                }
                text = args.readRest();
            }
            args.expectEnd();
            engine.queueKeys(toPetscii(text));
        }

        /** Converts text with escapes to PETSCII, as VICE's keybuf does. */
        static String toPetscii(String text) {
            StringBuilder petscii = new StringBuilder();
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if ((c == '\\') && (i + 1 < text.length())) {
                    char next = text.charAt(i + 1);
                    if ((next == 'x') && isHex(text, i + 2)) {
                        petscii.append((char) Integer.parseInt(text.substring(i + 2, i + 4), 16));
                        i += 3;
                        continue;
                    }
                    if ((next == 'n') || (next == 'r')) {
                        petscii.append((char) 0x0D);
                        i++;
                        continue;
                    }
                    if (next == '\\') {
                        petscii.append('\\');
                        i++;
                        continue;
                    }
                }
                if ((c >= 'a') && (c <= 'z')) {
                    petscii.append((char) (c - 0x20));
                } else if ((c >= 'A') && (c <= 'Z')) {
                    petscii.append((char) (c + 0x80));
                } else if (c == '\n') {
                    petscii.append((char) 0x0D);
                } else {
                    petscii.append((char) (c & 0xFF));
                }
            }
            return petscii.toString();
        }

        private static boolean isHex(String text, int start) {
            return (start + 2 <= text.length()) && (Radix.digitValue(text.charAt(start), 16) >= 0)
                    && (Radix.digitValue(text.charAt(start + 1), 16) >= 0);
        }
    }

    static final class Reset extends MonitorCommand {

        Reset() {
            super("reset", null, "reset [<Type>]",
                    "Resets the machine: 0 (the default) as the reset line does, resetting\n"
                    + "the CPU, the VIAs and the drive; 1 turns it off and on, clearing RAM;\n"
                    + "8 resets only the drive. After 0 or 1, the monitor stops at the first\n"
                    + "instruction of the reset routine.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            int type = 0;
            if (!args.atEnd()) {
                type = args.parseExpression();
                args.expectEnd();
            }
            Machine machine = engine.getMachine();
            switch (type) {
                case 0:
                case 1:
                    if (type == 0) {
                        machine.reset();
                    } else {
                        machine.powerCycle();
                    }
                    // Run on to the reset routine's first instruction and stop there.
                    engine.resume();
                    engine.getDebugger().requestStop();
                    break;
                case 8:
                    machine.getC1541Drive().reset(false);
                    break;
                case 9:
                case 10:
                case 11:
                    out.print("Drive " + type + ": is not emulated in jvic.\n");
                    break;
                default:
                    out.print("Unknown reset type " + type + ".\n");
                    break;
            }
        }
    }

    static final class Export extends MonitorCommand {

        /** The expansion areas: start, end, and a description. */
        private static final Object[][] AREAS = {
            { 0x0400, 0x0FFF, "3K expansion" },
            { 0x2000, 0x3FFF, "block 1" },
            { 0x4000, 0x5FFF, "block 2" },
            { 0x6000, 0x7FFF, "block 3" },
            { 0xA000, 0xBFFF, "block 5" },
        };

        Export() {
            super("export", "exp", "export ",
                    "Shows what is in the VIC-20's expansion areas: RAM expansions and\n"
                    + "cartridge ROM. (VICE lists expansion port devices; jvic's VIC-20 has\n"
                    + "only memory there.)");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            args.expectEnd();
            MemoryMappedChip[] map = engine.getMachine().getMemory().getMemoryMap();
            StringBuilder text = new StringBuilder();
            for (Object[] area : AREAS) {
                int start = (Integer) area[0];
                MemoryMappedChip chip = map[start];
                if (chip instanceof WatchProxyChip) {
                    chip = ((WatchProxyChip) chip).getTarget();
                }
                String kind = (chip instanceof RamChip) ? "RAM" : (chip instanceof RomChip) ? "ROM" : null;
                if (kind != null) {
                    text.append(kind).append(": $").append(Format.hex4(start)).append("-$")
                            .append(Format.hex4((Integer) area[1])).append(" (").append(area[2]).append(")\n");
                }
            }
            out.print((text.length() == 0) ? "No expansion port devices.\n" : text.toString());
        }
    }
}
