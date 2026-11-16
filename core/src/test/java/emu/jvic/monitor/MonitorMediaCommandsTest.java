package emu.jvic.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import emu.jvic.MachineType;
import emu.jvic.memory.RamType;
import emu.jvic.testutil.MachineTestHarness;

/**
 * Tests for tapectrl, tapeoffs, screenshot, the resource commands, jpdb, updb and
 * cartfreeze. Expected output is xvic 3.10's where the command exists there.
 */
public class MonitorMediaCommandsTest {

    private static final class Host implements MonitorHost {
        boolean warp;

        @Override
        public void resume() {
        }

        @Override
        public void quit() {
        }

        @Override
        public boolean isWarp() {
            return warp;
        }

        @Override
        public void setWarp(boolean warp) {
            this.warp = warp;
        }

        @Override
        public void print(String text) {
        }
    }

    private MachineTestHarness h;
    private Host host;
    private MonitorEngine engine;
    private InMemoryMonitorFileSystem fs;

    private void setUp(RamType ramType) {
        h = MachineTestHarness.withSyntheticRoms(MachineType.PAL, ramType);
        host = new Host();
        engine = new MonitorEngine(h.machine, host);
        fs = new InMemoryMonitorFileSystem();
        engine.setFileSystem(fs);
    }

    private String run(String... lines) {
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            engine.execute(line, out::append);
        }
        return out.toString();
    }

    private static byte[] tape(int pulses) {
        ByteBuffer tap = ByteBuffer.allocate(20 + pulses).order(ByteOrder.LITTLE_ENDIAN);
        tap.put("C64-TAPE-RAW".getBytes(StandardCharsets.ISO_8859_1));
        tap.put(new byte[] { 1, 1, 0, 0 });
        tap.putInt(pulses);
        for (int i = 0; i < pulses; i++) {
            tap.put((byte) 0x30);
        }
        return tap.array();
    }

    @Test
    void tapeCommandsMatchVice() {
        setUp(RamType.RAM_UNEXPANDED);
        assertEquals("No tape attached.\n", run("tapeoffs"));
        assertEquals("", run("tapectrl 1"));
        assertEquals("Unknown command.\n", run("tapectrl 9"));
        fs.write("t.tap", tape(200));
        assertEquals("", run("attach \"t.tap\" 1"));
        assertEquals("Current tape offset is: 0\n", run("tapeoffs"));
        assertEquals("Setting tape to offset: 80\n", run("tapeoffs 50"));
        assertEquals("Current tape offset is: 80\n", run("tapeoffs"));
        run("tapectrl 5");
        assertEquals("Current tape offset is: 0\n", run("tapeoffs"));
        // jvic winds at once (VICE winds over time).
        run("tapectrl 2");
        assertEquals("Current tape offset is: 200\n", run("tapeoffs"));
        run("tapectrl 3");
        assertEquals("Current tape offset is: 0\n", run("tapeoffs"));
        assertEquals("Recording to tape is not supported by jvic.\n", run("tapectrl 4"));
        assertEquals("", run("detach 1"));
        assertEquals("No tape attached.\n", run("tapeoffs"));
    }

    @Test
    void screenshotsAreValidImagesOfTheVisibleScreen() throws IOException {
        setUp(RamType.RAM_UNEXPANDED);
        for (int i = 0; i < 3; i++) {
            h.machine.update();
        }
        assertEquals("", run("scrsh \"shot.png\" 2", "scrsh \"shot\"", "screenshot \"x.bmp\" 0"));
        MachineType type = MachineType.PAL;
        for (String name : new String[] { "shot.png", "shot.bmp", "x.bmp" }) {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(fs.read(name)));
            assertNotNull(image, name);
            assertEquals(type.getVisibleScreenWidth(), image.getWidth(), name);
            assertEquals(type.getVisibleScreenHeight(), image.getHeight(), name);
        }
        // The PNG and BMP have the same pixels as the frame the VIC drew.
        BufferedImage png = ImageIO.read(new ByteArrayInputStream(fs.read("shot.png")));
        BufferedImage bmp = ImageIO.read(new ByteArrayInputStream(fs.read("shot.bmp")));
        int x = type.getVisibleScreenWidth() / 2;
        int y = type.getVisibleScreenHeight() / 2;
        int expected = h.pixelData.getPixel((type.getVerticalOffset() + y) * type.getTotalScreenWidth()
                + type.getHorizontalOffset() + x);
        assertEquals(expected >>> 8, png.getRGB(x, y) & 0xFFFFFF);
        assertEquals(png.getRGB(x, y), bmp.getRGB(x, y));
        assertTrue(run("scrsh \"a.gif\" 3").startsWith("Screenshot format 3 is not supported"));
        assertFalse(fs.exists("shot.png.bmp"));
    }

    @Test
    void resourcesMatchVicesFormat() {
        setUp(RamType.RAM_8K);
        assertEquals("MachineVideoStandard=1\n", run("resget \"MachineVideoStandard\""));
        assertEquals("RAMBlock0=0\n", run("resget \"RAMBlock0\""));
        assertEquals("RAMBlock1=1\n", run("resget \"RAMBlock1\""));
        assertEquals("Unknown resource \"NoSuchThing\".\n", run("resget \"NoSuchThing\""));
        assertEquals("", run("resset \"WarpMode\" \"1\""));
        assertTrue(host.warp);
        assertEquals("Unknown resource \"NoSuchThing\".\n", run("resset \"NoSuchThing\" \"1\""));
        assertEquals("Resource \"RAMBlock0\" can't be changed while jvic is running.\n",
                run("resset \"RAMBlock0\" \"1\""));
        assertEquals("", run("ressave \"res.txt\""));
        String saved = new String(fs.read("res.txt"), StandardCharsets.ISO_8859_1);
        assertTrue(saved.startsWith("[VIC20]\nMachineVideoStandard=1\nRAMBlock0=0\nRAMBlock1=1\n"), saved);
        assertTrue(saved.contains("WarpMode=1\n"), saved);
        run("resset \"WarpMode\" \"0\"");
        assertEquals("", run("resload \"res.txt\""));
        assertTrue(host.warp);
        assertEquals("Cannot open 'none.txt'.\n", run("resload \"none.txt\""));
    }

    @Test
    void jpdbHoldsJoystickLinesDown() {
        setUp(RamType.RAM_UNEXPANDED);
        assertEquals("Illegal port.\n", run("jpdb 2 1"));
        int idle = h.machine.getJoystick().getJoystickState();
        run("jpdb 1 11"); // up + fire (hex 11 = bits 0 and 4)
        int held = h.machine.getJoystick().getJoystickState();
        assertEquals(0, held & 0x04);
        assertEquals(0, held & 0x20);
        assertEquals(idle & 0x98, held & 0x98);
        run("jpdb 1 0");
        assertEquals(idle, h.machine.getJoystick().getJoystickState());
        assertEquals("", run("updb 1", "cartfreeze"));
    }
}
