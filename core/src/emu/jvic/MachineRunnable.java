package emu.jvic;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.WindowedMean;
import com.badlogic.gdx.utils.TimeUtils;

import emu.jvic.util.Sync;

/**
 * Using this MachineRunnable with a Thread is an alternative to relying on the GDX
 * UI thread for updating the Machine state. Normally libGDX would be used for 
 * building games, and the recommendation is usually to have your state update and 
 * render code happening within the render call. This is fine in the case of games,
 * where a delta value can be used to update movements, etc. relative to that delta
 * time. Moving a sprite 5 pixels versus 50 pixels takes the same amount of elapsed
 * CPU time.
 * 
 * For an emulator, this isn't the case. Performing an emulation for a delta 10 times
 * as long as another delta would result in the CPU spending 10 times as long 
 * executing that code. There are no shortcuts. So to go with the libGDX recommendation
 * can be a bit dangerous, particularly on slower devices it would seem. If there is 
 * a significant gap between two frames, the machine emulation attempts to execute
 * the number of machine cycles equivalent to the elapsed delta, which means that it
 * spends more time within the render method. From observation, this seems to result
 * in a bit of a snow ball effect. Slower devices appear to notice this and scale 
 * back on how often they're triggering the render and that results in it getting
 * progressively worse until the whole thing locks up. On one device, it was observed
 * that for the first 10-20 seconds or so, it was happily ticking along at 60 FPS. 
 * Something caused it to lag between a few cycles and then it suddenly started getting
 * slower and slower and slower until it was sitting on only 1 FPS!  
 * 
 * Making use of this MachineRunnable in a separate background thread appears to lead
 * to much better performance on such lower spec android devices. By using a separate
 * thread, we're allowing the UI thread to focus solely on rendering within the render
 * method. This results in it exiting the render method much quicker, which in turn appears
 * to make Android on some devices believe that the application is capable of a 
 * higher FPS and therefore it triggers the render more often. On the device that 
 * dropped to 1 FPS after only half a minute or so, it reports that it is now running
 * at over 8000 FPS simply by updating the Machine state in a separate Thread.
 * 
 * @author Lance Ewing
 */
public class MachineRunnable implements Runnable {

  /**
   * The Machine that this Runnable will be running.
   */
  private Machine machine;
  
  private long lastFrameTime = System.nanoTime();
  private float deltaTime = 0;
  private long frameStart = System.nanoTime();
  private int framesThisSecond = 0;
  private int framesLastSecond;
  private long avgUpdateTime;
  private long frameCount;
  
  private boolean exit = false;
  private boolean paused = false;
  
  private WindowedMean deltaTimeMean = new WindowedMean(5);
  
  /**
   * Constructor for MachineRunnable.
   * 
   * @param machine The Machine instance that this Runnable will be running.
   */
  public MachineRunnable(Machine machine) {
    this.machine = machine;
  }

  /**
   * Executes the Machine instance.
   */
  public void run () {
    while (true) {
      boolean resume = false;
      
      if (paused) {
        synchronized (this) {
          try {
            while (paused) {
              wait();
            }
            resume = true;
          } catch (InterruptedException e) {
            Gdx.app.log("MachineRunnable", e.getMessage(), e);
          }
        }
      }

      if (exit) return;

      long time = TimeUtils.nanoTime();
      deltaTime = (time - lastFrameTime) / 1000000000.0f;
      lastFrameTime = time;

      // After pause deltaTime can have somewhat huge value that destabilizes the mean, so let's cut it off
      if (!resume) {
        deltaTimeMean.addValue(deltaTime);
      } else {
        deltaTime = 0;
      }
      
      // TODO: Input events should ideally be processed here, if we can figure out how to do it outside of the UI thread that is.
      
      long updateStartTime = TimeUtils.nanoTime();
      
      // Updates the Machine's state for the time that has passed.
      machine.update(deltaTimeMean.getMean() == 0 ? deltaTime : deltaTimeMean.getMean());
      
      long updateEndTime = TimeUtils.nanoTime();
      long updateDuration = updateEndTime - updateStartTime;
      if (frameCount++ == 0) {
        avgUpdateTime = updateDuration;
      } else {
        avgUpdateTime = ((avgUpdateTime * frameCount) + updateDuration) / (frameCount + 1);
      }
      
      // Utilises a highly accurate FPS sync take from LWJGL project.
      Sync.sync(machine.getMachineType().getFramesPerSecond());
      
      if (time - frameStart > 1000000000) {
        framesLastSecond = framesThisSecond;
        framesThisSecond = 0;
        frameStart = time;
        Gdx.app.log("MachineRunnable", 
            "fps: " + framesLastSecond + 
            ", delta (mean): " + (deltaTimeMean.getMean() == 0 ? deltaTime : deltaTimeMean.getMean()) + 
            ", avgUpdateTime: " + avgUpdateTime);
      }
      framesThisSecond++;
    }
  }

  /**
   * Pauses the MachineRunnable.
   */
  public void pause() {
    paused = true;
  }
  
  /**
   * Resumes the MachineRunnable.
   */
  public void resume() {
    synchronized (this) {
      paused = false;
      this.notifyAll();
    }
  }
  
  /** 
   * Stops the MachineRunnable.
   */
  public void stop() {
    exit = true;
    if (paused) resume();
  }
}
