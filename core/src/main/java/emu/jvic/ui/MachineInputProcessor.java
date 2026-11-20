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

import emu.jvic.JVicRunner;
import emu.jvic.KeyboardMatrix;
import emu.jvic.KeyboardType;
import emu.jvic.MachineScreen;
import emu.jvic.MachineType;
import emu.jvic.VicKeys;

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
     * Whether sound should be turned off when the runner turns it on at the start of a
     * program (the "Sound at start" setting is off).
     */
    private boolean soundOffAtStart;

    /**
     * The side the touch joystick goes on when it is turned on (a setting).
     */
    private JoystickAlignment joystickSide = JoystickAlignment.LEFT;

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
        boolean altKeyDown = getKeyboardMatrix().isAltKeyDown();
        
        if (altKeyDown) {
            switch (keycode) {
                case Keys.ENTER:
                case Keys.D:
                    handleFullScreenToggle();
                    return true;
                case Keys.B:
                    handleShowFPSToggle();
                    return true;
                case Keys.J:
                    handleJoystickAlignment();
                    return true;
                case Keys.K:
                    handleKeyboardToggle();
                    return true;
                case Keys.P:
                    if (getKeyboardMatrix().isShiftKeyDown()) {
                        handleFrameAdvance();
                    } else {
                        handlePauseToggle();
                    }
                    return true;
                case Keys.F9:
                    handleReset(false);
                    return true;
                case Keys.F12:
                    handleReset(true);
                    return true;
                case Keys.F11:
                    handleQuickSave();
                    return true;
                case Keys.F10:
                    handleQuickLoad();
                    return true;
                case Keys.FORWARD_DEL:
                    if (machineScreen.getJvicRunner().isClipboardSupported()) {
                        machineScreen.getJvicRunner().requestCopyScreenText();
                    }
                    return true;
                case Keys.INSERT:
                    if (machineScreen.getJvicRunner().isClipboardSupported()) {
                        machineScreen.getJvicRunner().requestPaste();
                    }
                    return true;
                case Keys.S:
                    handleSoundToggle();
                    return true;
                case Keys.Q:
                case Keys.X:
                    handleExitMachine();
                    return true;
                case Keys.W:
                    handleWarpSpeedToggle();
                    return true;
                case Keys.Y:
                    // The GWT web version doesn't support screen shots.
                    if (machineScreen.getJvicRunner().isScreenshotSupported()) {
                        machineScreen.saveScreenshot();
                    }
                    return true;
                case Keys.Z:
                    handleDiskReset();
                    return true;
                case Keys.MINUS:
                    rotateScreenSize();
                    return true;
                case Keys.H:
                case Keys.M:
                    // The monitor, as in VICE. Ignored where it isn't supported.
                    machineScreen.toggleMonitorConsole();
                    return true;
                case Keys.O:
                    // Settings, as in VICE. Ignored when the deployment hides settings.
                    machineScreen.toggleSettings();
                    return true;
                default:
                    return false;
            }
        }
        else if (keycode == Keys.F11) {
            // Web version handles F11 full screen itself, i.e. its a browser feature.
            if (!Gdx.app.getType().equals(ApplicationType.WebGL)) {
                handleFullScreenToggle();
            }
        }
        else if (keycode == Keys.PAUSE) {
            // VICE's screenshot key, the same as Alt+Y.
            if (machineScreen.getJvicRunner().isScreenshotSupported()) {
                machineScreen.saveScreenshot();
            }
            return true;
        }
        return false;
    }

    /**
     * @param action An action from the machine screen's menu.
     *
     * @return true if the action can be used on this platform, now.
     */
    public boolean isActionAvailable(MachineAction action) {
        JVicRunner jvicRunner = machineScreen.getJvicRunner();
        switch (action) {
            case SETTINGS:
                return machineScreen.isSettingsAvailable();
            case RESET:
            case POWER_CYCLE:
            case QUICK_SAVE:
            case QUICK_LOAD:
                return jvicRunner.isMachineControlSupported();
            case SCREENSHOT:
                return jvicRunner.isScreenshotSupported();
            case RESET_DISK:
                return jvicRunner.canResetMountedDisk();
            case COPY_TEXT:
            case PASTE:
                return jvicRunner.isClipboardSupported();
            case MONITOR:
                return jvicRunner.isMonitorSupported();
            case SCREEN_SIZE:
                // Portrait always uses the size that fits.
                return viewportManager.isLandscape();
            case WARP:
            case SHORTCUTS:
            default:
                return true;
        }
    }

    /**
     * Carries out an action from the machine screen's menu, the same as its hotkey.
     *
     * @param action The action.
     */
    public void performAction(MachineAction action) {
        JVicRunner jvicRunner = machineScreen.getJvicRunner();
        switch (action) {
            case SETTINGS:
                machineScreen.toggleSettings();
                break;
            case SCREEN_SIZE:
                rotateScreenSize();
                break;
            case RESET:
                handleReset(false);
                break;
            case POWER_CYCLE:
                handleReset(true);
                break;
            case QUICK_SAVE:
                handleQuickSave();
                break;
            case QUICK_LOAD:
                handleQuickLoad();
                break;
            case SCREENSHOT:
                if (jvicRunner.isScreenshotSupported()) {
                    machineScreen.saveScreenshot();
                }
                break;
            case WARP:
                handleWarpSpeedToggle();
                break;
            case RESET_DISK:
                handleDiskReset();
                break;
            case COPY_TEXT:
                if (jvicRunner.isClipboardSupported()) {
                    jvicRunner.requestCopyScreenText();
                }
                break;
            case PASTE:
                if (jvicRunner.isClipboardSupported()) {
                    jvicRunner.requestPaste();
                }
                break;
            case MONITOR:
                machineScreen.toggleMonitorConsole();
                break;
            case SHORTCUTS:
                showShortcuts();
                break;
            default:
                break;
        }
    }

    private void showShortcuts() {
        JVicRunner jvicRunner = machineScreen.getJvicRunner();
        StringBuilder text = new StringBuilder("Keyboard shortcuts\n\n");
        text.append("Alt+D, Alt+Enter, F11: Full screen\n");
        if (machineScreen.isSettingsAvailable()) {
            text.append("Alt+O: Settings\n");
        }
        text.append("Alt+P: Pause / resume\n");
        text.append("Alt+Shift+P: Advance one frame\n");
        text.append("Alt+S: Sound on / off\n");
        text.append("Alt+W: Warp speed\n");
        text.append("Alt+J: Joystick\n");
        text.append("Alt+K: On-screen keyboard\n");
        text.append("Alt+-: Screen size\n");
        text.append("Alt+B: FPS overlay\n");
        if (jvicRunner.isScreenshotSupported()) {
            text.append("Alt+Y, Pause: Screenshot\n");
        }
        if (jvicRunner.isMachineControlSupported()) {
            text.append("Alt+F9: Reset\n");
            text.append("Alt+F12: Power cycle\n");
            text.append("Alt+F11: Quick save\n");
            text.append("Alt+F10: Quick load\n");
        }
        text.append("Alt+Z: Reset the mounted disk\n");
        if (jvicRunner.isClipboardSupported()) {
            text.append("Alt+Del: Copy screen text\n");
            text.append("Alt+Ins: Paste text\n");
        }
        if (jvicRunner.isMonitorSupported()) {
            text.append("Alt+H: Monitor\n");
        }
        text.append("Alt+Q, Alt+X: Quit\n");
        dialogHandler.showAboutDialog(text.toString(), new TextInputResponseHandler() {
            @Override
            public void inputTextResult(boolean success, String button) {
                // Nothing to do.
            }
        });
    }

    /**
     * Alt+F9 (reset) and Alt+F12 (power cycle), as in VICE. No confirmation is asked
     * for, as in VICE.
     */
    private void handleReset(boolean powerCycle) {
        JVicRunner jvicRunner = machineScreen.getJvicRunner();
        if (jvicRunner.isMachineControlSupported() && !dialogHandler.isDialogOpen()) {
            jvicRunner.requestReset(powerCycle);
        }
    }

    /** Alt+F11, as in VICE: saves the machine to the program's quick save slot. */
    private void handleQuickSave() {
        JVicRunner jvicRunner = machineScreen.getJvicRunner();
        if (jvicRunner.isMachineControlSupported() && !dialogHandler.isDialogOpen()) {
            jvicRunner.requestQuickSave(machineScreen.getQuickSaveSlot());
        }
    }

    /** Alt+F10, as in VICE: loads the machine from the program's quick save slot. */
    private void handleQuickLoad() {
        JVicRunner jvicRunner = machineScreen.getJvicRunner();
        if (jvicRunner.isMachineControlSupported() && !dialogHandler.isDialogOpen()) {
            jvicRunner.requestQuickLoad(machineScreen.getQuickSaveSlot());
        }
    }

    /**
     * Alt+Shift+P, as in VICE: pauses the machine if it is running, otherwise runs it
     * for one frame.
     */
    private void handleFrameAdvance() {
        JVicRunner jvicRunner = machineScreen.getJvicRunner();
        if (!jvicRunner.isMachineControlSupported() || dialogHandler.isDialogOpen()) {
            return;
        }
        if (jvicRunner.isPaused()) {
            jvicRunner.requestFrameAdvance();
        } else {
            jvicRunner.pause();
        }
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
            // The icon positions come from the layout the machine screen last drew.
            MachineIconLayout.Icon icon = machineScreen.getIconLayout().hitTest(touchXY.x, touchXY.y);
            if (icon != null) {
                switch (icon) {
                    case KEYBOARD:
                        handleKeyboardToggle();
                        break;
                    case JOYSTICK:
                        handleJoystickAlignment();
                        break;
                    case SPEAKER:
                        handleSoundToggle();
                        break;
                    case MENU:
                        machineScreen.openMachineMenu();
                        break;
                    case PAUSE_PLAY:
                        handlePauseToggle();
                        break;
                    case FULL_SCREEN:
                        handleFullScreenToggle();
                        break;
                    case BACK:
                        handleExitMachine();
                        break;
                    default:
                        break;
                }
            } else if (!joystickAlignment.equals(JoystickAlignment.OFF)) {
                // If the joystick is ON, a tap that didn't land on an icon is the fire button.
                KeyboardMatrix keyboardMatrix = machineScreen.getJvicRunner().getKeyboardMatrix();
                keyboardMatrix.vicKeyDown(VicKeys.JOYSTICK_FIRE);
                keyboardMatrix.vicKeyUp(VicKeys.JOYSTICK_FIRE);
            }
        }

        return true;
    }
    
    private void handlePauseToggle() {
        if (machineScreen.getJvicRunner().isPaused()) {
            machineScreen.getJvicRunner().resume();
        } else {
            machineScreen.getJvicRunner().pause();
        }
    }
    
    private void handleFullScreenToggle() {
        Boolean fullScreen = Gdx.graphics.isFullscreen();
        if (fullScreen == true) {
            switchOutOfFullScreen();
        }
        else {
            switchIntoFullScreen();
        }
    }
    
    private void handleKeyboardToggle() {
        if (keyboardType.equals(KeyboardType.OFF)) {
            keyboardType = (viewportManager.isPortrait() ? KeyboardType.PORTRAIT : KeyboardType.LANDSCAPE);
            viewportManager.update();
        } else {
            keyboardType = KeyboardType.OFF;
        }
    }
    
    private void handleJoystickAlignment() {
        // Rotate the joystick screen alignment.
        joystickAlignment = joystickAlignment.rotateValue();
        if (viewportManager.isLandscape() && ((cameraXOffset != 0))) {
            if (joystickAlignment.equals(JoystickAlignment.RIGHT)) {
                joystickAlignment = joystickAlignment.rotateValue();
            }
        }
        updateJoystickKeys();
    }

    /**
     * The arrow keys and INSERT act as the joystick only while the joystick is ON, i.e.
     * whenever the joystick icon is active, and as the VIC cursor keys/INST otherwise.
     */
    private void updateJoystickKeys() {
        getKeyboardMatrix().setJoystickKeysActive(!joystickAlignment.equals(JoystickAlignment.OFF));
    }
    
    private void handleSoundToggle() {
        // The user has chosen, so "sound at start" no longer applies.
        soundOffAtStart = false;
        speakerOn = !speakerOn;
        machineScreen.getJvicRunner().changeSound(speakerOn);
    }
    
    private void handleExitMachine() {
        if (Gdx.app.getType().equals(ApplicationType.Desktop) && Gdx.graphics.isFullscreen()) {
            // Dialog won't show for desktop unless we exit full screen,
            switchOutOfFullScreen();
        }
        
        machineScreen.getJvicRunner().pause();
        
        dialogHandler.confirm("Are you sure you want to quit the game?", 
                new ConfirmResponseHandler() {
            @Override
            public void yes() {
                machineScreen.getJvicRunner().stop();
            }
            
            @Override
            public void no() {
                // Nothing to do.
                machineScreen.getJvicRunner().resume();
            }
        });
    }

    private void handleDiskReset() {
        if (dialogHandler.isDialogOpen() || !machineScreen.getJvicRunner().canResetMountedDisk()) {
            return;
        }

        if (Gdx.app.getType().equals(ApplicationType.Desktop) && Gdx.graphics.isFullscreen()) {
            switchOutOfFullScreen();
        }

        machineScreen.getJvicRunner().pause();
        dialogHandler.confirm("Reset the mounted disk to its original image? Any persisted changes will be lost.",
                new ConfirmResponseHandler() {
                    @Override
                    public void yes() {
                        machineScreen.getJvicRunner().requestMountedDiskReset(
                                new JVicRunner.ResetDiskHandler() {
                                    @Override
                                    public void onResetComplete() {
                                        machineScreen.getJvicRunner().resume();
                                    }

                                    @Override
                                    public void onResetFailed() {
                                        machineScreen.getJvicRunner().resume();
                                    }
                                });
                    }

                    @Override
                    public void no() {
                        machineScreen.getJvicRunner().resume();
                    }
                });
    }

    private void handleShowFPSToggle() {
        machineScreen.toggleShowFPS();
    }

    private void handleWarpSpeedToggle() {
        if (!machineScreen.getJvicRunner().isWarpSpeed()) {
            speakerOn = false;
            machineScreen.getJvicRunner().changeSound(false);
        }
        machineScreen.getJvicRunner().toggleWarpSpeed();
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
        if (screenWidthBeforeFullScreen > (screenHeightBeforeFullScreen * 1.33f)) {
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
        updateJoystickKeys();
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
        if (speakerOn && soundOffAtStart) {
            // The runner turns sound on as a program starts; the "sound at start" setting
            // is off, so turn it off again. Only the first time.
            soundOffAtStart = false;
            this.speakerOn = false;
            machineScreen.getJvicRunner().changeSound(false);
            return;
        }
        if (!speakerOn) {
            // Sound couldn't start (e.g. the browser needs a tap first), so there is
            // nothing to turn off.
            soundOffAtStart = false;
        }
        this.speakerOn = speakerOn;
    }

    /**
     * Sets whether sound should be turned off when the runner next turns it on as the
     * program starts (the "Sound at start" setting).
     *
     * @param soundOffAtStart true to start with sound off.
     */
    public void setSoundOffAtStart(boolean soundOffAtStart) {
        this.soundOffAtStart = soundOffAtStart;
    }

    /**
     * Sets which side the touch joystick goes on (the "Touch joystick side" setting).
     * If the joystick is on, it moves there now. In a landscape layout without room on
     * the right it stays on the left.
     *
     * @param side LEFT or RIGHT.
     */
    public void setJoystickSide(JoystickAlignment side) {
        this.joystickSide = (side == JoystickAlignment.RIGHT) ? JoystickAlignment.RIGHT : JoystickAlignment.LEFT;
        if (!joystickAlignment.equals(JoystickAlignment.OFF)) {
            setJoystickAlignment(this.joystickSide);
        }
    }

    /**
     * @return The side the touch joystick goes on when it is turned on.
     */
    public JoystickAlignment getJoystickSide() {
        return joystickSide;
    }

    /**
     * Changes the screen size now (the "Screen size" setting). Sizes that don't fit the
     * window, and any size other than Fit in portrait, fall back to Fit.
     *
     * @param screenSize The screen size.
     */
    public void applyScreenSize(ScreenSize screenSize) {
        this.screenSize = viewportManager.isPortrait() ? ScreenSize.FIT : screenSize;
        ExtendViewport viewport = machineScreen.getViewport();
        adjustWorldMinMax(viewport.getScreenWidth(), viewport.getScreenHeight(), machineScreen.getMachineType());
    }

    public static enum ScreenSize {
        
        FIT(363, 272,  312,  234),     // 1.33 / 1.33
        X8(2860, 2176, 2388, 1872),    // 1.31 / 1.27
        X7(2420, 1904, 2189, 1638),    // 1.27 / 1.33
        X6(2200, 1632, 1791, 1404),    // 1.35 / 1.27
        X5(1760, 1360, 1592, 1170),    // 1.29 / 1.36
        X4(1320, 1088, 1194, 936),     // 1.21 / 1.27
        X3(1100, 816,  995,  702),     // 1.35 / 1.41
        X2(660,  544,  597,  468)      // 1.21 / 1.27
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
            return values()[(ordinal() + 1) % 8];  // WAS 9
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
