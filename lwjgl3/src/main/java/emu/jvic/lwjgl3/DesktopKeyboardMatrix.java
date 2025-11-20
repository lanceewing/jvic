package emu.jvic.lwjgl3;

import emu.jvic.KeyboardMatrix;

public class DesktopKeyboardMatrix extends KeyboardMatrix {

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
