package emu.jvic.monitor;

/**
 * The memory commands implemented so far: > and mem. Output matches VICE 3.10's,
 * except for the character column (see {@link Mem}). The syntax lines are VICE's;
 * the help descriptions are jvic's own.
 */
final class MemoryCommands {

    private MemoryCommands() {
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
