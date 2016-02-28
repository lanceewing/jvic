package emu.jvic;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

/**
 * Enum representing the different types of keyboard available within JVic.
 * 
 * @author Lance Ewing
 */
public enum KeyboardType {

  // TODO: Make these constants and handle them properly within the emulator.
  // RESTORE: -5 
  // RUN STOP: -4
  // SHIFTLOCK: -3
  
  LANDSCAPE(
        new Integer[][] {
          { Keys.LEFT, Keys.LEFT, Keys.NUM_1, Keys.NUM_1, Keys.NUM_2, Keys.NUM_2, Keys.NUM_3, Keys.NUM_3, Keys.NUM_4, Keys.NUM_4, Keys.NUM_5, Keys.NUM_5, Keys.NUM_6, Keys.NUM_6, Keys.NUM_7, Keys.NUM_7, Keys.NUM_8, Keys.NUM_8, Keys.NUM_9, Keys.NUM_9, Keys.NUM_0, Keys.NUM_0, Keys.PLUS, Keys.PLUS, Keys.MINUS, Keys.MINUS, Keys.POUND, Keys.POUND, Keys.HOME, Keys.HOME, Keys.DEL, Keys.DEL, null, null, null, null, null, null, null, null },
          { Keys.CONTROL_LEFT, Keys.CONTROL_LEFT, Keys.CONTROL_LEFT, Keys.Q, Keys.Q, Keys.W, Keys.W, Keys.E, Keys.E, Keys.R, Keys.R, Keys.T, Keys.T, Keys.Y, Keys.Y, Keys.U, Keys.U, Keys.I, Keys.I, Keys.O, Keys.O, Keys.P, Keys.P, Keys.AT, Keys.AT, Keys.STAR, Keys.STAR, Keys.UP, Keys.UP,  -5, -5, -5, null, null, null, null, null, null, null, null },
          { -4, -4, -3, -3, Keys.A, Keys.A, Keys.S, Keys.S, Keys.D, Keys.D, Keys.F, Keys.F, Keys.G, Keys.G, Keys.H, Keys.H, Keys.J, Keys.J, Keys.K, Keys.K, Keys.L, Keys.L, Keys.COLON, Keys.COLON, Keys.SEMICOLON, Keys.SEMICOLON, Keys.EQUALS, Keys.EQUALS, Keys.ENTER, Keys.ENTER, Keys.ENTER, Keys.ENTER, null, null, null, null, null, null, null, null },
          { Keys.ALT_LEFT, Keys.ALT_LEFT, Keys.SHIFT_LEFT, Keys.SHIFT_LEFT, Keys.SHIFT_LEFT, Keys.Z, Keys.Z, Keys.X, Keys.X, Keys.C, Keys.C, Keys.V, Keys.V, Keys.B, Keys.B, Keys.N, Keys.N, Keys.M, Keys.M, Keys.COMMA, Keys.COMMA, Keys.PERIOD, Keys.PERIOD, Keys.SLASH, Keys.SLASH, Keys.SHIFT_RIGHT, Keys.SHIFT_RIGHT, Keys.SHIFT_RIGHT, Keys.DOWN, Keys.DOWN, Keys.RIGHT, Keys.RIGHT, null, null, null, null, null, null, null, null },
          { null, null, null, null, null, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null }
        },
        "png/keyboard_landscape.png",
        0.5f,
        40
      ),
  PORTRAIT_12x6(
        new Integer[][] {
          { Keys.CONTROL_LEFT, Keys.CONTROL_LEFT, Keys.LEFT, Keys.LEFT, Keys.UP, Keys.UP, Keys.F1, Keys.F1, Keys.F1, Keys.F3, Keys.F3, Keys.F3, Keys.F5, Keys.F5, Keys.F5, Keys.F7, Keys.F7, Keys.F7, Keys.POUND, Keys.POUND, Keys.HOME, Keys.HOME, Keys.DEL, Keys.DEL, null, null },
          { Keys.NUM_1, Keys.NUM_2, Keys.NUM_3, Keys.NUM_4, Keys.NUM_5, Keys.NUM_6, Keys.NUM_7, Keys.NUM_8, Keys.NUM_9, Keys.NUM_0, Keys.PLUS, Keys.MINUS, null },
          { Keys.Q, Keys.W, Keys.E, Keys.R, Keys.T, Keys.Y, Keys.U, Keys.I, Keys.O, Keys.P, Keys.AT, Keys.STAR, null },
          { Keys.A, Keys.S, Keys.D, Keys.F, Keys.G, Keys.H, Keys.J, Keys.K, Keys.L, Keys.COLON, Keys.SEMICOLON, Keys.EQUALS, null },
          { Keys.Z, Keys.X, Keys.C, Keys.V, Keys.B, Keys.N, Keys.M, Keys.COMMA, Keys.PERIOD, Keys.SLASH, Keys.ENTER, Keys.ENTER, null },
          { Keys.ALT_LEFT, -3, Keys.SHIFT_LEFT, -4, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, -5, Keys.SHIFT_RIGHT, Keys.DOWN, Keys.RIGHT, null } 
        },
        "png/keyboard_portrait_12x6.png",
        1.0f,
        130
      ),
  PORTRAIT_10x7(
        new Integer[][] {
          { Keys.CONTROL_LEFT, Keys.LEFT, Keys.UP, Keys.F1, Keys.F3, Keys.F5, Keys.F7, Keys.POUND, Keys.HOME, Keys.DEL, null },
          { Keys.COLON, Keys.SEMICOLON, Keys.EQUALS, Keys.AT, Keys.PLUS, Keys.MINUS, Keys.STAR, Keys.SLASH, -4, -5, null },
          { Keys.NUM_1, Keys.NUM_2, Keys.NUM_3, Keys.NUM_4, Keys.NUM_5, Keys.NUM_6, Keys.NUM_7, Keys.NUM_8, Keys.NUM_9, Keys.NUM_0, null },
          { Keys.Q, Keys.W, Keys.E, Keys.R, Keys.T, Keys.Y, Keys.U, Keys.I, Keys.O, Keys.P, null },
          { Keys.A, Keys.S, Keys.D, Keys.F, Keys.G, Keys.H, Keys.J, Keys.K, Keys.L, Keys.ENTER, null },
          { Keys.Z, Keys.X, Keys.C, Keys.V, Keys.B, Keys.N, Keys.M, Keys.COMMA, Keys.PERIOD, Keys.ENTER, null },
          { Keys.ALT_LEFT, -3, Keys.SHIFT_LEFT, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SHIFT_RIGHT, Keys.DOWN, Keys.RIGHT, null }
        },
        "png/keyboard_portrait_10x7.png",
        1.0f,
        130
      ),
  MOBILE_ON_SCREEN,
  OFF;
  
  /**
   * The size of the keys in this KeyboardType.
   */
  private int keySize;
  
  /**
   * The position of each key within this KeyboardType.
   */
  private Integer[][] keyMap;
  
  /**
   * The Texture holding the keyboard image for this KeyboardType.
   */
  private Texture texture;
  
  /**
   * The path to the keyboard image file.
   */
  private String keyboardImage;
  
  /**
   * The Camera to be used when displaying this KeyboardType.
   */
  private Camera camera;
  
  /**
   * The Viewport to be used when displaying this KeyboardType.
   */
  private Viewport viewport;
  
  /**
   * The opacity of this KeyboardType.
   */
  private float opacity;
  
  /**
   * Offset from the bottom of the screen that the keyboard is rendered at.
   */
  private int renderOffset;
  
  /**
   * Constructor for KeyboardType.
   * 
   * @param keyMap The position of each key within this KeyboardType.
   * @param keyboardImage The path to the keyboard image file.
   * @param opacity The opacity of this KeyboardType.
   * @param renderOffset Offset from the bottom of the screen that the keyboard is rendered at.
   */
  KeyboardType(Integer[][] keyMap, String keyboardImage, float opacity, int renderOffset) {
    this.keyMap = keyMap;
    this.keyboardImage = keyboardImage;
    this.texture = new Texture(keyboardImage);
    this.camera = new OrthographicCamera();
    this.viewport = new ExtendViewport(texture.getWidth(), texture.getHeight(), camera);
    this.keySize = (this.texture.getHeight() / this.keyMap.length);
    this.opacity = opacity;
    this.renderOffset = renderOffset;
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
    Integer keyCode = null;
    
    // Work out the row for this x/y position. This is common to all layouts.
    int keyRow = (int)((texture.getHeight() - y + renderOffset) / keySize);
    if (keyRow > keyMap.length) keyRow = keyMap.length - 1;
    
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
        keyCode = keyMap[keyRow][(int)(x / (keySize / 2f))];
        break;
    
      case PORTRAIT_12x6:
        // First row of 12x6 layout is mapped by half key.
        if (keyRow == 0) x *= 2;
        
      case PORTRAIT_10x7:
        keyCode = keyMap[keyRow][(int)(x / keySize)];
        break;
        
      default:
        break;
    }
    
    return keyCode;
  }
  
  /**
   * @return The Texture holding the keyboard image for this KeyboardType.
   */
  public Texture getTexture() {
    if ((texture == null) && (keyboardImage != null)) {
      texture = new Texture(keyboardImage);
    }
    return texture;
  }

  /**
   * @return The Camera to be used when displaying this KeyboardType.
   */
  public Camera getCamera() {
    return camera;
  }

  /**
   * @return The Viewport to be used when displaying this KeyboardType.
   */
  public Viewport getViewport() {
    return viewport;
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
    return (texture != null);
  }

  /**
   * @return Offset from the bottom of the screen that the keyboard is rendered at.
   */
  public int getRenderOffset() {
    return renderOffset;
  }
  
  /**
   * Disposes of the libGDX Textures for all KeyboardTypes.
   */
  public static void dispose() {
    for (KeyboardType keyboardType : KeyboardType.values()) {
      if (keyboardType.texture != null) {
        keyboardType.texture.dispose();
        keyboardType.texture = null;
      }
    }
  }
  
  /**
   * Re-creates the libGDX Textures for all KeyboardTypes. 
   */
  public static void init() {
    for (KeyboardType keyboardType : KeyboardType.values()) {
      keyboardType.getTexture();
    }
  }
}
