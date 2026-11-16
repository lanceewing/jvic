package emu.jvic.lwjgl3.headless;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import emu.jvic.monitor.JavaIoMonitorFileSystem;

/**
 * Runs headless mode in-process with the real ROMs, standard input from a string,
 * and output captured.
 */
@Timeout(60)
public class HeadlessJVicRunnerTest {

    @TempDir
    Path dir;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private int run(String stdin, String... options) {
        Map<String, String> args = new HashMap<>();
        args.put("headless", "true");
        for (String option : options) {
            int equals = option.indexOf('=');
            args.put(option.substring(0, equals), option.substring(equals + 1));
        }
        HeadlessOptions parsed = HeadlessOptions.parse(args);
        return new HeadlessJVicRunner(parsed, new ByteArrayInputStream(stdin.getBytes(StandardCharsets.ISO_8859_1)),
                new PrintStream(out, true), new PrintStream(err, true), false,
                new JavaIoMonitorFileSystem(dir.toFile())).run();
    }

    private String output() {
        return new String(out.toByteArray(), StandardCharsets.ISO_8859_1).replace("\r\n", "\n");
    }

    private Path file(String name, String text) throws IOException {
        return Files.write(dir.resolve(name), text.getBytes(StandardCharsets.ISO_8859_1));
    }

    @Test
    void stopsAtTheResetAddressAndRunsPipedCommands() {
        assertEquals(HeadlessJVicRunner.EXIT_OK, run("r\nquit\nr\n"));
        assertEquals("(C:$fd22) r\n"
                + "  ADDR A  X  Y  SP 00 01 NV-BDIZC LIN CYC  STOPWATCH\n"
                + ".;fd22 00 00 00 ff 00 00 00100100 000 001          1\n"
                + "(C:$fd22) quit\n", output());
    }

    @Test
    void exitsAtTheEndOfInputWhileStopped() {
        assertEquals(HeadlessJVicRunner.EXIT_OK, run("p 1\n"));
        assertEquals("(C:$fd22) p 1\n\t1\n", output());
    }

    @Test
    void pipedLinesAfterAResumeWaitForTheNextStop() {
        assertEquals(HeadlessJVicRunner.EXIT_OK, run("break exec e518\nx\nr\n"));
        String output = output();
        assertTrue(output.contains("(C:$fd22) x\n#1 (Stop on  exec e518)"), output);
        assertTrue(output.contains("(C:$e518) r\n  ADDR"), output);
    }

    @Test
    void moncommandsRunAtStartupAndTheRestAtTheNextStop() throws IOException {
        file("mon.txt", "break exec e518\nx\np 7\n");
        assertEquals(HeadlessJVicRunner.EXIT_OK, run("", "moncommands=mon.txt", "monlog=log.txt"));
        String output = output();
        assertTrue(output.startsWith("BREAK: 1  C:$e518  (Stop on exec)\n#1 (Stop on  exec e518)"), output);
        assertTrue(output.endsWith("\t7\n"), output);
        assertEquals(output, new String(Files.readAllBytes(dir.resolve("log.txt")), StandardCharsets.ISO_8859_1));
    }

    @Test
    void moncommandsWithoutAResumeCarryOnAsInViceUntilTheCycleLimit() throws IOException {
        file("mon.txt", "p 1\n");
        assertEquals(HeadlessJVicRunner.EXIT_LIMIT, run("", "moncommands=mon.txt", "limitcycles=200000"));
        assertEquals("\t1\n", output());
    }

    @Test
    void aJamWithNoInputExitsWithCode3() {
        assertEquals(HeadlessJVicRunner.EXIT_JAM, run("> 1000 02\ng 1000\n"));
        assertTrue(output().contains("JAM at C:$1000"), output());
    }

    @Test
    void initbreakAtAnAddress() {
        assertEquals(HeadlessJVicRunner.EXIT_OK, run("r\n", "initbreak=e518"));
        assertTrue(output().startsWith("#1 (Stop on  exec e518)"), output());
        assertTrue(output().contains("(C:$e518) r\n"), output());
    }

    @Test
    void initbreakReady() {
        assertEquals(HeadlessJVicRunner.EXIT_OK, run("m 00d1 00d1\n", "initbreak=ready"));
        assertTrue(output().contains(">C:00d1  6e"), output());
    }

    @Test
    void autostartRunsABasicProgram() throws IOException {
        // 10 POKE 828,66
        byte[] prg = { 0x01, 0x10, 0x0D, 0x10, 0x0A, 0x00, (byte) 0x97, '8', '2', '8', ',', '6', '6', 0x00, 0x00,
                0x00 };
        Files.write(dir.resolve("poke.prg"), prg);
        assertEquals(HeadlessJVicRunner.EXIT_OK,
                run("watch store 033c if A == $42\nx\nm 033c 033c\n", "autostart=poke.prg"));
        assertTrue(output().contains(">C:033c  42"), output());
    }

    @Test
    void badOptionsAndMissingFilesExitWithCode2() {
        Map<String, String> args = new HashMap<>();
        args.put("headless", "true");
        args.put("limitcycle", "5");
        assertEquals(HeadlessJVicRunner.EXIT_ERROR, HeadlessJVicRunner.run(args));
        assertEquals(HeadlessJVicRunner.EXIT_ERROR, run("", "moncommands=nothere.txt"));
        assertTrue(new String(err.toByteArray(), StandardCharsets.ISO_8859_1).contains("nothere.txt"));
        assertEquals(HeadlessJVicRunner.EXIT_ERROR, run("", "autostart=nothere.prg"));
    }
}
