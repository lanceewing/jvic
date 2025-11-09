package emu.jvic;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Texture;

import emu.jvic.ui.ViewportManager;
import static emu.jvic.io.Keyboard.*;

/**
 * Enum representing the different types of keyboard available within JVic.
 * 
 * @author Lance Ewing
 */
public enum KeyboardType {

  LANDSCAPE(
        new Integer[][][] {{
          { Keys.LEFT, Keys.LEFT, Keys.NUM_1, Keys.NUM_1, Keys.NUM_2, Keys.NUM_2, Keys.NUM_3, Keys.NUM_3, Keys.NUM_4, Keys.NUM_4, Keys.NUM_5, Keys.NUM_5, Keys.NUM_6, Keys.NUM_6, Keys.NUM_7, Keys.NUM_7, Keys.NUM_8, Keys.NUM_8, Keys.NUM_9, Keys.NUM_9, Keys.NUM_0, Keys.NUM_0, Keys.PLUS, Keys.PLUS, Keys.MINUS, Keys.MINUS, Keys.POUND, Keys.POUND, Keys.HOME, Keys.HOME, Keys.DEL, Keys.DEL, null, null, null, null, null, null, null, null },
          { Keys.CONTROL_LEFT, Keys.CONTROL_LEFT, Keys.CONTROL_LEFT, Keys.Q, Keys.Q, Keys.W, Keys.W, Keys.E, Keys.E, Keys.R, Keys.R, Keys.T, Keys.T, Keys.Y, Keys.Y, Keys.U, Keys.U, Keys.I, Keys.I, Keys.O, Keys.O, Keys.P, Keys.P, Keys.AT, Keys.AT, Keys.STAR, Keys.STAR, Keys.UP, Keys.UP, RESTORE, RESTORE, RESTORE, null, null, null, null, null, null, null, null },
          { RUN_STOP, RUN_STOP, SHIFT_LOCK, SHIFT_LOCK, Keys.A, Keys.A, Keys.S, Keys.S, Keys.D, Keys.D, Keys.F, Keys.F, Keys.G, Keys.G, Keys.H, Keys.H, Keys.J, Keys.J, Keys.K, Keys.K, Keys.L, Keys.L, Keys.COLON, Keys.COLON, Keys.SEMICOLON, Keys.SEMICOLON, Keys.EQUALS, Keys.EQUALS, Keys.ENTER, Keys.ENTER, Keys.ENTER, Keys.ENTER, null, null, null, null, null, null, null, null },
          { Keys.ALT_LEFT, Keys.ALT_LEFT, Keys.SHIFT_LEFT, Keys.SHIFT_LEFT, Keys.SHIFT_LEFT, Keys.Z, Keys.Z, Keys.X, Keys.X, Keys.C, Keys.C, Keys.V, Keys.V, Keys.B, Keys.B, Keys.N, Keys.N, Keys.M, Keys.M, Keys.COMMA, Keys.COMMA, Keys.PERIOD, Keys.PERIOD, Keys.SLASH, Keys.SLASH, Keys.SHIFT_RIGHT, Keys.SHIFT_RIGHT, Keys.SHIFT_RIGHT, Keys.DOWN, Keys.DOWN, Keys.RIGHT, Keys.RIGHT, null, null, null, null, null, null, null, null },
          { null, null, null, null, null, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null }
        }},
        new String[] {"png/keyboard_landscape.png"},
        0.5f,
        0,
        0
      ),
  PORTRAIT_12x6(
        new Integer[][][] {{
          { Keys.CONTROL_LEFT, Keys.CONTROL_LEFT, Keys.LEFT, Keys.LEFT, Keys.UP, Keys.UP, Keys.F1, Keys.F1, Keys.F1, Keys.F3, Keys.F3, Keys.F3, Keys.F5, Keys.F5, Keys.F5, Keys.F7, Keys.F7, Keys.F7, Keys.POUND, Keys.POUND, Keys.HOME, Keys.HOME, Keys.DEL, Keys.DEL, null, null },
          { Keys.NUM_1, Keys.NUM_2, Keys.NUM_3, Keys.NUM_4, Keys.NUM_5, Keys.NUM_6, Keys.NUM_7, Keys.NUM_8, Keys.NUM_9, Keys.NUM_0, Keys.PLUS, Keys.MINUS, null },
          { Keys.Q, Keys.W, Keys.E, Keys.R, Keys.T, Keys.Y, Keys.U, Keys.I, Keys.O, Keys.P, Keys.AT, Keys.STAR, null },
          { Keys.A, Keys.S, Keys.D, Keys.F, Keys.G, Keys.H, Keys.J, Keys.K, Keys.L, Keys.COLON, Keys.SEMICOLON, Keys.EQUALS, null },
          { Keys.Z, Keys.X, Keys.C, Keys.V, Keys.B, Keys.N, Keys.M, Keys.COMMA, Keys.PERIOD, Keys.SLASH, Keys.ENTER, Keys.ENTER, null },
          { Keys.ALT_LEFT, SHIFT_LOCK, Keys.SHIFT_LEFT, RUN_STOP, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, RESTORE, Keys.SHIFT_RIGHT, Keys.DOWN, Keys.RIGHT, null } 
        }},
        new String[] {"png/keyboard_portrait_12x6.png"},
        1.0f,
        0,
        0
      ),
  PORTRAIT_10x7(
        new Integer[][][] {{
          { Keys.CONTROL_LEFT, Keys.LEFT, Keys.UP, Keys.F1, Keys.F3, Keys.F5, Keys.F7, Keys.POUND, Keys.HOME, Keys.DEL, null },
          { Keys.COLON, Keys.SEMICOLON, Keys.EQUALS, Keys.AT, Keys.PLUS, Keys.MINUS, Keys.STAR, Keys.SLASH, RUN_STOP, RESTORE, null },
          { Keys.NUM_1, Keys.NUM_2, Keys.NUM_3, Keys.NUM_4, Keys.NUM_5, Keys.NUM_6, Keys.NUM_7, Keys.NUM_8, Keys.NUM_9, Keys.NUM_0, null },
          { Keys.Q, Keys.W, Keys.E, Keys.R, Keys.T, Keys.Y, Keys.U, Keys.I, Keys.O, Keys.P, null },
          { Keys.A, Keys.S, Keys.D, Keys.F, Keys.G, Keys.H, Keys.J, Keys.K, Keys.L, Keys.ENTER, null },
          { Keys.Z, Keys.X, Keys.C, Keys.V, Keys.B, Keys.N, Keys.M, Keys.COMMA, Keys.PERIOD, Keys.ENTER, null },
          { Keys.ALT_LEFT, SHIFT_LOCK, Keys.SHIFT_LEFT, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SHIFT_RIGHT, Keys.DOWN, Keys.RIGHT, null }
        }},
        new String[] {"png/keyboard_portrait_10x7.png"},
        1.0f,
        0,
        0
      ),
  JOYSTICK(
        new Integer[][][] {{
          { Keys.NUMPAD_7, Keys.NUMPAD_7, Keys.NUMPAD_7, Keys.NUMPAD_8, Keys.NUMPAD_8, Keys.NUMPAD_8, Keys.NUMPAD_9, Keys.NUMPAD_9, Keys.NUMPAD_9 },
          { Keys.NUMPAD_7, Keys.NUMPAD_7, Keys.NUMPAD_7, Keys.NUMPAD_8, Keys.NUMPAD_8, Keys.NUMPAD_8, Keys.NUMPAD_9, Keys.NUMPAD_9, Keys.NUMPAD_9 },
          { Keys.NUMPAD_7, Keys.NUMPAD_7, Keys.NUMPAD_7, Keys.NUMPAD_8, Keys.NUMPAD_8, Keys.NUMPAD_8, Keys.NUMPAD_9, Keys.NUMPAD_9, Keys.NUMPAD_9 },
          { Keys.NUMPAD_4, Keys.NUMPAD_4, Keys.NUMPAD_4, Keys.NUMPAD_7, Keys.NUMPAD_8, Keys.NUMPAD_9, Keys.NUMPAD_6, Keys.NUMPAD_6, Keys.NUMPAD_6 },
          { Keys.NUMPAD_4, Keys.NUMPAD_4, Keys.NUMPAD_4, Keys.NUMPAD_4, null,          Keys.NUMPAD_6, Keys.NUMPAD_6, Keys.NUMPAD_6, Keys.NUMPAD_6 },
          { Keys.NUMPAD_4, Keys.NUMPAD_4, Keys.NUMPAD_4, Keys.NUMPAD_1, Keys.NUMPAD_2, Keys.NUMPAD_3, Keys.NUMPAD_6, Keys.NUMPAD_6, Keys.NUMPAD_6 },
          { Keys.NUMPAD_1, Keys.NUMPAD_1, Keys.NUMPAD_1, Keys.NUMPAD_2, Keys.NUMPAD_2, Keys.NUMPAD_2, Keys.NUMPAD_3, Keys.NUMPAD_3, Keys.NUMPAD_3 },
          { Keys.NUMPAD_1, Keys.NUMPAD_1, Keys.NUMPAD_1, Keys.NUMPAD_2, Keys.NUMPAD_2, Keys.NUMPAD_2, Keys.NUMPAD_3, Keys.NUMPAD_3, Keys.NUMPAD_3 },
          { Keys.NUMPAD_1, Keys.NUMPAD_1, Keys.NUMPAD_1, Keys.NUMPAD_2, Keys.NUMPAD_2, Keys.NUMPAD_2, Keys.NUMPAD_3, Keys.NUMPAD_3, Keys.NUMPAD_3 }
        },
        {
          { Keys.NUMPAD_0, Keys.NUMPAD_0, Keys.NUMPAD_0 },
          { Keys.NUMPAD_0, Keys.NUMPAD_0, Keys.NUMPAD_0 },
          { Keys.NUMPAD_0, Keys.NUMPAD_0, Keys.NUMPAD_0 },
          { Keys.NUMPAD_0, Keys.NUMPAD_0, Keys.NUMPAD_0 },
          { Keys.NUMPAD_0, Keys.NUMPAD_0, Keys.NUMPAD_0 },
          { Keys.NUMPAD_0, Keys.NUMPAD_0, Keys.NUMPAD_0 }
        }},
        new String[] {"png/joystick_arrows.png", "png/joystick_fire.png"},
        1.0f,
        0,
        100
      ),
  MOBILE_ON_SCREEN,
  OFF;
  
  // Constants for the two sides of a keyboard..
  public static final int LEFT = 0;
  public static final int RIGHT = 1;
  
  /**
   * The size of the keys in this KeyboardType.
   */
  private int keySize;
  
  /**
   * The position of each key within this KeyboardType.
   */
  private Integer[][][] keyMap;
  
  /**
   * The Texture(s) holding the keyboard image(s) for this KeyboardType.
   */
  private Texture[] textures;
  
  /**
   * The path to the keyboard image file(s).
   */
  private String[] keyboardImages;
  
  /**
   * The opacity of this KeyboardType.
   */
  private float opacity;
  
  /**
   * Offset from the bottom of the screen that the keyboard is rendered at.
   */
  private int renderOffset;
  
  /**
   * The number of sides that this keyboard has.
   */
  private int numOfSides;
  
  /**
   * The Y value above which the keyboard will be closed.
   */
  private int closeHeight;
  
  /**
   * Constructor for KeyboardType.
   * 
   * @param keyMap The position of each key within this KeyboardType.
   * @param keyboardImages The path to the keyboard image file(s).
   * @param opacity The opacity of this KeyboardType.
   * @param renderOffset Offset from the bottom of the screen that the keyboard is rendered at.
   * @param closeBuffer Buffer over the keyboard above which a tap or click will close the keyboard.
   */
  KeyboardType(Integer[][][] keyMap, String[] keyboardImages, float opacity, int renderOffset, int closeBuffer) {
    this.keyMap = keyMap;
    this.keyboardImages = keyboardImages;
    this.numOfSides = keyboardImages.length;
    
    int maxTextureHeight = 0;
    this.textures = new Texture[keyboardImages.length];
    for (int i=0; i<numOfSides; i++) {
      this.textures[i] = new Texture(keyboardImages[i]);
      if (this.textures[i].getHeight() > maxTextureHeight) {
        maxTextureHeight = this.textures[i].getHeight();
      }
    }
    
    this.keySize = (this.textures[0].getHeight() / this.keyMap[0].length);
    this.opacity = opacity;
    this.renderOffset = renderOffset;
    this.closeHeight = maxTextureHeight + renderOffset + closeBuffer;
  }

  /**
   * Variant of the Constructor that doesn't support any key mapping, or visual appearance
   */
  KeyboardType() {
  }
  
  /**
   * Gets the keycode that is mapped to the given X and Y world coordinates. Returns null
   * if there is no matching key at the given position.
   * 
   * @param x The X position within this KeyboardType's world coordinates.
   * @param y The Y position within this KeyboardType's world coordinates.
   * 
   * @return The keycode that is mapped to the given X and Y world coordinates, or null if there is not match.
   */
  public Integer getKeyCode(float x, float y) {
    if ((numOfSides == 1) || (x < getTexture(LEFT).getWidth())) {
      return getKeyCode(x, y, LEFT);
    } else {
      return getKeyCode(x, y, RIGHT);
    }
  }
  
  /**
   * Gets the keycode that is mapped to the given X and Y world coordinates. Returns null
   * if there is no matching key at the given position.
   * 
   * @param x The X position within this KeyboardType's world coordinates.
   * @param y The Y position within this KeyboardType's world coordinates.
   * @param side The side of the keyboard (left/right), for split keyboards.
   * 
   * @return The keycode that is mapped to the given X and Y world coordinates, or null if there is not match.
   */
  public Integer getKeyCode(float x, float y, int side) {
    Integer keyCode = null;
    int keyRow = 0;
    
    // If we're showing the mini version of the joystick, adjust the height here.
    if (equals(JOYSTICK) && !ViewportManager.getInstance().isPortrait() && (side == LEFT)) {
      // TODO: Make this a flag in the construction of a KeyboardType.
      keyRow = (int)((textures[side].getHeight() - (y * 2) + renderOffset) / keySize);
      
    } else {
      keyRow = (int)((textures[side].getHeight() - y + renderOffset) / keySize);
    }

    if (keyRow >= keyMap[side].length) keyRow = keyMap[side].length - 1;
    
    switch (this) {
      case LANDSCAPE:
        // Perform adjustment of x pos for rows 0, 1 and 4 to align with rows 2 and 3, but 
        // only if it is left of the function keys.
        if (((keyRow < 2) || (keyRow == 4)) && (x < 1768)) {
          x = x - 25.26f;
          if (x < 0) {
            return null;
          }
        }
        keyCode = keyMap[side][keyRow][(int)(x / (keySize / 2f))];
        break;
    
      case PORTRAIT_12x6:
        // First row of 12x6 layout is mapped by half key.
        if (keyRow == 0) x *= 2;
      
      case PORTRAIT_10x7:
        keyCode = keyMap[side][keyRow][(int)(x / keySize)];
        break;
      
      case JOYSTICK:
        if (!ViewportManager.getInstance().isPortrait() && (side == LEFT)) {
          x = x * 2;
        }
        if (side == RIGHT) {
          x = x - (ViewportManager.getInstance().getWidth() - getTexture(RIGHT).getWidth());
        }
        int keyCol = (int)(x / keySize);
        if (keyCol < keyMap[side][keyRow].length) {
          keyCode = keyMap[side][keyRow][keyCol];
        }
        break;
        
      default:
        break;
    }
    
    return keyCode;
  }
  
  /**
   * Tests if the given X/Y position is within the bounds of this KeyboardTypes keyboard image.
   * 
   * @param x The X position to test.
   * @param y The Y position to test.
   * 
   * @return true if the given X/Y position is within the keyboard image; otherwise false.
   */
  public boolean isInKeyboard(float x, float y) {
    if (numOfSides == 1) {
      return isInKeyboard(x, y, LEFT);
    } else {
      return isInKeyboard(x, y, LEFT) || isInKeyboard(x, y, RIGHT);
    }
  }
  
  /**
   * Tests if the given X/Y position is within the bounds of this KeyboardTypes keyboard image.
   * 
   * @param x The X position to test.
   * @param y The Y position to test.
   * @param side The side of the keyboard (left/right), for split keyboards.
   * 
   * @return true if the given X/Y position is within the keyboard image; otherwise false.
   */
  public boolean isInKeyboard(float x, float y, int side) {
    if (isRendered()) {
      int textureHeight = getTextures()[side].getHeight();
      int textureWidth = getTextures()[side].getWidth();
      
      if (this.equals(JOYSTICK) && !ViewportManager.getInstance().isPortrait() && (side == LEFT)) {
        textureHeight = textureHeight / 2;
        textureWidth = textureWidth / 2;
      }
      
      boolean isInYBounds = (y < (textureHeight + renderOffset) && (y > renderOffset));
      if (numOfSides == 1) {
        // In this case, we only need to test the Y position since the keyboard image will span the whole width.
        return isInYBounds;
      } else {
        // Must be two sides...
        if (side == LEFT) {
          // LEFT.
          return (isInYBounds && (x < textureWidth));
        } else {
          // RIGHT.
          return (isInYBounds && (x > (ViewportManager.getInstance().getWidth() - textureWidth)));
        }
      }
    } else {
      // isInKeyboard only applies to rendered keyboards.
      return false;
    }
  }
  
  /**
   * @return The array of Textures holding the keyboard images for this KeyboardType.
   */
  public Texture[] getTextures() {
    if ((textures == null) && (keyboardImages != null)) {
      this.textures = new Texture[keyboardImages.length];
      for (int i=0; i<keyboardImages.length; i++) {
        this.textures[i] = new Texture(keyboardImages[i]);
      }
    }
    return this.textures;
  }
  
  /**
   * @return The Texture holding the keyboard image for the given side of this keyboard.
   */
  public Texture getTexture(int side) {
    return textures[side];
  }
  
  /**
   * @return The Texture holding the keyboard image for this KeyboardType.
   */
  public Texture getTexture() {
    return textures[0];
  }

  /**
   * @return The opacity of this KeyboardType.
   */
  public float getOpacity() {
    return opacity;
  }
  
  /**
   * @return true if this KeyboardType is rendered by the JVic render code; otherwise false.
   */
  public boolean isRendered() {
    return (textures != null);
  }

  /**
   * @return Offset from the bottom of the screen that the keyboard is rendered at.
   */
  public int getRenderOffset() {
    return renderOffset;
  }
  
  /**
   * @return The height above which the keyboard will close.
   */
  public int getCloseHeight() {
    return closeHeight;
  }

  /**
   * Disposes of the libGDX Textures for all KeyboardTypes.
   */
  public static void dispose() {
    for (KeyboardType keyboardType : KeyboardType.values()) {
      if (keyboardType.textures != null) {
        for (int i=0; i<keyboardType.textures.length; i++) {
          keyboardType.textures[i].dispose();
          keyboardType.textures[i] = null;
        }
        keyboardType.textures = null;
      }
    }
  }
  
  /**
   * Re-creates the libGDX Textures for all KeyboardTypes. 
   */
  public static void init() {
    for (KeyboardType keyboardType : KeyboardType.values()) {
      keyboardType.getTextures();
    }
  }
}
