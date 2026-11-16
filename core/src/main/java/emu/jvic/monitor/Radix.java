package emu.jvic.monitor;

/**
 * The monitor's default radix, used for numbers typed without a prefix, and for the
 * default {@code mem} display format.
 */
public enum Radix {

    HEXADECIMAL(16, 'H', "Hexadecimal"),
    DECIMAL(10, 'D', "Decimal"),
    OCTAL(8, 'O', "Octal"),
    BINARY(2, 'B', "Binary");

    private final int base;
    private final char letter;
    private final String displayName;

    Radix(int base, char letter, String displayName) {
        this.base = base;
        this.letter = letter;
        this.displayName = displayName;
    }

    public int getBase() {
        return base;
    }

    /** The letter used by the radix command and as a mem data type: H, D, O or B. */
    public char getLetter() {
        return letter;
    }

    /** The name VICE prints, e.g. "Hexadecimal". */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns the Radix for the given letter, ignoring case, or null if none.
     */
    public static Radix fromLetter(char letter) {
        char upper = Character.toUpperCase(letter);
        for (Radix radix : values()) {
            if (radix.letter == upper) {
                return radix;
            }
        }
        return null;
    }

    /**
     * Returns the value of the given character as a digit in this radix, or -1 if it
     * isn't one.
     */
    public int digitValue(char c) {
        return digitValue(c, base);
    }

    /**
     * Returns the value of the given character as a digit in the given base, or -1.
     */
    public static int digitValue(char c, int base) {
        int value;
        if ((c >= '0') && (c <= '9')) {
            value = c - '0';
        } else if ((c >= 'a') && (c <= 'f')) {
            value = c - 'a' + 10;
        } else if ((c >= 'A') && (c <= 'F')) {
            value = c - 'A' + 10;
        } else {
            return -1;
        }
        return (value < base) ? value : -1;
    }
}
