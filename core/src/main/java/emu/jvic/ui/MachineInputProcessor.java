package emu.jvic.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.Application.ApplicationType;
import com.badlogic.gdx.Input.Buttons;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.viewport.ExtendViewport;

import emu.jvic.KeyboardMatrix;
import emu.jvic.KeyboardType;
import emu.jvic.MachineScreen;
import emu.jvic.MachineType;

/**
 * InputProcessor for the MachineScreen.
 * 
 * @author Lance Ewing
 */
public class MachineInputProcessor extends InputAdapter {

    /**
     * The MachineScreen that this InputProcessor is processing input for.
     */
    private MachineScreen machineScreen;

    /**
     * The type of keyboard currently being used for input.
     */
    private KeyboardType keyboardType;

    /**
     * The current alignment of the joystick on screen, if active, otherwise
     * set to the OFF value.
     */
    private JoystickAlignment joystickAlignment = JoystickAlignment.OFF;
    
    /**
     * The current screen size.
     */
    private ScreenSize screenSize = ScreenSize.FIT;
    
    /**
     * Whether or not the speaker is currently active, i.e. sound is on.
     */
    private boolean speakerOn;
    
    /**
     * The current offset from centre of the camera in the X direction.
     */
    private float cameraXOffset;
    
    /**
     * Invoked by JVic whenever it would like to show a dialog, such as when it
     * needs the user to confirm an action, or to choose a file.
     */
    private DialogHandler dialogHandler;

    /**
     * The one and only ViewportManager used by JVic.
     */
    private ViewportManager viewportManager;

    /**
     * We only track up to a maximum number of simultaneous touch events.
     */
    private static final int MAX_SIMULTANEOUS_TOUCH_EVENTS = 5;

    /**
     * Array of current touches indexed by touch pointer ID. This Map allows us to
     * keep drag of active dragging. If a drag happens to start within a keyboard
     * key and then leaves it before being released, we need to automatically fire a
     * key up event for our virtual keyboard. Without handling this, drags can
     * completely confuse the keyboard state. And the joystick logic relies on
     * dragging, so this needs to work well.
     */
    private TouchInfo[] touches;

    /**
     * Represents the touch info for a particular pointer ID.
     */
    class TouchInfo {
        float startX;
        float startY;
        float lastX;
        float lastY;
        Integer lastKey;
    }

    /**
     * The width of the screen/window before full screen mode was activated.
     */
    private int screenWidthBeforeFullScreen;

    /**
     * The height of the screen/width before ful screen mode was activated.
     */
    private int screenHeightBeforeFullScreen;

    /**
     * Constructor for MachineInputProcessor.
     * 
     * @param machineScreen
     * @param dialogHandler
     */
    public MachineInputProcessor(MachineScreen machineScreen, DialogHandler dialogHandler) {
        this.machineScreen = machineScreen;
        this.dialogHandler = dialogHandler;
        this.keyboardType = KeyboardType.OFF;
        this.viewportManager = ViewportManager.getInstance();

        // Initialise the touch info for max num of pointers (multi touch). We create
        // these up front and reuse them so as to avoid garbage collection.
        this.touches = new TouchInfo[MAX_SIMULTANEOUS_TOUCH_EVENTS];
        for (int i = 0; i < MAX_SIMULTANEOUS_TOUCH_EVENTS; i++) {
            touches[i] = new TouchInfo();
        }
    }

    /**
     * Handle keys that are not mapped to the VIC keyboard, such as the
     * function keys.
     * 
     * @param keycode 
     */
    public boolean keyUp(int keycode) {
        if (keycode == Keys.F6) {
            if (!machineScreen.getJvicRunner().isWarpSpeed()) {
                speakerOn = false;
                machineScreen.getJvicRunner().changeSound(false);
            }
            machineScreen.getJvicRunner().toggleWarpSpeed();
            return true;
        }
        else if (keycode == Keys.F3) {
            machineScreen.getJvicRunner().sendNmi();
            return true;
        }
        else if (keycode == Keys.F11) {
            if (!Gdx.app.getType().equals(ApplicationType.WebGL)) {
                Boolean fullScreen = Gdx.graphics.isFullscreen();
                if (fullScreen == true) {
                    switchOutOfFullScreen();
                }
                else {
                    switchIntoFullScreen();
                }
            }
        }
        else if (keycode == Keys.F12) {
            machineScreen.saveScreenshot();
        }
        return false;
    }

    /**
     * Called when the screen was touched or a mouse button was pressed. The button
     * parameter will be {@link Buttons#LEFT} on iOS.
     * 
     * @param screenX The x coordinate, origin is in the upper left corner
     * @param screenY The y coordinate, origin is in the upper left corner
     * @param pointer the pointer for the event.
     * @param button  the button
     * 
     * @return whether the input was processed
     */
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        // Convert the screen coordinates to world coordinates.
        Vector2 touchXY = viewportManager.unproject(screenX, screenY);

        // Update the touch info for this pointer.
        TouchInfo touchInfo = null;
        if (pointer < MAX_SIMULTANEOUS_TOUCH_EVENTS) {
            touchInfo = touches[pointer];
            touchInfo.startX = touchInfo.lastX = touchXY.x;
            touchInfo.startY = touchInfo.lastY = touchXY.y;
            touchInfo.lastKey = null;
        }

        // If the tap is within the keyboard...
        if (keyboardType.isInKeyboard(touchXY.x, touchXY.y)) {
            Integer keycode = keyboardType.getKeyCode(touchXY.x, touchXY.y);
            if (keycode != null) {
                processVirtualKeyboardKeyDown(keycode);
            }
            if (touchInfo != null) {
                touchInfo.lastKey = keycode;
            }
        }

        return true;
    }

    /**
     * Called when a finger was lifted or a mouse button was released. The button
     * parameter will be {@link Buttons#LEFT} on iOS.
     * 
     * @param pointer the pointer for the event.
     * @param button  the button
     * 
     * @return whether the input was processed
     */
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        // Convert the screen coordinates to world coordinates.
        Vector2 touchXY = viewportManager.unproject(screenX, screenY);

        // Update the touch info for this pointer.
        TouchInfo touchInfo = null;
        if (pointer < MAX_SIMULTANEOUS_TOUCH_EVENTS) {
            touchInfo = touches[pointer];
            touchInfo.lastX = touchXY.x;
            touchInfo.lastY = touchXY.y;
            touchInfo.lastKey = null;
        }

        if (keyboardType.isInKeyboard(touchXY.x, touchXY.y)) {
            Integer keycode = keyboardType.getKeyCode(touchXY.x, touchXY.y);
            if (keycode != null) {
                processVirtualKeyboardKeyUp(keycode);
            }
        } else {
            // TODO: Need to handle the magic numbers in this block in a better way.
            boolean keyboardClicked = false;
            boolean backArrowClicked = false;
            boolean fullScreenClicked = false;
            boolean speakerClicked = false;
            boolean screenSizeClicked = false;
            boolean pausePlayClicked = false;
            boolean joystickClicked = false;

            if (viewportManager.isPortrait()) {
                // Portrait.
                if (touchXY.y < 135) {
                    if (touchXY.x < 126) {
                        fullScreenClicked = true;
                    } else if (touchXY.x > (viewportManager.getWidth() - 126)) {
                        backArrowClicked = true;
                    } else {
                        float sixthPos = (viewportManager.getWidth() / 6);
                        float halfPos = (viewportManager.getWidth() / 2);
                        float thirdPos = (viewportManager.getWidth() / 3);
                        float twoThirdPos = (viewportManager.getWidth() - (viewportManager.getWidth() / 3));
                        float fiveSixths = (viewportManager.getWidth() - (viewportManager.getWidth() / 6));
                        
                        if ((touchXY.x > (sixthPos - 26)) && (touchXY.x < (sixthPos + 100))) {
                            screenSizeClicked = true;
                        }
                        else if ((touchXY.x > (thirdPos - 42)) && (touchXY.x < (thirdPos + 84))) {
                            speakerClicked = true;
                        }
                        else if ((touchXY.x > (halfPos - 58)) && (touchXY.x < (halfPos + 58))) {
                            pausePlayClicked = true;
                        }
                        else if ((touchXY.x > (twoThirdPos - 84)) && (touchXY.x < (twoThirdPos + 42))) {
                            keyboardClicked = true;
                        }
                        else if ((touchXY.x > (fiveSixths - 100)) && (touchXY.x < (fiveSixths + 26))) {
                            joystickClicked = true;
                        }
                    }
                }
            } else {
                // Landscape.
                int screenTop = (int) viewportManager.getHeight();
                if (cameraXOffset == 0) {
                    if ((viewportManager.getVICScreenBase() > 0) || (viewportManager.getSidePaddingWidth() <= 64)) {
                        if (touchXY.y < 104) {
                            float leftAdjustment = (viewportManager.getWidth() / 4) - 48;
                            if ((touchXY.x >= ((viewportManager.getWidth() - ((viewportManager.getWidth() * 6 ) / 12)) - 96) - leftAdjustment) && 
                                (touchXY.x <= ((viewportManager.getWidth() - ((viewportManager.getWidth() * 6 ) / 12)) - 0) - leftAdjustment)) {
                                fullScreenClicked = true;
                            }
                            else
                            if ((touchXY.x >= ((viewportManager.getWidth() - ((viewportManager.getWidth() * 5 ) / 12)) - 96) - leftAdjustment) && 
                                (touchXY.x <= ((viewportManager.getWidth() - ((viewportManager.getWidth() * 5 ) / 12)) - 0) - leftAdjustment)) {
                                screenSizeClicked = true;
                            } 
                            else 
                            if ((touchXY.x >= ((viewportManager.getWidth() - ((viewportManager.getWidth() * 4 ) / 12)) - 96) - leftAdjustment) && 
                                (touchXY.x <= ((viewportManager.getWidth() - ((viewportManager.getWidth() * 4 ) / 12)) - 0) - leftAdjustment)) {
                                speakerClicked = true;
                            }
                            else
                            if ((touchXY.x >= ((viewportManager.getWidth() - ((viewportManager.getWidth() * 3 ) / 12)) - 96) - leftAdjustment) && 
                                (touchXY.x <= ((viewportManager.getWidth() - ((viewportManager.getWidth() * 3 ) / 12)) - 0) - leftAdjustment)) {
                                pausePlayClicked = true;
                            }
                            else
                            if ((touchXY.x >= ((viewportManager.getWidth() - ((viewportManager.getWidth() * 2 ) / 12)) - 96) - leftAdjustment) && 
                                (touchXY.x <= ((viewportManager.getWidth() - ((viewportManager.getWidth() * 2 ) / 12)) - 0) - leftAdjustment)) {
                                keyboardClicked = true;
                            }
                            else
                            if ((touchXY.x >= ((viewportManager.getWidth() - ((viewportManager.getWidth() * 1 ) / 12)) - 96) - leftAdjustment) && 
                                (touchXY.x <= ((viewportManager.getWidth() - ((viewportManager.getWidth() * 1 ) / 12)) - 0) - leftAdjustment)) {
                                joystickClicked = true;
                            }
                            else
                            if ((touchXY.x >= ((viewportManager.getWidth() - ((viewportManager.getWidth() * 0 ) / 12)) - 96) - leftAdjustment) && 
                                (touchXY.x <= ((viewportManager.getWidth() - ((viewportManager.getWidth() * 0 ) / 12)) - 0) - leftAdjustment)) {
                                backArrowClicked = true;
                            }
                        }
                    } else {
                        // Screen in middle, buttons either side.
                        if (touchXY.y > (screenTop - 104)) {
                            if (touchXY.x < 112) {
                                speakerClicked = true;
                            } else if (touchXY.x > (viewportManager.getWidth() - 112)) {
                                fullScreenClicked = true;
                            }
                        } else if (touchXY.y < 104) {
                            if (touchXY.x > (viewportManager.getWidth() - 112)) {
                                backArrowClicked = true;
                            } else if (touchXY.x < 112) {
                                keyboardClicked = true;
                            }
                        } else if ((touchXY.y > (viewportManager.getHeight() - (viewportManager.getHeight() / 3)) - 74) &&
                                   (touchXY.y < (viewportManager.getHeight() - (viewportManager.getHeight() / 3)) + 42)) {
                            if (touchXY.x > (viewportManager.getWidth() - 112)) {
                                screenSizeClicked = true;
                            } else if (touchXY.x < 112) {
                                pausePlayClicked = true;
                            }
                        } else if ((touchXY.y > (viewportManager.getHeight() / 3) - 42) &&
                                (touchXY.y < (viewportManager.getHeight() / 3) + 74)) {
                             if (touchXY.x > (viewportManager.getWidth() - 112)) {
                                 if (!joystickAlignment.equals(JoystickAlignment.RIGHT)) {
                                     joystickClicked = true;
                                 }
                             } else if (touchXY.x < 112) {
                                 if (joystickAlignment.equals(JoystickAlignment.RIGHT)) {
                                     joystickClicked = true;
                                 }
                             }
                         }
                    }
                }
                else {
                    // All buttons on same side
                    if (((touchXY.x < 128) && (cameraXOffset < 0)) || 
                        ((touchXY.x > (viewportManager.getWidth() - 128)) && (cameraXOffset > 0))) {
                        
                        if (touchXY.y > (viewportManager.getHeight() - 126)) {
                            fullScreenClicked = true;
                        } else if (touchXY.y < 126) {
                            backArrowClicked = true;
                        } else {
                            float sixthPos = (viewportManager.getHeight() / 6);
                            float halfPos = (viewportManager.getHeight() / 2);
                            float thirdPos = (viewportManager.getHeight() / 3);
                            float twoThirdPos = (viewportManager.getHeight() - (viewportManager.getHeight() / 3));
                            float fiveSixths = (viewportManager.getHeight() - (viewportManager.getHeight() / 6));
                            
                            if ((touchXY.y > (sixthPos - 26)) && (touchXY.y < (sixthPos + 100))) {
                                keyboardClicked = true;
                            }
                            else if ((touchXY.y > (thirdPos - 42)) && (touchXY.y < (thirdPos + 84))) {
                                joystickClicked = true;
                            }
                            else if ((touchXY.y > (halfPos - 58)) && (touchXY.y < (halfPos + 58))) {
                                pausePlayClicked = true;
                            }
                            else if ((touchXY.y > (twoThirdPos - 84)) && (touchXY.y < (twoThirdPos + 42))) {
                                speakerClicked = true;
                            }
                            else if ((touchXY.y > (fiveSixths - 100)) && (touchXY.y < (fiveSixths + 26))) {
                                screenSizeClicked = true;
                            }
                        }
                    }
                }
            }
            
            if (keyboardClicked) {
                if (keyboardType.equals(KeyboardType.OFF)) {
                    keyboardType = (viewportManager.isPortrait() ? KeyboardType.PORTRAIT : KeyboardType.LANDSCAPE);
                    viewportManager.update();
                } else {
                    keyboardType = KeyboardType.OFF;
                }
            }
            
            if (joystickClicked) {
                // Rotate the joystick screen alignment.
                joystickAlignment = joystickAlignment.rotateValue();
                if (viewportManager.isLandscape() && ((cameraXOffset != 0))) {
                    if (joystickAlignment.equals(JoystickAlignment.RIGHT)) {
                        joystickAlignment = joystickAlignment.rotateValue();
                    }
                }
            }
            
            if (speakerClicked) {
                speakerOn = !speakerOn;
                machineScreen.getJvicRunner().changeSound(speakerOn);
            }
            
            if (screenSizeClicked) {
                rotateScreenSize();
            }
            
            if (pausePlayClicked) {
                if (machineScreen.getJvicRunner().isPaused()) {
                    machineScreen.getJvicRunner().resume();
                } else {
                    machineScreen.getJvicRunner().pause();
                }
            }
            
            if (fullScreenClicked) {
                Boolean fullScreen = Gdx.graphics.isFullscreen();
                if (fullScreen == true) {
                    switchOutOfFullScreen();
                }
                else {
                    switchIntoFullScreen();
                }
            }
            
            if (backArrowClicked) {
                if (Gdx.app.getType().equals(ApplicationType.Desktop) && Gdx.graphics.isFullscreen()) {
                    // Dialog won't show for desktop unless we exit full screen,
                    switchOutOfFullScreen();
                }
                
                dialogHandler.confirm("Are you sure you want to quit the game?", 
                        new ConfirmResponseHandler() {
                    @Override
                    public void yes() {
                        machineScreen.getJvicRunner().stop();
                    }
                    
                    @Override
                    public void no() {
                        // Nothing to do.
                    }
                });
            }
            
            if (!(backArrowClicked || fullScreenClicked || pausePlayClicked || screenSizeClicked || 
                    speakerClicked || joystickClicked || keyboardClicked)) {
                KeyboardMatrix keyboardMatrix = machineScreen.getJvicRunner().getKeyboardMatrix();
                keyboardMatrix.keyDown(Keys.INSERT);
                keyboardMatrix.keyUp(Keys.INSERT);
            }
        }

        return true;
    }

    public void adjustWorldMinMax(int width, int height, MachineType machineType) {
        ExtendViewport viewport = machineScreen.getViewport();
        
        // Keep rotating until the screen size will fit the current dimensions.
        while (((screenSize.getRenderHeight(machineType) > Gdx.graphics.getHeight()) || 
                (screenSize.getRenderWidth(machineType) > Gdx.graphics.getWidth())) && 
                (screenSize != ScreenSize.FIT)) {
            screenSize = screenSize.rotateValue();
        }
        
        if (screenSize == ScreenSize.FIT) {
            viewport.setMinWorldWidth(ScreenSize.FIT.getRenderWidth(machineType));
            viewport.setMaxWorldWidth(0);
            viewport.setMinWorldHeight(ScreenSize.FIT.getRenderHeight(machineType));
            viewport.setMaxWorldHeight(0);
            viewport.setScaling(Scaling.fit);
        } else {
            viewport.setMinWorldWidth(width);
            viewport.setMaxWorldWidth(width);
            viewport.setMinWorldHeight(height);
            viewport.setMaxWorldHeight(height);
            viewport.setScaling(Scaling.none);
        }
    }
    
    public void rotateScreenSize() {
        ExtendViewport viewport = machineScreen.getViewport();
        
        if (viewportManager.isPortrait()) {
            // Portrait always uses FIT.
            screenSize = ScreenSize.FIT;
        }
        else {
            screenSize = screenSize.rotateValue();
        }
        
        adjustWorldMinMax(viewport.getScreenWidth(), viewport.getScreenHeight(),
                machineScreen.getMachineType());
    }
    
    /**
     * Switches to full screen mode, storing the width and height beforehand so that
     * it can be restored when switching back.
     */
    public void switchIntoFullScreen() {
        keyboardType = KeyboardType.OFF;
        Graphics.DisplayMode currentMode = Gdx.graphics.getDisplayMode();
        screenWidthBeforeFullScreen = Gdx.graphics.getWidth();
        screenHeightBeforeFullScreen = Gdx.graphics.getHeight();
        Gdx.graphics.setFullscreenMode(currentMode);
    }

    /**
     * Switches out of full screen mode back to the windowed mode, restoring the
     * saved width and height.
     */
    public void switchOutOfFullScreen() {
        if (screenWidthBeforeFullScreen > (screenHeightBeforeFullScreen * 1.25f)) {
            keyboardType = KeyboardType.OFF;
        }
        Gdx.graphics.setWindowedMode(screenWidthBeforeFullScreen, screenHeightBeforeFullScreen);
    }

    /**
     * Called when a finger or the mouse was dragged.
     * 
     * @param pointer the pointer for the event.
     * 
     * @return whether the input was processed
     */
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        // Convert the screen coordinates to world coordinates.
        Vector2 touchXY = viewportManager.unproject(screenX, screenY);
        
        // TODO: Do we really need drag support for the keyboard? Doesn't seem useful.
        
        // TODO: Extend drag handle to experiment with joystick mechanism.

        // Update the touch info for this pointer.
        TouchInfo touchInfo = null;
        if (pointer < MAX_SIMULTANEOUS_TOUCH_EVENTS) {
            touchInfo = touches[pointer];

            Integer lastKey = touchInfo.lastKey;
            Integer newKey = null;

            if (keyboardType.isInKeyboard(touchXY.x, touchXY.y)) {
                newKey = keyboardType.getKeyCode(touchXY.x, touchXY.y);
            }

            // If the drag has resulting in the position moving in to or out of a key, then
            // we simulate the coresponding key events.
            if ((lastKey != null) && ((newKey == null) || (newKey != lastKey))) {
                processVirtualKeyboardKeyUp(lastKey);
            }
            if ((newKey != null) && ((lastKey == null) || (lastKey != newKey))) {
                processVirtualKeyboardKeyDown(newKey);
            }

            // Finally we update the new last position and last key for this pointer.
            touchInfo.lastX = touchXY.x;
            touchInfo.lastY = touchXY.y;
            touchInfo.lastKey = newKey;
        }

        return true;
    }

    /**
     * Invokes by its MachineScreen when the screen has resized.
     * 
     * @param width  The new screen width.
     * @param height The new screen height.
     */
    public void resize(int width, int height) {
        if (height > (width / 1.33f)) {
            // Change to portrait if it is not already a portrait keyboard.
            if (!keyboardType.isPortrait()) {
                keyboardType = KeyboardType.PORTRAIT;
            }
            
            // For non-standard portrait sizes, where the keyboard would overlap the
            // screen, we turn the keyboard off on resize.
            // TODO: The 1.77 probably needs adjustment for JVic.
            if (((float)height/width) < 1.77) {
                keyboardType = KeyboardType.OFF;
            }
        } else if (keyboardType.isRendered()) {
            // If it wasn't previously landscape, then turn it off.
            if (!keyboardType.isLandscape()) {
                keyboardType = KeyboardType.OFF;
            }
        }
    }

    /**
     * Gets the current KeyboardType that is being used for input.
     * 
     * @return The current KeyboardType this is being used for input.
     */
    public KeyboardType getKeyboardType() {
        return keyboardType;
    }
    
    public KeyboardMatrix getKeyboardMatrix() {
        return machineScreen.getJvicRunner().getKeyboardMatrix();
    }
    
    private void processVirtualKeyboardKeyDown(int vicKey) {
        getKeyboardMatrix().vicKeyDown(vicKey & 0xFF);
    }
    
    private void processVirtualKeyboardKeyUp(int vicKey) {
        getKeyboardMatrix().vicKeyUp(vicKey & 0xFF);
    }
    
    /**
     * Gets the current joystick screen alignment, i.e. where to place it on the 
     * screen (left aligned, middle aligned, right aligned, or turned off)
     * 
     * @return The current joystick screen alignment.
     */
    public JoystickAlignment getJoystickAlignment() {
        return joystickAlignment;
    }
    
    /**
     * Sets the current joystick screen alignment, i.e. where to place it on the 
     * screen (left aligned, middle aligned, right aligned, or turned off)
     * 
     * @param joystickAlignment
     */
    public void setJoystickAlignment(JoystickAlignment joystickAlignment) {
        this.joystickAlignment = joystickAlignment;
    }

    public static enum JoystickAlignment {
        OFF, LEFT, RIGHT;
        
        JoystickAlignment rotateValue() {
            return values()[(ordinal() + 1) % 3];
        }
    }

    /**
     * Returns whether the speaker is on or not.
     * 
     * @return
     */
    public boolean isSpeakerOn() {
        return speakerOn;
    }

    /**
     * Sets whether the speaker is on or not.
     * 
     * @param speakerOn
     */
    public void setSpeakerOn(boolean speakerOn) {
        this.speakerOn = speakerOn;
    }

    public static enum ScreenSize {
        
        FIT(363, 272,  335,  252),     // 1.33 / 1.33
        X7(2420, 1904, 2400, 1764),    // 1.27 / 1.36
        X6(2200, 1632, 2000, 1512),    // 1.35 / 1.32
        X5(1760, 1360, 1600, 1260),    // 1.29 / 1.27
        X4(1320, 1088, 1400, 1008),    // 1.21 / 1.39
        X3(1100, 816,  1000, 756),     // 1.35 / 1.32
        X2(660,  544,  600,  504)      // 1.21 / 1.19
        ;
        
        int palRenderWidth;
        int palRenderHeight;
        int ntscRenderWidth;
        int ntscRenderHeight;
        
        ScreenSize(int palRenderWidth, int palRenderHeight, int ntscRenderWidth, int ntscRenderHeight) {
            this.palRenderWidth = palRenderWidth;
            this.palRenderHeight = palRenderHeight;
            this.ntscRenderWidth = ntscRenderWidth;
            this.ntscRenderHeight = ntscRenderHeight;
        }
        
        ScreenSize rotateValue() {
            return values()[(ordinal() + 1) % 7];  // WAS 9
        }
        
        public int getRenderWidth(MachineType machineType) {
            return MachineType.PAL.equals(machineType)? palRenderWidth : ntscRenderWidth;
        }
        
        public int getRenderHeight(MachineType machineType) {
            return MachineType.PAL.equals(machineType)? palRenderHeight : ntscRenderHeight;
        }
    }
    
    /**
     * Gets the current screen size.
     * 
     * @return
     */
    public ScreenSize getScreenSize() {
        return screenSize;
    }

    /**
     * Sets the screen size.
     * 
     * @param screenSize
     */
    public void setScreenSize(ScreenSize screenSize) {
        this.screenSize = screenSize;
    }

    /**
     * Sets the current offset from centre of the camera in the X direction.
     * 
     * @param cameraXOffset The current offset from centre of the camera in the X direction.
     */
    public void setCameraXOffset(float cameraXOffset) {
        this.cameraXOffset = cameraXOffset;
    }
}
