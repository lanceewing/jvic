package emu.jvic.monitor;

/**
 * Platform services that the monitor engine can't provide itself. Each runner
 * (desktop, TeaVM, headless) implements this. Every method is a request that the
 * host may carry out after the current command finishes.
 */
public interface MonitorHost {

    /**
     * Resumes emulation after the monitor has finished with a stopped machine
     * (exit, goto, step, and so on). The engine has already taken the Debugger out
     * of its stopped state when this is called; the host un-pauses its runner.
     */
    void resume();

    /**
     * Quits the emulator (the {@code quit} command).
     */
    void quit();
}
