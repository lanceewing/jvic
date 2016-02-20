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

  LANDSCAPE(
        new int[][] {
          { Keys.CONTROL_LEFT, Keys.LEFT, Keys.UP, Keys.F1, -1, Keys.F3, Keys.F5, -2, Keys.F7, 0/* £ */, Keys.HOME, Keys.DEL },
          { Keys.NUM_1, Keys.NUM_2, Keys.NUM_3, Keys.NUM_4, Keys.NUM_5, Keys.NUM_6, Keys.NUM_7, Keys.NUM_8, Keys.NUM_9, Keys.NUM_0, Keys.PLUS, Keys.MINUS },
          { Keys.Q, Keys.W, Keys.E, Keys.R, Keys.T, Keys.Y, Keys.U, Keys.I, Keys.O, Keys.P, Keys.AT, Keys.STAR },
          { Keys.A, Keys.S, Keys.D, Keys.F, Keys.G, Keys.H, Keys.J, Keys.K, Keys.L, Keys.COLON, Keys.SEMICOLON, Keys.EQUALS },
          { Keys.Z, Keys.X, Keys.C, Keys.V, Keys.B, Keys.N, Keys.M, Keys.COMMA, Keys.PERIOD, Keys.SLASH, Keys.ENTER, Keys.ENTER },
          { Keys.ALT_LEFT, -3, Keys.SHIFT_LEFT, -4, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, -5, Keys.SHIFT_RIGHT, Keys.DOWN, Keys.RIGHT } 
        },
        "png/keyboard_landscape.png",
        0.5f
      ),
  PORTRAIT_12x6(
        new int[][] {
          { Keys.CONTROL_LEFT, Keys.LEFT, Keys.UP, Keys.F1, -1, Keys.F3, Keys.F5, -2, Keys.F7, 0/* £ */, Keys.HOME, Keys.DEL },
          { Keys.NUM_1, Keys.NUM_2, Keys.NUM_3, Keys.NUM_4, Keys.NUM_5, Keys.NUM_6, Keys.NUM_7, Keys.NUM_8, Keys.NUM_9, Keys.NUM_0, Keys.PLUS, Keys.MINUS },
          { Keys.Q, Keys.W, Keys.E, Keys.R, Keys.T, Keys.Y, Keys.U, Keys.I, Keys.O, Keys.P, Keys.AT, Keys.STAR },
          { Keys.A, Keys.S, Keys.D, Keys.F, Keys.G, Keys.H, Keys.J, Keys.K, Keys.L, Keys.COLON, Keys.SEMICOLON, Keys.EQUALS },
          { Keys.Z, Keys.X, Keys.C, Keys.V, Keys.B, Keys.N, Keys.M, Keys.COMMA, Keys.PERIOD, Keys.SLASH, Keys.ENTER, Keys.ENTER },
          { Keys.ALT_LEFT, -3, Keys.SHIFT_LEFT, -4, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, -5, Keys.SHIFT_RIGHT, Keys.DOWN, Keys.RIGHT } 
        },
        "png/keyboard_portrait_12x6.png",
        1.0f
      ),
  PORTRAIT_10x7(
        new int[][] {
          { Keys.CONTROL_LEFT, Keys.LEFT, Keys.UP, Keys.F1, Keys.F3, Keys.F5, Keys.F7, /* pound */0, Keys.HOME, Keys.DEL },
          { Keys.COLON, Keys.SEMICOLON, Keys.EQUALS, Keys.AT, Keys.PLUS, Keys.MINUS, Keys.STAR, Keys.SLASH, -4, -5 },
          { Keys.NUM_1, Keys.NUM_2, Keys.NUM_3, Keys.NUM_4, Keys.NUM_5, Keys.NUM_6, Keys.NUM_7, Keys.NUM_8, Keys.NUM_9, Keys.NUM_0 },
          { Keys.Q, Keys.W, Keys.E, Keys.R, Keys.T, Keys.Y, Keys.U, Keys.I, Keys.O, Keys.P },
          { Keys.A, Keys.S, Keys.D, Keys.F, Keys.G, Keys.H, Keys.J, Keys.K, Keys.L, Keys.ENTER },
          { Keys.Z, Keys.X, Keys.C, Keys.V, Keys.B, Keys.N, Keys.M, Keys.COMMA, Keys.PERIOD, Keys.ENTER },
          { Keys.ALT_LEFT, -3, Keys.SHIFT_LEFT, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SHIFT_RIGHT, Keys.DOWN, Keys.RIGHT }
        },
        "png/keyboard_portrait_10x7.png",
        1.0f
      );
  
  /**
   * The size of the keys in this KeyboardType.
   */
  private int keySize;
  
  /**
   * The position of each key within this KeyboardType.
   */
  private int[][] keyMap;
  
  /**
   * The Texture holding the keyboard image for this KeyboardType.
   */
  private Texture texture;
  
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
   * Constructor for KeyboardType.
   * 
   * @param keyMap The position of each key within this KeyboardType.
   * @param keyboardImage The path to the keyboard image file.
   * @param opacity The opacity of this KeyboardType.
   */
  KeyboardType(int[][] keyMap, String keyboardImage, float opacity) {
    this.keyMap = keyMap;
    this.texture = new Texture(keyboardImage);
    this.camera = new OrthographicCamera();
    this.viewport = new ExtendViewport(texture.getWidth(), texture.getHeight(), camera);
    this.keySize = (this.texture.getHeight() / this.keyMap.length);
    this.opacity = opacity;
  }

  /**
   * Gets the keycode that is mapped to the given X and Y world coordinates.
   * 
   * @param x The X position within this KeyboardType's world coordinates.
   * @param y The Y position within this KeyboardType's world coordinates.
   * 
   * @return The keycode that is mapped to the given X and Y world coordinates.
   */
  public Integer getKeyCode(float x, float y) {
    int keyColumn = (int)(x / keySize);
    int keyRow = (int)((texture.getHeight() - y) / keySize);
    if (keyColumn >= texture.getWidth()) keyColumn = texture.getWidth() - 1;
    int keyCode = keyMap[keyRow][keyColumn];
    return keyCode;
  }
  
  /**
   * @return The Texture holding the keyboard image for this KeyboardType.
   */
  public Texture getTexture() {
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
}
