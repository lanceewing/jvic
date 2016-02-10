package emu.jvic;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Camera;
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
  private Pixmap screenPixmap;
  private Viewport viewport;
  private Camera camera;
  private Camera cameraHUD;
  
  
  /**
   * Constructor for MachineScreen.
   */
  public MachineScreen() {
    this.machine = new Machine();
    
    batch = new SpriteBatch();
    screenPixmap = new Pixmap(machine.getMachineType().getTotalScreenWidth(), machine.getMachineType().getVisibleScreenHeight(), Pixmap.Format.RGBA8888);
    screenTexture = new Texture(screenPixmap, Pixmap.Format.RGBA8888, false);
    
    camera = new OrthographicCamera();
    
    // TODO: Decide if this is really needed. Surely if the main camera isn't actually moving around, we don't need a separate one for the UI.
    cameraHUD = new OrthographicCamera();

    viewport = new ExtendViewport(machine.getScreenWidth(), machine.getScreenHeight(), camera);
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
    //camera.update();
    
    batch.setProjectionMatrix(camera.combined);
    
    Gdx.gl.glClearColor(0, 0, 0, 1);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    
    // This is probably not the most efficient way of getting the pixels to the GPU, but
    // will suffice for this initial conversion of JVic. I'll spend more time profiling 
    // other options in the future.
    
    BufferUtils.copy(machine.getFramePixels(), 0, screenPixmap.getPixels(), 77248);
    
    screenTexture.draw(screenPixmap, 0, 0);

    batch.begin();
    batch.draw(screenTexture, 
        0,
        0,
        machine.getScreenWidth(), machine.getScreenHeight(), 
        machine.getScreenLeft(), machine.getScreenTop(), 
        machine.getScreenWidth() >> 1, machine.getScreenHeight(), 
        false, false);    
    batch.end();
  }
  
  @Override
  public void resize(int width, int height) {
    //viewport.update(width, height, true);
    viewport.update(width, height, false);
    
    //align game box's top edge to top of screen
    Camera camera = viewport.getCamera();
    camera.position.x = machine.getScreenWidth() /2;
    camera.position.y = machine.getScreenHeight() - viewport.getWorldHeight()/2;
    camera.update();
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
