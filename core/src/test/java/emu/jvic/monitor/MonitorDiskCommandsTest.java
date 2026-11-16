package emu.jvic.monitor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import emu.jvic.MachineType;
import emu.jvic.memory.RamType;
import emu.jvic.testutil.MachineTestHarness;

/**
 * Tests for the disk commands (list, load/save on device 8, block_read/block_write,
 * @, attach, detach, autoload), on the blank disk image in assets/data. Expected
 * output formats are xvic 3.10's, except where noted.
 */
public class MonitorDiskCommandsTest {

    private static final class Host implements MonitorHost {
        int resumes;

        @Override
        public void resume() {
            resumes++;
        }

        @Override
        public void quit() {
        }

        @Override
        public boolean isWarp() {
            return false;
        }

        @Override
        public void setWarp(boolean warp) {
        }

        @Override
        public void print(String text) {
        }
    }

    private static final String EMPTY_LISTING = "0 \"                \" 00 2A\n664 blocks free.\n";

    private MachineTestHarness h;
    private Host host;
    private MonitorEngine engine;
    private InMemoryMonitorFileSystem fs;

    private void setUp(boolean realRoms) throws IOException {
        h = realRoms ? MachineTestHarness.withRealRoms(MachineType.PAL, RamType.RAM_UNEXPANDED)
                : MachineTestHarness.withSyntheticRoms(MachineType.PAL, RamType.RAM_UNEXPANDED);
        host = new Host();
        engine = new MonitorEngine(h.machine, host);
        fs = new InMemoryMonitorFileSystem();
        engine.setFileSystem(fs);
        fs.write("blank.d64", blankDisk());
    }

    private void attached() throws IOException {
        // Real ROMs: attaching warms the drive up, which runs its DOS.
        setUp(true);
        assertEquals("", run("attach \"blank.d64\" 8"));
    }

    private static byte[] blankDisk() throws IOException {
        Path dir = Paths.get("").toAbsolutePath();
        while (!Files.isRegularFile(dir.resolve("assets/data/blank.d64"))) {
            dir = dir.getParent();
        }
        return Files.readAllBytes(dir.resolve("assets/data/blank.d64"));
    }

    private String run(String... lines) {
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            engine.execute(line, out::append);
        }
        return out.toString();
    }

    @Test
    void withoutADiskTheDriveIsNotReady() throws IOException {
        setUp(false);
        assertEquals("Drive 8 not ready.\n", run("list"));
        assertEquals("Drive 8 not ready.\n", run("br 12 0"));
        assertEquals("Cannot open 'x'.\n", run("load \"x\" 8"));
        assertEquals("74,DRIVE NOT READY,00,00\n", run("@i"));
        assertEquals("Failed.\n", run("attach \"none.d64\" 8"));
        // As in VICE, 32 is hex in the default radix: the cartridge is +32.
        assertEquals("Unknown device 50.\n", run("attach \"blank.d64\" 32"));
        assertTrue(run("attach \"blank.d64\" +32").startsWith("Attaching a cartridge needs a restart"));
    }

    @Test
    void saveListLoadAndVerifyMatchVice() throws IOException {
        attached();
        assertEquals(EMPTY_LISTING, run("list"));
        run("> 1000 a9 01 60");
        assertEquals("Saving file 'test' from $1000 to $1002\n", run("save \"test\" 8 1000 1002"));
        assertEquals("0 \"                \" 00 2A\n1    \"TEST\"             PRG \n663 blocks free.\n",
                run("list 8"));
        assertEquals("Loading 'test' from 2000 to 2002 (0003 bytes)\n", run("load \"test\" 8 2000"));
        assertEquals("Loading 't*' from 1000 to 1002 (0003 bytes)\n", run("load \"t*\" 8"));
        assertEquals("Verifying 'test' from 1000 to 1002 (0003 bytes)\n", run("verify \"test\" 8"));
        // The file is written back to the attached image file, as in VICE.
        D64Disk saved = new D64Disk(new emu.jvic.io.disk.GcrDiskImage(fs.read("blank.d64")));
        assertEquals(1, saved.entries().size());
    }

    @Test
    void longFilesFollowTheirSectorChain() throws IOException {
        attached();
        run("f 1000 17ff 11 22 33 44 55", "save \"long\" 8 1000 17ff", "bsave \"long.bin\" 0 1000 17ff");
        assertTrue(run("list").contains("9    \"LONG\"             PRG \n655 blocks free."));
        D64Disk disk = engine.getDisk();
        byte[] file = disk.read(disk.find("long"));
        byte[] expected = new byte[file.length];
        System.arraycopy(fs.read("long.bin"), 0, expected, 2, file.length - 2);
        expected[0] = 0x00;
        expected[1] = 0x10;
        assertArrayEquals(expected, file);
        // Saving it again replaces it.
        run("save \"long\" 8 1000 1001");
        assertTrue(run("list").contains("1    \"LONG\"             PRG \n663 blocks free."), run("list"));
    }

    @Test
    void diskCommandsMatchVice() throws IOException {
        attached();
        run("> 1000 1", "save \"one\" 8 1000 1000", "save \"two\" 8 1000 1000");
        assertEquals("00, OK,00,00\n", run("@"));
        assertEquals("00, OK,00,00\n", run("@$"));
        assertEquals("00, OK,00,00\n", run("@i"));
        assertEquals("62,FILE NOT FOUND,00,00\n", run("@r:new=old"));
        assertEquals("63,FILE EXISTS,00,00\n", run("@r:one=two"));
        assertEquals("00, OK,00,00\n", run("@r:three=two"));
        assertEquals("01,FILES SCRATCHED,01,00\n", run("@s:one"));
        assertEquals("01,FILES SCRATCHED,00,00\n", run("@s:one"));
        assertTrue(run("list").endsWith("1    \"THREE\"            PRG \n663 blocks free.\n"), run("list"));
        assertEquals("00, OK,00,00\n", run("@v"));
        assertTrue(run("list").endsWith("663 blocks free.\n"));
        assertEquals("31,SYNTAX ERROR,00,00\n", run("@zz"));
        assertEquals("00, OK,00,00\n", run("@n:new disk,ab"));
        assertEquals("0 \"NEW DISK        \" AB 2A\n664 blocks free.\n", run("list"));
    }

    @Test
    void blockReadAndWriteMatchVice() throws IOException {
        attached();
        String bam = run("br 12 0");
        assertTrue(bam.startsWith(">0000  12 01 41 00  15 ff ff 1f  15 ff ff 1f  15 ff ff 1f\n"), bam);
        assertEquals(16, bam.split("\n").length);
        assertEquals("Read track 18 sector 0 into address $1000\n", run("br 12 0 1000"));
        assertEquals(">C:1000  12 01 41 00                                          ..A.\n", run("m 1000 1003"));
        run("f 1100 11ff 7");
        assertEquals("Write data from address $1100 to track 1 sector 2\n", run("bw 1 2 1100"));
        assertTrue(run("br 1 2").startsWith(">0000  07 07 07 07  07 07"));
        assertEquals("Error reading track 64 sector 0\n", run("br 40 0"));
        assertEquals("Error writing track 1 sector 21\n", run("bw 1 15 1100"));
    }

    @Test
    void detachEmptiesTheDrive() throws IOException {
        attached();
        assertEquals("", run("detach 8"));
        assertNull(engine.getDisk());
        assertEquals("Drive 8 not ready.\n", run("list"));
    }

    @Test
    void wildcardsMatchAsTheDosDoes() {
        int[] name = new int[16];
        int[] petscii = D64Disk.toPetscii("hello");
        for (int i = 0; i < 16; i++) {
            name[i] = (i < petscii.length) ? petscii[i] : 0xA0;
        }
        assertTrue(D64Disk.matches(D64Disk.toPetscii("hello"), name));
        assertTrue(D64Disk.matches(D64Disk.toPetscii("HELLO"), name));
        assertTrue(D64Disk.matches(D64Disk.toPetscii("he*"), name));
        assertTrue(D64Disk.matches(D64Disk.toPetscii("h?llo"), name));
        assertTrue(D64Disk.matches(D64Disk.toPetscii("*"), name));
        assertFalse(D64Disk.matches(D64Disk.toPetscii("hell"), name));
        assertFalse(D64Disk.matches(D64Disk.toPetscii("hello2"), name));
    }

    @Test
    void autoloadOfAProgramLoadsItWhenBasicIsReadyAndRunTypesRun() throws IOException {
        setUp(true);
        // 10 PRINT "HI"
        fs.write("p.prg", new byte[] { 0x01, 0x10, 0x0b, 0x10, 0x0a, 0x00, (byte) 0x99, 0x22, 0x48, 0x49, 0x22, 0x00,
                0x00, 0x00 });
        assertEquals("autostarting p.prg #0\n", run("autostart \"p.prg\""));
        assertEquals(1, host.resumes);
        int[] mem = h.getMemory().getMemoryArray();
        for (int i = 0; (i < 300) && (mem[0x1002] != 0x10); i++) {
            h.machine.update();
            engine.onFrame();
        }
        assertEquals(0x99, mem[0x1005]);
        assertEquals(0x01, mem[0x2B]);
        assertEquals(0x10, mem[0x2C]);
        assertEquals(0x0D, mem[0x2D]);
        assertEquals(4, mem[0xC6]);
        assertArrayEquals(new int[] { 'R', 'U', 'N', 0x0D }, new int[] { mem[0x277], mem[0x278], mem[0x279],
            mem[0x27A] });
    }
}
