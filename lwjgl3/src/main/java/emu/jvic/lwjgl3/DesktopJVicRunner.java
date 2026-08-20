package emu.jvic.lwjgl3;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;

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
import emu.jvic.io._65omatic.MenuConfigIO;
import emu.jvic.io._65omatic.MenuConfigIO.ResetCallback;
import emu.jvic.io.disk.persistence.DiskImagePersistence;
import emu.jvic.io.disk.persistence.DiskImagePersistenceSession;
import emu.jvic.io.disk.persistence.NoOpDiskImagePersistence;
import emu.jvic.lwjgl3.disk.DesktopDiskImagePersistence;
import emu.jvic.memory.RamType;
import emu.jvic.sound.SoundGenerator;
import emu.jvic.ui.MachineInputProcessor.ScreenSize;

public class DesktopJVicRunner extends JVicRunner {

    private Thread machineThread;
    private final Queue<Runnable> pendingMachineActions = new ConcurrentLinkedQueue<Runnable>();
    
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
        byte[] mountedDiskImageData = resolveMountedDiskImageData(appConfigItem, program);
        if (mountedDiskImageData != null) {
            DiskImagePersistence diskImagePersistence = createDiskImagePersistence(appConfigItem);
            diskImagePersistence.resolve(appConfigItem, mountedDiskImageData,
                persistenceSession -> runProgram(appConfigItem, program,
                    persistenceSession, mountedDiskImageData));
            return;
        }

        runProgram(appConfigItem, program, null, null);
    }

    private DiskImagePersistence createDiskImagePersistence(AppConfigItem appConfigItem) {
        switch (appConfigItem.getDiskWriteMode()) {
            case OFF:
            case TEMP:
                return new NoOpDiskImagePersistence();
            case DEFAULT:
            case PERSIST:
            default:
                return new DesktopDiskImagePersistence();
        }
    }

    private MenuConfigIO applyMenuConfigIOOverrides(final AppConfigItem appConfigItem) {
        if ("65omatic".equals(appConfigItem.getName())) {
            MenuConfigIO menuConfigIO = new MenuConfigIO(
                new ResetCallback() {
                    @Override
                    public void triggerReset() {
                        // This callback is invoked from within machine.update(), which runs on
                        // the machine thread itself. We must NOT block here waiting for the
                        // machine thread to stop — that would cause a deadlock (the thread
                        // waiting for itself to die). Instead, we enqueue the stop+restart as
                        // a pending action. The machine thread returns from triggerReset(),
                        // completes the current cycle, then picks up the pending action at
                        // the top of the main loop via processPendingMachineActions().
                        enqueueMachineAction(new Runnable() {
                            @Override
                            public void run() {
                                stop();
                                start(appConfigItem);
                            }
                        });
                    }
                }
            );
            if (menuConfigIO.getBootMode() == MenuConfigIO.BOOT_SKIP_MENU) {
                System.out.println("65omatic menu config overrides: RAM=" + menuConfigIO.getRamType().name() + ", KERNAL machine type=" + menuConfigIO.getKernalMachineType().name());
                appConfigItem.setRam(menuConfigIO.getRamType().name());
                appConfigItem.setMachineType(menuConfigIO.getKernalMachineType().name());
                appConfigItem.setFilePath("");
                appConfigItem.setFileType("");
                appConfigItem.setLoadAddress(null);
                return null;
            } else {
                System.out.println("65omatic menu activated");
                appConfigItem.setRam("RAM_8K");
                appConfigItem.setMachineType("PAL");
                appConfigItem.setFilePath("roms/menu_rom.bin");
                appConfigItem.setFileType("CART");
                appConfigItem.setLoadAddress("A000");
                return menuConfigIO;
            }
        } else {
            // It isn't the 65omatic menu, so don't build a MenuConfigIO.
            return null;
        }
    }
    
    private void runProgram(AppConfigItem appConfigItem, Program program,
            DiskImagePersistenceSession persistenceSession, byte[] mountedDiskImageData) {

        // TODO: This will only work for Desktop at the moment, since web needs different storage for web worker.
        MenuConfigIO menuConfigIO = applyMenuConfigIOOverrides(appConfigItem);

        MachineType machineType = MachineType.valueOf(appConfigItem.getMachineType());
        RamType ramType = RamType.valueOf(appConfigItem.getRam());
        
        // Create the Machine instance that will run the VIC 20 program.
        machine = new Machine(soundGenerator, keyboardMatrix, pixelData);
        machine.setMenuConfigIO(menuConfigIO);

        // Load the ROM files.
        byte[] basicRom = loadBasicRom(machineType);
        byte[] dos1541Rom = Gdx.files.internal("roms/dos1541.rom").readBytes();
        byte[] charRom = Gdx.files.internal("roms/char.rom").readBytes();
        byte[] kernalRom = loadKernalRom(machineType);
        
        Queue<char[]> autoRunCmdQueue = null;
        Callable<Queue<char[]>> autoLoadProgram = machine.init(
            basicRom, kernalRom, charRom, dos1541Rom, program, appConfigItem,
            mountedDiskImageData, machineType, ramType, appConfigItem.getPalette(),
            persistenceSession);
        
        final int NANOS_PER_FRAME = (1000000000 / machineType.getFramesPerSecond());
        
        // Hung-CPU diagnostic: track the PC every CHECK_INTERVAL_FRAMES frames, starting
        // after WARMUP_FRAMES (to skip normal KERNAL boot). If the PC stays in the same
        // 256-byte page for STUCK_THRESHOLD consecutive checks, dump CPU state.
        final int WARMUP_FRAMES = machineType.getFramesPerSecond() * 3;   // 3 seconds
        final int CHECK_INTERVAL_FRAMES = machineType.getFramesPerSecond(); // every 1 second
        final int STUCK_THRESHOLD = 5;
        int frameCount = 0;
        int lastCheckedPc = -1;
        int stuckCount = 0;
        boolean hungReported = false;

        long lastTime = TimeUtils.nanoTime();

        while (true) {
            processPendingMachineActions();

            if (paused) {
                synchronized (this) {
                    try {
                        while (paused) {
                            if (processPendingMachineActions()) {
                                continue;
                            }
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
            
            // Hung-CPU diagnostic check.
            if (!hungReported) {
                frameCount++;
                if (frameCount > WARMUP_FRAMES && (frameCount % CHECK_INTERVAL_FRAMES) == 0) {
                    int currentPc = machine.getCpu().getProgramCounter();
                    if (lastCheckedPc >= 0 && ((currentPc >> 8) == (lastCheckedPc >> 8))) {
                        stuckCount++;
                        System.out.println(String.format(
                            "HungCPU[%d]: PC=$%04X still in same 256-byte page as last check ($%04X)",
                            stuckCount, currentPc, lastCheckedPc));
                        if (stuckCount >= STUCK_THRESHOLD) {
                            System.out.println("HungCPU: CPU appears stuck. Register state:");
                            System.out.println("  " + machine.getCpu().getRegisterStatus());
                            int[] mem = machine.getMemory().getMemoryArray();
                            StringBuilder sb = new StringBuilder("  Memory near PC:");
                            for (int i = -4; i <= 8; i++) {
                                int addr = (currentPc + i) & 0xFFFF;
                                sb.append(String.format(" $%04X=%02X", addr, mem[addr]));
                            }
                            System.out.println(sb.toString());
                            // Dump KERNAL vectors so we can trace where IRQ/NMI/BRK are going.
                            System.out.println(String.format(
                                "  Vectors: NMI=$%02X%02X  RESET=$%02X%02X  IRQ=$%02X%02X",
                                mem[0xFFFB], mem[0xFFFA], mem[0xFFFD], mem[0xFFFC],
                                mem[0xFFFF], mem[0xFFFE]));
                            System.out.println(String.format(
                                "  RAM vectors: userIRQ=$%02X%02X  BRK=$%02X%02X  NMI=$%02X%02X",
                                mem[0x0315], mem[0x0314], mem[0x0317], mem[0x0316],
                                mem[0x0319], mem[0x0318]));
                            hungReported = true;
                        }
                    } else {
                        stuckCount = 0;
                    }
                    lastCheckedPc = currentPc;
                }
            }
            
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

    private boolean processPendingMachineActions() {
        boolean processedAction = false;
        Runnable pendingAction;
        while ((pendingAction = pendingMachineActions.poll()) != null) {
            processedAction = true;
            pendingAction.run();
        }
        return processedAction;
    }

    private void enqueueMachineAction(Runnable pendingAction) {
        pendingMachineActions.offer(pendingAction);
        synchronized (this) {
            this.notifyAll();
        }
    }

    private byte[] resolveMountedDiskImageData(AppConfigItem appConfigItem, Program program) {
        if ((program != null) && "DISK".equals(program.getProgramType())) {
            return program.getProgramData();
        }

        if (appConfigItem.getDiskWriteMode() != AppConfigItem.DiskWriteMode.DEFAULT) {
            return loadBlankDiskImage();
        }

        return null;
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
    public boolean canResetMountedDisk() {
        return (machine != null) && machine.canResetMountedDisk();
    }

    @Override
    public void requestMountedDiskReset(ResetDiskHandler resetDiskHandler) {
        if (!canResetMountedDisk()) {
            resetDiskHandler.onResetFailed();
            return;
        }

        enqueueMachineAction(new Runnable() {
            @Override
            public void run() {
                if (machine == null) {
                    resetDiskHandler.onResetFailed();
                    return;
                }

                machine.resetMountedDisk(new Machine.ResetDiskHandler() {
                    @Override
                    public void onResetComplete() {
                        resetDiskHandler.onResetComplete();
                    }

                    @Override
                    public void onResetFailed() {
                        resetDiskHandler.onResetFailed();
                    }
                });
            }
        });
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
