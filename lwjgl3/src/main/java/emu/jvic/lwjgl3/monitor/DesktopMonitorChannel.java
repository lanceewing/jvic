package emu.jvic.lwjgl3.monitor;

import java.util.function.Consumer;

import emu.jvic.debug.MonitorChannel;
import emu.jvic.monitor.MonitorSession;

/**
 * The desktop's monitor channel: commands from any thread are run on the emulation
 * thread through the runner's queue of machine actions, by the runner's
 * {@link MonitorSession}.
 */
public final class DesktopMonitorChannel implements MonitorChannel {

    private final MonitorSession session;
    private final Consumer<Runnable> machineActions;

    /**
     * @param session        The runner's monitor session.
     * @param machineActions Queues an action to run on the emulation thread, and wakes
     *                       it if it is waiting.
     */
    public DesktopMonitorChannel(MonitorSession session, Consumer<Runnable> machineActions) {
        this.session = session;
        this.machineActions = machineActions;
    }

    @Override
    public void addListener(Listener listener) {
        session.addListener(listener);
    }

    @Override
    public void removeListener(Listener listener) {
        session.removeListener(listener);
    }

    @Override
    public void execute(String line) {
        // Telnet clients end lines with CR LF.
        String command = line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
        machineActions.accept(() -> session.execute(command));
    }

    @Override
    public void requestStop() {
        machineActions.accept(session::requestStop);
    }
}
