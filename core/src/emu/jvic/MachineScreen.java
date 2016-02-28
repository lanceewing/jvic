package emu.jvic;

import com.badlogic.gdx.Application.ApplicationType;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Input.Buttons;
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
import com.badlogic.gdx.utils.TimeUtils;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

import emu.jvic.ui.ViewportManager;

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

  /**
   * SpriteBatch shared by all rendered components.
   */
  private SpriteBatch batch;
  
  // Components to support rendering of the VIC 20 screen.
  private Texture screenTexture;
  private Pixmap screenPixmap;
  private Viewport viewport;
  private Camera camera;
  
  // UI components.
  private Texture joystickIcon;
  private Texture keyboardIcon;

  private ViewportManager viewportManager;
  
  /**
   * The type of keyboard currently being displayed.
   */
  private KeyboardType keyboardType;
  
  /**
   * Constructor for MachineScreen.
   */
  public MachineScreen() {
    this.machine = new Machine();
    
    batch = new SpriteBatch();
    screenPixmap = new Pixmap(machine.getMachineType().getTotalScreenWidth(), machine.getMachineType().getTotalScreenHeight(), Pixmap.Format.RGBA8888);
    screenTexture = new Texture(screenPixmap, Pixmap.Format.RGBA8888, false);
    
    this.keyboardType = KeyboardType.OFF;
    
    camera = new OrthographicCamera();
    viewport = new ExtendViewport(machine.getScreenWidth(), machine.getScreenHeight(), camera);
    
    keyboardIcon = new Texture("png/keyboard_icon.png");
    joystickIcon = new Texture("png/joystick_icon.png");
    
    viewportManager = new ViewportManager();
    
    // Register this MachineScreen instance as the input processor for keys, etc.
    Gdx.input.setInputProcessor(this);
  }

  private long lastLogTime;
  private long avgRenderTime;
  private long avgUpdateTime;
  private long avgDrawTime;
  private long renderCount;
  
  // Linux:
  //  RenderTime: avgUpdateTime: 1981313 avgDrawTime: 416109 avgRenderTime: 2346450 ns renderCount: 601 delta: 0.016525 fps: 60
  //  RenderTime: avgUpdateTime: 1919198 avgDrawTime: 377756 avgRenderTime: 2239919 ns renderCount: 1201 delta: 0.016679 fps: 60
  //  RenderTime: avgUpdateTime: 2091536 avgDrawTime: 406210 avgRenderTime: 2435471 ns renderCount: 1801 delta: 0.016684 fps: 60
  //  RenderTime: avgUpdateTime: 2197919 avgDrawTime: 418328 avgRenderTime: 2552785 ns renderCount: 2401 delta: 0.016760 fps: 60
  //  RenderTime: avgUpdateTime: 2081874 avgDrawTime: 395140 avgRenderTime: 2413089 ns renderCount: 3000 delta: 0.016661 fps: 60
  //  RenderTime: avgUpdateTime: 1954413 avgDrawTime: 368573 avgRenderTime: 2259497 ns renderCount: 3600 delta: 0.016667 fps: 60
  //  RenderTime: avgUpdateTime: 1899786 avgDrawTime: 351931 avgRenderTime: 2189077 ns renderCount: 4200 delta: 0.016406 fps: 60
  //  RenderTime: avgUpdateTime: 1978363 avgDrawTime: 356863 avgRenderTime: 2273328 ns renderCount: 4800 delta: 0.016696 fps: 60
  
  // Android - Oneplus One
  //  02-20 23:55:09.825: I/RenderTime(32375): avgUpdateTime: 5430502 avgDrawTime: 3003958 avgRenderTime: 8100821 ns renderCount: 630 delta: 0.015177 fps: 61
  //  02-20 23:55:19.827: I/RenderTime(32375): avgUpdateTime: 6449893 avgDrawTime: 3034441 avgRenderTime: 9028275 ns renderCount: 1251 delta: 0.015992 fps: 63
  //  02-20 23:55:29.837: I/RenderTime(32375): avgUpdateTime: 7045818 avgDrawTime: 2974562 avgRenderTime: 9518082 ns renderCount: 1874 delta: 0.015699 fps: 64
  //  02-20 23:55:39.876: I/RenderTime(32375): avgUpdateTime: 7227487 avgDrawTime: 3034167 avgRenderTime: 9736194 ns renderCount: 2498 delta: 0.018163 fps: 63
  //  02-20 23:55:49.879: I/RenderTime(32375): avgUpdateTime: 7347156 avgDrawTime: 3057218 avgRenderTime: 9861786 ns renderCount: 3125 delta: 0.015083 fps: 63
  //  02-20 23:55:59.888: I/RenderTime(32375): avgUpdateTime: 7367018 avgDrawTime: 3105098 avgRenderTime: 9917184 ns renderCount: 3752 delta: 0.015854 fps: 64
  //  02-20 23:56:09.903: I/RenderTime(32375): avgUpdateTime: 7301162 avgDrawTime: 3132884 avgRenderTime: 9868789 ns renderCount: 4378 delta: 0.015967 fps: 65
  //  02-20 23:56:19.775: I/RenderTime(32375): avgUpdateTime: 7331545 avgDrawTime: 3147967 avgRenderTime: 9905072 ns renderCount: 5005 delta: 0.017148 fps: 64
  //  02-20 23:56:29.772: I/RenderTime(32375): avgUpdateTime: 7313469 avgDrawTime: 3159467 avgRenderTime: 9890995 ns renderCount: 5633 delta: 0.013460 fps: 64
  //  02-20 23:56:39.790: I/RenderTime(32375): avgUpdateTime: 7360768 avgDrawTime: 3178611 avgRenderTime: 9951423 ns renderCount: 6258 delta: 0.020102 fps: 63
  //  02-20 23:56:49.784: I/RenderTime(32375): avgUpdateTime: 7360072 avgDrawTime: 3206466 avgRenderTime: 9973250 ns renderCount: 6887 delta: 0.016099 fps: 63
  //  02-20 23:56:59.795: I/RenderTime(32375): avgUpdateTime: 7427312 avgDrawTime: 3191925 avgRenderTime: 10023032 ns renderCount: 7504 delta: 0.015853 fps: 63
  //  02-20 23:57:09.762: I/RenderTime(32375): avgUpdateTime: 7487957 avgDrawTime: 3180946 avgRenderTime: 10070630 ns renderCount: 8123 delta: 0.013127 fps: 62
  //  02-20 23:57:19.773: I/RenderTime(32375): avgUpdateTime: 7505270 avgDrawTime: 3182673 avgRenderTime: 10087244 ns renderCount: 8749 delta: 0.015861 fps: 64
  
  @Override
  public void render(float delta) {
    // Note: On some android phones, the render method is invoked over 8000 times a second.
    long renderStartTime = TimeUtils.nanoTime();
    long fps = Gdx.graphics.getFramesPerSecond();
    long maxFrameDuration = (long)(1000000000L * (fps == 0? 0.016667f : delta));
    
    // Update the machine's state.
    boolean render = machine.update(delta);
    
    long updateEndTime = TimeUtils.nanoTime();
    long updateDuration = updateEndTime - renderStartTime;
    if (renderCount == 0) {
      avgUpdateTime = updateDuration;
    } else {
      avgUpdateTime = ((avgUpdateTime * renderCount) + updateDuration) / (renderCount + 1);
    }
    
    // TODO: For slower phones, might need to skip drawing some frames.
    
    if (render) {
      draw();
      long drawDuration = TimeUtils.nanoTime() - updateEndTime;
      if (renderCount == 0) {
        avgDrawTime = drawDuration;
      } else {
        avgDrawTime = ((avgDrawTime * renderCount) + drawDuration) / (renderCount + 1);
      }
    }
    
    long renderDuration = TimeUtils.nanoTime() - renderStartTime;
    if (renderCount == 0) {
      avgRenderTime = renderDuration;
    } else {
      avgRenderTime = ((avgRenderTime * renderCount) + renderDuration) / (renderCount + 1);
    }
    
    renderCount++;
    
    if ((lastLogTime == 0) || (renderStartTime - lastLogTime > 10000000000L)) {
      lastLogTime = renderStartTime;
      Gdx.app.log("RenderTime", String.format(
          "avgUpdateTime: %d avgDrawTime: %d avgRenderTime: %d maxFrameDuration: %d delta: %f fps: %d", 
          avgUpdateTime, avgDrawTime, avgRenderTime, maxFrameDuration, delta, Gdx.graphics.getFramesPerSecond()));
    }
  }

  private void draw() {
    Gdx.gl.glClearColor(0, 0, 0, 1);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    
    // This is probably not the most efficient way of getting the pixels to the GPU, but
    // will suffice for this initial conversion of JVic. I'll spend more time profiling 
    // other options in the future. It's possible that we could get the Vic class to 
    // write directly in to the Pixmap ByteBuffer. This copy is done natively though, so
    // is potentially not adding that much overhead. I'm also wondering whether an opengl
    // shader can take care of some of the work, but need to research that further.
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

    // Render the UI elements, e.g. the keyboard and joystick icons.
    viewportManager.getCurrentCamera().update();
    batch.setProjectionMatrix(viewportManager.getCurrentCamera().combined);
    batch.enableBlending();
    batch.begin();
    if (keyboardType.isRendered()) {
      batch.setColor(c.r, c.g, c.b, keyboardType.getOpacity());
      batch.draw(keyboardType.getTexture(), 0, keyboardType.getRenderOffset());
    }
    batch.setColor(c.r, c.g, c.b, 0.5f);
    if (viewportManager.isPortrait()) {
      batch.draw(joystickIcon, 0, 0);
      batch.draw(keyboardIcon, viewportManager.getCurrentViewport().getWorldWidth() - 145, 0);
      
      if (Gdx.app.getType().equals(ApplicationType.Android)) {
        // Mobile keyboard for debug purpose. Wouldn't normally make this available.
        batch.setColor(c.r, c.g, c.b, 0.15f);
        batch.draw(keyboardIcon, 
            viewportManager.getCurrentViewport().getWorldWidth() - viewportManager.getCurrentViewport().getWorldWidth()/2 - 70, 
            0);
      }
    } else {
      batch.draw(joystickIcon, 0, viewportManager.getCurrentViewport().getWorldHeight() - 140);
      batch.draw(keyboardIcon, 
          viewportManager.getCurrentViewport().getWorldWidth() - 150, 
          viewportManager.getCurrentViewport().getWorldHeight() - 125);
    }
    batch.end();
  }
  
  @Override
  public void resize(int width, int height) {
    viewport.update(width, height, false);
    
    // Align VIC screen's top edge to top of the viewport.
    Camera camera = viewport.getCamera();
    camera.position.x = machine.getScreenWidth() /2;
    camera.position.y = machine.getScreenHeight() - viewport.getWorldHeight()/2;
    camera.update();
    
    if (keyboardType.isRendered()) {
      // Switch keyboard layout based on the orientation.
      keyboardType = (height > width? KeyboardType.PORTRAIT_10x7 : KeyboardType.LANDSCAPE);
    }
    
    viewportManager.update(width, height);
  }

  @Override
  public void pause() {
    // On Android, this is also called when the "Home" button is pressed.
    machine.setPaused(true);
  }

  @Override
  public void resume() {
    KeyboardType.init();
    machine.setPaused(false);
  }
  
  @Override
  public void show() {
    KeyboardType.init();
  }
  
  @Override
  public void hide() {
    // On Android, this is also called when the "Back" button is pressed.
    KeyboardType.dispose();
  }

  @Override
  public void dispose() {
    KeyboardType.dispose();
    keyboardIcon.dispose();
    joystickIcon.dispose();
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
    viewportManager.getCurrentViewport().unproject(touchXY);
    
    // If the tap is within the keyboard...
    if (keyboardType.isInKeyboard(touchXY.x, touchXY.y)) {
      Integer keycode = keyboardType.getKeyCode(touchXY.x, touchXY.y);
      if (keycode != null) {
        machine.getKeyboard().keyPressed(keycode);
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
    // Convert the screen coordinates to world coordinates.
    Vector2 touchXY = new Vector2(screenX, screenY);
    viewportManager.getCurrentViewport().unproject(touchXY);
    
    if (keyboardType.isInKeyboard(touchXY.x, touchXY.y)) {
      Integer keycode = keyboardType.getKeyCode(touchXY.x, touchXY.y);
      if (keycode != null) {
        machine.getKeyboard().keyReleased(keycode);
      }
      
    } else if (keyboardType.equals(KeyboardType.MOBILE_ON_SCREEN)) {
      // If the onscreen keyboard is being shown then if we receive a tap event, it won't be
      // on the virtual keyboard but must therefore be outside it. So we hide the keyboard.
      Gdx.input.setOnscreenKeyboardVisible(false);
      keyboardType = KeyboardType.OFF;
      
    } else {
      // TODO: Need to handle the magic numbers in this block in a better way. 
      // TODO: The ViewportManager should really have a getWidth method that internally gets the current Viewport's world width.
      boolean keyboardClicked = false;
      boolean joystickClicked = false;
      
      if (viewportManager.isPortrait()) {
        // Portrait.
        if (touchXY.y < 130) {
          if (touchXY.x < 140) {
            joystickClicked = true;
            
          } else if (touchXY.x > (viewportManager.getCurrentViewport().getWorldWidth() - 145)) {
            keyboardClicked = true;
            
          } else if (Gdx.app.getType().equals(ApplicationType.Android)) {
            // Mobile soft keyboard is only available in portrait mode (debug only)
            int midWidth = (int)(viewportManager.getCurrentViewport().getWorldWidth() - viewportManager.getCurrentViewport().getWorldWidth()/2);
            if ((touchXY.x > (midWidth - 70)) && 
                (touchXY.y < (midWidth + 70))) {
              Gdx.input.setOnscreenKeyboardVisible(true);
              keyboardType = KeyboardType.MOBILE_ON_SCREEN;
            }
          }
        }
      } else {
        // Landscape.
        int screenTop = (int)viewportManager.getCurrentViewport().getWorldHeight();
        if (touchXY.y > (screenTop - 140)) {
          if (touchXY.x < 140) {
            joystickClicked = true;
            
          } else if (touchXY.x > (viewportManager.getCurrentViewport().getWorldWidth() - 150)) {
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
        // TODO: Show/Hide joystick.
      }
    }
    
    return true;
  }
}
