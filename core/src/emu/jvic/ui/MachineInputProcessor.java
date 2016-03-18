package emu.jvic.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.Application.ApplicationType;
import com.badlogic.gdx.Input.Buttons;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.math.Vector2;

import emu.jvic.KeyboardType;
import emu.jvic.MachineScreen;

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
   * Invoked by JVic whenever it would like the user to confirm an action.
   */
  private ConfirmHandler confirmHandler;
  
  /**
   * The one and only ViewportManager used by JVic.
   */
  private ViewportManager viewportManager;
  
  /**
   * We only track up to a maximum number of simultaneous touch events.
   */
  private static final int MAX_SIMULTANEOUS_TOUCH_EVENTS = 5;
  
  /**
   * Array of current touches indexed by touch pointer ID. This Map allows us to keep drag of
   * active dragging. If a drag happens to start within a keyboard key and then leaves it
   * before being released, we need to automatically fire a key up event for our virtual
   * keyboard. Without handling this, drags can completely confuse the keyboard state. And
   * the joystick logic relies on dragging, so this needs to work well.
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
   * Constructor for MachineInputProcessor.
   * 
   * @param machineScreen 
   * @param confirmHandler 
   */
  public MachineInputProcessor(MachineScreen machineScreen, ConfirmHandler confirmHandler) {
    this.machineScreen = machineScreen;
    this.confirmHandler = confirmHandler;
    this.keyboardType = KeyboardType.OFF;
    this.viewportManager = ViewportManager.getInstance();
    
    // Initialise the touch info for max num of pointers (multi touch). We create these up 
    // front and reuse them so as to avoid garbage collection.
    this.touches = new TouchInfo[MAX_SIMULTANEOUS_TOUCH_EVENTS];
    for (int i=0; i<MAX_SIMULTANEOUS_TOUCH_EVENTS; i++) {
      touches[i] = new TouchInfo();
    }
  }
  
  /** 
   * Called when a key was pressed
   * 
   * @param keycode one of the constants in {@link Input.Keys}
   * 
   * @return whether the input was processed 
   */
  public boolean keyDown(int keycode) {
    machineScreen.getMachine().getKeyboard().keyPressed(keycode);
    machineScreen.getMachine().getJoystick().keyPressed(keycode);
    return true;
  }
  
  /** 
   * Called when a key was released
   * 
   * @param keycode one of the constants in {@link Input.Keys}
   * 
   * @return whether the input was processed 
   */
  public boolean keyUp(int keycode) {
    if (keycode == Keys.BACK) {
      if (keyboardType.equals(KeyboardType.OFF)) {
        if (Gdx.app.getType().equals(ApplicationType.Android)) {
          machineScreen.getMachineRunnable().pause();
          confirmHandler.confirm("Do you really want to Exit?", new ConfirmResponseHandler() {
            public void yes() {
              Gdx.app.exit();
            }
            public void no() {
              machineScreen.getMachineRunnable().resume();
            }
          });
        }
      } else {
        // If a keyboard is being shown, then BACK will close this.
        keyboardType = KeyboardType.OFF;
      }
    } else {
      machineScreen.getMachine().getKeyboard().keyReleased(keycode);
      machineScreen.getMachine().getJoystick().keyReleased(keycode);
    }
    return true;
  }
  
  /** 
   * Called when the screen was touched or a mouse button was pressed. The button parameter will be {@link Buttons#LEFT} on iOS.
   * 
   * @param screenX The x coordinate, origin is in the upper left corner
   * @param screenY The y coordinate, origin is in the upper left corner
   * @param pointer the pointer for the event.
   * @param button the button
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
        keyDown(keycode);
      }
      if (touchInfo != null) {
        touchInfo.lastKey = keycode;
      }
    }
    
    return true;
  }

  /** 
   * Called when a finger was lifted or a mouse button was released. The button parameter will be {@link Buttons#LEFT} on iOS.
   * 
   * @param pointer the pointer for the event.
   * @param button the button
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
        keyUp(keycode);
      }
    } else if (keyboardType.equals(KeyboardType.MOBILE_ON_SCREEN)) {
      // If the onscreen keyboard is being shown then if we receive a tap event, it won't be
      // on the virtual keyboard but must therefore be outside it. So we hide the keyboard.
      Gdx.input.setOnscreenKeyboardVisible(false);
      keyboardType = KeyboardType.OFF;
      
    } else if (!keyboardType.equals(KeyboardType.OFF)) {
      // If rendered keyboard is being shown, and the tap isn't within the keyboard, but is 
      // instead above the close height, then we close it.
      if (touchXY.y > keyboardType.getCloseHeight()) {
        keyboardType = KeyboardType.OFF;
      }
      
    } else {
      // TODO: Need to handle the magic numbers in this block in a better way. 
      boolean keyboardClicked = false;
      boolean joystickClicked = false;
      
      if (viewportManager.isPortrait()) {
        // Portrait.
        if (touchXY.y < 130) {
          if (touchXY.x < 140) {
            joystickClicked = true;
            
          } else if (touchXY.x > (viewportManager.getWidth() - 145)) {
            keyboardClicked = true;
            
          } else if (Gdx.app.getType().equals(ApplicationType.Android)) {
            // Mobile soft keyboard is only available in portrait mode (debug only)
            int midWidth = (int)(viewportManager.getWidth() - viewportManager.getWidth()/2);
            if ((touchXY.x > (midWidth - 70)) && 
                (touchXY.y < (midWidth + 70))) {
              Gdx.input.setOnscreenKeyboardVisible(true);
              keyboardType = KeyboardType.MOBILE_ON_SCREEN;
            }
          }
        }
      } else {
        // Landscape.
        int screenTop = (int)viewportManager.getHeight();
        if (touchXY.y > (screenTop - 140)) {
          if (touchXY.x < 140) {
            joystickClicked = true;
            
          } else if (touchXY.x > (viewportManager.getWidth() - 150)) {
            keyboardClicked = true;
          }
        }
      }
      
      if (keyboardClicked) {
        if (keyboardType.equals(KeyboardType.OFF)) {
          keyboardType = (viewportManager.isPortrait()? KeyboardType.PORTRAIT_10x7 : KeyboardType.LANDSCAPE);
          viewportManager.update();
        } else {
          keyboardType = KeyboardType.OFF;
        }
      }
      
      if (joystickClicked) {
        keyboardType = KeyboardType.JOYSTICK;
      }
    }
    
    return true;
  }
  
  /** 
   * Called when a finger or the mouse was dragged.
   * 
   * @param pointer the pointer for the event.
   * 
   * @return whether the input was processed 
   */
  public boolean touchDragged (int screenX, int screenY, int pointer) {
    // Convert the screen coordinates to world coordinates.
    Vector2 touchXY = viewportManager.unproject(screenX, screenY);
    
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
        keyUp(lastKey);
      }
      if ((newKey != null) && ((lastKey == null) || (lastKey != newKey))) {
        keyDown(newKey);
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
   * @param width The new screen width.
   * @param height The new screen height.
   */
  public void resize(int width, int height) {
    if (keyboardType.isRendered() && !keyboardType.equals(KeyboardType.JOYSTICK)) {
      // Switch keyboard layout based on the orientation.
      keyboardType = (height > width? KeyboardType.PORTRAIT_10x7 : KeyboardType.LANDSCAPE);
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
}
