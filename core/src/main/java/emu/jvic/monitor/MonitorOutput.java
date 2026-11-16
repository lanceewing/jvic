package emu.jvic.monitor;

/**
 * Where the monitor engine writes its text output. Front ends (the remote text
 * monitor, the in-app console, tests) each provide one.
 */
public interface MonitorOutput {

    /**
     * Prints text. Lines end with '\n'; a call may contain several lines, or part
     * of one.
     *
     * @param text The text to print.
     */
    void print(String text);
}
