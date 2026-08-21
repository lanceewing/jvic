package emu.jvic.testutil;

import com.badlogic.gdx.graphics.Pixmap;
import emu.jvic.PixelData;

/**
 * A {@link PixelData} implementation that simply records every pixel written into
 * an {@code int[]} buffer (RGBA8888 colour values), indexed exactly as the VIC
 * chip writes them (0 = top-left, row-major). No actual rendering/graphics context
 * is touched, so this works in a headless test JVM.
 */
public class RecordingPixelData extends PixelData {

    public int[] pixels;
    public int width;
    public int height;
    public long putPixelCallCount;

    @Override
    public void init(int width, int height) {
        this.width = width;
        this.height = height;
        this.pixels = new int[width * height];
    }

    @Override
    public void putPixel(int index, int rgba8888Colour) {
        putPixelCallCount++;
        if (index >= 0 && index < pixels.length) {
            pixels[index] = rgba8888Colour;
        }
    }

    @Override
    public void clearPixels() {
        java.util.Arrays.fill(pixels, 0);
    }

    @Override
    public void updatePixmap(Pixmap pixmap) {
        // Not needed for headless testing.
    }
}
