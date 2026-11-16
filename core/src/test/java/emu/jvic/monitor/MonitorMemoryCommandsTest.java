package emu.jvic.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import emu.jvic.MachineType;
import emu.jvic.memory.RamType;
import emu.jvic.testutil.MachineTestHarness;

/**
 * Tests for disass and the memory commands c, f, h, t, i, ii and mc. Expected output
 * comes from xvic 3.10 (PAL) running the same commands, with the same ROMs, except
 * where noted (jvic's RAM starts zeroed; jvic's character and text columns show '.'
 * for bytes with no printable equivalent).
 */
public class MonitorMemoryCommandsTest {

    private static final int HEX = 62;

    private MachineTestHarness h;
    private MonitorEngine engine;

    @BeforeEach
    void setUp() {
        h = MachineTestHarness.withRealRoms(MachineType.PAL, RamType.RAM_UNEXPANDED);
        engine = new MonitorEngine(h.machine, new MonitorHost() {
            @Override
            public void resume() {
            }

            @Override
            public void quit() {
            }

            @Override
            public void print(String text) {
            }

            @Override
            public boolean isWarp() {
                return false;
            }

            @Override
            public void setWarp(boolean warp) {
            }
        });
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
            mem[(address + i) & 0xFFFF] = values[i];
        }
    }

    private static String resource(String name) throws IOException {
        try (InputStream in = MonitorMemoryCommandsTest.class.getResourceAsStream(name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }

    private static String error(String message, String line, int column) {
        return new MonitorException(message, column).format(line);
    }

    private static String line(String values, String chars) {
        return Format.padRight(values, HEX) + chars + "\n";
    }

    // ---------------------------------------------------------------------------------
    // disass
    // ---------------------------------------------------------------------------------

    @Test
    void allOpcodesDisassembleLikeVice() throws IOException {
        StringBuilder out = new StringBuilder();
        for (int op = 0; op < 256; op++) {
            int address = 0x1000 + (op * 4);
            poke(address, op, 0x34, 0x12, 0x00);
        }
        for (int op = 0; op < 256; op++) {
            String a = Format.hex4(0x1000 + (op * 4));
            out.append(run("d " + a + " " + a));
        }
        String[] expected = resource("/monitor/vice-3.10-disass-all-opcodes.txt").split("\n");
        String[] actual = out.toString().split("\n");
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i], "opcode $" + Format.hex2(i));
        }
    }

    @Test
    void opcodeLengthsMatchTheCpu() {
        // Cross-check the table against jvic's own CPU decode: an instruction's length
        // is how far the PC moves for a non-jump. Spot-check by mode.
        assertEquals(1, Opcodes6502.length(0x00)); // BRK, as VICE shows it
        assertEquals(3, Opcodes6502.length(0x20)); // JSR
        assertEquals(2, Opcodes6502.length(0x10)); // BPL
        assertEquals(3, Opcodes6502.length(0x6C)); // JMP ()
        assertEquals(2, Opcodes6502.length(0xB1)); // LDA (),Y
        assertEquals(1, Opcodes6502.length(0x0A)); // ASL A
    }

    @Test
    void sequentialDisassemblyMatchesVice() {
        for (int op = 0; op < 4; op++) {
            poke(0x1000 + (op * 4), op, 0x34, 0x12, 0x00);
        }
        assertOutput(".C:1000  00          BRK\n"
                + ".C:1001  34 12       NOOP $12,X\n"
                + ".C:1003  00          BRK\n"
                + ".C:1004  01 34       ORA ($34,X)\n"
                + ".C:1006  12          JAM\n"
                + ".C:1007  00          BRK\n"
                + ".C:1008  02          JAM\n"
                + ".C:1009  34 12       NOOP $12,X\n"
                + ".C:100b  00          BRK\n"
                + ".C:100c  03 34       SLO ($34,X)\n"
                + ".C:100e  12          JAM\n"
                + ".C:100f  00          BRK\n", "d 1000 100f");
    }

    @Test
    void romDisassemblyAndDefaultLengthMatchVice() throws IOException {
        assertEquals(resource("/monitor/vice-3.10-disass-c000-x3.txt"), run("d c000", "d", "d"));
        assertEquals(resource("/monitor/vice-3.10-disass-e000-x2.txt"), run("d e000", "d"));
        assertOutput(".C:fd1e  02          JAM\n"
                + ".C:fd1f  E6 AD       INC $AD\n"
                + ".C:fd21  60          RTS\n"
                + ".C:fd22  A2 FF       LDX #$FF\n"
                + ".C:fd24  78          SEI\n"
                + ".C:fd25  9A          TXS\n"
                + ".C:fd26  D8          CLD\n"
                + ".C:fd27  20 3F FD    JSR $FD3F\n"
                + ".C:fd2a  D0 03       BNE $FD2F\n", "d fd1e fd2a");
    }

    @Test
    void disassemblyWrapsAtTheTopOfMemory() {
        poke(0x0000, 0xFF, 0x00); // VICE's power-on pattern there
        assertOutput(".C:fffe  72          JAM\n.C:ffff  FF FF 00    ISB $00FF,X\n", "d fffe ffff");
    }

    @Test
    void driveDisassemblyMatchesVice() {
        // jvic's bundled DOS ROM differs from VICE's from $EAA4 (JMP $FF10 where VICE's
        // has STX $1803), so only the shared part is compared.
        assertOutput(".8:eaa0  78          SEI\n"
                + ".8:eaa1  D8          CLD\n"
                + ".8:eaa2  A2 FF       LDX #$FF\n", "d 8:eaa0 eaa3");
    }

    @Test
    void disassSharesTheDotAddressWithMem() {
        String out = run("m 0 1", "d");
        String[] lines = out.split("\n");
        assertEquals(1 + MemoryCommands.DEFAULT_DISASS_LINES, lines.length);
        assertTrue(lines[1].startsWith(".C:0002 "), lines[1]);
        run("d e000 e000");
        assertTrue(run("m").startsWith(">C:e003 "));
    }

    @Test
    void disassErrorsMatchVice() {
        assertOutput(error(MonitorException.UNEXPECTED_TOKEN, "d 1000 1000 1000", 12), "d 1000 1000 1000");
        assertOutput(error(MonitorException.UNEXPECTED_TOKEN, "d x", 2), "d x");
    }

    // ---------------------------------------------------------------------------------
    // fill, compare, move, hunt
    // ---------------------------------------------------------------------------------

    @Test
    void fillRepeatsTheData() {
        assertOutput(line(">C:1000  11 22 33 11  22 33 11 22  33 11 22 33  11 22 33 11", ".\"3.\"3.\"3.\"3.\"3.")
                + line(">C:1010  00 00 00 00  00 00 00 00  00 00 00 00  00 00 00 00", "................"),
                "f 1000 103f 00", "f 1000 100f 11 22 33", "m 1000 101f");
        assertOutput(line(">C:1010  48 49 48 49  00 00 00 00", "HIHI...."), "f 1010 1013 \"HI\"", "m 1010 1017");
    }

    @Test
    void compareListsDifferences() {
        run("> 1100 01 02 03 04 05 06 07 08", "> 1200 01 02 03 04 05 06 07 08");
        assertOutput("", "c 1100 1107 1200");
        run("> 1203 99", "> 1206 aa");
        String differences = "$1103 $1203: 04 99\n$1106 $1206: 07 aa\n";
        assertOutput(differences, "c 1100 1107 1200");
        assertOutput(differences, "c 1100 1107 c:1200");
        assertOutput("Invalid range.\n", "c 8:0 7 0");
    }

    @Test
    void moveHandlesOverlap() {
        run("> 1100 01 02 03 04 05 06 07 08");
        assertOutput(line(">C:1300  01 02 03 04  05 06 07 08", "........"), "t 1100 1107 1300", "m 1300 1307");
        // VICE shows "ff 00" after the 08, from its RAM pattern; jvic's RAM is zeroed.
        assertOutput(line(">C:1100  01 02 01 02  03 04 05 06  07 08 00 00", "............"),
                "t 1100 1107 1102", "m 1100 110b");
    }

    @Test
    void huntFindsDataWithWildcards() {
        run("> 1400 a9 01 a9 02 a9 01 85 10 a9 01");
        assertOutput("1400\n1404\n1408\n", "h 1400 140f a9 01");
        assertOutput("1400\n1402\n1404\n1408\n", "h 1400 140f a9 xx");
        assertOutput("", "h 1400 140f \"A\"");
        assertOutput("", "h 1400 140f 33");
        assertOutput("c007\n", "h c000 dfff \"BASIC\"");
        assertOutput("c0be\nc0e9\n", "h c000 c0ff 4c");
    }

    @Test
    void missingArgumentsAreWrongSyntaxLikeVice() {
        String wrong = MonitorException.WRONG_SYNTAX;
        assertOutput(error(wrong, "f 1000", 6), "f 1000");
        assertOutput(error(wrong, "f", 1), "f");
        assertOutput(error(wrong, "c 1100 1107", 11), "c 1100 1107");
        assertOutput(error(wrong, "t 1100", 6), "t 1100");
        assertOutput(error(wrong, "h 1400 140f", 11), "h 1400 140f");
    }

    // ---------------------------------------------------------------------------------
    // i, ii, mc
    // ---------------------------------------------------------------------------------

    @Test
    void textCommands() {
        run("> 1500 \"HELLO WORLD\" 0d 41 c1 01");
        // VICE writes the raw bytes; jvic converts them, with '.' for control codes.
        assertOutput(">C:1500  " + Format.padRight("HELLO WORLD.A....", 64) + "\n", "i 1500 1510");
        run("> 1600 08 05 0c 0c 0f 20 17 0f 12 0c 04 41 81 01");
        assertOutput("*C:1600  " + Format.padRight("HELLO WORLD.AA@@", 64) + "\n", "ii 1600 160f");

        String[] lines = run("i 1500").split("\n");
        assertEquals(MonitorEngine.DEFAULT_MEM_LINES, lines.length);
        assertTrue(lines[21].startsWith(">C:1a40  "));
        assertTrue(run("ii 1600").split("\n")[21].startsWith("*C:1b40  "));
    }

    @Test
    void memcharMatchesVice() throws IOException {
        assertEquals(resource("/monitor/vice-3.10-memchar-8000-8017.txt"), run("mc 8000 8017"));
        String one = run("mc 8000");
        assertEquals(9, one.split("\n", -1).length - 1, "8 rows and a blank line");
        assertTrue(run("mc").startsWith(">C:8008 "), "continues from the dot address");
        assertOutput(error(MonitorException.UNEXPECTED_TOKEN, "mc H 8000 8007", 3), "mc H 8000 8007");
        assertOutput(error(MonitorException.UNEXPECTED_TOKEN, "mc D 8000 8007", 3), "mc D 8000 8007");
    }
}
