package emu.jvic.teavm;

import java.util.Arrays;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.BufferUtils;

import emu.jvic.PixelData;

public class TeaVMPixelData extends PixelData {

    private byte[] imageData;

    @Override
    public void init(int width, int height) {
        imageData = new byte[width * height * 4 * 2];
    }

    @Override
    public void putPixel(int index, int rgba8888Colour) {
        int offset = index << 2;
        imageData[offset] = (byte)((rgba8888Colour >> 24) & 0xFF);
        imageData[offset + 1] = (byte)((rgba8888Colour >> 16) & 0xFF);
        imageData[offset + 2] = (byte)((rgba8888Colour >> 8) & 0xFF);
        imageData[offset + 3] = (byte)(rgba8888Colour & 0xFF);
    }

    @Override
    public void clearPixels() {
        Arrays.fill(imageData, (byte)0);
    }

    @Override
    public void updatePixmap(Pixmap pixmap) {
        BufferUtils.copy(imageData, 0, pixmap.getPixels(), pixmap.getPixels().remaining());
    }
}