package emu.jvic.io;

import emu.jvic.KeyboardMatrix;

/**
 * This class emulates the VIC 20 joystick by listening to key events and
 * translating the relevant key codes in to joystick signals.
 * 
 * @author Lance Ewing
 */
public class Joystick {

    /**
     * Platform specific interface through which to get keyboard matrix.
     */
    private KeyboardMatrix keyboardMatrix;
    
    /**
     * Constructor for Joystick.
     * 
     * @param keyboardMatrix
     */
    public Joystick(KeyboardMatrix keyboardMatrix) {
        this.keyboardMatrix = keyboardMatrix;
    }

    /**
     * Gets the current joystick state.
     * 
     * @return The current joystick state.
     */
    public int getJoystickState() {
        int joystickState = keyboardMatrix.getKeyMatrixRow(KeyboardMatrix.JOYSTICK);
        return ((~joystickState) & 0xFF);
    }
}
