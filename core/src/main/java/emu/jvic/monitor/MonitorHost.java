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

    /**
     * Whether warp mode (running as fast as possible) is on.
     */
    boolean isWarp();

    /**
     * Turns warp mode on or off.
     */
    void setWarp(boolean warp);

    /**
     * Prints output that isn't the direct result of a command: checkpoint hits,
     * tracepoint lines and the results of step, next, return and until, which arrive
     * while the machine runs, on the emulation thread.
     *
     * @param text The text, ending with a newline.
     */
    void print(String text);
}
