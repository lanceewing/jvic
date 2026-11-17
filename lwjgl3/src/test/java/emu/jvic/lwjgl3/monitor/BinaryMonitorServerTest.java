package emu.jvic.lwjgl3.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import emu.jvic.Machine;
import emu.jvic.MachineType;
import emu.jvic.lwjgl3.DesktopKeyboardMatrix;
import emu.jvic.lwjgl3.DesktopPixelData;
import emu.jvic.memory.RamType;
import emu.jvic.monitor.MonitorSession;
import emu.jvic.sound.SoundGenerator;

/**
 * Tests the binary monitor's TCP transport: packets split from the byte stream,
 * responses sent back, and the reply to quit sent before the server closes.
 */
@Timeout(60)
public class BinaryMonitorServerTest {

    private static final class SilentSound extends SoundGenerator {
        @Override
        public void initSound(MachineType machineType) {
        }

        @Override
        public void emulateCycle() {
        }

        @Override
        public void pauseSound() {
        }

        @Override
        public void resumeSound() {
        }

        @Override
        public boolean isSoundOn() {
            return false;
        }

        @Override
        public void dispose() {
        }
    }

    private static byte[] rom(String name) throws IOException {
        Path dir = Paths.get("").toAbsolutePath();
        while (!Files.isRegularFile(dir.resolve("assets/roms").resolve(name))) {
            dir = dir.getParent();
        }
        return Files.readAllBytes(dir.resolve("assets/roms").resolve(name));
    }

    private static byte[] command(int type, int requestId) {
        ByteBuffer buffer = ByteBuffer.allocate(11).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put((byte) 2).put((byte) 2).putInt(0).putInt(requestId).put((byte) type);
        return buffer.array();
    }

    private static int[] readResponse(DataInputStream in) throws IOException {
        byte[] header = new byte[12];
        in.readFully(header);
        ByteBuffer b = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        int length = b.getInt(2);
        in.readFully(new byte[length]);
        return new int[] { header[6] & 0xFF, header[7] & 0xFF, b.getInt(8) };
    }

    @Test
    void answersCommandsOverTcpAndSendsTheQuitReplyBeforeClosing() throws Exception {
        DesktopPixelData pixels = new DesktopPixelData();
        pixels.init(MachineType.PAL.getTotalScreenWidth(), MachineType.PAL.getTotalScreenHeight());
        Machine machine = new Machine(new SilentSound(), new DesktopKeyboardMatrix(), pixels);
        machine.init(rom("basic.rom"), rom("kernal_pal.rom"), rom("char.rom"), rom("dos1541.rom"), null,
                MachineType.PAL, RamType.RAM_UNEXPANDED, null);
        AtomicInteger quits = new AtomicInteger();
        MonitorSession session = new MonitorSession(new MonitorSession.Platform() {
            @Override
            public void resumeEmulation() {
            }

            @Override
            public void quit() {
                quits.incrementAndGet();
            }

            @Override
            public boolean isWarp() {
                return false;
            }

            @Override
            public void setWarp(boolean warp) {
            }
        });
        session.attach(machine);
        machine.getDebugger().requestStop();
        while (!machine.isDebuggerStopped()) {
            machine.update();
        }
        session.onMachineStopped();

        // Actions run straight away: the machine is stopped and nothing else touches it.
        BinaryMonitorServer server = new BinaryMonitorServer(session, Runnable::run, 0);
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.getPort())) {
            OutputStream out = socket.getOutputStream();
            InputStream raw = socket.getInputStream();
            DataInputStream in = new DataInputStream(raw);
            // Junk before a command is skipped, as VICE resynchronises on STX.
            out.write(new byte[] { 0x55, 0x66 });
            out.write(command(0x81, 7));
            out.flush();
            int[] ping = readResponse(in);
            assertEquals(0x81, ping[0]);
            assertEquals(7, ping[2]);

            out.write(command(0x77, 8));
            out.flush();
            int[] unknown = readResponse(in);
            assertEquals(0x00, unknown[0]);
            assertEquals(0x83, unknown[1]);

            out.write(command(0xBB, 9));
            out.flush();
            Thread.sleep(200);
            server.stop();
            int[] quit = readResponse(in);
            assertEquals(0xBB, quit[0]);
            assertEquals(1, quits.get());
        } finally {
            server.stop();
        }
    }
}
