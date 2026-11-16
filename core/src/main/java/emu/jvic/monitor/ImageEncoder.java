package emu.jvic.monitor;

import java.io.ByteArrayOutputStream;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * Encodes an image as PNG or BMP, in plain Java, so that the monitor's screenshot
 * command works the same way, and synchronously, on desktop, headless and in the
 * browser's worker (TeaVM has Deflater and CRC32).
 */
public final class ImageEncoder {

    private ImageEncoder() {
    }

    /**
     * Encodes 24-bit PNG.
     *
     * @param rgba   Pixels as RGBA8888, row by row from the top.
     * @param width  The width.
     * @param height The height.
     */
    public static byte[] png(int[] rgba, int width, int height) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x89);
        writeAscii(out, "PNG");
        out.write(0x0D);
        out.write(0x0A);
        out.write(0x1A);
        out.write(0x0A);

        ByteArrayOutputStream header = new ByteArrayOutputStream();
        writeInt(header, width);
        writeInt(header, height);
        header.write(8); // Bit depth.
        header.write(2); // Colour type: RGB.
        header.write(0); // Compression.
        header.write(0); // Filter.
        header.write(0); // No interlace.
        chunk(out, "IHDR", header.toByteArray());

        byte[] raw = new byte[height * (1 + width * 3)];
        int i = 0;
        for (int y = 0; y < height; y++) {
            raw[i++] = 0; // Filter: none.
            for (int x = 0; x < width; x++) {
                int pixel = rgba[y * width + x];
                raw[i++] = (byte) (pixel >>> 24);
                raw[i++] = (byte) (pixel >> 16);
                raw[i++] = (byte) (pixel >> 8);
            }
        }
        Deflater deflater = new Deflater();
        deflater.setInput(raw);
        deflater.finish();
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            compressed.write(buffer, 0, count);
        }
        deflater.end();
        chunk(out, "IDAT", compressed.toByteArray());
        chunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    /**
     * Encodes 24-bit uncompressed BMP.
     *
     * @param rgba   Pixels as RGBA8888, row by row from the top.
     * @param width  The width.
     * @param height The height.
     */
    public static byte[] bmp(int[] rgba, int width, int height) {
        int rowSize = (width * 3 + 3) & ~3;
        int imageSize = rowSize * height;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeAscii(out, "BM");
        writeLittle(out, 54 + imageSize, 4);
        writeLittle(out, 0, 4);
        writeLittle(out, 54, 4);
        writeLittle(out, 40, 4);
        writeLittle(out, width, 4);
        writeLittle(out, height, 4);
        writeLittle(out, 1, 2);
        writeLittle(out, 24, 2);
        writeLittle(out, 0, 4);
        writeLittle(out, imageSize, 4);
        writeLittle(out, 2835, 4);
        writeLittle(out, 2835, 4);
        writeLittle(out, 0, 4);
        writeLittle(out, 0, 4);
        for (int y = height - 1; y >= 0; y--) {
            for (int x = 0; x < width; x++) {
                int pixel = rgba[y * width + x];
                out.write((pixel >> 8) & 0xFF);
                out.write((pixel >> 16) & 0xFF);
                out.write((pixel >>> 24) & 0xFF);
            }
            for (int pad = width * 3; pad < rowSize; pad++) {
                out.write(0);
            }
        }
        return out.toByteArray();
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) {
        writeInt(out, data.length);
        byte[] typeBytes = { (byte) type.charAt(0), (byte) type.charAt(1), (byte) type.charAt(2),
            (byte) type.charAt(3) };
        out.write(typeBytes, 0, 4);
        out.write(data, 0, data.length);
        CRC32 crc = new CRC32();
        crc.update(typeBytes, 0, 4);
        crc.update(data, 0, data.length);
        writeInt(out, (int) crc.getValue());
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write(value >>> 24);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static void writeLittle(ByteArrayOutputStream out, int value, int bytes) {
        for (int i = 0; i < bytes; i++) {
            out.write((value >> (8 * i)) & 0xFF);
        }
    }

    private static void writeAscii(ByteArrayOutputStream out, String text) {
        for (int i = 0; i < text.length(); i++) {
            out.write(text.charAt(i));
        }
    }
}
