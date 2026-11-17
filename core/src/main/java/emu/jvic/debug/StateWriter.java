package emu.jvic.debug;

/**
 * Writes a jvic save state: a header, then tagged sections, each holding one chip's
 * fields in a fixed order (see each chip's saveState method). Every section has a
 * length, so a reader can skip sections it doesn't know.
 *
 * <p>Plain Java only (arrays and arithmetic), as this package is translated by GWT.
 */
public final class StateWriter {

    /** The first bytes of every jvic save state. */
    public static final String MAGIC = "JVICSNAP";

    /** The format version. */
    public static final int VERSION = 1;

    private byte[] buffer = new byte[1 << 16];
    private int size;
    private int sectionStart = -1;

    public StateWriter() {
        for (int i = 0; i < MAGIC.length(); i++) {
            writeByte(MAGIC.charAt(i));
        }
        writeInt(VERSION);
    }

    /**
     * Starts a section.
     *
     * @param tag Four characters, e.g. "CPU ".
     */
    public void beginSection(String tag) {
        if (sectionStart >= 0) {
            endSection();
        }
        for (int i = 0; i < 4; i++) {
            writeByte((i < tag.length()) ? tag.charAt(i) : ' ');
        }
        sectionStart = size;
        writeInt(0);
    }

    /** Ends the current section, filling in its length. */
    public void endSection() {
        if (sectionStart < 0) {
            return;
        }
        int length = size - sectionStart - 4;
        buffer[sectionStart] = (byte) (length >>> 24);
        buffer[sectionStart + 1] = (byte) (length >>> 16);
        buffer[sectionStart + 2] = (byte) (length >>> 8);
        buffer[sectionStart + 3] = (byte) length;
        sectionStart = -1;
    }

    private void ensure(int extra) {
        if (size + extra > buffer.length) {
            int capacity = buffer.length * 2;
            while (capacity < size + extra) {
                capacity *= 2;
            }
            byte[] bigger = new byte[capacity];
            System.arraycopy(buffer, 0, bigger, 0, size);
            buffer = bigger;
        }
    }

    public void writeByte(int value) {
        ensure(1);
        buffer[size++] = (byte) value;
    }

    public void writeBoolean(boolean value) {
        writeByte(value ? 1 : 0);
    }

    public void writeInt(int value) {
        ensure(4);
        buffer[size++] = (byte) (value >>> 24);
        buffer[size++] = (byte) (value >>> 16);
        buffer[size++] = (byte) (value >>> 8);
        buffer[size++] = (byte) value;
    }

    public void writeLong(long value) {
        writeInt((int) (value >>> 32));
        writeInt((int) value);
    }

    /** A byte array, with its length. */
    public void writeBytes(byte[] data) {
        writeInt(data.length);
        ensure(data.length);
        System.arraycopy(data, 0, buffer, size, data.length);
        size += data.length;
    }

    /** An int array whose values are bytes (e.g. memory), with its length. */
    public void writeByteValues(int[] data) {
        writeInt(data.length);
        ensure(data.length);
        for (int value : data) {
            buffer[size++] = (byte) value;
        }
    }

    /** An int array of full ints, with its length. */
    public void writeInts(int[] data) {
        writeInt(data.length);
        for (int value : data) {
            writeInt(value);
        }
    }

    /** A string of 8-bit characters, with its length. */
    public void writeString(String text) {
        writeInt(text.length());
        for (int i = 0; i < text.length(); i++) {
            writeByte(text.charAt(i));
        }
    }

    /** The finished state. */
    public byte[] toByteArray() {
        endSection();
        byte[] result = new byte[size];
        System.arraycopy(buffer, 0, result, 0, size);
        return result;
    }
}
