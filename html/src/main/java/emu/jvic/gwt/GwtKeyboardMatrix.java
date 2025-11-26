package emu.jvic.gwt;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.typedarrays.shared.Uint8ClampedArray;

import emu.jvic.KeyboardMatrix;

/**
 * GWT implementation of the KeyboardMatrix interface. It uses a SharedArrayBuffer
 * so that both the UI thread and web worker see the same shared data. It does not
 * need to worry about synchronising the SharedArrayBuffer access with Atomics either,
 * as one side is always reading and will not modify.
 */
public class GwtKeyboardMatrix extends KeyboardMatrix {
    
    private Uint8ClampedArray keyMatrix;

    /**
     * Constructor for GwtKeyboardMatrix (used by UI thread)
     */
    public GwtKeyboardMatrix() {
        keyMatrix = createKeyMatrixArray();
    }
    
    /**
     * Constructor for GwtKeyboardMatrix (used by web worker).
     * 
     * @param sharedArrayBuffer The same SharedArrayBuffer used by the UI thread.
     */
    public GwtKeyboardMatrix(JavaScriptObject sharedArrayBuffer) {
        keyMatrix = createKeyMatrixArray(sharedArrayBuffer);
    }

    private native Uint8ClampedArray createKeyMatrixArray(JavaScriptObject sharedArrayBuffer)/*-{
        return new Uint8ClampedArray(sharedArrayBuffer);
    }-*/;
    
    private native Uint8ClampedArray createKeyMatrixArray()/*-{
        var sharedArrayBuffer = new SharedArrayBuffer(8);
        return new Uint8ClampedArray(sharedArrayBuffer);
    }-*/;
    
    public native JavaScriptObject getSharedArrayBuffer()/*-{
        var keyMatrix = this.@emu.jvic.gwt.GwtKeyboardMatrix::keyMatrix;
        return keyMatrix.buffer;
    }-*/;
    
    @Override
    public int getKeyMatrixRow(int row) {
        return keyMatrix.get(row);
    }

    @Override
    public void setKeyMatrixRow(int row, int value) {
        keyMatrix.set(row, value);
    }
}
