package emu.jvic;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Pixmap;

import emu.jvic.config.AppConfigItem;
import emu.jvic.sound.SoundGenerator;
import emu.jvic.ui.MachineInputProcessor;

/**
 * Using this JVicRunner with a Thread/Web Worker is an alternative to relying on 
 * the GDX UI thread for updating the Machine state. Normally libGDX would be used for 
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
 * Making use of this JVicRunner in a separate background thread/worker appears to 
 * lead to much better performance on such lower spec devices. By using a separate
 * thread, we're allowing the UI thread to focus solely on rendering within the render
 * method. This results in it exiting the render method much quicker, which in turn appears
 * to make Android on some devices believe that the application is capable of a 
 * higher FPS and therefore it triggers the render more often. On the device that 
 * dropped to 1 FPS after only half a minute or so, it reports that it is now running
 * at over 8000 FPS simply by updating the Machine state in a separate Thread/worker.
 * 
 * @author Lance Ewing
 */
public abstract class JVicRunner {

    protected MachineScreen machineScreen;
    
    protected KeyboardMatrix keyboardMatrix;
    protected PixelData pixelData;
    protected SoundGenerator soundGenerator;
    
    protected boolean exit = false;
    protected boolean paused = true;
    protected boolean warpSpeed = false;
    
    /**
     * Constructor for JVicRunner.
     * 
     * @param keyboardMatrix
     * @param pixelData
     * @param psg
     */
    public JVicRunner(KeyboardMatrix keyboardMatrix, PixelData pixelData, SoundGenerator soundGenerator) {
        this.keyboardMatrix = keyboardMatrix;
        this.pixelData = pixelData;
        this.soundGenerator = soundGenerator;
    }
    
    /**
     * Initialises the JVicRunner with anything that needs setting up before it starts.
     * 
     * @param machineScreen 
     * @param pixmap
     */
    public void init(MachineScreen machineScreen, int width, int height) {
        this.machineScreen = machineScreen;
        
        pixelData.init(width, height);
        
        // These are keys that we want to catch and not let the web browser 
        // respond to.
        Gdx.input.setCatchKey(Input.Keys.TAB, true);
        Gdx.input.setCatchKey(Input.Keys.ESCAPE, true);
        Gdx.input.setCatchKey(Input.Keys.F1, true);
        Gdx.input.setCatchKey(Input.Keys.F2, true);
        Gdx.input.setCatchKey(Input.Keys.F3, true);
        Gdx.input.setCatchKey(Input.Keys.F4, true);
        Gdx.input.setCatchKey(Input.Keys.F5, true);
        Gdx.input.setCatchKey(Input.Keys.F6, true);
        Gdx.input.setCatchKey(Input.Keys.F7, true);
        Gdx.input.setCatchKey(Input.Keys.F8, true);
        Gdx.input.setCatchKey(Input.Keys.F9, true);
        Gdx.input.setCatchKey(Input.Keys.F10, true);
        // F11 in the browser is full screen, which is what JVic does anyway, so its fine.
        Gdx.input.setCatchKey(Input.Keys.F12, true);
        Gdx.input.setCatchKey(Input.Keys.CONTROL_LEFT, true);
        Gdx.input.setCatchKey(Input.Keys.CONTROL_RIGHT, true);
        Gdx.input.setCatchKey(Input.Keys.ALT_LEFT, true);
        Gdx.input.setCatchKey(Input.Keys.ALT_RIGHT, true);
    }
    
    /**
     * Returns the KeyboardMatrix implementation class instance in use by JVic. 
     * 
     * @return
     */
    public KeyboardMatrix getKeyboardMatrix() {
        return keyboardMatrix;
    }
    
    /**
     * Returns the SoundGenerator implementation class instance in use by JVic.
     * 
     * @return
     */
    public SoundGenerator getSoundGenerator() {
        return soundGenerator;
    }
    
    /**
     * Updates Pixmap with the latest local changes within our implementation specific
     * PixelData.
     * 
     * @param pixmap
     */
    public void updatePixmap(Pixmap pixmap) {
        pixelData.updatePixmap(pixmap);
    }
        
    /**
     * Toggles the current warp speed state.
     */
    public void toggleWarpSpeed() {
        warpSpeed = !warpSpeed;
    }

    /**
     * Returns whether or not warp speed is active.
     * 
     * @return
     */
    public boolean isWarpSpeed() {
        return warpSpeed;
    }
    
    /**
     * Pauses the MachineRunnable.
     */
    public void pause() {
        paused = true;
    }

    /**
     * Resumes the JVicRunner..
     */
    public void resume() {
        paused = false;
        // NOTE: subclasses will override and do additional actions.
    }

    /**
     * Stops the JVicRunner.
     */
    public void stop() {
        exit = true;
        if (paused) resume();
        // NOTE: subclasses will override and do additional actions.
    }

    public boolean isPaused() {
        return paused;
    }
    
    public void changeSound(boolean soundOn) {
        if (soundOn) {
            soundGenerator.resumeSound();
        } else {
            soundGenerator.pauseSound();
        }
    }

    public boolean isSoundOn() {
        return soundGenerator.isSoundOn();
    }
    
    public MachineScreen getMachineScreen() {
        return machineScreen;
    }
    
    public MachineInputProcessor getMachineInputProcessor() {
        return (machineScreen != null? machineScreen.getMachineInputProcessor() : null);
    }
    
    public byte[] loadKernalRom(MachineType machineType) {
        byte[] kernalRom = null;
        
        switch (machineType) {
            case NTSC:
                kernalRom = Gdx.files.internal("roms/kernal_ntsc.rom").readBytes();
                break;
            case PAL:
                kernalRom = Gdx.files.internal("roms/kernal_pal.rom").readBytes();
                break;
            case VIC44:
                kernalRom = Gdx.files.internal("roms/vic_44_kernal.rom").readBytes();
                break;
            case VIC44K:
                kernalRom = Gdx.files.internal("roms/vic_44k_kernal.rom").readBytes();
                break;
        }
        
        return kernalRom;
    }
    
    public byte[] loadBasicRom(MachineType machineType) {
        byte[] basicRom = null;
        
        switch (machineType) {
            case NTSC:
                basicRom = Gdx.files.internal("roms/basic.rom").readBytes();
                break;
            case PAL:
                basicRom = Gdx.files.internal("roms/basic.rom").readBytes();
                break;
            case VIC44:
                basicRom = Gdx.files.internal("roms/vic_44_basic.rom").readBytes();
                break;
            case VIC44K:
                basicRom = Gdx.files.internal("roms/vic_44k_basic.rom").readBytes();
                break;
        }
        
        return basicRom;
    }
    
    public abstract void start(AppConfigItem appConfigItem);

    public abstract void reset();

    public abstract boolean hasStopped();

    public abstract boolean hasTouchScreen();

    public abstract boolean isMobile();

    public abstract String slugify(String input);

    public abstract void cancelImport();

    public abstract boolean isRunning();
    
    public abstract void sendNmi();
    
    public abstract void saveScreenshot(Pixmap screenPixmap, AppConfigItem appConfigItem);
}
