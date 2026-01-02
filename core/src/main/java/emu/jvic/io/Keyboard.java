package emu.jvic.io;

import emu.jvic.KeyboardMatrix;

/**
 * This class emulates the VIC 20 keyboard by listening to key events,
 * translating them in to VIC 20 key codes, and then responding to VIC 20
 * keyboard scans when they are invoked.
 * 
 * @author Lance Ewing
 */
public class Keyboard {

    /**
     * Platform specific interface through which to get keyboard matrix.
     */
    private KeyboardMatrix keyboardMatrix;
    
    /**
     * Constructor for Keyboard.
     * 
     * @param keyboardMatrix
     */
    public Keyboard(KeyboardMatrix keyboardMatrix) {
        this.keyboardMatrix = keyboardMatrix;
    }
    
    /**
     * Performs a row scan of the keyboard.
     *
     * @param selectedRow the selected rows.
     *
     * @return the matching column states.
     */
    public int scanKeyboardRow(int selectedRow) {
        int columnData = 0;
        
        if ((selectedRow & 0x80) != 0) columnData |= keyboardMatrix.getKeyMatrixRow(0x80);
        if ((selectedRow & 0x40) != 0) columnData |= keyboardMatrix.getKeyMatrixRow(0x40);
        if ((selectedRow & 0x20) != 0) columnData |= keyboardMatrix.getKeyMatrixRow(0x20);
        if ((selectedRow & 0x10) != 0) columnData |= keyboardMatrix.getKeyMatrixRow(0x10);
        if ((selectedRow & 0x08) != 0) columnData |= keyboardMatrix.getKeyMatrixRow(0x08);
        if ((selectedRow & 0x04) != 0) columnData |= keyboardMatrix.getKeyMatrixRow(0x04);
        if ((selectedRow & 0x02) != 0) columnData |= keyboardMatrix.getKeyMatrixRow(0x02);
        if ((selectedRow & 0x01) != 0) columnData |= keyboardMatrix.getKeyMatrixRow(0x01);
        
        return ((~(columnData)) & 0xFF);
    }

    /**
     * Performs a columns scan of the keyboard.
     *
     * @param selectedColumn the selected columns.
     *
     * @return the matching row states.
     */
    public int scanKeyboardColumn(int selectedColumn) {
        int rowData = 0;
        
        if ((selectedColumn & 0x80) != 0) rowData |= keyboardMatrix.getKeyMatrixRow(0x80);
        if ((selectedColumn & 0x40) != 0) rowData |= keyboardMatrix.getKeyMatrixRow(0x40);
        if ((selectedColumn & 0x20) != 0) rowData |= keyboardMatrix.getKeyMatrixRow(0x20);
        if ((selectedColumn & 0x10) != 0) rowData |= keyboardMatrix.getKeyMatrixRow(0x10);
        if ((selectedColumn & 0x08) != 0) rowData |= keyboardMatrix.getKeyMatrixRow(0x08);
        if ((selectedColumn & 0x04) != 0) rowData |= keyboardMatrix.getKeyMatrixRow(0x04);
        if ((selectedColumn & 0x02) != 0) rowData |= keyboardMatrix.getKeyMatrixRow(0x02);
        if ((selectedColumn & 0x01) != 0) rowData |= keyboardMatrix.getKeyMatrixRow(0x01);
        
        return ((~(rowData)) & 0xFF);
    }
    
    /**
     * Gets the current restore key state.
     * 
     * @return The current restore key state.
     */
    public int getRestoreKeyState() {
        int restoreKeyState = keyboardMatrix.getKeyMatrixRow(KeyboardMatrix.RESTORE);
        // TODO: For some reason pressing the RESTORE key hangs the machine.
        //return (restoreKeyState & 0x01);
        return 0;
    }
    
    private int currentRestoreState;
}
