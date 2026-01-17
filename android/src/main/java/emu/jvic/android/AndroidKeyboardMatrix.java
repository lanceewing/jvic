package emu.jvic.android;

import emu.jvic.KeyboardMatrix;

public class AndroidKeyboardMatrix extends KeyboardMatrix {

    private int[] keyMatrix = new int[8];

    @Override
    public int getKeyMatrixRow(int row) {
        return keyMatrix[row];
    }

    @Override
    public void setKeyMatrixRow(int row, int value) {
        keyMatrix[row] = value;
    }
}
