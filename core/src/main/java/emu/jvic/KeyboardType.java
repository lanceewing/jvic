package emu.jvic;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Texture;

import emu.jvic.ui.ViewportManager;

import static emu.jvic.KeyboardMatrix.RESTORE;
import static emu.jvic.KeyboardMatrix.RUN_STOP;
import static emu.jvic.KeyboardMatrix.SHIFT_LOCK;

/**
 * Enum representing the different types of keyboard available within JVic.
 * 
 * @author Lance Ewing
 */
public enum KeyboardType {

      LANDSCAPE(
            new Integer[][] {
              { Keys.LEFT, Keys.LEFT, Keys.NUM_1, Keys.NUM_1, Keys.NUM_2, Keys.NUM_2, Keys.NUM_3, Keys.NUM_3, Keys.NUM_4, Keys.NUM_4, Keys.NUM_5, Keys.NUM_5, Keys.NUM_6, Keys.NUM_6, Keys.NUM_7, Keys.NUM_7, Keys.NUM_8, Keys.NUM_8, Keys.NUM_9, Keys.NUM_9, Keys.NUM_0, Keys.NUM_0, Keys.PLUS, Keys.PLUS, Keys.MINUS, Keys.MINUS, Keys.POUND, Keys.POUND, Keys.HOME, Keys.HOME, Keys.DEL, Keys.DEL },
              { Keys.CONTROL_LEFT, Keys.CONTROL_LEFT, Keys.CONTROL_LEFT, Keys.Q, Keys.Q, Keys.W, Keys.W, Keys.E, Keys.E, Keys.R, Keys.R, Keys.T, Keys.T, Keys.Y, Keys.Y, Keys.U, Keys.U, Keys.I, Keys.I, Keys.O, Keys.O, Keys.P, Keys.P, Keys.AT, Keys.AT, Keys.STAR, Keys.STAR, Keys.UP, Keys.UP, RESTORE, RESTORE, RESTORE },
              { RUN_STOP, RUN_STOP, SHIFT_LOCK, SHIFT_LOCK, Keys.A, Keys.A, Keys.S, Keys.S, Keys.D, Keys.D, Keys.F, Keys.F, Keys.G, Keys.G, Keys.H, Keys.H, Keys.J, Keys.J, Keys.K, Keys.K, Keys.L, Keys.L, Keys.COLON, Keys.COLON, Keys.SEMICOLON, Keys.SEMICOLON, Keys.EQUALS, Keys.EQUALS, Keys.ENTER, Keys.ENTER, Keys.ENTER, Keys.ENTER },
              { Keys.ALT_LEFT, Keys.ALT_LEFT, Keys.SHIFT_LEFT, Keys.SHIFT_LEFT, Keys.SHIFT_LEFT, Keys.Z, Keys.Z, Keys.X, Keys.X, Keys.C, Keys.C, Keys.V, Keys.V, Keys.B, Keys.B, Keys.N, Keys.N, Keys.M, Keys.M, Keys.COMMA, Keys.COMMA, Keys.PERIOD, Keys.PERIOD, Keys.SLASH, Keys.SLASH, Keys.SHIFT_RIGHT, Keys.SHIFT_RIGHT, Keys.SHIFT_RIGHT, Keys.DOWN, Keys.DOWN, Keys.RIGHT, Keys.RIGHT },
              { Keys.F1, Keys.F1, Keys.F1, Keys.F3, Keys.F3, Keys.F3, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.F5, Keys.F5, Keys.F5, Keys.F7, Keys.F7, Keys.F7 }
            },
            "png/keyboard_landscape.png",
            0.5f,
            230,
            208,
            1504,
            0
          ),
      PORTRAIT(
            new Integer[][] {
              { Keys.CONTROL_LEFT, Keys.LEFT, Keys.UP, Keys.F1, Keys.F3, Keys.F5, Keys.F7, Keys.POUND, Keys.HOME, Keys.DEL },
              { Keys.COLON, Keys.SEMICOLON, Keys.EQUALS, Keys.AT, Keys.PLUS, Keys.MINUS, Keys.STAR, Keys.SLASH, RUN_STOP, RESTORE },
              { Keys.NUM_1, Keys.NUM_2, Keys.NUM_3, Keys.NUM_4, Keys.NUM_5, Keys.NUM_6, Keys.NUM_7, Keys.NUM_8, Keys.NUM_9, Keys.NUM_0 },
              { Keys.Q, Keys.W, Keys.E, Keys.R, Keys.T, Keys.Y, Keys.U, Keys.I, Keys.O, Keys.P },
              { Keys.A, Keys.S, Keys.D, Keys.F, Keys.G, Keys.H, Keys.J, Keys.K, Keys.L, Keys.ENTER },
              { Keys.Z, Keys.X, Keys.C, Keys.V, Keys.B, Keys.N, Keys.M, Keys.COMMA, Keys.PERIOD, Keys.ENTER },
              { Keys.ALT_LEFT, SHIFT_LOCK, Keys.SHIFT_LEFT, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SHIFT_RIGHT, Keys.DOWN, Keys.RIGHT }
            },
            "png/keyboard_portrait_10x7.png",
            1.0f,
            135,
            0,
            -1,
            0
          ),
      OFF;
    
    /**
     * The horizontal size of the keys in this KeyboardType.
     */
    private float horizKeySize;

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
     * The opacity of this KeyboardType.
     */
    private float opacity;

    /**
     * Offset from the bottom of the screen that the keyboard is rendered at.
     */
    private int renderOffset;
    
    /**
     * The X value at which the keyboard starts in the keyboard image.
     */
    private int xStart;

    /**
     * The Y value at which the keyboard starts in the keyboard image.
     */
    private int yStart;
    
    /**
     * The width of the active part of the keyboard image, or -1 to deduce from texture width and xStart.
     */
    private int activeWidth;

    /**
     * Constructor for KeyboardType.
     * 
     * @param keyMap         The position of each key within this KeyboardType.
     * @param keyboardImages The path to the keyboard image file(s).
     * @param opacity        The opacity of this KeyboardType.
     * @param renderOffset   Offset from the bottom of the screen that the keyboard is rendered at.
     * @param xStart         The X value at which the keyboard starts in the keyboard image.
     * @param activeWidth    The width of the active part of the keyboard image, or
     *                       -1 to deduce from texture width and xStart.
     * @param yStart         The Y value at which the keyboard starts in the keyboard image.
     */
    KeyboardType(Integer[][] keyMap, String keyboardImage, float opacity, int renderOffset,
            int xStart, int activeWidth, int yStart) {
        this.keyMap = keyMap;
        this.keyboardImage = keyboardImage;
        this.texture = new Texture(keyboardImage);
        this.xStart = xStart;
        this.yStart = yStart;
        
        activeWidth = (activeWidth == -1 ? this.texture.getWidth() - this.xStart : activeWidth);
        
        this.horizKeySize = ((float) activeWidth / (float) this.keyMap[0].length);
        this.opacity = opacity;
        this.renderOffset = renderOffset;
        this.activeWidth = activeWidth;
    }

    /**
     * Variant of the Constructor that doesn't support any key mapping, or visual
     * appearance
     */
    KeyboardType() {
    }

    /**
     * Gets the keycode that is mapped to the given X and Y world coordinates.
     * Returns null if there is no matching key at the given position.
     * 
     * @param x The X position within this KeyboardType's world coordinates.
     * @param y The Y position within this KeyboardType's world coordinates.
     * 
     * @return The keycode that is mapped to the given X and Y world coordinates, or
     *         null if there is not match.
     */
    public Integer getKeyCode(float x, float y) {
        Integer keyCode = null;
        float height = getHeight();
        float vertKeySize = ((float) (((float) height) - (float) this.yStart) / (float) this.keyMap.length);
        
        int keyRow = (int) ((getHeight() - (y - yStart) + getRenderOffset()) / vertKeySize);

        if (keyRow >= keyMap.length) {
            keyRow = keyMap.length - 1;
        }

        switch (this) {
            case LANDSCAPE:
            case PORTRAIT:
                if (x >= xStart) {
                    keyCode = keyMap[keyRow][(int) ((x - xStart) / horizKeySize)];
                }
                break;
    
            default:
                break;
        }

        return keyCode;
    }

    /**
     * Gets the height of the keyboard.
     * 
     * @return The current height of the keyboard.
     */
    public float getHeight() {
        if (isLandscape()) {
            return getTexture().getHeight();
        } else {
            ViewportManager viewportManager = ViewportManager.getInstance();
            int keyboardHeight = viewportManager.getVICScreenBase() - getRenderOffset();
            // TODO: Work out the equivalent to 365.
            return Math.max(Math.min(keyboardHeight, texture.getHeight()), 365);
        }
    }
    
    /**
     * Gets the Y value for the top of this KeyboardType.
     * 
     * @return The Y value for the top of this KeyboardType.
     */
    public float getTop() {
        return (getRenderOffset() + getHeight());
    }
    
    /**
     * Tests if the given X/Y position is within the bounds of this KeyboardTypes
     * keyboard image.
     * 
     * @param x The X position to test.
     * @param y The Y position to test.
     * 
     * @return true if the given X/Y position is within the keyboard image;
     *         otherwise false.
     */
    public boolean isInKeyboard(float x, float y) {
        if (isRendered()) {
            float renderOffset = getRenderOffset();
            boolean isInYBounds = (y < (getHeight() + renderOffset) && (y > renderOffset));
            boolean isInXBounds = ((x >= xStart) && (x < (xStart + activeWidth)));
            return isInYBounds && isInXBounds;
        } else {
            // isInKeyboard only applies to rendered keyboards.
            return false;
        }
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
     * @return The opacity of this KeyboardType.
     */
    public float getOpacity() {
        return opacity;
    }

    /**
     * @return true if this KeyboardType is rendered by the JVic render code;
     *         otherwise false.
     */
    public boolean isRendered() {
        return (texture != null);
    }

    /**
     * @return true if this KeyboardType is a landscape keyboard, otherwise false.
     */
    public boolean isLandscape() {
        return (equals(LANDSCAPE));
    }

    /**
     * @return true if this KeyboardType is a portrait keyboard, otherwise false.
     */
    public boolean isPortrait() {
        return (equals(PORTRAIT));
    }

    /**
     * @return Offset from the bottom of the screen that the keyboard is rendered
     *         at.
     */
    public int getRenderOffset() {
        if (isLandscape()) {
            ViewportManager viewportManager = ViewportManager.getInstance();
            if (viewportManager.getVICScreenBase() > 0) {
                // Landscape mode where icons are at bottom.
                return renderOffset;
            } else {
                float sidePaddingWidth = viewportManager.getSidePaddingWidth();
                if (sidePaddingWidth < 128) {
                    if (sidePaddingWidth > 64) {
                        // Landscape mode where icons only on right (but joystick at button).
                        return 0;
                    } else {
                        // Landscape mode where icons at bottom and joystick left and right.
                        return renderOffset;
                    }
                } else {
                    // Landscape mode where icons on left and right (and the joystick).
                    return 0;
                }
            }
        } else {
            return renderOffset;
        }
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
