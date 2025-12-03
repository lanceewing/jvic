package emu.jvic;

import com.badlogic.gdx.graphics.Pixmap;

/**
 * An Interface for plotting individual pixels. The desktop, mobile, and HTML 
 * platforms will implement this in their own way. The HTML platform in particular
 * is a bit different and needs to be handled in a platform specific way, which is 
 * the primary reason this interface exists. All platforms use the same colour
 * format though, i.e. RGBA8888, which makes things a little easier. For both
 * HTML5, where the Pixmap is a wrapper around an HTML5 canvas, and for Desktop
 * and Android, the colours are updated via a byte array where the RGBA components
 * are stored in the order R, G, B, A.
 */
public abstract class PixelData {

    /**
     * Initialises the PixelData implementation with the given width and height.
     * 
     * @param width The width of the pixel data.
     * @param height The height of the pixel data.
     */
    public abstract void init(int width, int height);
    
    /**
     * Puts a single pixel into the pixel data using an index position.
     * 
     * @param index AGI screen position (i.e. (y * 320) + x
     * @param rgba8888Colour
     */
    public abstract void putPixel(int index, int rgba8888Colour);
    
    /**
     * Clears all pixels, i.e. sets to black.
     */
    public abstract void clearPixels();
    
    /**
     * Updates Pixmap with the latest local changes. 
     * 
     * @param pixmap 
     */
    public abstract void updatePixmap(Pixmap pixmap);
    
}