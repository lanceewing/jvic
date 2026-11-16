package emu.jvic.debug;

/**
 * The text connection between a monitor front end (the remote text monitor, and
 * later the in-app console) and the monitor engine, which runs on the emulation
 * thread. Commands go in as lines of text; output comes back as text through
 * {@link Listener}s.
 *
 * <p>Plain Java only, as this package is translated by GWT, where
 * {@link UnsupportedMonitorChannel} is used.
 */
public interface MonitorChannel {

    /**
     * Receives the monitor's output. Called on the emulation thread.
     */
    interface Listener {

        /**
         * @param text Output text: whole lines ending in '\n', or a prompt such as
         *             "(C:$e5d1) " with no newline.
         */
        void onOutput(String text);

        /**
         * Receives a prompt such as "(C:$e5d1) ", sent after each command that leaves
         * the machine stopped, and whenever it stops. By default it is output like
         * any other text; a front end that shows commands differently can override it.
         *
         * @param prompt The prompt, with no newline.
         */
        default void onPrompt(String prompt) {
            onOutput(prompt);
        }
    }

    void addListener(Listener listener);

    void removeListener(Listener listener);

    /**
     * Executes a command line. Can be called from any thread. If the machine is
     * running, it is first stopped at the next instruction boundary, as in VICE.
     *
     * @param line The command line.
     */
    void execute(String line);

    /**
     * Asks the machine to stop and enter the monitor (as Alt+H does). Can be called
     * from any thread.
     */
    void requestStop();
}
