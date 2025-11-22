package emu.jvic.io;

import java.util.HashMap;
import com.badlogic.gdx.Input.Keys;

/**
 * This class emulates the VIC 20 joystick by listening to key events and
 * translating the relevant key codes in to joystick signals.
 * 
 * @author Lance Ewing
 */
public class Joystick {

    /**
     * Data used to convert Java keypresses into Joystick signals.
     */
    private static int keyToJoystickData[][] = { 
            
            { Keys.NUMPAD_0, 0x20 },  // Fire button
            { Keys.NUMPAD_1, 0x18 },  // SW
            { Keys.NUMPAD_2, 0x08 },  // Down
            { Keys.NUMPAD_3, 0x88 },  // SE
            { Keys.NUMPAD_4, 0x10 },  // Left
            { Keys.NUMPAD_6, 0x80 },  // Right
            { Keys.NUMPAD_7, 0x14 },  // NW
            { Keys.NUMPAD_8, 0x04 },  // Up
            { Keys.NUMPAD_9, 0x84 },  // NE

            { Keys.INSERT,   0x20 },  // Fire button
            { Keys.DOWN,     0x08 },  // Down
            { Keys.LEFT,     0x10 },  // Left
            { Keys.RIGHT,    0x80 },  // Right
            { Keys.UP,       0x04 },  // Up
    };

    /**
     * HashMap used to store mappings between Java key events and joystick signals.
     */
    private HashMap<Integer, Integer> keyToJoystickMap;

    /**
     * The current state of the joystick signals.
     */
    private int joystickState;

    /**
     * Constructor for Joystick.
     */
    public Joystick() {
        // Create the hash map for fast lookup.
        keyToJoystickMap = new HashMap<Integer, Integer>();

        // Initialise the key to joystick signal HashMap.
        for (int i = 0; i < keyToJoystickData.length; i++) {
            keyToJoystickMap.put(new Integer(keyToJoystickData[i][0]), keyToJoystickData[i][1]);
        }
    }

    /**
     * Gets the current joystick state.
     * 
     * @return The current joystick state.
     */
    public int getJoystickState() {
        return ((~joystickState) & 0xFF);
    }

    /**
     * Invoked when a key has been pressed.
     *
     * @param keycode The keycode of the key that has been pressed.
     */
    public void keyPressed(int keycode) {
        Integer joystickSignal = keyToJoystickMap.get(keycode);
        if (joystickSignal != null) {
            joystickState |= joystickSignal;
        }
    }

    /**
     * Invoked when a key has been released.
     *
     * @param keycode The keycode of the key that has been released.
     */
    public void keyReleased(int keycode) {
        Integer joystickSignal = keyToJoystickMap.get(keycode);
        if (joystickSignal != null) {
            joystickState &= (~joystickSignal);
        }
    }
}
