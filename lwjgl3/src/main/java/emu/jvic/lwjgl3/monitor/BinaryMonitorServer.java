package emu.jvic.lwjgl3.monitor;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

import emu.jvic.monitor.BinaryMonitor;
import emu.jvic.monitor.MonitorSession;

/**
 * VICE's binary monitor over TCP (port 6502 by default), for IDE debuggers such as
 * VS64. It only listens on 127.0.0.1, and only when enabled with the binarymonitor
 * option. One client is served at a time. Packets are handled on the emulation
 * thread by {@link BinaryMonitor}; responses and events are written by a separate
 * thread, so a slow client never holds up the emulation.
 */
public final class BinaryMonitorServer {

    /** VICE's default port for the binary monitor. */
    public static final int DEFAULT_PORT = 6502;

    /** The longest command body accepted (memory set of 64K, plus a little). */
    private static final int MAX_BODY = 0x10100;

    private final MonitorSession session;
    private final Consumer<Runnable> machineActions;
    private final ServerSocket serverSocket;
    private volatile boolean stopped;
    private volatile BlockingQueue<byte[]> clientOutput;
    private volatile OutputStream clientStream;

    /**
     * Opens the server socket and starts accepting connections.
     *
     * @param session        The runner's monitor session.
     * @param machineActions Runs an action on the emulation thread.
     * @param port           The port, or 0 for any free port (tests).
     */
    public BinaryMonitorServer(MonitorSession session, Consumer<Runnable> machineActions, int port)
            throws IOException {
        this.session = session;
        this.machineActions = machineActions;
        this.serverSocket = new ServerSocket(port, 1, InetAddress.getLoopbackAddress());
        Thread acceptThread = new Thread(this::acceptLoop, "jvic-binary-monitor");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public int getPort() {
        return serverSocket.getLocalPort();
    }

    /**
     * Closes the server. Responses still queued for the client (e.g. the reply to
     * quit) are sent first.
     */
    public void stop() {
        stopped = true;
        BlockingQueue<byte[]> output = clientOutput;
        OutputStream out = clientStream;
        if ((output != null) && (out != null)) {
            synchronized (out) {
                try {
                    byte[] packet;
                    while ((packet = output.poll()) != null) {
                        out.write(packet);
                    }
                    out.flush();
                } catch (IOException e) {
                    // The client has gone.
                }
            }
        }
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
        socket.setTcpNoDelay(true);
        BlockingQueue<byte[]> output = new LinkedBlockingQueue<>();
        OutputStream out = socket.getOutputStream();
        Thread writer = new Thread(() -> writeLoop(output, out), "jvic-binary-monitor-writer");
        writer.setDaemon(true);
        writer.start();
        BinaryMonitor monitor = new BinaryMonitor(session, output::offer);
        clientOutput = output;
        clientStream = out;
        try {
            DataInputStream in = new DataInputStream(socket.getInputStream());
            while (!stopped) {
                int first = in.read();
                if (first < 0) {
                    break;
                }
                if (first != 0x02) {
                    // Not the start of a command: skip until STX, as VICE resynchronises.
                    continue;
                }
                byte[] header = new byte[BinaryMonitor.HEADER_LENGTH];
                header[0] = 0x02;
                in.readFully(header, 1, header.length - 1);
                int length = BinaryMonitor.bodyLength(header);
                if ((length < 0) || (length > MAX_BODY)) {
                    continue;
                }
                byte[] packet = new byte[header.length + length];
                System.arraycopy(header, 0, packet, 0, header.length);
                in.readFully(packet, header.length, length);
                machineActions.accept(() -> monitor.handle(packet));
            }
        } catch (EOFException e) {
            // The client closed the connection.
        } finally {
            clientOutput = null;
            clientStream = null;
            monitor.close();
            writer.interrupt();
        }
    }

    private static void writeLoop(BlockingQueue<byte[]> output, OutputStream out) {
        try {
            while (true) {
                byte[] packet = output.take();
                synchronized (out) {
                    out.write(packet);
                    if (output.isEmpty()) {
                        out.flush();
                    }
                }
            }
        } catch (InterruptedException | IOException e) {
            // The client has gone.
        }
    }
}
