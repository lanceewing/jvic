package emu.jvic.testutil;

import emu.jvic.KeyboardMatrix;

/**
 * Minimal {@link KeyboardMatrix} implementation backed by a plain array, for
 * driving Via1/Via2 keyboard/joystick/restore-key wiring tests directly (bypassing
 * the libgdx key event handling entirely).
 */
public class TestKeyboardMatrix extends KeyboardMatrix {

    private final int[] rows = new int[513];

    @Override
    public int getKeyMatrixRow(int row) {
        return rows[row];
    }

    @Override
    public void setKeyMatrixRow(int row, int value) {
        rows[row] = value;
    }
}
