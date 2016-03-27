package emu.jvic;

import com.badlogic.gdx.Application.ApplicationType;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.TimeUtils;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

import emu.jvic.config.AppConfigItem;
import emu.jvic.ui.ConfirmHandler;
import emu.jvic.ui.MachineInputProcessor;
import emu.jvic.ui.ViewportManager;

/**
 * The main screen in the JVic emulator, i.e. the one that shows the video 
 * output of the VIC.
 * 
 * @author Lance Ewing
 */
public class MachineScreen implements Screen {

  /**
   * The Game object for JVicGdx. Allows us to easily change screens.
   */
  private JVicGdx jvic;
  
  /**
   * This represents the VIC 20 machine.
   */
  private Machine machine;

  /**
   * The Thread that updates the machine at the expected rate.
   */
  private MachineRunnable machineRunnable;
  
  /**
   * The InputProcessor for the MachineScreen. Handles the key and touch input.
   */
  private MachineInputProcessor machineInputProcessor;
  
  /**
   * SpriteBatch shared by all rendered components.
   */
  private SpriteBatch batch;
  
  // Components to support rendering of the VIC 20 screen.
  private Pixmap screenPixmap;
  private Viewport viewport;
  private Camera camera;
  private Texture[] screens;
  private int drawScreen = 1;
  private int updateScreen = 0;
  
  // UI components.
  private Texture joystickIcon;
  private Texture keyboardIcon;

  private ViewportManager viewportManager;
  
  /**
   * Constructor for MachineScreen.
   * 
   * @param jvic The JVicGdx instance.
   * @param confirmHandler
   */
  public MachineScreen(JVicGdx jvic, ConfirmHandler confirmHandler) {
    this.jvic = jvic;
    
    // Create the Machine, at this point not configured with a MachineType.
    this.machine = new Machine();
    this.machineRunnable = new MachineRunnable(this.machine);
    
    batch = new SpriteBatch();
    
    keyboardIcon = new Texture("png/keyboard_icon.png");
    joystickIcon = new Texture("png/joystick_icon.png");
    
    viewportManager = ViewportManager.getInstance();
    
    // Create and register an input processor for keys, etc.
    machineInputProcessor = new MachineInputProcessor(this, confirmHandler);
    
    // Start up the MachineRunnable Thread. It will initially be paused, awaiting machine configuration.
    Thread machineThread = new Thread(this.machineRunnable);
    machineThread.start();
  }
  
  /**
   * Initialises the Machine with the given AppConfigItem. This will represent an app that was
   * selected on the HomeScreen. As part of this initialisation, it creates the Pixmap, screen
   * Textures, Camera and Viewport required to render the VIC 20 screen at the size needed for
   * the MachineType being emulatoed.
   * 
   * @param appConfigItem The configuration for the app that was selected on the HomeScreen.
   */
  public void initMachine(AppConfigItem appConfigItem) {
    if (appConfigItem.getFileType().equals("")) {
      // If there is no file type, there is no file to load and we simply boot in to BASIC.
      machine.init(appConfigItem.getRam(), appConfigItem.getMachineType());
    } else {
      // Otherwise there is a file to load.
      machine.init(appConfigItem.getFilePath(), appConfigItem.getFileType(), appConfigItem.getMachineType(), appConfigItem.getRam());
    }
    
    // Create the libGDX screen resources used by the VIC 20 screen to the size required by the MachineType.
    screenPixmap = new Pixmap(machine.getMachineType().getTotalScreenWidth(), machine.getMachineType().getTotalScreenHeight(), Pixmap.Format.RGB565);
    screens = new Texture[3];
    screens[0] = new Texture(screenPixmap, Pixmap.Format.RGB565, false);
    screens[1] = new Texture(screenPixmap, Pixmap.Format.RGB565, false);
    screens[2] = new Texture(screenPixmap, Pixmap.Format.RGB565, false);
    camera = new OrthographicCamera();
    viewport = new ExtendViewport(machine.getScreenWidth(), machine.getScreenHeight(), camera);
  }
  
  private long lastLogTime;
  private long avgRenderTime;
  private long avgDrawTime;
  private long renderCount;
  private long drawCount;
  
  @Override
  public void render(float delta) {
    long renderStartTime = TimeUtils.nanoTime();
    long fps = Gdx.graphics.getFramesPerSecond();
    long maxFrameDuration = (long)(1000000000L * (fps == 0? 0.016667f : delta));
    boolean draw = false;
    
    // This is to be absolutely safe. We shouldn't have shown this screen if it hasn't yet
    // been initialised with an AppConfigItem that would have configured the camera, etc.
    if (camera == null) return;
    
    if (machine.isPaused()) {
      // When paused, we limit the draw frequency since there isn't anything to change.
      draw = ((fps < 30) || ((renderCount % (fps/30)) == 0));
      
    } else {
      // Check if the Machine has a frame ready to be displayed.
      short[] framePixels = machine.getFramePixels();
      if (framePixels != null) {
        // If it does then update the Texture on the GPU.
        BufferUtils.copy(framePixels, 0, screenPixmap.getPixels(), 
            machine.getMachineType().getTotalScreenWidth() * machine.getMachineType().getTotalScreenHeight());
        screens[updateScreen].draw(screenPixmap, 0, 0);
        updateScreen = (updateScreen + 1) % 3;
        drawScreen = (drawScreen + 1) % 3;
      }
      
      draw = true;
    }
    
    if (draw) {
      drawCount++;
      draw();
      long drawDuration = TimeUtils.nanoTime() - renderStartTime;
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
          "[%d] avgDrawTime: %d avgRenderTime: %d maxFrameDuration: %d delta: %f fps: %d", 
          drawCount, avgDrawTime, avgRenderTime, maxFrameDuration, delta, Gdx.graphics.getFramesPerSecond()));
    }
  }

  private void draw() {
    // Get the KeyboardType currently being used by the MachineScreenProcessor.
    KeyboardType keyboardType = machineInputProcessor.getKeyboardType();
    
    Gdx.gl.glClearColor(0, 0, 0, 1);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    
    // Render the VIC screen.
    camera.update();
    batch.setProjectionMatrix(camera.combined);
    batch.disableBlending();
    batch.begin();
    Color c = batch.getColor();
    batch.setColor(c.r, c.g, c.b, 1f);
    batch.draw(screens[drawScreen], 
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
    if (keyboardType.equals(KeyboardType.JOYSTICK)) {
      if (viewportManager.isPortrait()) {
        batch.draw(keyboardType.getTexture(KeyboardType.LEFT), 0, 0);
        batch.draw(keyboardType.getTexture(KeyboardType.RIGHT), viewportManager.getWidth() - 135, 0);
      } else {
        batch.draw(keyboardType.getTexture(KeyboardType.LEFT), 0, 0, 201, 201);
        batch.draw(keyboardType.getTexture(KeyboardType.RIGHT), viewportManager.getWidth() - 135, 0);
      }
    } else if (keyboardType.isRendered()) {
      batch.setColor(c.r, c.g, c.b, keyboardType.getOpacity());
      batch.draw(keyboardType.getTexture(), 0, keyboardType.getRenderOffset());
    }
    else if (keyboardType.equals(KeyboardType.OFF)) {
      // The keyboard and joystick icons are rendered only when an input type isn't showing.
      batch.setColor(c.r, c.g, c.b, 0.5f);
      if (viewportManager.isPortrait()) {
        batch.draw(joystickIcon, 0, 0);
        batch.draw(keyboardIcon, viewportManager.getWidth() - 145, 0);
        
        if (Gdx.app.getType().equals(ApplicationType.Android)) {
          // Mobile keyboard for debug purpose. Wouldn't normally make this available.
          batch.setColor(c.r, c.g, c.b, 0.15f);
          batch.draw(keyboardIcon, viewportManager.getWidth() - viewportManager.getWidth()/2 - 70, 0);
        }
      } else {
        batch.draw(joystickIcon, 0, viewportManager.getHeight() - 140);
        batch.draw(keyboardIcon, viewportManager.getWidth() - 150, viewportManager.getHeight() - 125);
      }
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
    
    machineInputProcessor.resize(width, height);
    viewportManager.update(width, height);
  }

  @Override
  public void pause() {
    // On Android, this is also called when the "Home" button is pressed.
    machineRunnable.pause();
  }

  @Override
  public void resume() {
    KeyboardType.init();
    machineRunnable.resume();
  }
  
  @Override
  public void show() {
    // Note that this screen should not be shown unless the Machine has been initialised by calling
    // the initMachine method of MachineScreen. This will create the necessary PixMap and Textures 
    // required for the MachineType.
    KeyboardType.init();
    Gdx.input.setInputProcessor(machineInputProcessor);
    machineRunnable.resume();
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
    batch.dispose();
    machineRunnable.stop();
    disposeScreens();
  }
  
  /**
   * Disposes the libGDX screen resources that are recreated for each new AppConfigItem.
   */
  public void disposeScreens() {
    Gdx.app.log("MachineScreen", "Disposing screens");
    if (screenPixmap != null) {
      screenPixmap.dispose();
      screenPixmap = null;
    }
    if (screens != null) {
      screens[0].dispose();
      screens[1].dispose();
      screens[2].dispose();
      screens = null;
    }
    viewport = null;
    camera = null;
  }
  
  /**
   * Gets the Machine that this MachineScreen is running.
   *  
   * @return The Machine that this MachineScreen is running.
   */
  public Machine getMachine() {
    return machine;
  }
  
  /**
   * Gets the MachineRunnable that is running the Machine.
   * 
   * @return The MachineRunnable that is running the Machine.
   */
  public MachineRunnable getMachineRunnable() {
    return machineRunnable;
  }
  
  /**
   * Returns user to the Home screen.
   */
  public void exit() {
    // When we go back to the home screen, we free up any resources that were created specifically 
    // to the screen size of the MachineType that was specified by the AppConfigItem.
    disposeScreens();

    jvic.setScreen(jvic.getHomeScreen());
  }
}
