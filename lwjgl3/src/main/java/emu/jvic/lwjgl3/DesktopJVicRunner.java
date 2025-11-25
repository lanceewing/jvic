package emu.jvic.lwjgl3;

import java.text.Normalizer;
import java.text.Normalizer.Form;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.TimeUtils;

import emu.jvic.JVicRunner;
import emu.jvic.KeyboardMatrix;
import emu.jvic.Machine;
import emu.jvic.MachineType;
import emu.jvic.PixelData;
import emu.jvic.Program;
import emu.jvic.config.AppConfigItem;
import emu.jvic.cpu.Cpu6502;
import emu.jvic.memory.RamType;
import emu.jvic.sound.SoundGenerator;

public class DesktopJVicRunner extends JVicRunner {

    private Thread machineThread;
    
    private Machine machine;
    
    public DesktopJVicRunner(KeyboardMatrix keyboardMatrix, PixelData pixelData, SoundGenerator soundGenerator) {
        super(keyboardMatrix, pixelData, soundGenerator);
    }

    @Override
    public void start(AppConfigItem appConfigItem) {
        // Default speak state is ON for Desktop when starting.
        getMachineInputProcessor().setSpeakerOn(true);
        machineThread = new Thread(new Runnable() {
            @Override
            public void run() {
                runProgram(appConfigItem);
            }
        });
        machineThread.start();
    }

    private void runProgram(AppConfigItem appConfigItem) {
        // Start by loading game. We deliberately do this within the thread and
        // not in the main libgdx UI thread.
        DesktopProgramLoader programLoader = new DesktopProgramLoader(pixelData);
        
        // We fetch the files via a generic callback mechanism, mainly to support GWT,
        // but no reason we can't code it for Desktop as well.
        programLoader.fetchProgram(appConfigItem, p -> runProgram(appConfigItem, p));
    }
    
    private void runProgram(AppConfigItem appConfigItem, Program program) {
        MachineType machineType = MachineType.valueOf(appConfigItem.getMachineType());
        RamType ramType = RamType.valueOf(appConfigItem.getRam());
        
        // Create the Machine instance that will run the Oric program.
        machine = new Machine(soundGenerator, keyboardMatrix, pixelData);
        
        // Load the ROM files.
        byte[] basicRom = Gdx.files.internal("roms/basic.rom").readBytes();
        byte[] dos1541Rom = Gdx.files.internal("roms/dos1541.rom").readBytes();
        byte[] charRom = Gdx.files.internal("roms/char.rom").readBytes();
        byte[] kernalRom = (machineType.equals(MachineType.NTSC)?
                Gdx.files.internal("roms/kernal_ntsc.rom").readBytes() :
                Gdx.files.internal("roms/kernal_pal.rom").readBytes());
        
        machine.init(basicRom, kernalRom, charRom, dos1541Rom, program, machineType, ramType);
        
        final int NANOS_PER_FRAME = (1000000000 / MachineType.PAL.getFramesPerSecond());
        
        long lastTime = TimeUtils.nanoTime();

        while (true) {
            if (paused) {
                synchronized (this) {
                    try {
                        while (paused) {
                            wait();
                        }
                    } catch (InterruptedException e) {
                        // Nothing to do.
                    }

                    if (!exit) {
                        // An unknown amount of time will have passed. So reset timing.
                        lastTime = TimeUtils.nanoTime();
                    }
                }
            }

            if (exit) {
                // Returning from the method will stop the thread cleanly.
                pixelData.clearPixels();
                break;
            }

            // Updates the Machine's state for a frame.
            machine.update();

            if (!warpSpeed) {
                // Throttle at expected FPS. Note that the PSG naturally throttles at 50 FPS
                // without the yield.
                while (TimeUtils.nanoTime() - lastTime <= 0L) {
                    Thread.yield();
                }
                lastTime += NANOS_PER_FRAME;
            } else {
                lastTime = TimeUtils.nanoTime();
            }
        }
        
        machine = null;
    }

    @Override
    public void stop() {
        super.stop();
        
        if ((machineThread != null) && machineThread.isAlive()) {
            // If the thread is still running, and is either waiting on the wait() above,
            // or it is sleeping within the UserInput or TextGraphics classes, then this
            // interrupt call will wake it up, the QuitAction will be thrown, and then the
            // thread will cleanly and safely stop.
            machineThread.interrupt();
        }
    }
    
    @Override
    public void resume() {
        synchronized (this) {
            super.resume();
            this.notifyAll();
        }
    }
    
    @Override
    public void reset() {
        exit = false;
        machineThread = null;
        machine = null;
    }

    @Override
    public boolean hasStopped() {
        return ((machineThread != null) && !machineThread.isAlive());
    }

    @Override
    public boolean hasTouchScreen() {
        // We don't check this for Desktop.
        return false;
    }

    @Override
    public boolean isMobile() {
        // Desktop/Java/LWJGL platform is obviously not mobile.
        return false;
    }

    @Override
    public String slugify(String input) {
        if ((input == null) | (input.isEmpty())) {
            return "";
        }
        
        // Make lower case and trim.
        String slug = input.toLowerCase().trim();
        
        // Remove accents from characters.
        slug = Normalizer.normalize(slug, Form.NFD).replaceAll("[\u0300-\u036f]", "");
        
        // Remove invalid chars.
        slug = slug.replaceAll("[^a-z0-9\\s-]", "").trim();
        
        // Replace multiple spaces or hyphens with a single hyphen.
        slug = slug.replaceAll("[\\s-]+", "-");
        
        return slug;
    }

    @Override
    public void cancelImport() {
        // Nothing to do for Desktop.
    }

    @Override
    public boolean isRunning() {
        return (machineThread != null);
    }

    @Override
    public void sendNmi() {
        if (machine != null) {
            machine.getCpu().setInterrupt(Cpu6502.S_NMI);
        }
    }
}
