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
    }

    private final Platform platform;
    private final List<MonitorChannel.Listener> listeners = new CopyOnWriteArrayList<>();
    private final Deque<String> pendingLines = new ArrayDeque<>();

    private MonitorFileSystem fileSystem = new InMemoryMonitorFileSystem();
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
