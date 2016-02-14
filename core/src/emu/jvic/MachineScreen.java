package emu.jvic;

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
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

/**
 * The main screen in the JVic emulator, i.e. the one that shows the video 
 * output of the VIC.
 * 
 * @author Lance Ewing
 */
public class MachineScreen implements Screen {

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
    
    camera = new OrthographicCamera();
    cameraHUD = new OrthographicCamera();

    viewport = new ExtendViewport(machine.getScreenWidth(), machine.getScreenHeight(), camera);
    viewportHUD = new ExtendViewport(keyboardPortrait.getWidth(), keyboardPortrait.getHeight(), cameraHUD);
    //viewportHUD = new ExtendViewport(keyboardLandscape.getWidth(), keyboardLandscape.getHeight(), cameraHUD);
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
    BufferUtils.copy(machine.getFramePixels(), 0, screenPixmap.getPixels(), 88608);

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
    
    // Render the UI elements, e.g. the keyboard.
    cameraHUD.update();
    batch.setProjectionMatrix(cameraHUD.combined);
    batch.enableBlending();
    batch.begin();
    c = batch.getColor();
    batch.setColor(c.r, c.g, c.b, 0.5f);
    batch.draw(keyboardPortrait, 0, 0);
    //batch.draw(keyboardLandscape, 0, 0);
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
}
