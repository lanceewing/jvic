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
        return ((~joystickState) & 0xFF) & ~monitorPressed;
    }

    /** Joystick lines held down by the debug monitor (jpdb), in getJoystickState's bits. */
    private int monitorPressed;

    /**
     * Holds joystick lines down, for the debug monitor's jpdb command, on top of the
     * real input. The bits are those of {@link #getJoystickState()}: 0x04 up, 0x08
     * down, 0x10 left, 0x20 fire (VIA1 port A) and 0x80 right (VIA2 port B).
     *
     * @param pressed The lines to hold down; 0 releases them.
     */
    public void setMonitorPressed(int pressed) {
        this.monitorPressed = pressed & 0xBC;
    }
}
