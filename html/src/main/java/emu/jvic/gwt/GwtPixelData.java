package emu.jvic.gwt;

import com.badlogic.gdx.graphics.Pixmap;
import com.google.gwt.canvas.dom.client.Context2d;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.typedarrays.shared.ArrayBufferView;
import com.google.gwt.typedarrays.shared.Uint8ClampedArray;

import emu.jvic.PixelData;

/**
 * GWT implementation of the PixelData interface.
 * 
 * Some important notes about the GWT implementation of Pixmap:
 * 
 * - It uses a canvas element, not a ByteBuffer, for the pixels.
 * - When the Pixmap is drawn to the Texture, it is directly from the canvas.
 * - Therefore, as long as the canvas is up to date, it will render to the Texture.
 * - And so the updatePixel method simply copies the pixelArray to the canvas image data.
 * 
 * Using a SharedArrayBuffer to store the pixel data means that the pixel array 
 * does not need to be transferred to the UI thread after each frame. That would
 * be the alternative approach. We don't really have to worry about synchronising 
 * the SharedArrayBuffer access with Atomics either, as one side is always reading
 * and will not modify.
 */
public class GwtPixelData extends PixelData {
    
    private Uint8ClampedArray pixelArray;
    
    /**
     * Constructor for GwtPixelData (used by UI thread)
     */
    public GwtPixelData() {
    }
    
    /**
     * Constructor for GwtPixelData (used by web worker).
     * 
     * @param sharedArrayBuffer The same SharedArrayBuffer used by the UI thread.
     */
    public GwtPixelData(JavaScriptObject sharedArrayBuffer) {
        pixelArray = createPixelArray(sharedArrayBuffer);
    }
    
    private native Uint8ClampedArray createPixelArray(JavaScriptObject sharedArrayBuffer)/*-{
        return new Uint8ClampedArray(sharedArrayBuffer);
    }-*/;

    private native Uint8ClampedArray createPixelArray(int width, int height)/*-{
        var sharedArrayBuffer = new SharedArrayBuffer(width * height * 4);
        return new Uint8ClampedArray(sharedArrayBuffer);
    }-*/;

    public native JavaScriptObject getSharedArrayBuffer()/*-{
        var pixelArray = this.@emu.jvic.gwt.GwtPixelData::pixelArray;
        return pixelArray.buffer;
    }-*/;
    
    @Override
    public void init(int width, int height) {
        // The actual pixel array is created using a SharedArrayBuffer, so we need
        // to use a native method to do this.
        pixelArray = createPixelArray(width, height);
    }

    @Override
    public void putPixel(int ulaIndex, int rgba8888Colour) {
        int index = (ulaIndex << 2);
        
        // Adds RGBA8888 colour to byte array in expected R, G, B, A order.
        pixelArray.set(index, (rgba8888Colour >> 24) & 0xFF);
        pixelArray.set(index + 1, (rgba8888Colour >> 16) & 0xFF);
        pixelArray.set(index + 2, (rgba8888Colour >> 8) & 0xFF);
        pixelArray.set(index + 3, rgba8888Colour & 0xFF);
    }

    @Override
    public void clearPixels() {
        for (int index = 0; index < pixelArray.length(); index++) {
            pixelArray.set(index, 0);
        }
    }

    @Override
    public void updatePixmap(Pixmap pixmap) {
        setImageData(pixelArray, pixmap.getWidth(), pixmap.getHeight(), pixmap.getContext());
    }
    
    private native static void setImageData (ArrayBufferView pixels, int width, int height, Context2d ctx)/*-{
        var imgData = ctx.createImageData(width, height);
        var data = imgData.data;
    
        for (var i = 0, len = width * height * 4; i < len; i++) {
            data[i] = pixels[i] & 0xff;
        }
        ctx.putImageData(imgData, 0, 0);
    }-*/;
}
