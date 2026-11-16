package emu.jvic.lwjgl3.monitor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import emu.jvic.debug.MonitorChannel;

/**
 * VICE's remote text monitor: a TCP server on 127.0.0.1 (port 6510 by default) that
 * reads command lines from a client, such as {@code telnet localhost 6510} or a
 * script, and sends back the monitor's output and prompts. It only ever listens on
 * the loopback address, and only when enabled with the {@code remotemonitor}
 * command-line option.
 *
 * <p>As in VICE, one client is served at a time; another connection waits until the
 * first disconnects. Output is written by a separate thread, so a slow client never
 * holds up the emulation thread. If a client stops reading, output beyond
 * {@link #MAX_QUEUED_OUTPUT} pieces is dropped and replaced with a marker.
 */
public final class RemoteTextMonitor implements MonitorChannel.Listener {

    /** VICE's default port for the remote text monitor. */
    public static final int DEFAULT_PORT = 6510;

    static final int MAX_QUEUED_OUTPUT = 10000;

    private static final String DROPPED = "\n[jvic: monitor output dropped; the client is not reading]\n";

    private final MonitorChannel channel;
    private final ServerSocket serverSocket;
    private final Thread acceptThread;

    private volatile BlockingQueue<String> clientOutput;
    private volatile boolean stopped;

    /**
     * Opens the server socket and starts accepting connections.
     *
     * @param channel The monitor channel to send commands to.
     * @param port    The port, or 0 for any free port (for tests).
     *
     * @throws IOException if the port can't be opened.
     */
    public RemoteTextMonitor(MonitorChannel channel, int port) throws IOException {
        this.channel = channel;
        this.serverSocket = new ServerSocket(port, 1, InetAddress.getLoopbackAddress());
        this.acceptThread = new Thread(this::acceptLoop, "jvic-remote-monitor");
        this.acceptThread.setDaemon(true);
        channel.addListener(this);
        this.acceptThread.start();
    }

    /** The port the server is listening on. */
    public int getPort() {
        return serverSocket.getLocalPort();
    }

    /** Closes the server and any connected client. */
    public void stop() {
        stopped = true;
        channel.removeListener(this);
        try {
            serverSocket.close();
        } catch (IOException e) {
            // Nothing more to do.
        }
    }

    private void acceptLoop() {
        while (!stopped) {
            try (Socket socket = serverSocket.accept()) {
                serve(socket);
            } catch (IOException e) {
                // The client went away, or the server was closed.
            }
        }
    }

    private void serve(Socket socket) throws IOException {
        BlockingQueue<String> output = new LinkedBlockingQueue<>();
        OutputStream out = socket.getOutputStream();
        Thread writer = new Thread(() -> writeLoop(output, out), "jvic-remote-monitor-writer");
        writer.setDaemon(true);
        writer.start();
        clientOutput = output;
        try {
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
            String line;
            while (!stopped && ((line = in.readLine()) != null)) {
                channel.execute(line);
            }
        } finally {
            clientOutput = null;
            writer.interrupt();
        }
    }

    private static void writeLoop(BlockingQueue<String> output, OutputStream out) {
        try {
            while (true) {
                String text = output.take();
                out.write(text.getBytes(StandardCharsets.ISO_8859_1));
                if (output.isEmpty()) {
                    out.flush();
                }
            }
        } catch (InterruptedException | IOException e) {
            // The client has gone.
        }
    }

    @Override
    public void onOutput(String text) {
        BlockingQueue<String> output = clientOutput;
        if (output == null) {
            return;
        }
        if (output.size() < MAX_QUEUED_OUTPUT) {
            output.offer(text);
        } else if (output.size() == MAX_QUEUED_OUTPUT) {
            output.offer(DROPPED);
        }
    }
}
