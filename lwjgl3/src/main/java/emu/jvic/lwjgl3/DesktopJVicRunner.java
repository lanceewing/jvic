package emu.jvic.lwjgl3;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.Queue;
import java.util.concurrent.Callable;

import com.badlogic.gdx.Application.ApplicationType;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.PixmapIO.PNG;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Base64Coder;
import com.badlogic.gdx.utils.TimeUtils;

import emu.jvic.JVic;
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
import emu.jvic.ui.MachineInputProcessor.ScreenSize;

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
        
        // Create the Machine instance that will run the VIC 20 program.
        machine = new Machine(soundGenerator, keyboardMatrix, pixelData);
        
        // Load the ROM files.
        byte[] basicRom = Gdx.files.internal("roms/basic.rom").readBytes();
        byte[] dos1541Rom = Gdx.files.internal("roms/dos1541.rom").readBytes();
        byte[] charRom = Gdx.files.internal("roms/char.rom").readBytes();
        byte[] kernalRom = (machineType.equals(MachineType.NTSC)?
                Gdx.files.internal("roms/kernal_ntsc.rom").readBytes() :
                Gdx.files.internal("roms/kernal_pal.rom").readBytes());
        
        Queue<char[]> autoRunCmdQueue = null;
        Callable<Queue<char[]>> autoLoadProgram = machine.init(
                basicRom, kernalRom, charRom, dos1541Rom, program, machineType, ramType);
        
        final int NANOS_PER_FRAME = (1000000000 / machineType.getFramesPerSecond());
        
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
            
            // Check for BASIC program auto-load
            if (autoLoadProgram != null) {
                int[] mem = machine.getMemory().getMemoryArray();
                
                // We need to wait for BASIC to boot up before loading the program.
                // The simplest way to wait for BASIC to be ready is to check for
                // the starting cursor position.
                
                if (mem[0xD1] == 110) {
                    // Now that the BASIC cursor is in the start position, let's load the
                    // program data in to memory.
                    try {
                        autoRunCmdQueue = autoLoadProgram.call();
                    } catch (Exception e) {}
                    
                    // If there is an auto run command, then run it.
                    runNextBasicCommand(autoRunCmdQueue, mem);
                    
                    if (autoRunCmdQueue.isEmpty()) {
                        autoLoadProgram = null;
                    }
                }
                
                // If it is a DISK, then we run two commands, the second being the RUN.
                if (mem[0xD1] == 220) {
                    runNextBasicCommand(autoRunCmdQueue, mem);
                    autoLoadProgram = null;
                }
            }

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
    
    private void runNextBasicCommand(Queue<char[]> cmdQueue, int[] mem) {
        if ((cmdQueue != null) && (!cmdQueue.isEmpty())) {
            // Keyboard buffer, 10 bytes (631 - 640)
            char[] cmdChars = cmdQueue.remove();
            int cmdCharPos = 0;
            for (; cmdCharPos < cmdChars.length; cmdCharPos++) {
                mem[631 + cmdCharPos] = cmdChars[cmdCharPos];
            }
            mem[631 + cmdCharPos] = 0x0D;
            
            // Num of chars in keyboard buffer.
            mem[198] = cmdCharPos + 1;
        }
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

    @Override
    public void saveScreenshot(Pixmap screenPixmap, AppConfigItem appConfigItem) {
        String friendlyAppName = appConfigItem != null ? appConfigItem.getName().replaceAll("[ ,\n/\\:;*?\"<>|!]", "_")
                : "shot";
        if (Gdx.app.getType().equals(ApplicationType.Desktop)) {
            try {
                StringBuilder filePath = new StringBuilder("jvic_screens/");
                filePath.append(friendlyAppName);
                filePath.append("_");
                filePath.append(System.currentTimeMillis());
                filePath.append(".png");
                
                MachineType machineType = machineScreen.getMachineType();
                ScreenSize currentScreenSize = machineScreen.getMachineInputProcessor().getScreenSize();
                int renderWidth = currentScreenSize.getRenderWidth(machineType);
                int renderHeight = currentScreenSize.getRenderHeight(machineType);
                Pixmap pixmap = new Pixmap(renderWidth, renderHeight, Pixmap.Format.RGBA8888);
                pixmap.drawPixmap(
                        screenPixmap, 
                        machineType.getHorizontalOffset(), machineType.getVerticalOffset(),
                        machineType.getVisibleScreenWidth(), machineType.getVisibleScreenHeight(),
                        0, 0, renderWidth, renderHeight);
                
                PixmapIO.writePNG(Gdx.files.external(filePath.toString()), pixmap);
            } catch (Exception e) {
                // Ignore.
            }
        }
        
        if (appConfigItem != null) {
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                PNG writer = new PNG((int) (screenPixmap.getWidth() * screenPixmap.getHeight() * 1.5f));
                try {
                    writer.setFlipY(false);
                    writer.write(out, screenPixmap);
                } finally {
                    writer.dispose();
                }
                JVic jvic = machineScreen.getJVic();
                jvic.getScreenshotStore().putString(friendlyAppName, new String(Base64Coder.encode(out.toByteArray())));
                jvic.getScreenshotStore().flush();
            } catch (IOException ex) {
                // Ignore.
            }
        }
    }
}
