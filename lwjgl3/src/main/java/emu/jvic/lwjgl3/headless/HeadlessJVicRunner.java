package emu.jvic.lwjgl3.headless;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import emu.jvic.KeyboardMatrix;
import emu.jvic.Machine;
import emu.jvic.MachineType;
import emu.jvic.Program;
import emu.jvic.config.AppConfigItem;
import emu.jvic.debug.Debugger;
import emu.jvic.debug.MonitorChannel;
import emu.jvic.lwjgl3.DesktopProgramLoader;
import emu.jvic.lwjgl3.monitor.BinaryMonitorServer;
import emu.jvic.lwjgl3.monitor.DesktopMonitorChannel;
import emu.jvic.lwjgl3.monitor.RemoteTextMonitor;
import emu.jvic.memory.RamType;
import emu.jvic.monitor.JavaIoMonitorFileSystem;
import emu.jvic.monitor.MonitorEngine;
import emu.jvic.monitor.MonitorFileSystem;
import emu.jvic.monitor.MonitorSession;
import emu.jvic.sound.SoundGenerator;

/**
 * Runs jvic without a window, audio or input, driven by the monitor: the desktop
 * equivalent of running xvic with -moncommands, -limitcycles and so on. Started by
 * {@code headless=true} on the command line (see {@link HeadlessOptions} for the
 * other options). libGDX is never started.
 *
 * <p>How a run goes:
 * <ol>
 * <li>The machine stops once at startup: at the first instruction, or at the initbreak
 * address, or when BASIC is ready. The moncommands script runs there.</li>
 * <li>Then lines from standard input run, while the machine is stopped. When standard
 * input is a pipe or file, each line waits for the machine to stop, so a script such as
 * "break exec 1234 / x / r" is deterministic. When it is a console, a line typed while
 * the machine runs stops it, as with the remote monitor.</li>
 * <li>At the end of standard input while stopped: at the startup stop after a
 * moncommands script, the machine resumes (as VICE does after -moncommands); otherwise
 * jvic exits. With remotemonitor, it waits for the remote client instead.</li>
 * </ol>
 *
 * <p>Monitor output goes to standard output. When standard input isn't a console,
 * each line read from it is echoed after the prompt, as the monitor log does, and
 * prompts aren't printed otherwise.
 *
 * <p>Exit codes: 0 for quit or end of input, 1 when limitcycles is reached, 2 for bad
 * options or missing files, 3 if the CPU jammed and there was no input to handle it.
 */
public final class HeadlessJVicRunner {

    public static final int EXIT_OK = 0;
    public static final int EXIT_LIMIT = 1;
    public static final int EXIT_ERROR = 2;
    public static final int EXIT_JAM = 3;

    private static final String END_OF_INPUT = new String("<end of input>");

    private static final class SilentSoundGenerator extends SoundGenerator {
        @Override
        public void initSound(MachineType machineType) {
        }

        @Override
        public void emulateCycle() {
        }

        @Override
        public void pauseSound() {
        }

        @Override
        public void resumeSound() {
        }

        @Override
        public boolean isSoundOn() {
            return false;
        }

        @Override
        public void dispose() {
        }
    }

    private static final class IdleKeyboardMatrix extends KeyboardMatrix {
        private final int[] keyMatrix = new int[513];

        @Override
        public int getKeyMatrixRow(int row) {
            return keyMatrix[row];
        }

        @Override
        public void setKeyMatrixRow(int row, int value) {
            keyMatrix[row] = value;
        }
    }

    private final HeadlessOptions options;
    private final InputStream stdin;
    private final PrintStream stdout;
    private final PrintStream stderr;
    private final boolean interactive;
    private final MonitorFileSystem fileSystem;

    private final Queue<Runnable> actions = new ConcurrentLinkedQueue<>();
    private final BlockingQueue<String> inputLines = new LinkedBlockingQueue<>();

    private Machine machine;
    private MonitorSession session;
    private RemoteTextMonitor remoteMonitor;
    private BinaryMonitorServer binaryMonitor;
    private volatile boolean quitRequested;
    private boolean warp;
    /** initbreak=ready: stop once BASIC is ready. */
    private boolean readyBreakPending;

    // Auto-loading the program once BASIC is ready.
    private Callable<Queue<char[]>> autoLoadProgram;
    private Queue<char[]> autoRunCommands;

    /**
     * Runs jvic headless with the given command-line options, using the process's
     * standard streams, and returns the exit code.
     */
    public static int run(Map<String, String> args) {
        HeadlessOptions options;
        try {
            options = HeadlessOptions.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("jvic: " + e.getMessage());
            return EXIT_ERROR;
        }
        return new HeadlessJVicRunner(options, System.in, System.out, System.err, System.console() != null,
                new JavaIoMonitorFileSystem()).run();
    }

    /**
     * @param options     The options.
     * @param stdin       Monitor commands, one per line.
     * @param stdout      Where monitor output goes.
     * @param stderr      Where errors in starting up go.
     * @param interactive Whether stdin is a console (see the class description).
     * @param fileSystem  The file system for the monitor's file commands and for the
     *                    program, moncommands and monlog files.
     */
    HeadlessJVicRunner(HeadlessOptions options, InputStream stdin, PrintStream stdout, PrintStream stderr,
            boolean interactive, MonitorFileSystem fileSystem) {
        this.options = options;
        this.stdin = stdin;
        this.stdout = stdout;
        this.stderr = stderr;
        this.interactive = interactive;
        this.fileSystem = fileSystem;
    }

    /**
     * Runs until quit, the end of input, or the cycle limit.
     *
     * @return The exit code.
     */
    int run() {
        try {
            if (!start()) {
                return EXIT_ERROR;
            }
            return loop();
        } finally {
            if (session != null) {
                session.detach();
            }
            if (remoteMonitor != null) {
                remoteMonitor.stop();
            }
            if (binaryMonitor != null) {
                binaryMonitor.stop();
            }
            stdout.flush();
        }
    }

    // ------------------------------------------------------------------------------
    // Starting up.
    // ------------------------------------------------------------------------------

    private boolean start() {
        byte[] basicRom = loadRom("basic.rom");
        byte[] kernalRom = loadRom(options.machineType.isNTSC() ? "kernal_ntsc.rom" : "kernal_pal.rom");
        byte[] charRom = loadRom("char.rom");
        byte[] dosRom = loadRom("dos1541.rom");
        if ((basicRom == null) || (kernalRom == null) || (charRom == null) || (dosRom == null)) {
            return false;
        }
        if ((options.monCommands != null) && !fileSystem.exists(options.monCommands)) {
            stderr.println("jvic: moncommands file not found: " + options.monCommands);
            return false;
        }

        AppConfigItem appConfigItem = new AppConfigItem();
        appConfigItem.setMachineType(options.machineType.name());
        Program program = null;
        if (options.program != null) {
            program = loadProgram(appConfigItem);
            if (program == null) {
                return false;
            }
        }
        RamType ramType = options.ramType;
        if (ramType == null) {
            ramType = (program != null) ? RamType.RAM_AUTO : RamType.RAM_UNEXPANDED;
        }
        byte[] mountedDisk = ((program != null) && "DISK".equals(program.getProgramType()))
                ? program.getProgramData() : null;

        HeadlessPixelData pixelData = new HeadlessPixelData();
        pixelData.init(options.machineType.getTotalScreenWidth() * 2, options.machineType.getTotalScreenHeight() * 2);
        machine = new Machine(new SilentSoundGenerator(), new IdleKeyboardMatrix(), pixelData);
        autoLoadProgram = machine.init(basicRom, kernalRom, charRom, dosRom, program, appConfigItem, mountedDisk,
                options.machineType, ramType, null, null);

        session = new MonitorSession(new MonitorSession.Platform() {
            @Override
            public void resumeEmulation() {
                // The loop sees that the machine is no longer stopped.
            }

            @Override
            public void quit() {
                quitRequested = true;
            }

            @Override
            public boolean isWarp() {
                return warp;
            }

            @Override
            public void setWarp(boolean warp) {
                // Headless mode always runs as fast as it can.
                HeadlessJVicRunner.this.warp = warp;
            }
        });
        session.setFileSystem(fileSystem);
        session.addListener(new MonitorChannel.Listener() {
            @Override
            public void onOutput(String text) {
                stdout.print(text);
                stdout.flush();
            }

            @Override
            public void onPrompt(String prompt) {
                if (interactive) {
                    onOutput(prompt);
                }
            }
        });
        session.attach(machine);
        // As in xvic, chis always has the history. It costs some speed, which matters
        // less without a display to keep up with.
        session.getEngine().setHistoryEnabled(true);

        if (options.monLog != null) {
            MonitorEngine engine = session.getEngine();
            engine.setLogName(options.monLog);
            String error = engine.setLogEnabled(true);
            if (error != null) {
                stderr.print("jvic: " + error);
                return false;
            }
        }

        if (options.remoteMonitorPort != 0) {
            MonitorChannel channel = new DesktopMonitorChannel(session, actions::offer);
            try {
                remoteMonitor = new RemoteTextMonitor(channel, options.remoteMonitorPort);
                stderr.println("Remote monitor listening on 127.0.0.1:" + remoteMonitor.getPort());
            } catch (IOException e) {
                stderr.println("jvic: could not start the remote monitor on port " + options.remoteMonitorPort
                        + ": " + e.getMessage());
                return false;
            }
        }

        if (options.binaryMonitorPort != 0) {
            try {
                binaryMonitor = new BinaryMonitorServer(session, actions::offer, options.binaryMonitorPort);
                stderr.println("Binary monitor listening on 127.0.0.1:" + binaryMonitor.getPort());
            } catch (IOException e) {
                stderr.println("jvic: could not start the binary monitor on port " + options.binaryMonitorPort
                        + ": " + e.getMessage());
                return false;
            }
        }

        if (options.initBreak == HeadlessOptions.INITBREAK_RESET) {
            session.requestStop();
        } else if (options.initBreak == HeadlessOptions.INITBREAK_READY) {
            readyBreakPending = true;
        } else {
            session.getEngine().addInitialBreak(options.initBreak);
        }

        startInputThread();
        return true;
    }

    private byte[] loadRom(String name) {
        try (InputStream in = HeadlessJVicRunner.class.getResourceAsStream("/roms/" + name)) {
            if (in != null) {
                return in.readAllBytes();
            }
        } catch (IOException e) {
            // Try the assets folder.
        }
        for (File dir = new File(System.getProperty("user.dir")).getAbsoluteFile(); dir != null;
                dir = dir.getParentFile()) {
            File rom = new File(dir, "assets/roms/" + name);
            if (rom.isFile()) {
                try {
                    return Files.readAllBytes(rom.toPath());
                } catch (IOException e) {
                    break;
                }
            }
        }
        stderr.println("jvic: ROM not found: roms/" + name);
        return null;
    }

    private Program loadProgram(AppConfigItem appConfigItem) {
        String path = fileSystem.absolute(options.program);
        if (!new File(path).isFile()) {
            stderr.println("jvic: file not found: " + options.program);
            return null;
        }
        appConfigItem.setFilePath(path);
        appConfigItem.setFileLocation(AppConfigItem.FileLocation.ABSOLUTE);
        appConfigItem.setName(new File(path).getName());
        Program[] result = new Program[1];
        new DesktopProgramLoader(null).fetchProgram(appConfigItem, p -> result[0] = p);
        if (result[0] == null) {
            stderr.println("jvic: could not load " + options.program);
        }
        return result[0];
    }

    private void startInputThread() {
        Thread reader = new Thread(() -> {
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(stdin, StandardCharsets.ISO_8859_1));
                String line;
                while ((line = in.readLine()) != null) {
                    inputLines.add(line.endsWith("\r") ? line.substring(0, line.length() - 1) : line);
                }
            } catch (IOException e) {
                // Treat as the end of input.
            }
            inputLines.add(END_OF_INPUT);
        }, "jvic-headless-stdin");
        reader.setDaemon(true);
        reader.start();
    }

    // ------------------------------------------------------------------------------
    // Running.
    // ------------------------------------------------------------------------------

    private int loop() {
        boolean startupHandled = false;
        boolean atStartupStop = false;
        boolean endOfInput = false;

        while (true) {
            runActions();
            if (quitRequested) {
                return EXIT_OK;
            }
            if (startupHandled && !machine.isDebuggerStopped()) {
                atStartupStop = false;
            }

            if (machine.isDebuggerStopped()) {
                if (!startupHandled) {
                    startupHandled = true;
                    atStartupStop = true;
                    if (options.monCommands != null) {
                        session.playback(options.monCommands);
                        continue;
                    }
                }
                session.onMachineStopped();
                if (quitRequested) {
                    return EXIT_OK;
                }
                if (!machine.isDebuggerStopped()) {
                    atStartupStop = false;
                    continue;
                }

                String line = endOfInput ? null : nextInputLine();
                if (line == END_OF_INPUT) {
                    endOfInput = true;
                    continue;
                }
                if (line != null) {
                    if (!interactive) {
                        stdout.print(session.getEngine().getPrompt() + line + "\n");
                        stdout.flush();
                    }
                    session.execute(line);
                    if (!machine.isDebuggerStopped()) {
                        atStartupStop = false;
                    }
                    continue;
                }
                if (!endOfInput) {
                    continue;
                }
                // Stopped, with no more input.
                if ((remoteMonitor != null) || (binaryMonitor != null)) {
                    waitForActions();
                } else if (atStartupStop && (options.monCommands != null)) {
                    // As VICE does after a -moncommands script that doesn't resume.
                    atStartupStop = false;
                    session.getEngine().execute("x", text -> stdout.print(text));
                } else {
                    return (machine.getDebugger().getStopReason() == Debugger.STOP_JAM) ? EXIT_JAM : EXIT_OK;
                }
                continue;
            }

            if (interactive) {
                String line = inputLines.poll();
                if ((line != null) && (line != END_OF_INPUT)) {
                    session.execute(line);
                } else if (line == END_OF_INPUT) {
                    endOfInput = true;
                }
            }
            if (runFrame()) {
                return EXIT_LIMIT;
            }
        }
    }

    private String nextInputLine() {
        try {
            return inputLines.poll(20, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return END_OF_INPUT;
        }
    }

    private void runActions() {
        Runnable action;
        while ((action = actions.poll()) != null) {
            action.run();
        }
    }

    private void waitForActions() {
        for (int i = 0; (i < 20) && actions.isEmpty() && !quitRequested; i++) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                quitRequested = true;
            }
        }
    }

    /**
     * Runs the machine until the end of a frame, a monitor stop, or the cycle limit.
     *
     * @return true if the cycle limit was reached.
     */
    private boolean runFrame() {
        long limit = options.limitCycles;
        boolean frameComplete = false;
        do {
            frameComplete = machine.emulateCycle();
            if (machine.checkDebuggerStop()) {
                return false;
            }
            if ((limit > 0) && (machine.getCpu().getTotalCycles() >= limit)) {
                return true;
            }
        } while (!frameComplete);
        onFrame();
        return false;
    }

    /**
     * Once per frame: stops for initbreak=ready, and auto-loads the program once
     * BASIC is ready, as the windowed desktop runner does.
     */
    private void onFrame() {
        session.onFrame();
        int[] mem = machine.getMemory().getMemoryArray();
        if (readyBreakPending && (mem[0xD1] == 110)) {
            // Stop before the program is loaded; it loads after the monitor resumes.
            readyBreakPending = false;
            session.requestStop();
            return;
        }
        if (autoLoadProgram == null) {
            return;
        }
        // Wait for BASIC's cursor to be at its start position before loading.
        if (mem[0xD1] == 110) {
            try {
                autoRunCommands = autoLoadProgram.call();
            } catch (Exception e) {
                autoRunCommands = null;
            }
            if (!options.run && (autoRunCommands != null)) {
                for (Iterator<char[]> it = autoRunCommands.iterator(); it.hasNext();) {
                    if (new String(it.next()).equals("RUN")) {
                        it.remove();
                    }
                }
            }
            typeNextCommand(mem);
            if ((autoRunCommands == null) || autoRunCommands.isEmpty()) {
                autoLoadProgram = null;
            }
        }
        // A disk has two commands: LOAD, then RUN once the load has finished.
        if (mem[0xD1] == 220) {
            typeNextCommand(mem);
            autoLoadProgram = null;
        }
    }

    /** Puts the next auto-run command in the keyboard buffer, followed by RETURN. */
    private void typeNextCommand(int[] mem) {
        if ((autoRunCommands != null) && !autoRunCommands.isEmpty()) {
            char[] command = autoRunCommands.remove();
            int i = 0;
            for (; i < command.length; i++) {
                mem[631 + i] = command[i];
            }
            mem[631 + i] = 0x0D;
            mem[198] = i + 1;
        }
    }
}
