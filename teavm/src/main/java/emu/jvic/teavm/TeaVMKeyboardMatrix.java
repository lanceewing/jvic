package emu.jvic.teavm;

import emu.jvic.KeyboardMatrix;

public class TeaVMKeyboardMatrix extends KeyboardMatrix {

    private final int[] keyMatrix = new int[513];

    @Override
    public int getKeyMatrixRow(int row) {
        return keyMatrix[row];
    }

    @Override
    public void setKeyMatrixRow(int row, int value) {
        keyMatrix[row] = value;
    }
}