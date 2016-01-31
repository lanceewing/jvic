package emu.jvic;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.BufferUtils;

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
  
  /**
   * Constructor for MachineScreen.
   */
  public MachineScreen() {
    this.machine = new Machine();
    
    batch = new SpriteBatch();
    screenPixmap = new Pixmap(machine.getMachineType().getTotalScreenWidth(), machine.getMachineType().getVisibleScreenHeight(), Pixmap.Format.RGBA8888);
    screenTexture = new Texture(screenPixmap, Pixmap.Format.RGBA8888, false);
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
    // other options in the future.
    
    BufferUtils.copy(machine.getFramePixels(), 0, screenPixmap.getPixels(), 77248);
    
    screenTexture.draw(screenPixmap, 0, 0);
    
    batch.begin();
    batch.draw(screenTexture, 0, 0, 408, 272, machine.getScreenLeft(), machine.getScreenTop(), 204, 272, false, false);
    batch.end();
  }
  
  @Override
  public void resize(int width, int height) {
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
