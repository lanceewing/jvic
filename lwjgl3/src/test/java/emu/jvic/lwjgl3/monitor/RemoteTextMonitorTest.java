package emu.jvic.lwjgl3.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import emu.jvic.debug.MonitorChannel;

/**
 * Socket tests for the remote text monitor, with a fake channel that answers each
 * command line with "got: line" and a prompt.
 */
public class RemoteTextMonitorTest {

    private static final class FakeChannel implements MonitorChannel {
        final List<Listener> listeners = new CopyOnWriteArrayList<>();
        final LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();

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
            output("got: " + line + "\n(C:$e5d1) ");
            received.add(line);
        }

        @Override
        public void requestStop() {
        }

        void output(String text) {
            for (Listener listener : listeners) {
                listener.onOutput(text);
            }
        }
    }

    private RemoteTextMonitor monitor;

    @AfterEach
    void tearDown() {
        if (monitor != null) {
            monitor.stop();
        }
    }

    private static String read(Socket socket, int length) throws IOException {
        socket.setSoTimeout(5000);
        byte[] buffer = new byte[length];
        int total = 0;
        while (total < length) {
            int count = socket.getInputStream().read(buffer, total, length - total);
            if (count < 0) {
                break;
            }
            total += count;
        }
        return new String(buffer, 0, total, StandardCharsets.ISO_8859_1);
    }

    @Test
    void sendsCommandsAndReturnsOutput() throws Exception {
        FakeChannel channel = new FakeChannel();
        monitor = new RemoteTextMonitor(channel, 0);
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), monitor.getPort())) {
            OutputStream out = socket.getOutputStream();
            out.write("r\r\nm 0 f\n".getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
            String expected = "got: r\n(C:$e5d1) got: m 0 f\n(C:$e5d1) ";
            assertEquals(expected, read(socket, expected.length()));
            assertEquals("r", channel.received.poll(5, TimeUnit.SECONDS), "telnet's CR LF is one line ending");
        }
    }

    @Test
    void asynchronousOutputReachesTheClient() throws Exception {
        FakeChannel channel = new FakeChannel();
        monitor = new RemoteTextMonitor(channel, 0);
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), monitor.getPort())) {
            socket.getOutputStream().write("p 1\n".getBytes(StandardCharsets.ISO_8859_1));
            channel.received.poll(5, TimeUnit.SECONDS);
            channel.output("#1 (Stop on  exec fd27)\n");
            String expected = "got: p 1\n(C:$e5d1) #1 (Stop on  exec fd27)\n";
            assertEquals(expected, read(socket, expected.length()));
        }
    }

    @Test
    void servesOneClientAfterAnother() throws Exception {
        FakeChannel channel = new FakeChannel();
        monitor = new RemoteTextMonitor(channel, 0);
        for (int i = 0; i < 2; i++) {
            try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), monitor.getPort())) {
                socket.getOutputStream().write(("p " + i + "\n").getBytes(StandardCharsets.ISO_8859_1));
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(),
                        StandardCharsets.ISO_8859_1));
                socket.setSoTimeout(5000);
                assertEquals("got: p " + i, in.readLine());
            }
        }
    }

    @Test
    void listensOnLoopbackOnlyAndStopCloses() throws Exception {
        FakeChannel channel = new FakeChannel();
        monitor = new RemoteTextMonitor(channel, 0);
        int port = monitor.getPort();
        assertTrue(port > 0);
        monitor.stop();
        monitor = null;
        assertTrue(channel.listeners.isEmpty());
        assertThrows(ConnectException.class, () -> new Socket(InetAddress.getLoopbackAddress(), port).close());
    }
}
