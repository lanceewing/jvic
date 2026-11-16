package emu.jvic.monitor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import emu.jvic.MachineType;
import emu.jvic.debug.Debugger;
import emu.jvic.memory.RamType;
import emu.jvic.testutil.MachineTestHarness;

/**
 * Tests for labels, the assembler, command, dummy, io, screen, memsprite, backtrace,
 * cpuhistory, keybuf, reset and export. Expected output is from xvic 3.10 where the
 * command exists there, except where noted.
 */
public class MonitorPhase2CommandsTest {

    private static final class Host implements MonitorHost {
        final StringBuilder printed = new StringBuilder();

        @Override
        public void resume() {
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

    private void setUp() {
        setUp(RamType.RAM_UNEXPANDED);
    }

    private String run(String... lines) {
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            engine.execute(line, out::append);
        }
        return out.toString();
    }

    private void poke(int address, int... values) {
        int[] mem = h.getMemory().getMemoryArray();
        for (int i = 0; i < values.length; i++) {
            mem[address + i] = values[i];
        }
    }

    private void runUntilStopped() {
        for (int i = 0; (i < 200) && !h.machine.isDebuggerStopped(); i++) {
            h.machine.update();
        }
        assertTrue(h.machine.isDebuggerStopped(), "never stopped");
    }

    private void stopNow() {
        h.machine.getDebugger().requestStop();
        runUntilStopped();
    }

    // ---------------------------------------------------------------------------------
    // Labels
    // ---------------------------------------------------------------------------------

    @Test
    void labelsInDisassemblyAndAddressesMatchVice() {
        setUp();
        run("al 1000 .start", "al c:1003 .loop", "al 8:0500 .drv", "> 1000 4c 03 10 20 00 10 4c 00 10");
        assertEquals(".C:1000   .start:\n"
                + ".C:1000  4C 03 10    JMP .loop\n"
                + ".C:1003   .loop:\n"
                + ".C:1003  20 00 10    JSR .start\n"
                + ".C:1006  4C 00 10    JMP .start\n", run("d 1000 1008"));
        assertEquals(">C:1000  4c 03 10 20                                          L.. \n", run("m .start .loop"));
        assertEquals("$1000 .start\n$1003 .loop\n", run("shl"));
        assertEquals("$0500 .drv\n", run("shl 8:"));
        assertEquals("Symbol .nothere not found.\n", run("dl .nothere"));
        assertEquals("", run("dl .start"));
        assertEquals("$1003 .loop\n", run("shl c:"));
        assertEquals("", run("cl"));
        assertEquals("", run("shl"));
        assertEquals("$0500 .drv\n", run("shl 8:"));
    }

    @Test
    void theLastLabelAddedAtAnAddressIsShown() {
        setUp();
        run("al 1000 .start", "al 1000 .other");
        assertTrue(run("d 1000 1000").startsWith(".C:1000   .other:\n"));
        run("dl .other");
        assertTrue(run("d 1000 1000").startsWith(".C:1000   .start:\n"));
    }

    @Test
    void saveAndLoadLabels() {
        setUp();
        run("al 1003 .loop", "al 1000 .start", "al 8:0500 .drv");
        assertEquals("Saving symbol table to `/labels.txt'...\n", run("sl \"labels.txt\""));
        assertEquals("al C:1000 .start\nal C:1003 .loop\nal 8:0500 .drv\n",
                new String(fs.read("labels.txt"), StandardCharsets.ISO_8859_1));
        run("cl", "cl 8:");
        assertEquals("", run("ll \"labels.txt\""));
        assertEquals("$1000 .start\n$1003 .loop\n", run("shl"));
        assertEquals("$0500 .drv\n", run("shl 8:"));
        assertEquals("Cannot open 'none.txt'.\n", run("ll \"none.txt\""));
    }

    @Test
    void loadLabelsReadsAcmeAndCc65Files() {
        setUp();
        fs.write("acme.txt", "\tchrout = $ffd2 ; KERNAL\n  screen\t= 7680\n".getBytes(StandardCharsets.ISO_8859_1));
        fs.write("cc65.lbl", "al 00FFD2 .CHROUT\nal 001001 .__BASIC\n".getBytes(StandardCharsets.ISO_8859_1));
        assertEquals("", run("ll \"acme.txt\"", "ll \"cc65.lbl\""));
        assertEquals("$1001 .__BASIC\n$1e00 .screen\n$ffd2 .chrout\n$ffd2 .CHROUT\n", run("shl"));
    }

    // ---------------------------------------------------------------------------------
    // The assembler
    // ---------------------------------------------------------------------------------

    @Test
    void assemblyModeMatchesVice() {
        setUp();
        assertEquals("", run("a 1100 lda #$01"));
        assertEquals(".1102  ", engine.getPrompt());
        run("sta $0400,x", "inx", "bne $1102");
        assertEquals(".1108  ", engine.getPrompt());
        run("");
        assertEquals("(C:$1108) ", engine.getPrompt());
        assertEquals(".C:1100  A9 01       LDA #$01\n"
                + ".C:1102  9D 00 04    STA $0400,X\n"
                + ".C:1105  E8          INX\n"
                + ".C:1106  D0 FA       BNE $1102\n", run("d 1100 1107"));
    }

    @Test
    void severalInstructionsOnALineMatchVice() {
        setUp();
        assertEquals("", run("a 1200 lda ($fb),y : jmp ($fffc) : asl : lax $12 : bit $1003"));
        assertEquals("(C:$120b) ", engine.getPrompt());
        assertEquals(".C:1200  B1 FB       LDA ($FB),Y\n"
                + ".C:1202  6C FC FF    JMP ($FFFC)\n"
                + ".C:1205  0A          ASL A\n"
                + ".C:1206  A7 12       LAX $12\n"
                + ".C:1208  2C 03 10    BIT $1003\n", run("d 1200 120a"));
        assertEquals("", run("a 1400 shx $1234,y : anc #$0f : sbx #$01 : jam"));
        assertEquals(".C:1400  9E 34 12    SHX $1234,Y\n"
                + ".C:1403  0B 0F       ANC #$0F\n"
                + ".C:1405  CB 01       SBX #$01\n"
                + ".C:1407  02          JAM\n", run("d 1400 1407"));
    }

    @Test
    void assemblerErrorsMatchVice() {
        setUp();
        assertEquals("Branch offset too large.\n", run("a 1310 bvc $1300 : beq $1400"));
        assertEquals("(C:$1312) ", engine.getPrompt());
        // An invalid instruction alone still enters assembly mode, at the same address.
        assertEquals("Instruction not valid.\n", run("a 1300 nop #$01"));
        assertEquals(".1300  ", engine.getPrompt());
        // A syntax error in assembly mode leaves it.
        String error = run("lda $12,");
        assertTrue(error.startsWith("ERROR -- Wrong syntax:"), error);
        assertEquals("(C:$1300) ", engine.getPrompt());
    }

    @Test
    void assemblerAcceptsLabelsAndTheAccumulator() {
        setUp();
        run("al 1300 .print", "a 1320 jsr .print : rol a : rol $0a");
        assertEquals(".C:1320  20 00 13    JSR .print\n"
                + ".C:1323  2A          ROL A\n"
                + ".C:1324  26 0A       ROL $0A\n", run("d 1320 1325"));
    }

    @Test
    void everyOpcodeAssemblesBackToTheSameInstruction() {
        setUp();
        for (int opcode = 0; opcode < 256; opcode++) {
            int[] bytes = { opcode, 0x12, 0x34 };
            String text = Disassembler6502.instruction(0x2000, bytes);
            int[] assembled = Assembler6502.assemble(text, 0x2000, engine, engine.getMainSpace(), 0);
            int[] original = new int[Opcodes6502.length(opcode)];
            System.arraycopy(bytes, 0, original, 0, original.length);
            String again = Disassembler6502.instruction(0x2000, assembled);
            assertEquals(text, again, "opcode $" + Integer.toHexString(opcode));
            if (Opcodes6502.mode(opcode) != Opcodes6502.ZP || !text.startsWith("NOOP")) {
                assertEquals(original.length, assembled.length, text);
            }
        }
    }

    // ---------------------------------------------------------------------------------
    // command and dummy
    // ---------------------------------------------------------------------------------

    @Test
    void commandRunsWhenTheCheckpointStops() {
        setUp();
        assertEquals("#1 not a valid checkpoint\n", run("command 1 \"p 7\""));
        run("break exec e001");
        assertEquals("Setting checkpoint 1 command to: p 7\n", run("command 1 \"p 7\""));
        assertEquals("BREAK: 1  C:$e001  (Stop on exec)\n\tCommand: p 7\n", run("bk"));
        run("x");
        runUntilStopped();
        assertTrue(host.printed.toString().startsWith("#1 (Stop on  exec e001)"), host.printed.toString());
        assertTrue(engine.hasPendingScript());
        StringBuilder out = new StringBuilder();
        engine.continueScript(out::append);
        assertEquals("\t7\n", out.toString());
    }

    @Test
    void aCommandOfXResumesAfterTheHit() {
        setUp();
        run("break exec e001", "command 1 \"x\"", "x");
        runUntilStopped();
        engine.continueScript(text -> { });
        assertFalse(h.machine.isDebuggerStopped());
    }

    @Test
    void dummyReadsTriggerWatchpointsOnlyWithDummyOn() {
        setUp();
        stopNow();
        // LDX #1; LDA $10FF,X (dummy read of $1000 as the page is crossed); JMP *
        poke(0x1100, 0xA2, 0x01, 0xBD, 0xFF, 0x10, 0x4C, 0x05, 0x11);
        assertEquals("Checkpoints will not trigger on dummy accesses.\n", run("dummy"));
        run("watch load 1000", "r pc=1100", "x");
        for (int i = 0; i < 3; i++) {
            h.machine.update();
        }
        assertFalse(h.machine.isDebuggerStopped());

        stopNow();
        run("dummy on");
        assertEquals("Checkpoints will trigger on dummy accesses.\n", run("dummy"));
        run("r pc=1100", "x");
        runUntilStopped();
        assertEquals(Debugger.STOP_WATCH, h.machine.getDebugger().getStopReason());
        run("dummy toggle");
        assertEquals("Checkpoints will not trigger on dummy accesses.\n", run("dummy"));
    }

    // ---------------------------------------------------------------------------------
    // io, screen, memsprite
    // ---------------------------------------------------------------------------------

    @Test
    void ioListsTheChipsAsVice() {
        setUp();
        String io = run("io");
        assertTrue(io.startsWith("VIC:\n>C:9000  "), io);
        assertTrue(io.contains("\nVIA1:\n>C:9110  "), io);
        assertTrue(io.contains("\nVIA2:\n>C:9120  "), io);
        String via = run("io 9110");
        assertTrue(via.contains("\n\nPort A: "), via);
        assertTrue(via.contains("\nIRQ enable: "), via);
        assertTrue(via.endsWith("\n\nShift Register: 00\n"), via);
        assertTrue(run("io 9000").contains("\nMatrix: $"));
        assertEquals("", run("io 9400"));
        String drive = run("io 8:1c00");
        assertTrue(drive.startsWith("VIA2:\n>8:1c00  "), drive);
    }

    @Test
    void screenReadsTheVicsLayoutAsVice() {
        setUp();
        assertEquals("Displaying 0x0 screen at $8000:\n", run("sc"));
        // 22x23 at $1E00, as the KERNAL sets it up.
        poke(0x9002, 0x96);
        poke(0x9003, 0x2E);
        poke(0x9005, 0xF0);
        poke(0x1E00, 0x12, 0x05, 0x01, 0x04, 0x19, 0x2E);
        String screen = run("sc");
        // Screen code 0 is '@', as in VICE.
        assertTrue(screen.startsWith("Displaying 22x23 screen at $1e00:\n*C:1e00  ready.@@@@@@@@@@@@@@@@\n"
                + "*C:1e16  @@@@@@@@@@@@@@@@@@@@@@\n"), screen);
        assertEquals(24, screen.split("\n").length);
    }

    @Test
    void memspriteMatchesVice() {
        setUp();
        run("> 1000 4c 03 10 20 00 10");
        String sprite = run("ms 1000");
        assertTrue(sprite.startsWith(">C:1000 .#..##........##...#.... 4c 03 10\n"
                + ">C:1003 ..#................#.... 20 00 10\n"), sprite);
        assertEquals(22, sprite.split("\n", -1).length - 1);
        assertTrue(sprite.endsWith("\n\n"));
    }

    // ---------------------------------------------------------------------------------
    // backtrace, cpuhistory
    // ---------------------------------------------------------------------------------

    @Test
    void backtraceFindsJsrReturnAddresses() {
        setUp();
        stopNow();
        // JSR $1010 at $1000; at $1010, JSR $1020; at $1020, a loop.
        poke(0x1000, 0x20, 0x10, 0x10);
        poke(0x1010, 0x20, 0x20, 0x10);
        poke(0x1020, 0x4C, 0x20, 0x10);
        run("r pc=1000", "break exec 1020", "x");
        runUntilStopped();
        assertEquals("             PC        .C:1020   4C 20 10    JMP $1020\n"
                + "1010 -> 1020 [SP +  1] .C:1010   20 20 10    JSR $1020\n"
                + "1000 -> 1010 [SP +  3] .C:1000   20 10 10    JSR $1010\n", run("bt"));
    }

    @Test
    void cpuHistoryMatchesVicesLayout() {
        setUp();
        assertEquals("No CPU history has been recorded. Use `chis on' to record it.\n", run("chis"));
        stopNow();
        poke(0x1000, 0xA9, 0x0A, 0xA2, 0x05, 0x4C, 0x04, 0x10); // LDA #$0A; LDX #$05; JMP $1004
        run("chis on", "r pc=1000", "x");
        for (int i = 0; i < 3; i++) {
            h.machine.update();
        }
        stopNow();
        String history = run("chis 3");
        String[] lines = history.split("\n");
        assertEquals(3, lines.length, history);
        assertTrue(lines[2].startsWith(".C:1004  4C 04 10    JMP $1004      A:0a X:05 Y:00 SP:"), history);
        // Both memspaces, merged in cycle order. (The drive only runs with a disk in,
        // so it has no history here.)
        assertEquals(1, run("chis 1 c: 8:").split("\n").length);
        run("chis off");
        assertFalse(engine.isHistoryEnabled());
    }

    // ---------------------------------------------------------------------------------
    // keybuf, reset, export
    // ---------------------------------------------------------------------------------

    @Test
    void keybufFillsTheKeyboardBufferAsItEmpties() {
        setUp();
        run("keybuf \"print 1\\x0d\"");
        assertArrayEquals(new int[] { 0x50, 0x52, 0x49, 0x4E, 0x54, 0x20, 0x31, 0x0D }, keyboardBuffer(8));
        assertEquals(8, h.getMemory().getMemoryArray()[0xC6]);

        poke(0xC6, 0);
        run("keybuf abcdefghijkL");
        assertEquals(10, h.getMemory().getMemoryArray()[0xC6]);
        poke(0xC6, 0);
        engine.onFrame();
        assertEquals(2, h.getMemory().getMemoryArray()[0xC6]);
        assertArrayEquals(new int[] { 0x4B, 0xCC }, keyboardBuffer(2));
    }

    private int[] keyboardBuffer(int count) {
        int[] keys = new int[count];
        System.arraycopy(h.getMemory().getMemoryArray(), 0x277, keys, 0, count);
        return keys;
    }

    @Test
    void resetStopsAtTheResetRoutine() {
        setUp();
        stopNow();
        poke(0x9112, 0xFF); // VIA1 DDRB
        run("r pc=1000");
        run("reset");
        runUntilStopped();
        assertEquals("(C:$e000) ", engine.getPrompt());
        assertEquals(0, h.getMemory().getMemoryArray()[0x9112] & 0, "VIA");
        assertEquals(0, engine.getMainSpace().read(0x9112, false));

        poke(0x1000, 0x55);
        run("reset 1");
        runUntilStopped();
        assertEquals(0, h.getMemory().getMemoryArray()[0x1000]);
        assertEquals("Drive 9: is not emulated in jvic.\n", run("reset 9"));
        assertEquals("", run("reset 8"));
    }

    @Test
    void exportListsTheExpansionMemory() {
        setUp();
        assertEquals("No expansion port devices.\n", run("export"));
        setUp(RamType.RAM_35K);
        assertEquals("RAM: $0400-$0fff (3K expansion)\n"
                + "RAM: $2000-$3fff (block 1)\n"
                + "RAM: $4000-$5fff (block 2)\n"
                + "RAM: $6000-$7fff (block 3)\n"
                + "RAM: $a000-$bfff (block 5)\n", run("exp"));
    }
}
