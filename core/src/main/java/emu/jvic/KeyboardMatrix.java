package emu.jvic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;

import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.utils.TimeUtils;

/**
 * Handles the input of keyboard events, mapping them to a form that the JVic
 * emulator can query as required. The storage of the state is platform dependent,
 * which is why this class is abstract. This is primarily due to the HTML5/GWT/web 
 * implementation needing to store that state in a form that can be immediately 
 * accessed by both the UI thread (for updates) and the web worker thread (for
 * querying).
 */
public abstract class KeyboardMatrix extends InputAdapter {
    
    // The keyboard matrix data array is of size 513, in both desktop and html versions,
    // so all index values must be below this. 0-255 is reversed for the normal keys,
    // whereas the joystick keys and special keys (such as restore) use values above 255.
    public static final int SHIFT_LOCK = 511;
    public static final int RUN_STOP = 510;
    public static final int RESTORE = 509;
    
    public static final int JOYSTICK = 256;
    
    /**
     * Data used to map VIC 20 keys to the appropriate keyboard col/row scan values.
     */
    private static int keyConvMapArr[][] = {
            
        { VicKeys.DELETE, 1, 128 },
        { VicKeys.POUND, 1, 64 },
        { VicKeys.PLUS, 1, 32 },
        { VicKeys.NINE, 1, 16 },
        { VicKeys.SEVEN, 1, 8 },
        { VicKeys.FIVE, 1, 4 },
        { VicKeys.THREE, 1, 2 },
        { VicKeys.ONE, 1, 1 },
        
        { VicKeys.RETURN, 2, 128 },
        { VicKeys.ASTERISK, 2, 64 },
        { VicKeys.P, 2, 32 },
        { VicKeys.I, 2, 16 },
        { VicKeys.Y, 2, 8 },
        { VicKeys.R, 2, 4 },
        { VicKeys.W, 2, 2 },
        { VicKeys.LEFT_ARROW, 2, 1 },
        
        { VicKeys.CURSOR_RIGHT, 4, 128},
        { VicKeys.SEMI_COLON, 4, 64},
        { VicKeys.L, 4, 32},
        { VicKeys.J, 4, 16},
        { VicKeys.G, 4, 8},
        { VicKeys.D, 4, 4},
        { VicKeys.A, 4, 2},
        { VicKeys.CONTROL, 4, 1},
        
        { VicKeys.CURSOR_DOWN, 8, 128 },
        { VicKeys.FORWARD_SLASH, 8, 64 },
        { VicKeys.COMMA, 8, 32 },
        { VicKeys.N, 8, 16 },
        { VicKeys.V, 8, 8 },
        { VicKeys.X, 8, 4 },
        { VicKeys.LEFT_SHIFT, 8, 2 },
        { VicKeys.RUN_STOP, 8, 1 },
        
        { VicKeys.F1, 16, 128 },
        { VicKeys.RIGHT_SHIFT, 16, 64 },
        { VicKeys.PERIOD, 16, 32 },
        { VicKeys.M, 16, 16 },
        { VicKeys.B, 16, 8 },
        { VicKeys.C, 16, 4 },
        { VicKeys.Z, 16, 2 },
        { VicKeys.SPACE, 16, 1 },
        
        { VicKeys.F3, 32, 128 },
        { VicKeys.EQUALS, 32, 64 },
        { VicKeys.COLON, 32, 32 },
        { VicKeys.K, 32, 16 },
        { VicKeys.H, 32, 8 },
        { VicKeys.F, 32, 4 },
        { VicKeys.S, 32, 2 },
        { VicKeys.CBM, 32, 1 },
        
        { VicKeys.F5, 64, 128 },
        { VicKeys.UP_ARROW, 64, 64 },
        { VicKeys.AT, 64, 32 },
        { VicKeys.O, 64, 16 },
        { VicKeys.U, 64, 8 },
        { VicKeys.T, 64, 4 },
        { VicKeys.E, 64, 2 },
        { VicKeys.Q, 64, 1 },
        
        { VicKeys.F7, 128, 128 },
        { VicKeys.HOME, 128, 64 },
        { VicKeys.HYPHEN, 128, 32 },
        { VicKeys.ZERO, 128, 16 },
        { VicKeys.EIGHT, 128, 8 },
        { VicKeys.SIX, 128, 4 },
        { VicKeys.FOUR, 128, 2 },
        { VicKeys.TWO, 128, 1 },
        
        { VicKeys.JOYSTICK_FIRE,  JOYSTICK, 0x20 },  // Fire button
        { VicKeys.JOYSTICK_DOWN,  JOYSTICK, 0x08 },  // Down
        { VicKeys.JOYSTICK_LEFT,  JOYSTICK, 0x10 },  // Left
        { VicKeys.JOYSTICK_RIGHT, JOYSTICK, 0x80 },  // Right
        { VicKeys.JOYSTICK_UP,    JOYSTICK, 0x04 },  // Up
        { VicKeys.JOYSTICK_SW,    JOYSTICK, 0x18 },  // SW
        { VicKeys.JOYSTICK_SE,    JOYSTICK, 0x88 },  // SE
        { VicKeys.JOYSTICK_NW,    JOYSTICK, 0x14 },  // NW
        { VicKeys.JOYSTICK_NE,    JOYSTICK, 0x84 },  // NE
        
        { VicKeys.RESTORE, RESTORE, 0x01 },
    };
    
    /**
     * Whether the SHIFT LOCK is currently on or not (this is a toggle).
     */
    private boolean shiftLockOn;
    
    /**
     * The ALT key is not directly mapped to the VIC keyboard, as there is no corresponding 
     * key. So we use it for various hot keys instead.
     */
    private boolean altKeyDown;
    
    /**
     * HashMap used to store mappings between Java key events and VIC 20
     * keyboard scan codes.
     */
    private HashMap<Integer, int[]> vickeyConvHashMap;
    
    /**
     * Holds the last time that the key was pressed down, or 0 if it has since been released.
     */
    private long minKeyReleaseTimes[] = new long[512];
    
    /**
     * Holds a queue of keycodes whose key release processing has been delayed. This is
     * supported primarily for use with the Android virtual keyboard on some devices, 
     * where the key pressed and release both get fired on release of the key, so have
     * virtually no time between them.
     */
    private TreeMap<Long, Integer> delayedReleaseKeys = new TreeMap<Long, Integer>();
    
    /**
     * Maps keypress characters to VIC keys.
     */
    private HashMap<Character, int[]> charConvHashMap;
    
    /**
     * Maps libgdx keycodes to VIC keys.
     */
    private HashMap<Integer, int[]> keycodeConvHashMap;
    
    /**
     * Constructor for KeyboardMatrix.
     */
    public KeyboardMatrix() {
        // Converts VIC keys to keyboard row/col scan matrix positions.
        vickeyConvHashMap = new HashMap<Integer, int[]>();
        for (int i = 0; i < keyConvMapArr.length; i++) {
            int[] keyDetails = keyConvMapArr[i];
            vickeyConvHashMap.put(keyDetails[0], keyDetails);
        }
        
        // Converts typed characters into VIC key combinations.
        charConvHashMap = new HashMap<Character, int[]>();
        for (int i=0; i < VicKeys.VIC_CHAR_MAP.length; i++) {
            int[] vicKeyMapping = VicKeys.VIC_CHAR_MAP[i];
            int[] vicKeys = new int[vicKeyMapping.length - 1];
            System.arraycopy(vicKeyMapping, 1, vicKeys, 0, vicKeyMapping.length-1);
            charConvHashMap.put((char)vicKeyMapping[0], vicKeys);
        }
        
        // Converts libgdx keycodes into VIC key combinations.
        keycodeConvHashMap = new HashMap<Integer, int[]>();
        for (int i=0; i < VicKeys.VIC_KEY_MAP.length; i++) {
            int[] vicKeyMapping = VicKeys.VIC_KEY_MAP[i];
            int[] vicKeys = new int[vicKeyMapping.length - 1];
            System.arraycopy(vicKeyMapping, 1, vicKeys, 0, vicKeyMapping.length-1);
            keycodeConvHashMap.put(vicKeyMapping[0], vicKeys);
        }
    }
    
    public boolean keyDown(int keycode) {
        if ((keycode == Keys.ALT_LEFT) || (keycode == Keys.ALT_RIGHT)) {
            altKeyDown = true;
            return true;
        }
        if (altKeyDown) return false;
        if (!keycodeConvHashMap.containsKey(keycode)) return false;
        int[] vicKeys = keycodeConvHashMap.get(keycode);
        for (int i=0; i<vicKeys.length; i++) {
            vicKeyDown(vicKeys[i]);
        }
        return true;
    }
    
    public void vicKeyDown(int vicKey) {
        // Store the minimum expected release time for this key, i.e. current time + 50ms.
        minKeyReleaseTimes[vicKey] = TimeUtils.nanoTime() + 50000000;
        
        // Update the key matrix to indicate to the VIC 20 that this key is down.
        int keyDetails[] = (int[]) vickeyConvHashMap.get(vicKey);
        if (keyDetails != null) {
            int currentRowValue = getKeyMatrixRow(keyDetails[1]);
            setKeyMatrixRow(keyDetails[1], currentRowValue | keyDetails[2]);
        } else {
            // Special keycodes without direct mappings.
            switch (vicKey) {
                case SHIFT_LOCK:
                    shiftLockOn = !shiftLockOn;
                    if (shiftLockOn) {
                        setKeyMatrixRow(8, getKeyMatrixRow(8) | 2);
                    } else {
                        setKeyMatrixRow(8, getKeyMatrixRow(8) & ~2);
                    }
                    break;
                default:
                    break;
            }
        }
    }
    
    public boolean keyUp(int keycode) {
        if ((keycode == Keys.ALT_LEFT) || (keycode == Keys.ALT_RIGHT)) {
            altKeyDown = false;
            return true;
        }
        if (altKeyDown) return false;
        if (!keycodeConvHashMap.containsKey(keycode)) return false;
        if (keycode != 0) {
            int[] vicKeys = keycodeConvHashMap.get(keycode);
            for (int i=0; i<vicKeys.length; i++) {
                vicKeyUp(vicKeys[i]);
            }
        }
        return true;
    }

    public void vicKeyUp(int vicKey) {
        if (vicKey != 0) {
            long currentTime = TimeUtils.nanoTime();
            long minKeyReleaseTime = minKeyReleaseTimes[vicKey];
            minKeyReleaseTimes[vicKey] = 0;
            
            if (currentTime < minKeyReleaseTime) {
                // Key hasn't been down long enough (possibly due to it being an Android virtual 
                // keyboard or something similar that doesn't reflect the actual time the key 
                // is down), so let's add this keycode to the delayed release list.
                synchronized(delayedReleaseKeys) {
                    delayedReleaseKeys.put(minKeyReleaseTime, vicKey);
                }
                
            } else {
                // Otherwise we process the release by updating the key matrix that the VIC 20 polls.
                int keyDetails[] = (int[]) vickeyConvHashMap.get(vicKey);
                if (keyDetails != null) {
                    int currentRowValue = getKeyMatrixRow(keyDetails[1]);
                    setKeyMatrixRow(keyDetails[1], currentRowValue & ~keyDetails[2]);
                }
                
                // If SHIFT LOCK is on, we override the left shift key.
                if ((vicKey == Keys.SHIFT_LEFT) && shiftLockOn) {
                    setKeyMatrixRow(8, getKeyMatrixRow(8) | 2);
                }
            }
        }
    }

    public boolean keyTyped(char ch) {
        if (altKeyDown) return false;
        int[] vicKeys = charConvHashMap.get(ch);
        if (vicKeys != null) {
            for (int i=0; i<vicKeys.length; i++) {
                if (vicKeys[i] == VicKeys.NO_SHIFT) {
                    noShift();
                } else {
                    vicKeyDown(vicKeys[i]);
                }
            }
            for (int i=0; i<vicKeys.length; i++) {
                vicKeyUp(vicKeys[i]);
            }
            return true;
        } else {
            return false;
        }
    }
    
    /**
     * Forces the shift key status to be up. Does nothing if it already is up.
     */
    private void noShift() {
        minKeyReleaseTimes[VicKeys.LEFT_SHIFT] = 0;
        minKeyReleaseTimes[VicKeys.RIGHT_SHIFT] = 0;
        vicKeyUp(VicKeys.LEFT_SHIFT);
        vicKeyUp(VicKeys.RIGHT_SHIFT);
    }
    
    /**
     * Checks if the ALT key is currently down.
     * 
     * @return true if the ALT key is currently down; otherwise false.
     */
    public boolean isAltKeyDown() {
        return altKeyDown;
    }
    
    /**
     * Checks if there are any keys whose release processed has been delayed that
     * are now able to be processed due to the minimum release time having been
     * passed.
     */
    public void checkDelayedReleaseKeys() {
        if (!delayedReleaseKeys.isEmpty()) {
            synchronized (delayedReleaseKeys) {
                List<Long> processedReleases = new ArrayList<Long>();
                processedReleases.addAll(delayedReleaseKeys.headMap(TimeUtils.nanoTime()).keySet());
                for (Long keyReleaseTime : processedReleases) {
                    int delayedReleaseVicKey = delayedReleaseKeys.remove(keyReleaseTime);
                    vicKeyUp(delayedReleaseVicKey);
                }
            }
        }
    }
    
    public abstract int getKeyMatrixRow(int row);
    
    public abstract void setKeyMatrixRow(int row, int value);
    
}
