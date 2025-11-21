package emu.jvic.lwjgl3;

import java.util.Arrays;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.BufferUtils;

import emu.jvic.PixelData;

public class DesktopPixelData extends PixelData {

    // This byte array is in exactly the format that we can copy into the Pixmap's buffer.
    private byte[] imageData;
    
    @Override
    public void init(int width, int height) {
        imageData = new byte[width * height * 4];
    }

    @Override
    public void putPixel(int ulaIndex, int rgba8888Colour) {
        int index = (ulaIndex << 2);
        
        // Adds RGBA8888 colour to byte array in expected R, G, B, A order.
        imageData[index + 0] = (byte)((rgba8888Colour >> 24) & 0xFF);
        imageData[index + 1] = (byte)((rgba8888Colour >> 16) & 0xFF);
        imageData[index + 2] = (byte)((rgba8888Colour >>  8) & 0xFF);
        imageData[index + 3] = (byte)((rgba8888Colour >>  0) & 0xFF);
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
