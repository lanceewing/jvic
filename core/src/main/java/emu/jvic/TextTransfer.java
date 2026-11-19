package emu.jvic;

import emu.jvic.debug.WatchProxyChip;
import emu.jvic.memory.MemoryMappedChip;

/**
 * Copying the VIC-20's text screen to the host clipboard (Alt+Delete) and typing the
 * host clipboard into the VIC-20 (Alt+Insert), as VICE's edit-copy and edit-paste do.
 *
 * <p>Both follow the VIC's current character set. In the upper case/graphics set the
 * screen can't show lower case letters, so pasted letters of either case type as
 * plain letters, and copied letters come out in upper case. In the lower case set,
 * the case is kept both ways. That way, a listing pasted in from the host types in
 * as letters rather than graphics characters.
 *
 * <p>The static methods must be called on the thread that runs the machine. A
 * TextTransfer instance holds the text still to be typed, which
 * {@link #feed(int[])} moves into the KERNAL's keyboard buffer as it empties.
 */
public final class TextTransfer {

    /** The most characters a paste takes; the rest of a huge clipboard is ignored. */
    public static final int MAX_PASTE_LENGTH = 65536;

    /** The KERNAL's keyboard buffer, its count, and its size. */
    private static final int KEYBOARD_BUFFER = 0x0277;
    private static final int KEYBOARD_BUFFER_COUNT = 0xC6;
    private static final int KEYBOARD_BUFFER_SIZE = 10;

    /** PETSCII characters still to be typed. */
    private final StringBuilder queue = new StringBuilder();

    /**
     * Adds PETSCII characters to those still to be typed.
     *
     * @param petscii The characters, as from {@link #toPetscii(String, boolean)}.
     */
    public void queue(String petscii) {
        queue.append(petscii);
    }

    /** Drops any characters not yet typed, e.g. after a reset. */
    public void clear() {
        queue.setLength(0);
    }

    public boolean isEmpty() {
        return queue.length() == 0;
    }

    /**
     * Moves as many queued characters as fit into the KERNAL's keyboard buffer. Called
     * once per frame; the KERNAL empties the buffer as it reads the keys.
     *
     * @param mem The machine's memory array.
     */
    public void feed(int[] mem) {
        if (queue.length() == 0) {
            return;
        }
        int count = mem[KEYBOARD_BUFFER_COUNT];
        if ((count < 0) || (count > KEYBOARD_BUFFER_SIZE)) {
            return;
        }
        int taken = 0;
        while ((count < KEYBOARD_BUFFER_SIZE) && (taken < queue.length())) {
            mem[KEYBOARD_BUFFER + count] = queue.charAt(taken) & 0xFF;
            taken++;
            count++;
        }
        queue.delete(0, taken);
        mem[KEYBOARD_BUFFER_COUNT] = count;
    }

    /**
     * Returns the text on the screen, using the screen size and address from the VIC's
     * registers, as the monitor's screen command does. Trailing spaces on each line and
     * trailing blank lines are removed.
     *
     * @param machine The machine.
     *
     * @return The screen text, with lines separated by '\n'.
     */
    public static String readScreen(Machine machine) {
        int[] r = readVicRegisters(machine);
        int columns = Math.min(r[2] & 0x7F, 64);
        int rows = Math.min((r[3] >> 1) & 0x3F, 64);
        int matrix = matrixAddress(r);
        boolean lowerCase = isLowerCaseSet(r);

        StringBuilder text = new StringBuilder();
        int lastNonBlankLength = 0;
        for (int row = 0; row < rows; row++) {
            StringBuilder line = new StringBuilder();
            for (int column = 0; column < columns; column++) {
                int address = (matrix + (row * columns) + column) & 0xFFFF;
                line.append(screenCodeToText(peek(machine, address), lowerCase));
            }
            int end = line.length();
            while ((end > 0) && (line.charAt(end - 1) == ' ')) {
                end--;
            }
            if (row > 0) {
                text.append('\n');
            }
            text.append(line, 0, end);
            if (end > 0) {
                lastNonBlankLength = text.length();
            }
        }
        return text.substring(0, lastNonBlankLength);
    }

    /**
     * Whether the VIC is currently showing the lower case character set.
     *
     * @param machine The machine.
     *
     * @return true for the lower case set, false for the upper case/graphics set (or
     *         a character set in RAM).
     */
    public static boolean isLowerCaseMode(Machine machine) {
        return isLowerCaseSet(readVicRegisters(machine));
    }

    /**
     * Converts host text to the PETSCII codes that type it, for the given character
     * set. Line breaks (CR, LF or CRLF) become RETURN. Characters with no VIC-20
     * equivalent are left out.
     *
     * @param text      The host text.
     * @param lowerCase Whether the VIC is showing the lower case character set.
     *
     * @return The PETSCII codes, one per char.
     */
    public static String toPetscii(String text, boolean lowerCase) {
        StringBuilder petscii = new StringBuilder();
        int length = Math.min(text.length(), MAX_PASTE_LENGTH);
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            if (c == '\r') {
                petscii.append((char) 0x0D);
                if ((i + 1 < length) && (text.charAt(i + 1) == '\n')) {
                    i++;
                }
            } else if (c == '\n') {
                petscii.append((char) 0x0D);
            } else if (c == '\t') {
                petscii.append(' ');
            } else if ((c >= 'a') && (c <= 'z')) {
                // Unshifted: a lower case letter in the lower case set, else upper case.
                petscii.append((char) (c - 0x20));
            } else if ((c >= 'A') && (c <= 'Z')) {
                // Shifted only in the lower case set, where that's an upper case letter.
                petscii.append((char) (lowerCase ? (c + 0x80) : c));
            } else if ((c >= ' ') && (c <= ']')) {
                // Digits, punctuation, '@', '[' and ']' are the same in PETSCII; '\' is
                // where the pound sign is.
                petscii.append(c);
            } else if ((c == '£') || (c == '^') || (c == '↑')) {
                petscii.append((char) ((c == '£') ? 0x5C : 0x5E));
            } else if ((c == '_') || (c == '←')) {
                petscii.append((char) 0x5F);
            }
        }
        return petscii.toString();
    }

    /**
     * Converts a screen code to the host character it shows. Reversed characters show
     * as normal ones. Graphics characters, which have no host equivalent, become '.'.
     *
     * @param value     The screen code.
     * @param lowerCase Whether the VIC is showing the lower case character set.
     *
     * @return The host character.
     */
    static char screenCodeToText(int value, boolean lowerCase) {
        int code = value & 0x7F;
        if (code == 0x00) {
            return '@';
        }
        if (code < 0x1B) {
            return (char) ((lowerCase ? 'a' : 'A') + code - 1);
        }
        if (code < 0x20) {
            return "[£]^_".charAt(code - 0x1B);
        }
        if (code < 0x40) {
            return (char) code;
        }
        if (lowerCase && (code >= 0x41) && (code <= 0x5A)) {
            return (char) ('A' + code - 0x41);
        }
        if (code == 0x60) {
            // A shifted space looks like a space.
            return ' ';
        }
        return '.';
    }

    /** Converts a 14-bit VIC address to the CPU address the VIC sees there. */
    public static int cpuAddress(int vicAddress) {
        return ((vicAddress & 0x2000) != 0) ? (vicAddress & 0x1FFF) : (0x8000 | vicAddress);
    }

    /** The CPU address of the screen matrix, from VIC registers 0-15. */
    public static int matrixAddress(int[] vicRegisters) {
        return cpuAddress(((vicRegisters[5] & 0xF0) << 6) | ((vicRegisters[2] & 0x80) << 2));
    }

    /**
     * Whether the character set address in VIC register 5 is the lower case ROM
     * character set ($8800), or its reversed half ($8C00).
     */
    private static boolean isLowerCaseSet(int[] vicRegisters) {
        return cpuAddress((vicRegisters[5] & 0x0E) << 10) == 0x8800;
    }

    private static int[] readVicRegisters(Machine machine) {
        int[] r = new int[16];
        for (int i = 0; i < 16; i++) {
            r[i] = peek(machine, 0x9000 + i);
        }
        return r;
    }

    /** Reads memory without side effects, as the monitor does. */
    private static int peek(Machine machine, int address) {
        MemoryMappedChip chip = machine.getMemory().getMemoryMap()[address & 0xFFFF];
        if (chip instanceof WatchProxyChip) {
            chip = ((WatchProxyChip) chip).getTarget();
        }
        return chip.peekMemory(address & 0xFFFF) & 0xFF;
    }
}
