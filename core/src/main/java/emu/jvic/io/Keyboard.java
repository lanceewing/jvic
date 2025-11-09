package emu.jvic.io;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.utils.TimeUtils;

/**
 * This class emulates the VIC 20 keyboard by listening to key events, translating
 * them in to VIC 20 key codes, and then responding to VIC 20 keyboard scans when they 
 * are invoked.
 * 
 * @author Lance Ewing
 */
public class Keyboard {

  public static final int SHIFT_LOCK = 511;
  public static final int RUN_STOP   = 510;
  public static final int RESTORE    = 509;

  /**
   * Data used to convert Java keypresses into VIC 20 keypresses.
   */
  private static int keyConvMapArr[][] = {
    {Keys.BACKSPACE, 1, 128},
    {Keys.POUND, 1, 64},
    {Keys.PLUS, 1, 32},
    {Keys.NUM_9, 1, 16},
    {Keys.NUM_7, 1, 8},
    {Keys.NUM_5, 1, 4},
    {Keys.NUM_3, 1, 2},
    {Keys.NUM_1, 1, 1},
    
    {Keys.ENTER, 2, 128},
    {Keys.STAR, 2, 64},
    {Keys.P, 2, 32},
    {Keys.I, 2, 16},
    {Keys.Y, 2, 8},
    {Keys.R, 2, 4},
    {Keys.W, 2, 2},
    {Keys.PAGE_DOWN, 2, 1},  // TODO: What about ````
    
    {Keys.CONTROL_RIGHT, 4, 128},
    {Keys.SEMICOLON, 4, 64},
    {Keys.L, 4, 32},
    {Keys.J, 4, 16},
    {Keys.G, 4, 8},
    {Keys.D, 4, 4},
    {Keys.A, 4, 2},
    {Keys.CONTROL_LEFT, 4, 1},
    
    // Special Ctrl key mapping for Android Hacker's Keyboard.
    {113, 4, 1},
    
    {Keys.ALT_RIGHT, 8, 128},
    {Keys.SLASH, 8, 64},
    {Keys.COMMA, 8, 32},
    {Keys.N, 8, 16},
    {Keys.V, 8, 8},
    {Keys.X, 8, 4},
    {Keys.SHIFT_LEFT, 8, 2},
    {Keys.TAB, 8, 1},
    {RUN_STOP, 8, 1},
    
    {Keys.F1, 16, 128},
    {Keys.SHIFT_RIGHT, 16, 64},
    {Keys.PERIOD, 16, 32},
    {Keys.M, 16, 16},
    {Keys.B, 16, 8},
    {Keys.C, 16, 4},
    {Keys.Z, 16, 2},
    {Keys.SPACE, 16, 1},
    
    {Keys.F3, 32, 128},
    {Keys.EQUALS, 32, 64},
    {Keys.COLON, 32, 32},
    {Keys.K, 32, 16},
    {Keys.H, 32, 8},
    {Keys.F, 32, 4},
    {Keys.S, 32, 2},
    {Keys.ALT_LEFT, 32, 1},
    
    {Keys.F5, 64, 128},
    {Keys.PAGE_UP, 64, 64},
    {Keys.AT, 64, 32},
    {Keys.O, 64, 16},
    {Keys.U, 64, 8},
    {Keys.T, 64, 4},
    {Keys.E, 64, 2},
    {Keys.Q, 64, 1},
    
    {Keys.F7, 128, 128},
    {Keys.HOME, 128, 64},
    {Keys.MINUS, 128, 32},
    {Keys.NUM_0, 128, 16},
    {Keys.NUM_8, 128, 8},
    {Keys.NUM_6, 128, 4},
    {Keys.NUM_4, 128, 2},
    {Keys.NUM_2, 128, 1}
  };
  
  /**
   * HashMap used to store mappings between Java key events and VIC 20
   * keyboard scan codes.
   */
  private HashMap<Integer, int[]> keyConvHashMap;

  
  /**
   * Holds the current state of the keyboard matrix and joystick switches.
   */
  private int keyMatrix[] = new int[513];
  
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
   * Whether the SHIFT LOCK is currently on or not (this is a toggle).
   */
  private boolean shiftLockOn;
  
  /**
   * Whether the RESTORE key is currently down or not.
   */
  private boolean restoreDown;
  
  /**
   * Constructor for Keyboard.
   */
  public Keyboard() {
    // Clear the key matrix array positions.
    keyMatrix[1] = 0;
    keyMatrix[2] = 0;
    keyMatrix[4] = 0;
    keyMatrix[8] = 0;
    keyMatrix[16] = 0;
    keyMatrix[32] = 0;
    keyMatrix[64] = 0;
    keyMatrix[128] = 0;
    keyMatrix[256] = 0;
    keyMatrix[512] = 0;
    
    // Create the hash map for fast lookup.
    keyConvHashMap = new HashMap<Integer, int[]>();
    
    // Initialise the hashmap.
    for (int i=0; i<keyConvMapArr.length; i++) {
        int[] keyDetails = keyConvMapArr[i];
        keyConvHashMap.put(new Integer(keyDetails[0]), keyDetails);
    }
  }
  
  /**
   * Invoked when a key has been pressed.
   *
   * @param keycode The keycode of the key that has been pressed.
   */
  public void keyPressed(int keycode) {
    // Store the minimum expected release time for this key, i.e. current time + 50ms.
    minKeyReleaseTimes[keycode] = TimeUtils.nanoTime() + 50000000;
    
    // Update the key matrix to indicate to the VIC that this key is down.
    int keyDetails[] = (int[])keyConvHashMap.get(new Integer(keycode));
    if (keyDetails != null) {
      keyMatrix[keyDetails[1]] |= keyDetails[2];
    } else {
      // Special keycodes without direct mappings.
      switch (keycode) {
        case SHIFT_LOCK:
          shiftLockOn = !shiftLockOn;
          if (shiftLockOn) {
            keyMatrix[8] |= 2;
          } else {
            keyMatrix[8] &= ~2;
          }
          break;
        case RESTORE:
          restoreDown = true;
          break;
        default:
          break;
      }
    }
  }
  
  /**
   * Invoked when a key has been released.
   *
   * @param keycode The keycode of the key that has been released.
   */
  public void keyReleased(int keycode) {
    long currentTime = TimeUtils.nanoTime();
    long minKeyReleaseTime = minKeyReleaseTimes[keycode];
    minKeyReleaseTimes[keycode] = 0;
    
    if (currentTime < minKeyReleaseTime) {
      // Key hasn't been down long enough (possibly due to it being an Android virtual 
      // keyboard or something similar that doesn't reflect the actual time the key 
      // is down), so let's add this keycode to the delayed release list.
      synchronized(delayedReleaseKeys) {
        delayedReleaseKeys.put(minKeyReleaseTime, keycode);
      }
      
    } else {
      // Otherwise we process the release by updating the key matrix that the VIC polls.
      int keyDetails[] = (int[])keyConvHashMap.get(new Integer(keycode));
      if (keyDetails != null) {
        keyMatrix[keyDetails[1]] &= (~keyDetails[2]);
      } else {
        // Special keycodes without direct mappings.
        if (keycode == RESTORE) {
          restoreDown = true;
        }
      }
      
      // If SHIFT LOCK is on, we override the left shift key.
      if ((keycode == Keys.SHIFT_LEFT) && shiftLockOn) {
        keyMatrix[8] |= 2;
      }
    }
  }
  
  /**
   * Checks if there are any keys whose release processed has been delayed that are 
   * now able to be processed due to the minimum release time having been passed.
   */
  public void checkDelayedReleaseKeys() {
    if (!delayedReleaseKeys.isEmpty()) {
      synchronized(delayedReleaseKeys) {
        List<Long> processedReleases = new ArrayList<Long>();
        processedReleases.addAll(delayedReleaseKeys.headMap(TimeUtils.nanoTime()).keySet());
        for (Long keyReleaseTime : processedReleases) {
          keyReleased(delayedReleaseKeys.remove(keyReleaseTime));
        }
      }
    }
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
    
    if ((selectedRow & 0x80) != 0) columnData |= keyMatrix[0x80];
    if ((selectedRow & 0x40) != 0) columnData |= keyMatrix[0x40];
    if ((selectedRow & 0x20) != 0) columnData |= keyMatrix[0x20];
    if ((selectedRow & 0x10) != 0) columnData |= keyMatrix[0x10];
    if ((selectedRow & 0x08) != 0) columnData |= keyMatrix[0x08];
    if ((selectedRow & 0x04) != 0) columnData |= keyMatrix[0x04];
    if ((selectedRow & 0x02) != 0) columnData |= keyMatrix[0x02];
    if ((selectedRow & 0x01) != 0) columnData |= keyMatrix[0x01];
    
    checkDelayedReleaseKeys();
    
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
    
    if ((selectedColumn & 0x80) != 0) rowData |= keyMatrix[0x80];
    if ((selectedColumn & 0x40) != 0) rowData |= keyMatrix[0x40];
    if ((selectedColumn & 0x20) != 0) rowData |= keyMatrix[0x20];
    if ((selectedColumn & 0x10) != 0) rowData |= keyMatrix[0x10];
    if ((selectedColumn & 0x08) != 0) rowData |= keyMatrix[0x08];
    if ((selectedColumn & 0x04) != 0) rowData |= keyMatrix[0x04];
    if ((selectedColumn & 0x02) != 0) rowData |= keyMatrix[0x02];
    if ((selectedColumn & 0x01) != 0) rowData |= keyMatrix[0x01];
    
    checkDelayedReleaseKeys();
    
    return ((~(rowData)) & 0xFF);
  }
}
