package emu.jvic.teavm;

import emu.jvic.KeyboardMatrix;
import org.teavm.jso.JSBody;
import org.teavm.jso.typedarrays.SharedArrayBuffer;
import org.teavm.jso.typedarrays.Uint8ClampedArray;

public class TeaVMKeyboardMatrix extends KeyboardMatrix {

    private final Uint8ClampedArray keyMatrix;

    public TeaVMKeyboardMatrix() {
        this(createSharedArrayBuffer(513));
    }

    public TeaVMKeyboardMatrix(SharedArrayBuffer sharedArrayBuffer) {
        this.keyMatrix = Uint8ClampedArray.create(sharedArrayBuffer);
    }

    SharedArrayBuffer getSharedArrayBuffer() {
        return (SharedArrayBuffer)keyMatrix.getBuffer();
    }

    @Override
    public int getKeyMatrixRow(int row) {
        return keyMatrix.get(row);
    }

    @Override
    public void setKeyMatrixRow(int row, int value) {
        keyMatrix.set(row, value);
    }

    @JSBody(params = "byteLength", script = "return new SharedArrayBuffer(byteLength);")
    private static native SharedArrayBuffer createSharedArrayBuffer(int byteLength);
}