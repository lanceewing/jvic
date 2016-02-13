package emu.jvic.io;

import java.util.HashMap;
import com.badlogic.gdx.Input.Keys;

/**
 * This class emulates the VIC 20 keyboard by listening to key events, translating
 * them in to VIC 20 key codes, and then responding to VIC 20 keyboard scans when they 
 * are invoked.
 * 
 * @author Lance Ewing
 */
public class Keyboard {

  /**
   * Data used to convert Java keypresses into VIC 20 keypresses.
   */
  private static int keyConvMapArr[][] = {
    {Keys.BACKSPACE, 1, 128},
    {/* TODO: DOLLAR. Where is it in libGDX? */ 0, 1, 64},
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
    {Keys.LEFT, 2, 1},
    
    {Keys.RIGHT, 4, 128},
    {Keys.SEMICOLON, 4, 64},
    {Keys.L, 4, 32},
    {Keys.J, 4, 16},
    {Keys.G, 4, 8},
    {Keys.D, 4, 4},
    {Keys.A, 4, 2},
    {Keys.CONTROL_LEFT, 4, 1},
    
    {Keys.DOWN, 8, 128},
    {Keys.SLASH, 8, 64},
    {Keys.COMMA, 8, 32},
    {Keys.N, 8, 16},
    {Keys.V, 8, 8},
    {Keys.X, 8, 4},
    {Keys.SHIFT_LEFT, 8, 2},
    {Keys.TAB, 8, 1},
    
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
    {Keys.UP, 64, 64},
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
   * Holds the current state of the keyboard matrix and joystick switches.
   */
  private int keyMatrix[] = new int[513];
  
  /**
   * Invoked when a key has been pressed.
   *
   * @param keycode The keycode of the key that has been pressed.
   */
  public void keyPressed(int keycode) {
    int keyDetails[] = (int[])keyConvHashMap.get(new Integer(keycode));
    if (keyDetails != null) {
      keyMatrix[keyDetails[1]] |= keyDetails[2];
    }
  }
  
  /**
   * Invoked when a key has been released.
   *
   * @param keycode The keycode of the key that has been released.
   */
  public void keyReleased(int keycode) {
    int keyDetails[] = (int[])keyConvHashMap.get(new Integer(keycode));
    if (keyDetails != null) {
      keyMatrix[keyDetails[1]] &= (~keyDetails[2]);
    }
  }
  
  // TODO: Needs to change to keep track keys that are down too quickly, such as on Android. Store a timestamp for key presses. 50 ms.
  
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
    
    return ((~(rowData)) & 0xFF);
  }
}
