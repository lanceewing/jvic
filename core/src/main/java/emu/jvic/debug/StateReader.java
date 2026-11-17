package emu.jvic.debug;

/**
 * Reads a jvic save state written by {@link StateWriter}. Sections are found by tag;
 * reading past the end of a section, or a section that's missing, is an error.
 *
 * <p>Plain Java only (arrays and arithmetic), as this package is translated by GWT.
 */
public final class StateReader {

    /** The state isn't a jvic save state, or is damaged. */
    public static final class StateException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public StateException(String message) {
            super(message);
        }
    }

    private final byte[] data;
    private final int version;
    private int pos;
    private int sectionEnd;

    /**
     * @param data The whole save state.
     *
     * @throws StateException if it doesn't start with jvic's header.
     */
    public StateReader(byte[] data) {
        this.data = data;
        if (data.length < StateWriter.MAGIC.length() + 4) {
            throw new StateException("Not a jvic save state.");
        }
        for (int i = 0; i < StateWriter.MAGIC.length(); i++) {
            if (data[i] != StateWriter.MAGIC.charAt(i)) {
                throw new StateException("Not a jvic save state.");
            }
        }
        pos = StateWriter.MAGIC.length();
        sectionEnd = data.length;
        version = readInt();
        if (version > StateWriter.VERSION) {
            throw new StateException("The save state is from a newer jvic (format " + version + ").");
        }
    }

    public int getVersion() {
        return version;
    }

    /**
     * Moves to the start of a section.
     *
     * @return false if there is no such section.
     */
    public boolean hasSection(String tag) {
        int p = StateWriter.MAGIC.length() + 4;
        while (p + 8 <= data.length) {
            int length = intAt(p + 4);
            boolean match = true;
            for (int i = 0; i < 4; i++) {
                char c = (i < tag.length()) ? tag.charAt(i) : ' ';
                if (data[p + i] != c) {
                    match = false;
                }
            }
            if (match) {
                pos = p + 8;
                sectionEnd = pos + length;
                return true;
            }
            p += 8 + length;
        }
        return false;
    }

    /**
     * Moves to the start of a section that must be there.
     *
     * @throws StateException if it is missing.
     */
    public void section(String tag) {
        if (!hasSection(tag)) {
            throw new StateException("The save state has no " + tag.trim() + " section.");
        }
    }

    private int intAt(int p) {
        return ((data[p] & 0xFF) << 24) | ((data[p + 1] & 0xFF) << 16) | ((data[p + 2] & 0xFF) << 8)
                | (data[p + 3] & 0xFF);
    }

    private void need(int count) {
        if (pos + count > sectionEnd) {
            throw new StateException("The save state is damaged (a section is too short).");
        }
    }

    public int readByte() {
        need(1);
        return data[pos++] & 0xFF;
    }

    public boolean readBoolean() {
        return readByte() != 0;
    }

    public int readInt() {
        need(4);
        int value = intAt(pos);
        pos += 4;
        return value;
    }

    public long readLong() {
        long high = readInt() & 0xFFFFFFFFL;
        long low = readInt() & 0xFFFFFFFFL;
        return (high << 32) | low;
    }

    public byte[] readBytes() {
        int length = readInt();
        need(length);
        byte[] result = new byte[length];
        System.arraycopy(data, pos, result, 0, length);
        pos += length;
        return result;
    }

    /** Reads byte values into an existing int array, which must be the same length. */
    public void readByteValues(int[] target) {
        int length = readInt();
        if (length != target.length) {
            throw new StateException("The save state doesn't fit this machine (" + length + " bytes, not "
                    + target.length + ").");
        }
        need(length);
        for (int i = 0; i < length; i++) {
            target[i] = data[pos++] & 0xFF;
        }
    }

    public int[] readInts() {
        int length = readInt();
        int[] result = new int[length];
        for (int i = 0; i < length; i++) {
            result[i] = readInt();
        }
        return result;
    }

    public String readString() {
        int length = readInt();
        need(length);
        StringBuilder text = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            text.append((char) (data[pos++] & 0xFF));
        }
        return text.toString();
    }
}
