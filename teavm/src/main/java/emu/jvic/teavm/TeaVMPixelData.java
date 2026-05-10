package emu.jvic.teavm;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.BufferUtils;
import org.teavm.jso.JSBody;
import org.teavm.jso.typedarrays.SharedArrayBuffer;
import org.teavm.jso.typedarrays.Uint32Array;
import org.teavm.jso.typedarrays.Uint8ClampedArray;

import emu.jvic.PixelData;

public class TeaVMPixelData extends PixelData {

    private Uint8ClampedArray pixelArray;
    private Uint32Array packedPixelArray;
    private boolean packedPixelWritesSupported;
    private int activePixelByteLength;
    private int activePackedPixelLength;
    private byte[] imageData;

    public TeaVMPixelData() {
    }

    public TeaVMPixelData(SharedArrayBuffer sharedArrayBuffer) {
        pixelArray = Uint8ClampedArray.create(sharedArrayBuffer);
        packedPixelArray = new Uint32Array(sharedArrayBuffer);
        packedPixelWritesSupported = isLittleEndian();
        initialiseActivePixelLengths(pixelArray.getLength() / 2);
    }

    SharedArrayBuffer getSharedArrayBuffer() {
        return (SharedArrayBuffer)pixelArray.getBuffer();
    }

    @Override
    public void init(int width, int height) {
        pixelArray = Uint8ClampedArray.create(createSharedArrayBuffer(width * height * 4 * 2));
        packedPixelArray = new Uint32Array(getSharedArrayBuffer());
        packedPixelWritesSupported = isLittleEndian();
        initialiseActivePixelLengths(width * height * 4);
    }

    @Override
    public void putPixel(int index, int rgba8888Colour) {
        if (packedPixelWritesSupported) {
            packedPixelArray.set(index, convertRgba8888ToPackedPixel(rgba8888Colour));
        } else {
            int offset = index << 2;
            pixelArray.set(offset, (rgba8888Colour >> 24) & 0xFF);
            pixelArray.set(offset + 1, (rgba8888Colour >> 16) & 0xFF);
            pixelArray.set(offset + 2, (rgba8888Colour >> 8) & 0xFF);
            pixelArray.set(offset + 3, rgba8888Colour & 0xFF);
        }
    }

    @Override
    public void clearPixels() {
        if (packedPixelWritesSupported) {
            for (int index = 0; index < activePackedPixelLength; index++) {
                packedPixelArray.set(index, 0);
            }
        } else {
            for (int index = 0; index < activePixelByteLength; index++) {
                pixelArray.set(index, 0);
            }
        }
    }

    @Override
    public void updatePixmap(Pixmap pixmap) {
        for (int index = 0; index < activePixelByteLength; index++) {
            imageData[index] = (byte)(pixelArray.get(index) & 0xFF);
        }
        BufferUtils.copy(imageData, 0, pixmap.getPixels(), Math.min(activePixelByteLength, pixmap.getPixels().remaining()));
    }

    private void initialiseActivePixelLengths(int activePixelByteLength) {
        this.activePixelByteLength = activePixelByteLength;
        activePackedPixelLength = activePixelByteLength >> 2;
        imageData = new byte[activePixelByteLength];
    }

    private int convertRgba8888ToPackedPixel(int rgba8888Colour) {
        return ((rgba8888Colour & 0x000000FF) << 24)
                | ((rgba8888Colour & 0x0000FF00) << 8)
                | ((rgba8888Colour & 0x00FF0000) >>> 8)
                | ((rgba8888Colour & 0xFF000000) >>> 24);
    }

    @JSBody(params = "byteLength", script = "return new SharedArrayBuffer(byteLength);")
    private static native SharedArrayBuffer createSharedArrayBuffer(int byteLength);

    @JSBody(script = "var buffer = new ArrayBuffer(4); var uint32 = new Uint32Array(buffer); var uint8 = new Uint8Array(buffer); uint32[0] = 0x01020304; return uint8[0] === 0x04;")
    private static native boolean isLittleEndian();
}