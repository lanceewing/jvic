package emu.jvic.monitor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import emu.jvic.MachineType;
import emu.jvic.memory.RamType;
import emu.jvic.testutil.MachineTestHarness;

/**
 * Tests for the file, log and command file commands, run against both the in-memory
 * file system (web) and the real one (desktop) in a temporary directory, which must
 * give the same output. Expected output comes from xvic 3.10 running the same
 * commands, with paths replaced.
 */
public class MonitorFileCommandsTest {

    private static final class Host implements MonitorHost {
        int resumes;
        final StringBuilder printed = new StringBuilder();

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
            printed.append(text);
        }
    }

    @TempDir
    Path tempDir;

    private MachineTestHarness h;
    private Host host;
    private MonitorEngine engine;
    private MonitorFileSystem fs;

    private void setUp(String kind) {
        h = MachineTestHarness.withSyntheticRoms(MachineType.PAL, RamType.RAM_UNEXPANDED);
        host = new Host();
        engine = new MonitorEngine(h.machine, host);
        fs = kind.equals("memory") ? new InMemoryMonitorFileSystem() : new JavaIoMonitorFileSystem(tempDir.toFile());
        engine.setFileSystem(fs);
    }

    private String run(String... lines) {
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            engine.execute(line, out::append);
        }
        return out.toString();
    }

    private String typed(String... lines) {
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            engine.executeTyped(line, out::append);
        }
        return out.toString();
    }

    private void runUntilStopped() {
        for (int i = 0; (i < 100) && !h.machine.isDebuggerStopped(); i++) {
            h.machine.update();
        }
        assertTrue(h.machine.isDebuggerStopped(), "never stopped");
    }

    private static String error(String message, String line, int column) {
        StringBuilder caret = new StringBuilder();
        for (int i = 0; i < column + 2; i++) {
            caret.append(' ');
        }
        return "ERROR -- " + message + "\n  " + line + "\n" + caret + "^\n";
    }

    private String text(String path) {
        return new String(fs.read(path), StandardCharsets.ISO_8859_1);
    }

    // ---------------------------------------------------------------------------------
    // Directories
    // ---------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = { "memory", "disk" })
    void directoryCommandsMatchVice(String kind) {
        setUp(kind);
        String home = fs.pwd();
        assertEquals(home + "\n", run("pwd"));
        assertEquals("Created directory 'sub'\n", run("mkdir sub"));
        assertEquals("Cannot create directory 'sub': 17: File exists\n", run("mkdir sub"));
        assertEquals("Displaying directory: `" + home + "'\n"
                + "     <dir> .\n"
                + "     <dir> ..\n"
                + "     <dir> sub\n", run("dir"));
        assertEquals(run("dir"), run("ls"));
        assertEquals("Displaying directory: `sub'\n     <dir> .\n     <dir> ..\n", run("dir sub"));
        assertEquals("Displaying directory: `nothere'\nCouldn't open directory.\n", run("dir nothere"));

        assertEquals("Changing to directory: 'sub'\n", run("cd sub"));
        assertEquals(fs.absolute("."), fs.pwd());
        assertTrue(fs.pwd().endsWith("sub"), fs.pwd());
        assertEquals("Changing to directory: '..'\n", run("cd \"..\""));
        assertEquals(home, fs.pwd());
        assertEquals("Cannot change to directory: 'nonexist'\n", run("cd nonexist"));

        assertEquals("Removed directory 'sub'\n", run("rmdir sub"));
        assertEquals("Cannot remove directory 'sub': 2: No such file or directory\n", run("rmdir sub"));

        assertEquals(error(MonitorException.WRONG_SYNTAX, "mkdir", 5), run("mkdir"));
        assertEquals(error(MonitorException.WRONG_SYNTAX, "cd", 2), run("cd"));
        assertEquals(error(MonitorException.WRONG_SYNTAX, "rmdir", 5), run("rmdir"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "memory", "disk" })
    void rmdirRefusesADirectoryThatIsNotEmpty(String kind) {
        setUp(kind);
        run("mkdir d", "> 1000 1");
        run("bsave \"d/x.bin\" 0 1000 1000");
        assertEquals("Cannot remove directory 'd': 41: Directory not empty\n", run("rmdir d"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "memory", "disk" })
    void dirListsDirectoriesThenFilesWithSizes(String kind) {
        setUp(kind);
        run("> 1000 a9 01 8d 00 10 60", "mkdir sub");
        run("save \"a.prg\" 0 1000 1005", "bsave \"b.bin\" 0 1000 1005");
        assertEquals("Displaying directory: `" + fs.pwd() + "'\n"
                + "     <dir> .\n"
                + "     <dir> ..\n"
                + "     <dir> sub\n"
                + "         8 a.prg\n"
                + "         6 b.bin\n", run("dir"));
    }

    // ---------------------------------------------------------------------------------
    // load, save, verify
    // ---------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = { "memory", "disk" })
    void saveAndLoadMatchVice(String kind) {
        setUp(kind);
        run("> 1000 a9 01 8d 00 10 60");
        assertEquals("Saving file 'a.prg' from $1000 to $1005\n", run("save \"a.prg\" 0 1000 1005"));
        assertEquals("Saving file 'b.bin' from $1000 to $1005\n", run("bsave \"b.bin\" 0 1000 1005"));
        assertArrayEquals(new byte[] { 0x00, 0x10, (byte) 0xa9, 0x01, (byte) 0x8d, 0x00, 0x10, 0x60 },
                fs.read("a.prg"));
        assertArrayEquals(new byte[] { (byte) 0xa9, 0x01, (byte) 0x8d, 0x00, 0x10, 0x60 }, fs.read("b.bin"));

        assertEquals("Loading 'a.prg' from 1000 to 1005 (0006 bytes)\n", run("load \"a.prg\" 0"));
        assertEquals("Loading 'a.prg' from 1100 to 1105 (0006 bytes)\n", run("load \"a.prg\" 0 1100"));
        assertEquals(">C:1100  a9 01 8d 00  10 60 00 00                             .....`..\n",
                run("m 1100 1107"));
        // load skips the first two bytes, even when given an address.
        assertEquals("Loading 'b.bin' from 1200 to 1203 (0004 bytes)\n", run("l \"b.bin\" 0 1200"));
        assertEquals("Loading 'b.bin' from 1300 to 1305 (0006 bytes)\n", run("bload \"b.bin\" 0 1300"));
        assertEquals(run("m 1000 1005").replace("1000", "1300"), run("m 1300 1305"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "memory", "disk" })
    void verifyMatchesVice(String kind) {
        setUp(kind);
        run("> 1000 a9 01 8d 00 10 60", "save \"a.prg\" 0 1000 1005", "bsave \"b.bin\" 0 1000 1005");
        assertEquals("Verifying 'a.prg' from 1000 to 1005 (0006 bytes)\n", run("verify \"a.prg\" 0"));
        run("> 1001 02");
        assertEquals("Verifying 'a.prg' from 1000 \n"
                + "addr:mem file\n"
                + "1001: 02 01\n"
                + "to 1005 (0006 bytes)\n"
                + "1 byte(s) different\n", run("verify \"a.prg\" 0"));
        assertEquals("Verifying 'b.bin' from 1000 \n"
                + "addr:mem file\n"
                + "1001: 02 01\n"
                + "to 1005 (0006 bytes)\n"
                + "1 byte(s) different\n", run("bverify \"b.bin\" 0 1000"));
        assertEquals(run("v \"a.prg\" 0 1000"), run("verify \"a.prg\" 0"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "memory", "disk" })
    void loadWrapsAtTheTopOfMemoryAsInVice(String kind) {
        setUp(kind);
        fs.write("big.bin", new byte[16]);
        assertEquals("Loading 'big.bin' from FFF8 to 0007 (0010 bytes)\n", run("bload \"big.bin\" 0 fff8"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "memory", "disk" })
    void shortFilesMatchVice(String kind) {
        setUp(kind);
        fs.write("empty.prg", new byte[0]);
        fs.write("one.prg", new byte[] { 1 });
        assertEquals("Loading 'empty.prg' from 0000 to FFFF (0000 bytes)\n", run("load \"empty.prg\" 0"));
        assertEquals("Loading 'one.prg' from 1000 to 1000 (0001 bytes)\n", run("bload \"one.prg\" 0 1000"));
        assertEquals("Loading 'one.prg' from 0001 to 0000 (0000 bytes)\n", run("load \"one.prg\" 0"));
        assertEquals("Verifying 'one.prg' from 0001 to 0000 (0000 bytes)\n", run("verify \"one.prg\" 0"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "memory", "disk" })
    void loadbasicSetsTheBasicPointersAsVice(String kind) {
        setUp(kind);
        fs.write("bas.prg", new byte[] { 0x01, 0x12, 0x0b, 0x12, 0x0a, 0x00, (byte) 0x99, 0x22, 0x48, 0x49, 0x22,
                0x00, 0x00, 0x00 });
        assertEquals("Loading 'bas.prg' from 1201 to 120C (000C bytes)\n", run("loadbasic \"bas.prg\" 0"));
        assertEquals(">C:002b  01 12 0d 12  0d 12 0d 12                             ........\n", run("m 2b 32"));
        assertEquals(">C:00ac  01 12 0d 12                                          ....\n", run("m ac af"));
        assertEquals("Loading 'bas.prg' from 1300 to 130B (000C bytes)\n", run("ldb \"bas.prg\" 0 1300"));
        assertEquals(">C:002b  00 13 0c 13  0c 13 0c 13                             ........\n", run("m 2b 32"));
        // load and bload leave the pointers alone.
        run("load \"bas.prg\" 0 1400", "bload \"bas.prg\" 0 1500");
        assertEquals(">C:002b  00 13 0c 13  0c 13 0c 13                             ........\n", run("m 2b 32"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "memory", "disk" })
    void fileErrorsMatchVice(String kind) {
        setUp(kind);
        assertEquals("Cannot open 'missing.prg'.\n", run("load \"missing.prg\" 0"));
        assertEquals(error("Expecting an address.", "bload \"missing.bin\" 0", 21),
                run("bload \"missing.bin\" 0"));
        assertEquals(error("Expecting an address.", "save \"c.prg\" 0 1000", 19), run("save \"c.prg\" 0 1000"));
        assertEquals("Start address must be below end address.\n", run("save \"r.prg\" 0 1102 1100"));
        assertFalse(fs.exists("r.prg"));
        assertEquals(error("Expecting a filename:", "logname", 7), run("logname"));
        assertEquals("Device 8 (the emulated disk) is not supported by this command in jvic yet.\n",
                run("load \"x.prg\" 8"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "memory", "disk" })
    void loadsAndSavesInTheDriveMemspace(String kind) {
        setUp(kind);
        run("> 8:0300 11 22 33");
        run("bsave \"d.bin\" 0 8:0300 0302");
        assertArrayEquals(new byte[] { 0x11, 0x22, 0x33 }, fs.read("d.bin"));
        run("bload \"d.bin\" 0 8:0400");
        assertEquals(">8:0400  11 22 33                                             .\"3\n", run("m 8:0400 0402"));
    }

    // ---------------------------------------------------------------------------------
    // log and logname
    // ---------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = { "memory", "disk" })
    void logEchoesTypedCommandsAfterThePromptAsVice(String kind) {
        setUp(kind);
        assertEquals("", typed("logname \"log.txt\""));
        assertEquals("Logging is disabled.\n", typed("log"));
        typed("log on");
        assertEquals("Logging to 'log.txt' is enabled.\n", typed("log"));
        typed("p 5");
        run("p 6"); // Not typed (e.g. from a playback file): not echoed.
        typed("log off");
        assertEquals("Logging is disabled.\n", typed("log"));
        String prompt = engine.getPrompt();
        assertEquals(prompt + "log\nLogging to 'log.txt' is enabled.\n"
                + prompt + "p 5\n\t5\n"
                + "\t6\n"
                + prompt + "log off\n", text("log.txt"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "memory", "disk" })
    void logDefaultsToMonitorLogAndAddsToItAsVice(String kind) {
        setUp(kind);
        fs.write("monitor.log", "old\n".getBytes(StandardCharsets.ISO_8859_1));
        run("log on");
        assertEquals("Logging to 'monitor.log' is enabled.\n", run("log"));
        run("log toggle", "p 1", "log on", "p 2", "log off");
        assertEquals("old\nLogging to 'monitor.log' is enabled.\n\t2\n", text("monitor.log"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "memory", "disk" })
    void logFileStaysPutAfterCd(String kind) {
        setUp(kind);
        run("mkdir sub", "log on", "cd sub", "p 1", "log off", "cd ..");
        assertEquals("Changing to directory: 'sub'\n\t1\n", text("monitor.log"));
    }

    @Test
    void logIncludesCheckpointHitsAndTraceOutput() {
        setUp("memory");
        run("log on", "break exec e001", "x");
        runUntilStopped();
        String log = text("monitor.log");
        assertTrue(log.contains("#1 (Stop on  exec e001)"), log);
    }

    // ---------------------------------------------------------------------------------
    // record, playback, stop
    // ---------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = { "memory", "disk" })
    void recordWritesTypedCommandsAsVice(String kind) {
        setUp(kind);
        assertEquals("", typed("record \"r1.txt\""));
        assertEquals("Recording already in progress. Use 'stop' to end recording.\n", typed("record \"r2.txt\""));
        typed("p 1");
        run("p 2"); // From a playback file: not recorded.
        assertEquals("Closed file r1.txt.\n", typed("stop"));
        assertEquals("No file is currently being recorded.\n", typed("stop"));
        assertEquals("record \"r2.txt\"\np 1\n", text("r1.txt"));
        assertFalse(fs.exists("r2.txt"));
        // Playing it back starts recording r2.txt, as in VICE; the playback lines
        // themselves are not recorded.
        assertEquals("\t1\n", typed("playback \"r1.txt\""));
        typed("p 3", "stop");
        assertEquals("p 3\n", text("r2.txt"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "memory", "disk" })
    void playbackMatchesVice(String kind) {
        setUp(kind);
        fs.write("rec.txt", "p 5\r\nr\n\nm 1000 1003\n".getBytes(StandardCharsets.ISO_8859_1));
        String expected = "\t5\n"
                + "  ADDR A  X  Y  SP 00 01 NV-BDIZC LIN CYC  STOPWATCH\n";
        String output = run("playback \"rec.txt\"");
        assertTrue(output.startsWith(expected), output);
        assertTrue(output.endsWith(">C:1000  00 00 00 00                                          ....\n"), output);
        assertEquals(output, run("pb \"rec.txt\""));
        assertEquals("Cannot open 'nothere.txt'.\n", run("pb \"nothere.txt\""));
    }

    @ParameterizedTest
    @ValueSource(strings = { "memory", "disk" })
    void playbackNestsAndStopEndsOnlyTheCurrentFile(String kind) {
        setUp(kind);
        fs.write("outer.txt", "p 1\npb \"inner.txt\"\np 4\n".getBytes(StandardCharsets.ISO_8859_1));
        fs.write("inner.txt", "p 2\nstop\np 3\n".getBytes(StandardCharsets.ISO_8859_1));
        assertEquals("\t1\n\t2\n\t4\n", run("pb \"outer.txt\""));
    }

    @Test
    void playbackOfAFileThatPlaysItselfIsLimited() {
        setUp("memory");
        fs.write("self.txt", "p 1\npb \"self.txt\"\n".getBytes(StandardCharsets.ISO_8859_1));
        String output = run("pb \"self.txt\"");
        StringBuilder expected = new StringBuilder();
        for (int i = 0; i < MonitorEngine.MAX_PLAYBACK_DEPTH; i++) {
            expected.append("\t1\n");
        }
        expected.append("Playback files are nested too deeply; not playing 'self.txt'.\n");
        assertEquals(expected.toString(), output);
    }

    @Test
    void playbackLinesAfterAResumeRunAtTheNextStop() {
        setUp("memory");
        fs.write("s.txt", "break exec e001\nx\np 7\n".getBytes(StandardCharsets.ISO_8859_1));
        assertEquals("BREAK: 1  C:$e001  (Stop on exec)\n", run("pb \"s.txt\""));
        assertEquals(1, host.resumes);
        assertTrue(engine.hasPendingScript());
        runUntilStopped();
        StringBuilder out = new StringBuilder();
        engine.continueScript(out::append);
        assertEquals("\t7\n", out.toString());
        assertFalse(engine.hasPendingScript());
    }

    // ---------------------------------------------------------------------------------
    // The file systems themselves
    // ---------------------------------------------------------------------------------

    @Test
    void inMemoryFileSystemTracksChangesAndEnforcesItsSizeLimit() {
        InMemoryMonitorFileSystem memory = new InMemoryMonitorFileSystem(10);
        memory.mkdir("d");
        memory.write("d/a", new byte[4]);
        memory.append("d/a", new byte[2]);
        memory.write("b", new byte[4]);
        assertEquals(10, memory.getTotalSize());
        assertEquals(List.of("/d", "/d/a", "/b"), memory.takeDirtyPaths());
        assertEquals(List.of(), memory.takeDirtyPaths());
        MonitorFileException full = assertThrows(MonitorFileException.class, () -> memory.write("c", new byte[1]));
        assertEquals(MonitorFileException.NO_SPACE, full.getMessage());
        memory.write("b", new byte[3]);
        memory.write("c", new byte[1]);
        assertEquals(10, memory.getTotalSize());

        memory.preload("/x/y/z.prg", new byte[] { 1 });
        assertTrue(memory.isDirectory("/x/y"));
        assertEquals(List.of("/b", "/c"), memory.takeDirtyPaths());
        assertEquals("/x/y", memory.absolute("/x/./y/z/.."));
        memory.cd("x\\y");
        assertEquals("/x/y", memory.pwd());
        assertArrayEquals(new byte[] { 1 }, memory.read("z.prg"));
        assertEquals(MonitorFileException.ACCESS_DENIED,
                assertThrows(MonitorFileException.class, () -> memory.rmdir("/x/y")).getMessage());
    }

    @Test
    void javaIoFileSystemStartsInTheGivenDirectory() {
        JavaIoMonitorFileSystem disk = new JavaIoMonitorFileSystem(tempDir.toFile());
        assertEquals(tempDir.toFile().getAbsolutePath(), new File(disk.pwd()).getAbsolutePath());
        disk.write("f.bin", new byte[] { 9 });
        assertTrue(new File(tempDir.toFile(), "f.bin").isFile());
    }
}
