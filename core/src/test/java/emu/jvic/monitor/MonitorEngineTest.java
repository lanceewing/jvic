package emu.jvic.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import emu.jvic.MachineType;
import emu.jvic.debug.Debugger;
import emu.jvic.memory.RamType;
import emu.jvic.testutil.MachineTestHarness;

/**
 * Tests for the monitor engine's first commands. Expected output comes from xvic 3.10
 * (PAL) running the same commands with "log on", except where noted: jvic's RAM
 * starts zeroed where VICE's has a power-on pattern, and jvic's mem character column
 * shows '.' for non-printable bytes where VICE writes the raw bytes.
 */
public class MonitorEngineTest {

    private static final class RecordingHost implements MonitorHost {
        int resumes;
        int quits;

        @Override
        public void resume() {
            resumes++;
        }

        @Override
        public void quit() {
            quits++;
        }

        @Override
        public void print(String text) {
        }

        boolean warp;

        @Override
        public boolean isWarp() {
            return warp;
        }

        @Override
        public void setWarp(boolean warp) {
            this.warp = warp;
        }
    }

    private MachineTestHarness h;
    private RecordingHost host;
    private MonitorEngine engine;

    @BeforeEach
    void setUp() {
        h = MachineTestHarness.withRealRoms(MachineType.PAL, RamType.RAM_UNEXPANDED);
        host = new RecordingHost();
        engine = new MonitorEngine(h.machine, host);
    }

    private String run(String... lines) {
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            engine.execute(line, out::append);
        }
        return out.toString();
    }

    private void assertOutput(String expected, String... lines) {
        assertEquals(expected, run(lines), String.join(" / ", lines));
    }

    private void poke(int address, int... values) {
        int[] mem = h.getMemory().getMemoryArray();
        for (int i = 0; i < values.length; i++) {
            mem[address + i] = values[i];
        }
    }

    private static String error(String message, String line, int column) {
        StringBuilder caret = new StringBuilder();
        for (int i = 0; i < column + 2; i++) {
            caret.append(' ');
        }
        return "ERROR -- " + message + "\n  " + line + "\n" + caret + "^\n";
    }

    private static String resource(String name) throws IOException {
        try (InputStream in = MonitorEngineTest.class.getResourceAsStream(name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }

    // ---------------------------------------------------------------------------------
    // help
    // ---------------------------------------------------------------------------------

    @Test
    void helpListMatchesVice() throws IOException {
        assertEquals(resource("/monitor/vice-3.10-help-list.txt"), run("help"));
        assertEquals(run("help"), run("?"));
    }

    @Test
    void helpForOneCommand() {
        String help = run("help p");
        assertTrue(help.startsWith("\nSyntax: print <expression>\nAbbreviation: p\n"), help);
        assertEquals(help, run("help print"));
        assertEquals(help, run("Help P"));
        assertTrue(run("help q").startsWith("\nSyntax: quit \nAbbreviation: q\n"));
        assertTrue(run("help >").startsWith("\nSyntax: > [<address>] <data_list>\n"));
        assertTrue(run("help registers").startsWith(
                "\nSyntax: registers [<reg_name> = <number> [, <reg_name> = <number>]*]\nAbbreviation: r\n"));
        assertEquals(run("help help"), run("help ?"));
        assertOutput("\nNo help available for 'nonexistent'. Use \"help\" to get the full list.\n",
                "help nonexistent");
    }

    @Test
    void unimplementedCommandsSaySo() {
        assertOutput("The profile command is not implemented in jvic yet.\n", "profile");
        assertOutput("The list command is not implemented in jvic yet.\n", "list");
    }

    // ---------------------------------------------------------------------------------
    // print and expressions
    // ---------------------------------------------------------------------------------

    @Test
    void printEvaluatesLikeVice() {
        String[][] cases = {
            { "p 10", "16" }, { "p $10", "16" }, { "p +10", "10" }, { "p %101", "5" }, { "p &17", "15" },
            { "p 3-5", "-2" }, { "p 7/2", "3" }, { "p a", "10" }, { "p 2*3", "6" }, { "p 2 * 3", "6" },
            { "p 2 - 3 * 4", "-10" }, { "p 2*3-1", "5" }, { "p 10-1-1", "14" }, { "p 2*(3-1)", "4" },
            { "p 0-1", "-1" }, { "p (5)", "5" }, { "p $ff*$100", "65280" }, { "p 1234567", "19088743" },
            { "p 2 + 3", "5" }, { "p (2 + 3) * 4", "20" }, { "p 10 - +3", "13" }, { "p 2 + +3", "5" },
            { "p 2 +a", "12" }, { "p ( 3 )", "3" }, { "p 3 *4", "12" }, { "P 3", "3" }, { "p 4/2*3", "6" },
            { "p 8/2/2", "2" },
        };
        for (String[] c : cases) {
            assertOutput("\t" + c[1] + "\n", c[0]);
        }
    }

    @Test
    void expressionErrorsMatchVice() {
        String wrong = MonitorException.WRONG_SYNTAX;
        String unexpected = MonitorException.UNEXPECTED_TOKEN;
        String paren = MonitorException.PAREN_EXPECTED;
        Object[][] cases = {
            { "p 2+3*4", unexpected, 3 }, { "p (2+3)*4", paren, 4 }, { "p ffff+1", unexpected, 6 },
            { "p 5==5", unexpected, 3 }, { "p 5!=5", unexpected, 3 }, { "p 6&3", unexpected, 3 },
            { "p 6|3", unexpected, 3 }, { "p 1&&0", unexpected, 3 }, { "p 3<4", unexpected, 3 },
            { "p pc", wrong, 2 }, { "p", wrong, 1 }, { "p -5", wrong, 2 }, { "p $", wrong, 2 },
            { "p %2", wrong, 2 }, { "p &8", wrong, 2 }, { "p +a", wrong, 2 }, { "p 0x10", unexpected, 3 },
            { "p .", wrong, 2 }, { "p (1", paren, 4 }, { "p 1)", unexpected, 3 }, { "p 1 2", unexpected, 4 },
            { "p foo", unexpected, 3 }, { "~ -1", wrong, 2 }, { "p 7fffffff", wrong, 2 },
            { "p 0 - 7fffffff - 2", unexpected, 6 },
        };
        for (Object[] c : cases) {
            String line = (String) c[0];
            assertOutput(error((String) c[1], line, (Integer) c[2]), line);
        }
    }

    @Test
    void divisionByZeroIsAnError() {
        // VICE 3.10 silently prints 1. jvic reports it.
        assertOutput(error("Division by zero.", "p 7/0", 3), "p 7/0");
    }

    @Test
    void convertMatchesVice() {
        assertOutput("+4660\n$1234\n0011064\n%00010010 00110100\n", "~ 1234");
        assertOutput("+0\n$00\n0000\n%00000000\n", "~ 0");
        assertOutput("+65535\n$ffff\n0177777\n%11111111 11111111\n", "~ ffff");
        assertOutput("+65536\n$10000\n0200000\n%00000000 00000000\n", "~ 10000");
        assertOutput("+1193046\n$123456\n04432126\n%00110100 01010110\n", "~ 123456");
        assertOutput("+2\n$02\n0002\n%00000010\n", "~ 1 + 1");
    }

    // ---------------------------------------------------------------------------------
    // Monitor state commands
    // ---------------------------------------------------------------------------------

    @Test
    void radix() {
        assertOutput("Default radix is Hexadecimal\n", "radix");
        assertOutput("\t10\n+10\n$0a\n0012\n%00001010\n", "radix d", "p 10", "~ 10");
        assertOutput("Default radix is Decimal\n", "rad");
        // VICE 3.10 reads octal input as binary here (p 10 gives 2); jvic reads octal.
        assertOutput("Default radix is Octal\n\t8\n", "radix o", "radix", "p 10");
        assertOutput("\t5\n", "radix b", "p 101");
        assertOutput("Default radix is Hexadecimal\n", "radix H", "radix");
        assertOutput(error(MonitorException.UNEXPECTED_TOKEN, "radix x", 6), "radix x");
        assertOutput(error(MonitorException.UNEXPECTED_TOKEN, "radix dd", 7), "radix dd");
    }

    @Test
    void sidefx() {
        assertOutput("I/O side effects are disabled\nI/O side effects are enabled\nI/O side effects are disabled\n",
                "sfx", "sfx on", "sfx", "sfx toggle", "sidefx");
        assertOutput(error(MonitorException.UNEXPECTED_TOKEN, "sfx foo", 4), "sfx foo");
    }

    @Test
    void device() {
        assertOutput("Setting default device to `Disk8'\n", "dev 8:");
        assertTrue(engine.getPrompt().startsWith("(8:$"));
        assertOutput("Setting default device to `Computer'\n", "device");
        assertOutput("Setting default device to `Computer'\n", "dev c:");
        assertEquals("(C:$fd22) ", engine.getPrompt());
        assertOutput(error(MonitorException.UNEXPECTED_TOKEN, "dev 8", 4), "dev 8");
        assertOutput(error(MonitorException.UNEXPECTED_TOKEN, "dev c", 4), "dev c");
        assertOutput(error("Drive 9: is not emulated in jvic.", "dev 9:", 4), "dev 9:");
    }

    @Test
    void sidefxOffDoesNotClearViaInterruptFlags() {
        // Start VIA1's timer 1 with a short count and let it time out, setting IFR bit 6.
        run("> 9114 10 00");
        for (int i = 0; i < 100; i++) {
            h.machine.emulateCycle();
        }
        int ifr = 0x911D;
        assertTrue((h.machine.getVia1().peekMemory(ifr) & 0x40) != 0, "T1 timed out");

        // Reading T1C-L as the CPU would clears the flag; peeking doesn't.
        run("m 9114 9114", "d 9110 9120", "i 9110 911f", "mc 9110");
        assertTrue((h.machine.getVia1().peekMemory(ifr) & 0x40) != 0, "sidefx off: still set");
        run("sfx on", "m 9114 9114");
        assertEquals(0, h.machine.getVia1().peekMemory(ifr) & 0x40, "sidefx on: cleared");
    }

    @Test
    void peeksDoNotChangeTheOpenBusValue() {
        h.getMemory().setLastBusData(0x5A);
        run("m 9000 900f", "m 0 ff", "m 9400 94ff", "m 9110 913f", "d c000 c100");
        assertEquals(0x5A, h.getMemory().getLastBusData());
    }

    @Test
    void viaPeekMatchesReadWithoutSideEffects() {
        // Every VIA register peeks as readMemory reads it.
        for (int register = 0; register < 16; register++) {
            int address = 0x9120 + register;
            int peeked = h.machine.getVia2().peekMemory(address);
            assertEquals(h.machine.getVia2().readMemory(address), peeked, "register " + register);
        }
    }

    @Test
    void warpMatchesVice() {
        assertOutput("Warp mode is off.\nWarp mode is on.\nWarp mode is off.\n",
                "warp", "warp on", "warp", "warp toggle", "warp", "warp off");
        assertOutput("", "warp toggle");
        assertTrue(host.warp);
        assertOutput(error(MonitorException.UNEXPECTED_TOKEN, "warp foo", 5), "warp foo");
    }

    @Test
    void commentsAndBlankLinesDoNothing() {
        assertOutput("", "; this is a comment", "", "   ");
    }

    @Test
    void unknownCommandIsAWrongSyntaxErrorAtTheEnd() {
        assertOutput(error(MonitorException.WRONG_SYNTAX, "foo", 3), "foo");
    }

    @Test
    void exitResumesAndQuitAsksTheHost() {
        Debugger debugger = h.machine.getDebugger();
        debugger.requestStop();
        h.machine.update();
        h.machine.update();
        assertTrue(debugger.isStopped());

        assertOutput(error(MonitorException.UNEXPECTED_TOKEN, "x 1", 2), "x 1");
        assertTrue(debugger.isStopped());
        assertOutput("", "x");
        assertFalse(debugger.isStopped());
        assertEquals(1, host.resumes);

        assertOutput("", "q");
        assertEquals(1, host.quits);
    }

    // ---------------------------------------------------------------------------------
    // Machine state commands
    // ---------------------------------------------------------------------------------

    @Test
    void bankAndCpu() {
        assertOutput("Available banks (some may be equivalent to others):\n*default \t*cpu \t\n", "bank");
        assertOutput("", "bank cpu");
        assertOutput("Unknown bank name `ram'\n", "bank ram");
        assertOutput("Available banks (some may be equivalent to others):\n*default \t*cpu \t\n", "bank c:");
        assertOutput("Banks not available in this memspace\n", "bank 8:");
        assertOutput("Banks not available in this memspace\n", "bank 8: cpu");

        String cpus = "This device (Computer) supports the following CPU types: 6502\n";
        assertOutput(cpus, "cpu");
        assertOutput("", "cpu 6502");
        assertOutput("Unknown CPU type `z80'\n" + cpus, "cpu z80");
    }

    private String registerLine(String body) {
        String raster = String.format("%03d %03d", h.machine.getVic().getRasterLine(),
                h.machine.getVic().getRasterCycle());
        return "  ADDR A  X  Y  SP 00 01 NV-BDIZC LIN CYC  STOPWATCH\n"
                + ".;" + body + " " + raster + " " + String.format("%10d", h.machine.getCpu().getTotalCycles())
                + "\n";
    }

    @Test
    void registersDisplayAndAssignment() {
        assertOutput(registerLine("fd22 00 00 00 ff 00 00 00100100"), "r");
        assertOutput("", "r a=12", "r x=34, y=56", "r sp=1fd");
        assertOutput(registerLine("fd22 12 34 56 fd 00 00 00100100"), "r");
        assertOutput("", "r a=1ff", "r a = 3", "r fl=0");
        assertOutput(registerLine("fd22 03 34 56 fd 00 00 00100000"), "r");
        // jvic's CPU doesn't hold the B flag (it only exists on the stack), so FL=FF
        // shows B clear, where VICE shows 11111111.
        assertOutput("", "r fl=ff");
        assertOutput(registerLine("fd22 03 34 56 fd 00 00 11101111"), "r");
        poke(0x00, 0xAB, 0xCD);
        assertTrue(run("r").contains(" fd ab cd "));
    }

    @Test
    void registerErrorsMatchVice() {
        assertOutput(error(MonitorException.INVALID_REGISTER, "r q=1", 2), "r q=1");
        assertOutput(error(MonitorException.UNEXPECTED_TOKEN, "r a=", 4), "r a=");
        assertOutput(error(MonitorException.UNEXPECTED_TOKEN, "r a=1 x=2", 6), "r a=1 x=2");
        assertOutput(error(MonitorException.UNEXPECTED_TOKEN, "r a=12,", 7), "r a=12,");
        assertOutput(error(MonitorException.UNEXPECTED_TOKEN, "r 8:", 2), "r 8:");
        assertEquals(0, h.machine.getCpu().getAccumulator(), "nothing applied after an error");
    }

    @Test
    void settingPcRedispatches() {
        poke(0x1234, 0xA9, 0x42); // LDA #$42
        String prompt = engine.getPrompt();
        assertOutput("", "r pc=1234");
        assertTrue(run("r").contains(".;1234 "));
        assertEquals(0xA9, h.machine.getCpu().getInstructionRegister());
        // As in xvic, the prompt shows the dot address, which r doesn't move.
        assertEquals(prompt, engine.getPrompt());
    }

    @Test
    void driveRegistersHaveNoRasterColumns() {
        run("dev 8:");
        String r = run("r");
        assertTrue(r.startsWith("  ADDR A  X  Y  SP 00 01 NV-BDIZC  STOPWATCH\n.;"), r);
        assertTrue(r.endsWith("          0\n"), r);
    }

    // ---------------------------------------------------------------------------------
    // Memory commands
    // ---------------------------------------------------------------------------------

    @Test
    void writeAndMemHex() {
        assertOutput("", "> 1000 01 02 03 \"AB\" 41");
        poke(0x1006, 0xFF, 0x00, 0xFF, 0x00, 0xFF, 0x00, 0xFF, 0x00, 0xFF, 0x00);
        assertOutput(">C:1000  01 02 03 41  42 41 ff 00  ff 00 ff 00  ff 00 ff 00   ...ABA..........\n",
                "m 1000 100f");
        assertOutput(">C:1000  01 02 03 41  42 41 ff 00                             ...ABA..\n",
                "mem c:1000 1007");
        assertOutput(">C:1000  01 02                                                ..\n", "m H 1000 1001");
        assertOutput(">C:1000  01                                                   .\n", "m 1000 1000");
    }

    @Test
    void memOtherFormatsMatchVice() {
        poke(0x1000, 1, 2, 3, 65);
        assertOutput(">C:1000  00000001 00000010 00000011 01000001   ...A\n", "m B 1000 1003");
        assertOutput(">C:1000    1   2   3  65                    ...A\n", "m D 1000 1003");
        poke(0x1000, 0xFF, 0, 0xFF, 0, 0xFF, 0, 0xFF, 0, 0xFF, 0, 0xFF, 0, 0xFF, 0, 0xFF, 0, 0xFF);
        assertOutput(">C:1000  377 000 377 000  377 000 377 000   ........\n"
                + ">C:1008  377 000 377 000  377 000 377 000   ........\n"
                + ">C:1010  377                                .\n", "m O 1000 1010");
        assertOutput(">C:1000  11111111 00000000 11111111 00000000   ....\n"
                + ">C:1004  11111111 00000000 11111111 00000000   ....\n"
                + ">C:1008  11111111                              .\n", "m B 1000 1008");
        poke(0x0000, 0xFF, 0, 0xFF, 0, 0xFF, 0, 0xFF, 0, 0xFF, 0, 0xFF);
        assertOutput(">C:0000  255   0 255   0  255   0 255   0   ........\n"
                + ">C:0008  255   0 255                        ...\n", "radix d", "m 0 10");
        assertOutput(">C:0000  11111111 00000000 11111111            ...\n", "radix b", "m 0 10");
    }

    @Test
    void memDefaultLengthAndDotAddress() {
        String out = run("m 0 3f");
        assertEquals(4, out.split("\n").length);
        out = run("m");
        String[] lines = out.split("\n");
        assertEquals(MonitorEngine.DEFAULT_MEM_LINES, lines.length);
        assertTrue(lines[0].startsWith(">C:0040 "));
        assertTrue(lines[21].startsWith(">C:0190 "));
        assertTrue(run("m 1000").split("\n")[21].startsWith(">C:1150 "));
        assertTrue(run("m").startsWith(">C:1160 "));
    }

    @Test
    void memStartsAtThePcWhenTheMonitorIsEntered() {
        run("m 1000 1000");
        Debugger debugger = h.machine.getDebugger();
        debugger.requestStop();
        while (!debugger.isStopped()) {
            h.machine.update();
        }
        String pc = Format.hex4(engine.getMainSpace().getDisplayPc());
        assertTrue(run("m").startsWith(">C:" + pc + " "));
    }

    @Test
    void memWrapsAroundTheTopOfMemory() {
        String[] lines = run("m ffff 10").split("\n");
        assertEquals(2, lines.length);
        assertTrue(lines[0].startsWith(">C:ffff "));
        assertTrue(lines[1].startsWith(">C:000f  00 00 "));
    }

    @Test
    void memOnTheDrive() {
        assertOutput(">8:0000  00 00 00 00  00 00 00 00  00 00 00 00  00 00 00 00   ................\n", "m 8:0 f");
        run("dev 8:");
        assertOutput(">8:0000  00 00 00 00                                          ....\n", "m 0 3");
    }

    @Test
    void writesToRomAreIgnored() {
        assertOutput(">C:c000  78 e3 67 e4                                          x.g.\n", "> c000 99", "m c000 c003");
    }

    @Test
    void memErrorsMatchVice() {
        String unexpected = MonitorException.UNEXPECTED_TOKEN;
        assertOutput(error(unexpected, "m b 1000 1003", 9), "m b 1000 1003");
        assertOutput(error(unexpected, "m 1000 1003 1004", 12), "m 1000 1003 1004");
        assertOutput(error(unexpected, "m D", 3), "m D");
        assertOutput(error(unexpected, "m Z 0 1", 2), "m Z 0 1");
        assertOutput(error(unexpected, "m 1000 + 2", 7), "m 1000 + 2");
        assertOutput(error(MonitorException.WRONG_SYNTAX, "> 3000", 6), "> 3000");
    }

    @Test
    void writingOverTheStoppedInstructionRedispatches() {
        Debugger debugger = h.machine.getDebugger();
        debugger.requestStop();
        while (!debugger.isStopped()) {
            h.machine.update();
        }
        poke(0x1000, 0xEA); // NOP
        String prompt = engine.getPrompt();
        run("r pc=1000");
        assertEquals(0xEA, h.machine.getCpu().getInstructionRegister());
        run("> 1000 a9 07"); // LDA #$07
        assertEquals(0xA9, h.machine.getCpu().getInstructionRegister());
        // As in xvic, neither r nor > moves the dot address that the prompt shows.
        assertEquals(prompt, engine.getPrompt());
    }
}
