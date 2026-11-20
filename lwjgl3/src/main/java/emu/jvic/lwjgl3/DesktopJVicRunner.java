package emu.jvic.lwjgl3;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.badlogic.gdx.Application.ApplicationType;
import com.badlogic.gdx.files.FileHandle;
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
import emu.jvic.TextTransfer;
import emu.jvic.config.AppConfigItem;
import emu.jvic.cpu.Cpu6502;
import emu.jvic.debug.MonitorChannel;
import emu.jvic.debug.StateReader;
import emu.jvic.debug.StateWriter;
import emu.jvic.io.disk.persistence.DiskImagePersistence;
import emu.jvic.io.disk.persistence.DiskImagePersistenceSession;
import emu.jvic.io.disk.persistence.NoOpDiskImagePersistence;
import emu.jvic.lwjgl3.disk.DesktopDiskImagePersistence;
import emu.jvic.lwjgl3.monitor.BinaryMonitorServer;
import emu.jvic.lwjgl3.monitor.DesktopMonitorChannel;
import emu.jvic.lwjgl3.monitor.RemoteTextMonitor;
import emu.jvic.memory.RamType;
import emu.jvic.monitor.JavaIoMonitorFileSystem;
import emu.jvic.monitor.MonitorSession;
import emu.jvic.sound.SoundGenerator;
import emu.jvic.ui.MachineInputProcessor.ScreenSize;

public class DesktopJVicRunner extends JVicRunner {

    private Thread machineThread;
    private final Queue<Runnable> pendingMachineActions = new ConcurrentLinkedQueue<Runnable>();
    
    private Machine machine;

    /** Text pasted with Alt+Insert, typed in as the keyboard buffer empties. Machine thread only. */
    private final TextTransfer textTransfer = new TextTransfer();

    /**
     * Set by a reset or quick load (on the machine thread) so that the loop drops any
     * auto-load still pending, which would otherwise type into the new state.
     */
    private boolean cancelAutoLoad;

    /** Set when a frame advance has emulated a frame the screen hasn't shown yet. */
    private volatile boolean pausedFrameReady;

    /** The monitor, created when first needed (null until then, so no debugger runs). */
    private volatile MonitorSession monitorSession;
    private DesktopMonitorChannel monitorChannel;
    private RemoteTextMonitor remoteMonitor;
    private BinaryMonitorServer binaryMonitor;

    public DesktopJVicRunner(KeyboardMatrix keyboardMatrix, PixelData pixelData, SoundGenerator soundGenerator) {
        super(keyboardMatrix, pixelData, soundGenerator);
    }

    @Override
    public boolean isMonitorSupported() {
        return true;
    }

    @Override
    public boolean hasMonitorChannel() {
        return monitorSession != null;
    }

    @Override
    public synchronized MonitorChannel getMonitorChannel() {
        if (monitorSession == null) {
            monitorSession = new MonitorSession(new MonitorSession.Platform() {
                @Override
                public void resumeEmulation() {
                    synchronized (DesktopJVicRunner.this) {
                        DesktopJVicRunner.this.notifyAll();
                    }
                }

                @Override
                public void quit() {
                    if (binaryMonitor != null) {
                        // Sends the reply to the binary monitor's quit before exiting.
                        binaryMonitor.stop();
                    }
                    Gdx.app.postRunnable(() -> Gdx.app.exit());
                }

                @Override
                public boolean isWarp() {
                    return warpSpeed;
                }

                @Override
                public void setWarp(boolean warp) {
                    setWarpSpeed(warp);
                }
            });
            monitorSession.setFileSystem(new JavaIoMonitorFileSystem());
            monitorChannel = new DesktopMonitorChannel(monitorSession, this::enqueueMachineAction);
            // If a machine is already running, attach it on its own thread.
            enqueueMachineAction(() -> {
                if (machine != null) {
                    monitorSession.attach(machine);
                }
            });
        }
        return monitorChannel;
    }

    /**
     * Starts VICE's remote text monitor on 127.0.0.1 at the given port. It is off
     * unless the remotemonitor command-line option is given.
     *
     * @param port The port, e.g. 6510.
     *
     * @throws java.io.IOException if the port can't be opened.
     */
    /**
     * Starts VICE's binary monitor on 127.0.0.1 at the given port (for IDE
     * debuggers). It is off unless the binarymonitor command-line option is given.
     *
     * @param port The port, e.g. 6502.
     *
     * @throws java.io.IOException if the port can't be opened.
     */
    public void startBinaryMonitor(int port) throws IOException {
        if (binaryMonitor == null) {
            getMonitorChannel();
            binaryMonitor = new BinaryMonitorServer(monitorSession, this::enqueueMachineAction, port);
        }
    }

    /**
     * Starts VICE's remote text monitor on 127.0.0.1 at the given port. It is off
     * unless the remotemonitor command-line option is given.
     *
     * @param port The port, e.g. 6510.
     *
     * @throws java.io.IOException if the port can't be opened.
     */
    public void startRemoteMonitor(int port) throws IOException {
        if (remoteMonitor == null) {
            remoteMonitor = new RemoteTextMonitor(getMonitorChannel(), port);
        }
    }

    /**
     * Sets the monitor's startup options (moncommands, monlog, initbreak), which apply
     * to the next program started. Creates the monitor if needed.
     */
    public void setMonitorStartup(String monCommands, String monLog, int initBreak) {
        getMonitorChannel();
        monitorSession.setStartup(monCommands, monLog, initBreak);
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

    private void runProgram(AppConfigItem appConfigItem, Program program,
            DiskImagePersistenceSession persistenceSession, byte[] mountedDiskImageData) {
        MachineType machineType = MachineType.valueOf(appConfigItem.getMachineType());
        RamType ramType = RamType.valueOf(appConfigItem.getRam());
        
        // Create the Machine instance that will run the VIC 20 program.
        machine = new Machine(soundGenerator, keyboardMatrix, pixelData);
        
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

        MonitorSession session;
        synchronized (this) {
            session = monitorSession;
        }
        if (session != null) {
            session.attach(machine);
        }
        
        final int NANOS_PER_FRAME = (1000000000 / machineType.getFramesPerSecond());
        
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
            if (monitorSession != null) {
                monitorSession.onFrame();
            }

            if ((monitorSession != null) && machine.isDebuggerStopped()) {
                // Stopped in the monitor. Wait for monitor commands (which arrive as
                // machine actions) until one resumes the machine.
                monitorSession.onMachineStopped();
                synchronized (this) {
                    try {
                        while (machine.isDebuggerStopped() && !exit) {
                            if (processPendingMachineActions()) {
                                monitorSession.onMachineStopped();
                                continue;
                            }
                            wait();
                        }
                    } catch (InterruptedException e) {
                        // Stopping; the loop checks exit.
                    }
                }
                lastTime = TimeUtils.nanoTime();
                continue;
            }
            
            if (cancelAutoLoad) {
                autoLoadProgram = null;
                autoRunCmdQueue = null;
                cancelAutoLoad = false;
            }

            // Type any pasted text (Alt+Insert) as the keyboard buffer empties.
            textTransfer.feed(machine.getMemory().getMemoryArray());

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
        
        if (monitorSession != null) {
            monitorSession.detach();
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
    public boolean isMachineSettingSupported(String name) {
        return "palette".equals(name);
    }

    @Override
    public void applyMachineSetting(final String name, final String value) {
        enqueueMachineAction(new Runnable() {
            @Override
            public void run() {
                Machine current = machine;
                if (current != null) {
                    current.applySetting(name, value);
                }
            }
        });
    }

    @Override
    public boolean isRestartSupported() {
        return true;
    }

    @Override
    public void prepareRestart() {
        Thread oldThread = machineThread;
        exit = true;
        resume();
        if (oldThread != null) {
            oldThread.interrupt();
            try {
                // The loop ends at its next check of exit, which is at most a frame away.
                oldThread.join(5000);
            } catch (InterruptedException e) {
                // Carry on.
            }
        }
        pendingMachineActions.clear();
        exit = false;
        paused = false;
        machineThread = null;
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
    public boolean isMachineControlSupported() {
        return true;
    }

    @Override
    public void requestReset(boolean powerCycle) {
        enqueueMachineAction(() -> {
            if (machine == null) {
                return;
            }
            if (powerCycle) {
                machine.powerCycle();
            } else {
                machine.reset();
            }
            onMachineStateReplaced();
        });
    }

    @Override
    public void requestQuickSave(String slot) {
        enqueueMachineAction(() -> {
            if (machine == null) {
                return;
            }
            try {
                StateWriter out = new StateWriter();
                machine.saveState(out);
                quickSaveFile(slot).writeBytes(out.toByteArray(), false);
                showMessage("Quick save done");
            } catch (RuntimeException e) {
                Gdx.app.error("JVic", "Quick save failed", e);
                showMessage("Quick save failed");
            }
        });
    }

    @Override
    public void requestQuickLoad(String slot) {
        enqueueMachineAction(() -> {
            if (machine == null) {
                return;
            }
            FileHandle file = quickSaveFile(slot);
            if (!file.exists()) {
                showMessage("No quick save for this program");
                return;
            }
            try {
                machine.loadState(new StateReader(file.readBytes()));
                onMachineStateReplaced();
                showMessage("Quick load done");
            } catch (RuntimeException e) {
                // e.g. a StateReader.StateException: the machine is set up differently.
                Gdx.app.error("JVic", "Quick load failed: " + e.getMessage());
                showMessage("Quick load failed");
            }
        });
    }

    /**
     * The quick save file for a slot: jvic_saves/&lt;slot&gt;.jvs in the user's home
     * directory, beside jvic_screens.
     */
    private FileHandle quickSaveFile(String slot) {
        return Gdx.files.external("jvic_saves/" + slot + ".jvs");
    }

    /**
     * After a reset or quick load: drops the auto-load and pasted text still to be
     * typed, and tells the monitor, if it's in use, that the state has changed.
     */
    private void onMachineStateReplaced() {
        cancelAutoLoad = true;
        textTransfer.clear();
        if (monitorSession != null) {
            monitorSession.onMachineStateChanged();
        }
    }

    @Override
    public void requestFrameAdvance() {
        enqueueMachineAction(() -> {
            if ((machine == null) || !paused || machine.isDebuggerStopped()) {
                return;
            }
            machine.update();
            if (monitorSession != null) {
                monitorSession.onFrame();
                if (machine.isDebuggerStopped()) {
                    // A breakpoint was hit during the frame.
                    monitorSession.onMachineStopped();
                }
            }
            textTransfer.feed(machine.getMemory().getMemoryArray());
            pausedFrameReady = true;
        });
    }

    @Override
    public boolean consumePausedFrame() {
        if (pausedFrameReady) {
            pausedFrameReady = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean isClipboardSupported() {
        return true;
    }

    @Override
    public void requestCopyScreenText() {
        enqueueMachineAction(() -> {
            if (machine == null) {
                return;
            }
            String text = TextTransfer.readScreen(machine);
            // The clipboard belongs to the UI thread.
            Gdx.app.postRunnable(() -> {
                Gdx.app.getClipboard().setContents(text);
                showMessage("Screen text copied");
            });
        });
    }

    @Override
    public void requestPaste() {
        String text = Gdx.app.getClipboard().getContents();
        if ((text == null) || text.isEmpty()) {
            return;
        }
        enqueueMachineAction(() -> {
            if (machine != null) {
                textTransfer.queue(TextTransfer.toPetscii(text, TextTransfer.isLowerCaseMode(machine)));
            }
        });
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
