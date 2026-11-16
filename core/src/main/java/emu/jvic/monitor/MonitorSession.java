package emu.jvic.monitor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import emu.jvic.Machine;
import emu.jvic.debug.MonitorChannel;

/**
 * Connects the monitor engine to a runner and to its front ends, independently of
 * the platform. A runner owns one session for as long as the monitor is enabled, and
 * attaches each {@link Machine} it runs. Everything except adding and removing
 * listeners must be called on the emulation thread; a platform's
 * {@link MonitorChannel} gets commands onto that thread.
 *
 * <p>Behaviour, matching VICE's remote monitor:
 * <ul>
 * <li>A command that arrives while the machine is running stops it at the next
 * instruction boundary, and runs once it has stopped.</li>
 * <li>After each command that leaves the machine stopped, and whenever the machine
 * stops, the prompt (e.g. "(C:$e5d1) ") is sent.</li>
 * <li>Commands queued behind one that resumes the machine (x, g, z...) wait until it
 * next stops. So do the rest of a playback file's lines.</li>
 * <li>Lines given to {@link #execute} count as typed: they are logged after the
 * prompt and recorded, as in VICE.</li>
 * </ul>
 */
public final class MonitorSession implements MonitorHost {

    /**
     * The runner's side of the session.
     */
    public interface Platform {

        /** The engine has left the stopped state; wake the emulation loop. */
        void resumeEmulation();

        /** Quit the emulator. */
        void quit();

        boolean isWarp();

        void setWarp(boolean warp);

        /** See {@link MonitorHost#pickFile(String)}. */
        default boolean pickFile(String name) {
            return false;
        }

        /** See {@link MonitorHost#fetchUrl(String, String)}. */
        default boolean fetchUrl(String url, String name) {
            return false;
        }

        /** See {@link MonitorHost#downloadFile(String, byte[])}. */
        default boolean downloadFile(String name, byte[] data) {
            return false;
        }
    }

    private final Platform platform;
    private final List<MonitorChannel.Listener> listeners = new CopyOnWriteArrayList<>();
    private final Deque<String> pendingLines = new ArrayDeque<>();

    /** initbreak: no startup stop. */
    public static final int INITBREAK_NONE = -3;
    /** initbreak: stop at the first instruction (the reset routine). */
    public static final int INITBREAK_RESET = -1;
    /** initbreak: stop when BASIC is ready. */
    public static final int INITBREAK_READY = -2;

    private MonitorFileSystem fileSystem = new InMemoryMonitorFileSystem();

    // Startup options (moncommands, monlog, initbreak), applied to the first machine.
    private String startupCommands;
    private String startupLog;
    private int startupBreak = INITBREAK_NONE;
    private boolean startupPending;
    private boolean readyBreakPending;
    private Machine machine;
    private MonitorEngine engine;
    private boolean promptSent;

    public MonitorSession(Platform platform) {
        this.platform = platform;
    }

    /**
     * Sets the file system for the file commands, e.g. the real one on desktop. Set
     * it before attaching a machine.
     */
    public void setFileSystem(MonitorFileSystem fileSystem) {
        this.fileSystem = fileSystem;
        if (engine != null) {
            engine.setFileSystem(fileSystem);
        }
    }

    public MonitorFileSystem getFileSystem() {
        return fileSystem;
    }

    /**
     * Parses an initbreak value: reset, ready, or a hex address (with an optional $ or
     * 0x prefix).
     *
     * @return One of the INITBREAK_ constants, or the address.
     * @throws IllegalArgumentException if the value isn't valid.
     */
    public static int parseInitBreak(String value) {
        String text = value.trim().toLowerCase();
        if (text.equals("reset")) {
            return INITBREAK_RESET;
        }
        if (text.equals("ready")) {
            return INITBREAK_READY;
        }
        if (text.startsWith("$")) {
            text = text.substring(1);
        } else if (text.startsWith("0x")) {
            text = text.substring(2);
        }
        try {
            int address = Integer.parseInt(text, 16);
            if ((address >= 0) && (address <= 0xFFFF)) {
                return address;
            }
        } catch (NumberFormatException e) {
            // Reported below.
        }
        throw new IllegalArgumentException(
                "initbreak must be reset, ready, or a hex address such as e518, not '" + value + "'.");
    }

    /**
     * Sets the startup options, as VICE's -moncommands, -monlog/-monlogname and
     * -initbreak do. They apply to the next machine attached. The machine stops once
     * at startup: at the initbreak point, or, with only moncommands, at the first
     * instruction. The moncommands file runs there, and then the machine resumes (as
     * in VICE, the file needn't end with x). With initbreak and no moncommands, it
     * stays stopped in the monitor.
     *
     * @param monCommands A file of monitor commands, or null.
     * @param monLog      A file to log monitor output to, or null.
     * @param initBreak   INITBREAK_NONE, INITBREAK_RESET, INITBREAK_READY, or an
     *                    address.
     */
    public void setStartup(String monCommands, String monLog, int initBreak) {
        this.startupCommands = monCommands;
        this.startupLog = monLog;
        this.startupBreak = ((initBreak == INITBREAK_NONE) && (monCommands != null)) ? INITBREAK_RESET : initBreak;
        this.startupPending = (monCommands != null) || (monLog != null) || (initBreak != INITBREAK_NONE);
    }

    public void addListener(MonitorChannel.Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(MonitorChannel.Listener listener) {
        listeners.remove(listener);
    }

    /**
     * Attaches the machine that the runner has just initialised, creating its engine.
     */
    public void attach(Machine machine) {
        if (machine == this.machine) {
            return;
        }
        this.machine = machine;
        this.engine = new MonitorEngine(machine, this);
        this.engine.setFileSystem(fileSystem);
        this.promptSent = false;
        if (startupPending) {
            applyStartup();
        }
    }

    private void applyStartup() {
        if (startupLog != null) {
            engine.setLogName(startupLog);
            String error = engine.setLogEnabled(true);
            if (error != null) {
                print(error);
            }
            startupLog = null;
        }
        if (startupBreak == INITBREAK_RESET) {
            machine.getDebugger().requestStop();
        } else if (startupBreak == INITBREAK_READY) {
            readyBreakPending = true;
        } else if (startupBreak >= 0) {
            engine.addInitialBreak(startupBreak);
        } else {
            startupPending = false;
        }
    }

    /**
     * Detaches the machine when the runner has finished with it.
     */
    public void detach() {
        if (engine != null) {
            engine.flushLog();
        }
        machine = null;
        engine = null;
        pendingLines.clear();
    }

    public MonitorEngine getEngine() {
        return engine;
    }

    /**
     * Executes a command line, or, if the machine is running, stops it and queues the
     * line until it has stopped.
     */
    public void execute(String line) {
        if (engine == null) {
            print("No machine is running.\n");
            return;
        }
        if (machine.isDebuggerStopped() && pendingLines.isEmpty()) {
            run(line);
        } else {
            pendingLines.add(line);
            if (!machine.isDebuggerStopped()) {
                machine.getDebugger().requestStop();
            }
        }
    }

    /**
     * Called by the runner once per emulated frame while the machine runs, e.g. to
     * feed keybuf's characters into the keyboard buffer.
     */
    public void onFrame() {
        if (engine != null) {
            engine.onFrame();
            if (readyBreakPending && (machine.getMemory().getMemoryArray()[0xD1] == 110)) {
                // BASIC's cursor is at its start position: the same test as auto-load.
                readyBreakPending = false;
                machine.getDebugger().requestStop();
            }
        }
    }

    /**
     * Asks the machine to stop and enter the monitor.
     */
    public void requestStop() {
        if ((engine != null) && !machine.isDebuggerStopped()) {
            machine.getDebugger().requestStop();
        }
    }

    /**
     * Called by the runner's loop whenever it finds the machine stopped. Sends the
     * prompt once per stop, and runs the queued commands.
     */
    public void onMachineStopped() {
        if ((engine == null) || !machine.isDebuggerStopped()) {
            return;
        }
        if (startupPending) {
            // The startup stop: run moncommands, then resume, as VICE does.
            startupPending = false;
            if (startupCommands != null) {
                String commands = startupCommands;
                startupCommands = null;
                engine.playback(commands, this::print);
                if (machine.isDebuggerStopped() && !engine.hasPendingScript()) {
                    engine.execute("x", this::print);
                }
                if (!machine.isDebuggerStopped()) {
                    promptSent = false;
                    return;
                }
            }
        }
        if (engine.hasPendingScript()) {
            // The rest of a playback file that a command resumed the machine from.
            engine.continueScript(this::print);
            if (!machine.isDebuggerStopped()) {
                promptSent = false;
                return;
            }
        }
        if (!promptSent && pendingLines.isEmpty()) {
            promptSent = true;
            printPrompt();
        }
        while (!pendingLines.isEmpty() && machine.isDebuggerStopped()) {
            run(pendingLines.poll());
        }
    }

    /**
     * Runs a file of monitor commands, as playback does, e.g. a moncommands script.
     * As with playback, its lines are not logged as typed, and lines after one that
     * resumes the machine run when it next stops. Must be called while the machine
     * is stopped.
     *
     * @param path The file, in the session's file system.
     */
    public void playback(String path) {
        if (engine == null) {
            return;
        }
        engine.playback(path, this::print);
        promptSent = false;
    }

    private void run(String line) {
        engine.executeTyped(line, this::print);
        if (machine.isDebuggerStopped()) {
            promptSent = true;
            printPrompt();
        }
    }

    // ------------------------------------------------------------------------------
    // MonitorHost
    // ------------------------------------------------------------------------------

    @Override
    public void resume() {
        promptSent = false;
        platform.resumeEmulation();
    }

    @Override
    public void quit() {
        if (engine != null) {
            engine.flushLog();
        }
        platform.quit();
    }

    @Override
    public boolean pickFile(String name) {
        return platform.pickFile(name);
    }

    @Override
    public boolean fetchUrl(String url, String name) {
        return platform.fetchUrl(url, name);
    }

    @Override
    public boolean downloadFile(String name, byte[] data) {
        return platform.downloadFile(name, data);
    }

    @Override
    public boolean isWarp() {
        return platform.isWarp();
    }

    @Override
    public void setWarp(boolean warp) {
        platform.setWarp(warp);
    }

    private void printPrompt() {
        String prompt = engine.getPrompt();
        for (MonitorChannel.Listener listener : listeners) {
            listener.onPrompt(prompt);
        }
    }

    @Override
    public void print(String text) {
        for (MonitorChannel.Listener listener : listeners) {
            listener.onOutput(text);
        }
    }
}
