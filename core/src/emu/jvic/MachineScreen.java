package emu.jvic;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Input.Buttons;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

/**
 * The main screen in the JVic emulator, i.e. the one that shows the video 
 * output of the VIC.
 * 
 * @author Lance Ewing
 */
public class MachineScreen extends InputAdapter implements Screen {

  /**
   * This represents the VIC 20 machine.
   */
  private Machine machine;
  
  private SpriteBatch batch;
  private Texture screenTexture;
  private Texture keyboardLandscape;
  private Texture keyboardPortrait;
  private Pixmap screenPixmap;
  private Viewport viewport;
  private Viewport viewportHUD;
  private Camera camera;
  private Camera cameraHUD;
  
  private boolean showInput;
  
  
  /**
   * Constructor for MachineScreen.
   */
  public MachineScreen() {
    this.machine = new Machine();
    
    batch = new SpriteBatch();
    screenPixmap = new Pixmap(machine.getMachineType().getTotalScreenWidth(), machine.getMachineType().getTotalScreenHeight(), Pixmap.Format.RGBA8888);
    screenTexture = new Texture(screenPixmap, Pixmap.Format.RGBA8888, false);
    keyboardLandscape = new Texture("png/keyboard_landscape.png");
    keyboardPortrait = new Texture("png/keyboard_portrait.png");
    //keyboardPortrait = new Texture("png/big_vic_keys_3_1920.jpg");
    
    camera = new OrthographicCamera();
    cameraHUD = new OrthographicCamera();

    viewport = new ExtendViewport(machine.getScreenWidth(), machine.getScreenHeight(), camera);
    viewportHUD = new ExtendViewport(keyboardPortrait.getWidth(), keyboardPortrait.getHeight(), cameraHUD);
    //viewportHUD = new ExtendViewport(keyboardLandscape.getWidth(), keyboardLandscape.getHeight(), cameraHUD);
    
    // Register this MachineScreen instance as the input processor for keys, etc.
    Gdx.input.setInputProcessor(this);
  }
  
  @Override
  public void show() {
    // TODO Decide if this needs to resume, or whether that happens automatically.
  }

  @Override
  public void render(float delta) {
    // Update the machine's state.
    boolean render = machine.update(delta);
    
    if (render) {
      draw();
    }
  }

  private void draw() {
    Gdx.gl.glClearColor(0, 0, 0, 1);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    
    // This is probably not the most efficient way of getting the pixels to the GPU, but
    // will suffice for this initial conversion of JVic. I'll spend more time profiling 
    // other options in the future. It's possible that we could get the Vic class to 
    // write directly in to the Pixmap ByteBuffer. This copy is done natively though, so
    // is potentially not adding that much overhead.
    BufferUtils.copy(machine.getFramePixels(), 0, screenPixmap.getPixels(), 
        machine.getMachineType().getTotalScreenWidth() * machine.getMachineType().getTotalScreenHeight());

    // Updates the screen Texture with the Pixmap frame data.
    screenTexture.draw(screenPixmap, 0, 0);
    
    // Render the VIC screen.
    camera.update();
    batch.setProjectionMatrix(camera.combined);
    batch.disableBlending();
    batch.begin();
    Color c = batch.getColor();
    batch.setColor(c.r, c.g, c.b, 1f);
    batch.draw(screenTexture, 
        0, 0,
        machine.getScreenWidth(), machine.getScreenHeight(), 
        machine.getScreenLeft(), machine.getScreenTop(), 
        machine.getMachineType().getVisibleScreenWidth(), 
        machine.getMachineType().getVisibleScreenHeight(), 
        false, false);
    batch.end();
    
    if (showInput) {
      // Render the UI elements, e.g. the keyboard.
      cameraHUD.update();
      batch.setProjectionMatrix(cameraHUD.combined);
      batch.enableBlending();
      batch.begin();
      c = batch.getColor();
      batch.setColor(c.r, c.g, c.b, 1.0f);
      batch.draw(keyboardPortrait, 0, 0);
      //batch.draw(keyboardLandscape, 0, 0);
      batch.end();
    }
  }
  
  @Override
  public void resize(int width, int height) {
    viewport.update(width, height, false);
    
    // Align VIC screen's top edge to top of the viewport.
    Camera camera = viewport.getCamera();
    camera.position.x = machine.getScreenWidth() /2;
    camera.position.y = machine.getScreenHeight() - viewport.getWorldHeight()/2;
    camera.update();
    
    viewportHUD.update(width, height, true);
  }

  @Override
  public void pause() {
    machine.setPaused(true);
  }

  @Override
  public void resume() {
    machine.setPaused(false);
  }

  @Override
  public void hide() {
  }

  @Override
  public void dispose() {
    screenPixmap.dispose();
    screenTexture.dispose();
    batch.dispose();
  }
  
  /** 
   * Called when a key was pressed
   * 
   * @param keycode one of the constants in {@link Input.Keys}
   * 
   * @return whether the input was processed 
   */
  public boolean keyDown (int keycode) {
    machine.getKeyboard().keyPressed(keycode);
    machine.getJoystick().keyPressed(keycode);
    return true;
  }

  /** 
   * Called when a key was released
   * 
   * @param keycode one of the constants in {@link Input.Keys}
   * 
   * @return whether the input was processed 
   */
  public boolean keyUp (int keycode) {
    machine.getKeyboard().keyReleased(keycode);
    machine.getJoystick().keyReleased(keycode);
    return true;
  }
  
  /**
   * Lookup table for converting mouse and touch taps within the portrait keyboard image in to
   * a libGDX keycode.
   */
  private static int PORTRAIT_KEYBOARD_MAP[][] = {
    { Keys.CONTROL_LEFT, Keys.LEFT, Keys.UP, Keys.F1, -1, Keys.F3, Keys.F5, -2, Keys.F7, 0/* £ */, Keys.HOME, Keys.DEL },
    { Keys.NUM_1, Keys.NUM_2, Keys.NUM_3, Keys.NUM_4, Keys.NUM_5, Keys.NUM_6, Keys.NUM_7, Keys.NUM_8, Keys.NUM_9, Keys.NUM_0, Keys.PLUS, Keys.MINUS },
    { Keys.Q, Keys.W, Keys.E, Keys.R, Keys.T, Keys.Y, Keys.U, Keys.I, Keys.O, Keys.P, Keys.AT, Keys.STAR },
    { Keys.A, Keys.S, Keys.D, Keys.F, Keys.G, Keys.H, Keys.J, Keys.K, Keys.L, Keys.COLON, Keys.SEMICOLON, Keys.EQUALS },
    { Keys.Z, Keys.X, Keys.C, Keys.V, Keys.B, Keys.N, Keys.M, Keys.COMMA, Keys.PERIOD, Keys.SLASH, Keys.ENTER, Keys.ENTER },
    { Keys.ALT_LEFT, -3, Keys.SHIFT_LEFT, -4, Keys.SPACE, Keys.SPACE, Keys.SPACE, Keys.SPACE, -5, Keys.SHIFT_RIGHT, Keys.DOWN, Keys.RIGHT } 
  };
  
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
  public boolean touchDown (int screenX, int screenY, int pointer, int button) {
    // Convert the screen coordinates to world coordinates.
    Vector2 touchXY = new Vector2(screenX, screenY);
    this.viewportHUD.unproject(touchXY);

    if (showInput) {
      // If the tap is within the portrait keyboard...
      if (touchXY.y < 972) {
        // In portrait mode, the keyboard is a grid of squares where each square is 162x162.
        int keyX = (int)(touchXY.x / 162);
        int keyY = (int)((972 - touchXY.y) / 162);
        if (keyX >= 1944) keyX = 1943;
        int keyCode = PORTRAIT_KEYBOARD_MAP[keyY][keyX];
        machine.getKeyboard().keyPressed(keyCode);
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
  public boolean touchUp (int screenX, int screenY, int pointer, int button) {
    if (!showInput) {
      // If the input is not currently showing, then a tap anywhere will bring it up.
      showInput = true;
    } else {
      // Convert the screen coordinates to world coordinates.
      Vector2 touchXY = new Vector2(screenX, screenY);
      this.viewportHUD.unproject(touchXY);

      // If the click is above the keyboard, then we hide the input controls.
      if (touchXY.y >= 972) {
        showInput = false;
        
      } else {
        // In portrait mode, the keyboard is a grid of squares where each square is 162x162.
        int keyX = (int)(touchXY.x / 162);
        int keyY = (int)((972 - touchXY.y) / 162);
        if (keyX >= 1944) keyX = 1943;
        int keyCode = PORTRAIT_KEYBOARD_MAP[keyY][keyX];
        machine.getKeyboard().keyReleased(keyCode);
      }
    }
    
    return true;
  }
}
