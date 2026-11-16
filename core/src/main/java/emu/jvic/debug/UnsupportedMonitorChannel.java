package emu.jvic.debug;

import java.util.ArrayList;
import java.util.List;

/**
 * The monitor channel on platforms without a monitor (GWT and Android): every
 * command is answered with a "not implemented" message.
 */
public final class UnsupportedMonitorChannel implements MonitorChannel {

    public static final UnsupportedMonitorChannel INSTANCE = new UnsupportedMonitorChannel();

    private final List<Listener> listeners = new ArrayList<Listener>();

    private UnsupportedMonitorChannel() {
    }

    @Override
    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    @Override
    public void execute(String line) {
        for (Listener listener : listeners) {
            listener.onOutput("Monitor is not implemented on this platform.\n");
        }
    }

    @Override
    public void requestStop() {
        // Nothing to stop.
    }
}
