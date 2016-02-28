package emu.jvic.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

/**
 * Manages the Viewports that are used for rendering the UI components.
 * 
 * @author Lance Ewing
 */
public class ViewportManager {

  /**
   * Viewport to use when in portrait mode.
   */
  private Viewport portraitViewport;
  
  /**
   * Camera to use when in portrait mode.
   */
  private Camera portraitCamera;
  
  /**
   * Viewport to use when in landscape mode.
   */
  private Viewport landscapeViewport;
  
  /**
   * Camera to use when in landscape mode.
   */
  private Camera landscapeCamera;
  
  /**
   * Whether the current Viewport is the portrait Viewport or not.
   */
  private boolean portrait;
  
  /**
   * Constructor for ViewportManager.
   */
  public ViewportManager() {
    portraitCamera = new OrthographicCamera();
    portraitViewport = new ExtendViewport(1080, 1, portraitCamera);
    landscapeCamera = new OrthographicCamera();
    landscapeViewport = new ExtendViewport(1920, 1, landscapeCamera);
    portrait = true;
  }
  
  /**
   * Invoked when the MachineScreen is resized so that the UI viewport and camera
   * can be updated and switched for a possible change in orientation.
   * 
   * @param width The new screen width.
   * @param height The new screen height.
   */
  public void update(int width, int height) {
    portrait = (height > width);
    getCurrentViewport().update(width, height, true);
  }
  
  /**
   * Forces an update on the current Viewport using the current screen's size.
   */
  public void update() {
    update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
  }
  
  /**
   * Returns true if the orientation is currently portrait; otherwise false.
   * 
   * @return true if the orientation is currently portrait; otherwise false.
   */
  public boolean isPortrait() {
    return portrait;
  }

  /**
   * Gets the current Camera to use when rendering UI components.
   * 
   * @return The current Camera to use when rendering UI components.
   */
  public Camera getCurrentCamera() {
    return (portrait? portraitCamera : landscapeCamera);
  }
  
  /**
   * Gets the current Viewport to use when rendering UI components.
   * 
   * @return The current Viewport to use when rendering UI components.
   */
  public Viewport getCurrentViewport() {
    return (portrait? portraitViewport : landscapeViewport);
  }
}