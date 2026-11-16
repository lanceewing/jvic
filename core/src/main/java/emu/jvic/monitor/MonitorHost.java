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

    /**
     * Web only: opens the browser's file picker; the chosen file is added to the
     * monitor's file store later (the upload command).
     *
     * @param name The name to store it under, or null for the file's own name.
     * @return false if the platform has no use for this (desktop).
     */
    default boolean pickFile(String name) {
        return false;
    }

    /**
     * Web only: fetches a URL into the monitor's file store in the background (the
     * fetch command).
     *
     * @param url  The URL.
     * @param name The name to store it under, or null for the URL's last part.
     * @return false if the platform doesn't support this.
     */
    default boolean fetchUrl(String url, String name) {
        return false;
    }

    /**
     * Web only: offers a file from the monitor's store as a browser download (the
     * download command).
     *
     * @param name The file's name, as shown to the user.
     * @param data The file's contents.
     * @return false if the platform doesn't support this.
     */
    default boolean downloadFile(String name, byte[] data) {
        return false;
    }
}
