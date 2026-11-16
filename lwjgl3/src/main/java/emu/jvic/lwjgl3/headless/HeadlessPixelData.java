package emu.jvic.lwjgl3.headless;

import java.util.Arrays;

import com.badlogic.gdx.graphics.Pixmap;

import emu.jvic.PixelData;

/**
 * Pixel data for headless mode: keeps the frame in an int array (RGBA8888 per
 * pixel), so that a later screenshot command can save it without libGDX.
 */
final class HeadlessPixelData extends PixelData {

    private int width;
    private int height;
    private int[] pixels = new int[0];

    @Override
    public void init(int width, int height) {
        this.width = width;
        this.height = height;
        this.pixels = new int[width * height];
    }

    @Override
    public void putPixel(int index, int rgba8888Colour) {
        pixels[index] = rgba8888Colour;
    }

    @Override
    public void clearPixels() {
        Arrays.fill(pixels, 0);
    }

    @Override
    public void updatePixmap(Pixmap pixmap) {
        // There is no screen in headless mode.
    }

    int getWidth() {
        return width;
    }

    int getHeight() {
        return height;
    }

    /** The pixels, RGBA8888, row by row. */
    int[] getPixels() {
        return pixels;
    }
}
