package emu.jvic.monitor;

/**
 * The memory commands implemented so far: > and mem. Output matches VICE 3.10's,
 * except for the character column (see {@link Mem}). The syntax lines are VICE's;
 * the help descriptions are jvic's own.
 */
final class MemoryCommands {

    /** The number of instructions that disass shows when given no end address, as VICE does. */
    static final int DEFAULT_DISASS_LINES = 49;

    /** The number of bytes per line of the i and ii commands, as VICE does. */
    static final int TEXT_BYTES_PER_LINE = 64;

    private MemoryCommands() {
    }

    /** A range of addresses in one memspace, as given to c, f, h and t. */
    static final class Range {
        final AddressSpace space;
        final int start;
        final int count;

        Range(AddressSpace space, int start, int count) {
            this.space = space;
            this.start = start;
            this.count = count;
        }
    }

    /**
     * Reads a "start end" range, both required. As in VICE, a missing part is a
     * Wrong syntax error at that point.
     */
    static Range parseRange(Parser args, MonitorEngine engine) {
        if (args.atEnd()) {
            throw args.wrongSyntax();
        }
        int position = args.position();
        int start = args.parseAddress();
        AddressSpace space = engine.getSpace(args.getLastMemspace(), position);
        if (args.atEnd()) {
            throw args.wrongSyntax();
        }
        int end = args.parseAddress();
        return new Range(space, start, ((end - start) & 0xFFFF) + 1);
    }

    /**
     * Reads a destination address for c and t. Its memspace is its own prefix, or
     * the default memspace, and as in VICE it must be the source's memspace.
     *
     * @return The address, or -1 (after printing VICE's message) if the memspaces differ.
     */
    static int parseDestination(Parser args, MonitorEngine engine, Range range, MonitorOutput out) {
        if (args.atEnd()) {
            throw args.wrongSyntax();
        }
        int position = args.position();
        int destination = args.parseAddress();
        AddressSpace space = engine.getSpace(args.getLastMemspace(), position);
        args.expectEnd();
        if (space != range.space) {
            out.print("Invalid range.\n");
            return -1;
        }
        return destination;
    }

    /**
     * Reads a data list of numbers and "strings" to the end of the line. With
     * wildcards allowed (hunt), "xx" matches any byte and is returned as -1.
     */
    static int[] parseDataList(Parser args, boolean wildcards) {
        if (args.atEnd()) {
            throw args.wrongSyntax();
        }
        java.util.List<Integer> data = new java.util.ArrayList<>();
        while (!args.atEnd()) {
            String text = args.tryString();
            if (text != null) {
                for (int i = 0; i < text.length(); i++) {
                    data.add(text.charAt(i) & 0xFF);
                }
                continue;
            }
            if (wildcards) {
                int position = args.position();
                if (args.readWord().equalsIgnoreCase("xx")) {
                    data.add(-1);
                    continue;
                }
                args.rewind(position);
            }
            if (args.atNumber() || (args.peek() == '(')) {
                data.add(args.parseExpression() & 0xFF);
            } else {
                throw args.unexpected();
            }
        }
        int[] result = new int[data.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = data.get(i);
        }
        return result;
    }

    /**
     * Reads the optional "[start [end]]" of the display commands (m, d, i, ii, mc).
     * Returns {space, start, end} with end -1 if not given; with no addresses, the
     * start is the default memspace's dot address.
     */
    static Object[] parseOptionalRange(Parser args, MonitorEngine engine) {
        if (args.atEnd()) {
            AddressSpace space = engine.getDefaultSpace();
            return new Object[] { space, space.getDot(), -1 };
        }
        int position = args.position();
        int start = args.parseAddress();
        AddressSpace space = engine.getSpace(args.getLastMemspace(), position);
        int end = -1;
        if (!args.atEnd()) {
            end = args.parseAddress();
            args.expectEnd();
        }
        return new Object[] { space, start, end };
    }

    /** The disass (d) command. */
    static final class Disass extends MonitorCommand {

        Disass() {
            super("disass", "d", "disass [<address> [<address>]]",
                    "Disassembles instructions, from the start address to the end address.\n"
                    + "With only a start address, or none (continuing from the 'dot' address),\n"
                    + "shows a screenful of instructions. Undocumented opcodes use VICE's\n"
                    + "mnemonics.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            Object[] range = parseOptionalRange(args, engine);
            AddressSpace space = (AddressSpace) range[0];
            int address = (Integer) range[1];
            int end = (Integer) range[2];
            StringBuilder text = new StringBuilder();
            if (end < 0) {
                for (int i = 0; i < DEFAULT_DISASS_LINES; i++) {
                    address = (address + Disassembler6502.disassembleLine(space, address,
                            engine.isSideEffects(), text)) & 0xFFFF;
                }
            } else {
                // Instructions that start in the range, which may wrap past $FFFF.
                int remaining = ((end - address) & 0xFFFF) + 1;
                while (remaining > 0) {
                    int length = Disassembler6502.disassembleLine(space, address, engine.isSideEffects(), text);
                    address = (address + length) & 0xFFFF;
                    remaining -= length;
                }
            }
            out.print(text.toString());
            space.setDot(address);
        }
    }

    /** The compare (c) command. */
    static final class Compare extends MonitorCommand {

        Compare() {
            super("compare", "c", "compare <address_range> <address>",
                    "Compares the memory in the range with the memory at the address, and\n"
                    + "lists the bytes that differ.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            Range range = parseRange(args, engine);
            int destination = parseDestination(args, engine, range, out);
            if (destination < 0) {
                return;
            }
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < range.count; i++) {
                int a = (range.start + i) & 0xFFFF;
                int b = (destination + i) & 0xFFFF;
                int valueA = range.space.read(a, engine.isSideEffects());
                int valueB = range.space.read(b, engine.isSideEffects());
                if (valueA != valueB) {
                    text.append('$').append(Format.hex4(a)).append(" $").append(Format.hex4(b)).append(": ")
                            .append(Format.hex2(valueA)).append(' ').append(Format.hex2(valueB)).append('\n');
                }
            }
            out.print(text.toString());
        }
    }

    /** The fill (f) command. */
    static final class Fill extends MonitorCommand {

        Fill() {
            super("fill", "f", "fill <address_range> <data_list>",
                    "Fills the range with the data, repeating the data as often as needed.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            Range range = parseRange(args, engine);
            int[] data = parseDataList(args, false);
            for (int i = 0; i < range.count; i++) {
                range.space.write(range.start + i, data[i % data.length]);
            }
        }
    }

    /** The hunt (h) command. */
    static final class Hunt extends MonitorCommand {

        Hunt() {
            super("hunt", "h", "hunt <address_range> <data_list>",
                    "Searches the range for the data, and lists the addresses where it is\n"
                    + "found. 'xx' in the data matches any byte.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            Range range = parseRange(args, engine);
            int[] data = parseDataList(args, true);
            StringBuilder text = new StringBuilder();
            for (int i = 0; i <= range.count - data.length; i++) {
                boolean match = true;
                for (int j = 0; (j < data.length) && match; j++) {
                    int value = range.space.read(range.start + i + j, engine.isSideEffects());
                    match = (data[j] < 0) || (data[j] == value);
                }
                if (match) {
                    text.append(Format.hex4(range.start + i)).append('\n');
                }
            }
            out.print(text.toString());
        }
    }

    /** The move (t) command. */
    static final class Move extends MonitorCommand {

        Move() {
            super("move", "t", "move <address_range> <address>",
                    "Copies the memory in the range to the address. Overlapping ranges are\n"
                    + "copied correctly.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            Range range = parseRange(args, engine);
            int destination = parseDestination(args, engine, range, out);
            if (destination < 0) {
                return;
            }
            int[] buffer = new int[range.count];
            for (int i = 0; i < range.count; i++) {
                buffer[i] = range.space.read(range.start + i, engine.isSideEffects());
            }
            for (int i = 0; i < range.count; i++) {
                range.space.write(destination + i, buffer[i]);
            }
        }
    }

    /**
     * The i and ii commands: show memory as text, PETSCII (i) or screen codes (ii).
     *
     * <p>The layout is VICE's: ">C:addr  " (or "*C:addr  " for ii) and 64 characters
     * per line. VICE writes the raw bytes, without converting them; jvic converts them
     * to readable ASCII, showing anything without an ASCII equivalent as '.'.
     */
    static final class Text extends MonitorCommand {

        private final boolean screenCodes;

        Text(boolean screenCodes) {
            super(screenCodes ? "ii" : "i", null, (screenCodes ? "ii" : "i") + " <address_opt_range>",
                    screenCodes
                            ? "Shows memory as text, reading the bytes as screen codes."
                            : "Shows memory as text, reading the bytes as PETSCII.");
            this.screenCodes = screenCodes;
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            Object[] range = parseOptionalRange(args, engine);
            AddressSpace space = (AddressSpace) range[0];
            int address = (Integer) range[1];
            int end = (Integer) range[2];
            int count = (end < 0) ? (TEXT_BYTES_PER_LINE * MonitorEngine.DEFAULT_MEM_LINES)
                    : ((end - address) & 0xFFFF) + 1;
            StringBuilder text = new StringBuilder();
            while (count > 0) {
                int lineCount = Math.min(TEXT_BYTES_PER_LINE, count);
                StringBuilder chars = new StringBuilder();
                for (int i = 0; i < lineCount; i++) {
                    int value = space.read((address + i) & 0xFFFF, engine.isSideEffects());
                    chars.append(screenCodes ? screenCodeToAscii(value) : petsciiToAscii(value));
                }
                text.append(screenCodes ? '*' : '>').append(space.getName()).append(':')
                        .append(Format.hex4(address)).append("  ")
                        .append(Format.padRight(chars.toString(), TEXT_BYTES_PER_LINE)).append('\n');
                address = (address + lineCount) & 0xFFFF;
                count -= lineCount;
            }
            out.print(text.toString());
            space.setDot(address);
        }

        /** PETSCII to ASCII, for the unshifted (upper case and graphics) character set. */
        static char petsciiToAscii(int value) {
            if ((value >= 0x20) && (value < 0x5B)) {
                return (char) value;
            }
            switch (value) {
                case 0x5B: return '[';
                case 0x5C: return '\\'; // Pound sign.
                case 0x5D: return ']';
                case 0x5E: return '^';  // Up arrow.
                case 0x5F: return '_';  // Left arrow.
                case 0xA0: return ' ';  // Shifted space.
                default: return '.';
            }
        }

        /** Screen codes to ASCII. Reversed characters (bit 7 set) show as normal ones. */
        static char screenCodeToAscii(int value) {
            int code = value & 0x7F;
            if (code == 0x00) {
                return '@';
            }
            if (code < 0x1B) {
                return (char) ('A' + code - 1);
            }
            if (code < 0x20) {
                return "[\\]^_".charAt(code - 0x1B);
            }
            if (code < 0x40) {
                return (char) code;
            }
            return '.';
        }
    }

    /**
     * The memchar (mc) command: shows memory as 8x8 character bitmaps, one byte per
     * line, with a blank line after each 8 bytes.
     */
    static final class MemChar extends MonitorCommand {

        MemChar() {
            super("memchar", "mc", "memchar [<data_type>] [<address_opt_range>]",
                    "Shows memory as character bitmaps: one byte per line, with '#' for set\n"
                    + "bits, and a blank line after each 8 bytes. With one address or none,\n"
                    + "shows one character.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            // VICE accepts no data type letter here, despite the syntax line.
            char c = args.peek();
            if ((c == 'H') || (c == 'D') || (c == 'O') || (c == 'B')) {
                int position = args.position();
                if (args.readWord().length() == 1) {
                    throw new MonitorException(MonitorException.UNEXPECTED_TOKEN, position);
                }
                args.rewind(position);
            }
            Object[] range = parseOptionalRange(args, engine);
            AddressSpace space = (AddressSpace) range[0];
            int address = (Integer) range[1];
            int end = (Integer) range[2];
            int count = (end < 0) ? 8 : ((end - address) & 0xFFFF) + 1;
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < count; i++) {
                int value = space.read(address, engine.isSideEffects());
                text.append('>').append(space.getName()).append(':').append(Format.hex4(address)).append(' ');
                for (int bit = 7; bit >= 0; bit--) {
                    text.append(((value >> bit) & 1) != 0 ? '#' : '.');
                }
                text.append(' ').append(Format.hex2(value)).append('\n');
                if (((i + 1) % 8 == 0) || (i == count - 1)) {
                    text.append('\n');
                }
                address = (address + 1) & 0xFFFF;
            }
            out.print(text.toString());
            space.setDot(address);
        }
    }

    /** The > command: writes bytes to memory. */
    static final class Write extends MonitorCommand {

        Write() {
            super(">", null, "> [<address>] <data_list>",
                    "Writes the data to memory, starting at the address. The data is a list\n"
                    + "of numbers and \"strings\", separated by spaces. Writes go through the\n"
                    + "CPU's memory map, so writes to ROM have no effect.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            if (args.atEnd()) {
                throw args.wrongSyntax();
            }
            int position = args.position();
            int address = args.parseAddress();
            AddressSpace space = engine.getSpace(args.getLastMemspace(), position);
            if (args.atEnd()) {
                throw args.wrongSyntax();
            }
            while (!args.atEnd()) {
                String text = args.tryString();
                if (text != null) {
                    for (int i = 0; i < text.length(); i++) {
                        space.write(address, text.charAt(i));
                        address = (address + 1) & 0xFFFF;
                    }
                } else if (args.atNumber() || (args.peek() == '(')) {
                    space.write(address, args.parseExpression());
                    address = (address + 1) & 0xFFFF;
                } else {
                    throw args.unexpected();
                }
            }
        }
    }

    /**
     * The mem command: shows memory in hex, decimal, octal or binary.
     *
     * <p>Layout, as VICE: ">C:addr ", then the values with an extra space before each
     * group of four, then two spaces and the bytes as characters. Short lines are
     * padded so the character column lines up. Hex shows 16 bytes per line, decimal and
     * octal 8, binary 4.
     *
     * <p>Unlike VICE, which writes the raw bytes into the character column (so control
     * codes reach the terminal, and its log drops zero bytes), jvic shows printable
     * ASCII as itself and anything else as '.', so the column is always one character
     * per byte and safe to display.
     */
    static final class Mem extends MonitorCommand {

        Mem() {
            super("mem", "m", "mem [<data_type>] [<address_opt_range>]",
                    "Shows the contents of memory. The data type is H (hex), D (decimal),\n"
                    + "O (octal) or B (binary), and defaults to the current radix. B and D\n"
                    + "must be in upper case, as b and d are hex digits. With one address,\n"
                    + "shows a screenful from there. With no address, continues from where\n"
                    + "the last mem command ended (the 'dot' address).");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            Radix type = null;
            char c = args.peek();
            if ((c == 'H') || (c == 'h') || (c == 'O') || (c == 'o') || (c == 'B') || (c == 'D')) {
                int position = args.position();
                String word = args.readWord();
                if (word.length() == 1) {
                    type = Radix.fromLetter(c);
                    if (args.atEnd()) {
                        throw args.unexpected();
                    }
                } else {
                    // Not a data type after all, e.g. "m BEEF". Read it as an address.
                    args.rewind(position);
                }
            }
            if (type == null) {
                type = engine.getRadix();
            }

            AddressSpace space;
            int start;
            int count;
            int perLine = perLine(type);
            if (args.atEnd()) {
                space = engine.getDefaultSpace();
                start = space.getDot();
                count = perLine * MonitorEngine.DEFAULT_MEM_LINES;
            } else {
                int position = args.position();
                start = args.parseAddress();
                space = engine.getSpace(args.getLastMemspace(), position);
                if (args.atEnd()) {
                    count = perLine * MonitorEngine.DEFAULT_MEM_LINES;
                } else {
                    int end = args.parseAddress();
                    args.expectEnd();
                    count = ((end - start) & 0xFFFF) + 1;
                }
            }

            out.print(display(space, type, start, count, engine.isSideEffects()));
            space.setDot(start + count);
        }

        static int perLine(Radix type) {
            switch (type) {
                case HEXADECIMAL: return 16;
                case BINARY: return 4;
                default: return 8;
            }
        }

        private static int itemWidth(Radix type) {
            switch (type) {
                case HEXADECIMAL: return 3;
                case BINARY: return 9;
                default: return 4;
            }
        }

        private static String item(Radix type, int value) {
            switch (type) {
                case HEXADECIMAL: return Format.hex2(value);
                case DECIMAL: return Format.decimal(value, 3);
                case OCTAL: return Format.octal(value, 3);
                default: return Format.binary8(value);
            }
        }

        static String display(AddressSpace space, Radix type, int start, int count, boolean sideEffects) {
            int perLine = perLine(type);
            int width = itemWidth(type);
            StringBuilder text = new StringBuilder();
            int address = start & 0xFFFF;
            int remaining = count;
            while (remaining > 0) {
                int lineCount = Math.min(perLine, remaining);
                StringBuilder chars = new StringBuilder(lineCount);
                text.append('>').append(space.getName()).append(':').append(Format.hex4(address)).append(' ');
                for (int i = 0; i < perLine; i++) {
                    if ((i % 4) == 0) {
                        text.append(' ');
                    }
                    if (i < lineCount) {
                        int value = space.read((address + i) & 0xFFFF, sideEffects);
                        text.append(item(type, value)).append(' ');
                        chars.append(((value >= 0x20) && (value < 0x7F)) ? (char) value : '.');
                    } else {
                        Format.spaces(text, width);
                    }
                }
                text.append("  ").append(chars).append('\n');
                address = (address + lineCount) & 0xFFFF;
                remaining -= lineCount;
            }
            return text.toString();
        }
    }
}
