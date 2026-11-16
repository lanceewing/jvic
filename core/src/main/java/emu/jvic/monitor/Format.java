package emu.jvic.monitor;

/**
 * Small number formatting helpers that match VICE's printf formats. Written by hand
 * rather than with String.format, to keep the engine cheap and portable to TeaVM.
 */
final class Format {

    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    private Format() {
    }

    /** "%0{width}x" for non-negative values. */
    static String hex(int value, int width) {
        return pad(Integer.toHexString(value), width, '0');
    }

    /** "%02x" of the low byte. */
    static String hex2(int value) {
        int v = value & 0xFF;
        return new String(new char[] { HEX_DIGITS[v >> 4], HEX_DIGITS[v & 0xF] });
    }

    /** "%04x" of the low 16 bits. */
    static String hex4(int value) {
        int v = value & 0xFFFF;
        return new String(new char[] {
                HEX_DIGITS[v >> 12], HEX_DIGITS[(v >> 8) & 0xF], HEX_DIGITS[(v >> 4) & 0xF], HEX_DIGITS[v & 0xF] });
    }

    /** "%02X" of the low byte. */
    static String hex2Upper(int value) {
        return hex2(value).toUpperCase();
    }

    /** "%04X" of the low 16 bits. */
    static String hex4Upper(int value) {
        return hex4(value).toUpperCase();
    }

    /** "%0{width}o" for non-negative values. */
    static String octal(int value, int width) {
        return pad(Integer.toOctalString(value), width, '0');
    }

    /** The low 8 bits as 8 binary digits. */
    static String binary8(int value) {
        return pad(Integer.toBinaryString(value & 0xFF), 8, '0');
    }

    /** "%{width}d". */
    static String decimal(long value, int width) {
        return pad(Long.toString(value), width, ' ');
    }

    /** Left-pads the text with the given character to at least the given width. */
    static String pad(String text, int width, char padding) {
        if (text.length() >= width) {
            return text;
        }
        StringBuilder padded = new StringBuilder(width);
        for (int i = text.length(); i < width; i++) {
            padded.append(padding);
        }
        return padded.append(text).toString();
    }

    /** Right-pads the text with spaces to at least the given width ("%-{width}s"). */
    static String padRight(String text, int width) {
        StringBuilder padded = new StringBuilder(text);
        while (padded.length() < width) {
            padded.append(' ');
        }
        return padded.toString();
    }

    /** Appends the given number of spaces. */
    static void spaces(StringBuilder text, int count) {
        for (int i = 0; i < count; i++) {
            text.append(' ');
        }
    }
}
