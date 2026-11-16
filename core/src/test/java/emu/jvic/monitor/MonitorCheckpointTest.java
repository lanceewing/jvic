package emu.jvic.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import emu.jvic.MachineType;
import emu.jvic.debug.Debugger;
import emu.jvic.memory.RamType;
import emu.jvic.testutil.MachineTestHarness;

/**
 * Tests for checkpoints and stepping, driving the real KERNAL's reset code as the
 * VICE 3.10 captures did. Expected text comes from those captures. The raster
 * position in hit headers and the stopwatch column of state lines are removed before
 * comparing ({@link #normalise}), because jvic counts the reset sequence's cycles
 * differently from VICE; separate tests check those columns against jvic's own
 * counters.
 */
public class MonitorCheckpointTest {

    private MachineTestHarness h;
    private MonitorEngine engine;
    private Debugger debugger;
    private StringBuilder async;

    private static MachineTestHarness realRoms() {
        return MachineTestHarness.withRealRoms(MachineType.PAL, RamType.RAM_UNEXPANDED);
    }

    private void start(MachineTestHarness harness) {
        h = harness;
        async = new StringBuilder();
        engine = new MonitorEngine(h.machine, new MonitorHost() {
            @Override
            public void resume() {
            }

            @Override
            public void quit() {
            }

            @Override
            public void print(String text) {
                async.append(text);
            }

            @Override
            public boolean isWarp() {
                return false;
            }

            @Override
            public void setWarp(boolean warp) {
            }
        });
        debugger = h.machine.getDebugger();
    }

    @BeforeEach
    void setUp() {
        start(realRoms());
    }

    private String run(String... lines) {
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            engine.execute(line, out::append);
        }
        return out.toString();
    }

    /** Runs the machine until the debugger stops, and returns what was printed. */
    private String runUntilStopped() {
        async.setLength(0);
        for (int i = 0; (i < 2000) && !debugger.isStopped(); i++) {
            h.machine.update();
        }
        if (!debugger.isStopped()) {
            fail("never stopped; output: " + async);
        }
        return async.toString();
    }

    /** Executes a command that resumes the machine, then runs until it stops again. */
    private String resume(String command) {
        String direct = run(command);
        return direct + runUntilStopped();
    }

    private void poke(int address, int... values) {
        int[] mem = h.getMemory().getMemoryArray();
        for (int i = 0; i < values.length; i++) {
            mem[(address + i) & 0xFFFF] = values[i];
        }
    }

    /**
     * Removes the raster position from hit headers and the stopwatch from state lines.
     */
    private static String normalise(String text) {
        return text.replaceAll("\\) +\\d+/\\$[0-9a-f]{3}, +\\d+/\\$[0-9a-f]{2}\n", ")\n")
                .replaceAll("(?m)^(\\.[C8]:[0-9a-f]{4} .* - A:.*?) +\\d+$", "$1");
    }

    private static String error(String message, String line, int column) {
        return new MonitorException(message, column).format(line);
    }

    // ---------------------------------------------------------------------------------
    // Setting and listing
    // ---------------------------------------------------------------------------------

    @Test
    void settingAndListingMatchVice() {
        assertEquals("No breakpoints are set\n", run("bk"));
        String set = "BREAK: 1  C:$fd27  (Stop on exec)\n"
                + "WATCH: 2  C:$0281-$0282  (Stop on store)\n"
                + "WATCH: 3  C:$fd51  (Stop on load)\n"
                + "TRACE: 4  C:$fd44  (Trace exec)\n"
                + "BREAK: 5  C:$e000-$e010  (Stop on exec)\n"
                + "WATCH: 6  C:$9000  (Stop on load)\n"
                + "WATCH: 7  C:$9110  (Stop on load store)\n"
                + "TRACE: 8  C:$fd2f  (Trace load store exec)\n";
        assertEquals(set, run("bk fd27", "w store 0281 0282", "w load fd51", "tr exec fd44", "bk e000 e010",
                "bk load 9000", "watch 9110", "trace fd2f"));
        assertEquals(set, run("bk"));
        assertEquals(set, run("w"));
        assertEquals(set, run("until"));
        assertEquals("WATCH: 9  C:$1300  (Stop on load store exec)\nTRACE: 10  C:$1100  (Trace load)\n"
                + "BREAK: 11  8:$eaa0  (Stop on exec)\nWATCH: 12  8:$1800  (Stop on load store)\n",
                run("bk load store exec 1300", "trace load 1100", "bk 8:eaa0", "w 8:1800"));
    }

    @Test
    void enableDisableDeleteAndIgnoreMatchVice() {
        run("bk fd27", "w store 0281 0282", "bk fd44");
        assertEquals("BREAK: 1  C:$fd27  (Stop on exec)\n"
                + "WATCH: 2  C:$0281-$0282  (Stop on store) disabled\n"
                + "BREAK: 3  C:$fd44  (Stop on exec)\n", run("dis 2", "bk"));
        assertEquals("Will ignore the next 2 hits of checkpoint #2\n", run("en 2", "dis 3", "ignore 2 2"));
        assertEquals("Setting checkpoint 2 condition to: X == $82\n"
                + "BREAK: 1  C:$fd27  (Stop on exec)\n"
                + "WATCH: 2  C:$0281-$0282  (Stop on store)\n"
                + "\tCondition: X == $82\n"
                + "BREAK: 3  C:$fd44  (Stop on exec) disabled\n", run("cond 2 if X == $82", "bk"));
        assertEquals("#99 not a valid checkpoint\n#99 not a valid checkpoint\n", run("del 99", "dis 99"));
        assertEquals("Set all checkpoints to state: enabled\n", run("en"));
        assertEquals("", run("del 2"));
        assertEquals("Deleting all checkpoints\nNo breakpoints are set\n", run("del", "bk"));
        assertEquals("BREAK: 1  C:$1000  (Stop on exec)\n", run("bk 1000"), "numbering restarts after del");
    }

    @Test
    void checkpointNumbersAreDecimal() {
        for (int i = 1; i <= 17; i++) {
            run("bk " + Format.hex4(0x1000 + i));
        }
        run("del 10");
        assertFalse(run("bk").contains("BREAK: 10 "));
        assertTrue(run("bk").contains("BREAK: 16  C:$1010"));
        assertEquals(error(MonitorException.CHECKPOINT_NUMBER_EXPECTED, "dis b", 4), run("dis b"));
    }

    @Test
    void checkpointErrorsMatchVice() {
        run("bk 1000");
        assertEquals(error(MonitorException.UNEXPECTED_TOKEN, "bk exec", 7), run("bk exec"));
        assertEquals(error(MonitorException.CHECKPOINT_NUMBER_EXPECTED, "cond 1", 6), run("cond 1"));
        assertEquals(error(MonitorException.UNEXPECTED_TOKEN, "dis 1 2", 6), run("dis 1 2"));
        assertEquals(error(MonitorException.UNEXPECTED_TOKEN, "del 1 2", 6), run("del 1 2"));
        assertEquals(error(MonitorException.UNEXPECTED_TOKEN, "bk exec 1007 if .foo == 1", 16),
                run("bk exec 1007 if .foo == 1"));
        assertEquals(error(MonitorException.UNEXPECTED_TOKEN, "bk exec 1000 if @c:$0100 == $00", 16),
                run("bk exec 1000 if @c:$0100 == $00"));
        assertEquals(error(MonitorException.MISSING_OPERAND, "bk 1000 if A ==", 15), run("bk 1000 if A =="));
    }

    @Test
    void conditionsAreNormalisedLikeVice() {
        String[][] cases = {
            { "(A + 1) == 2", "( A + $01 ) == $02" }, { "A + 1 * 2 == 4", "A + $01 * $02 == $04" },
            { "A != 5 || X > 3", "A != $05 || X > $03" }, { "A", "A" }, { "A == 1 + 2", "A == $01 + $02" },
            { "CY >= 10", "CY >= $10" }, { "PC == $1000", "PC == $1000" }, { "SP < $f0", "SP < $f0" },
            { "FL & 2", "FL & $02" }, { "a == 1", "A == $01" }, { "A==1", "A == $01" },
            { "A == $00 && X == $10", "A == $00 && X == $10" },
        };
        for (String[] c : cases) {
            String out = run("bk 1000 if " + c[0]);
            assertTrue(out.endsWith(" condition to: " + c[1] + "\n"), c[0] + " -> " + out);
        }
        // jvic extensions: RL, which VICE 3.10 rejects, and a memspace prefix, which VICE drops.
        assertTrue(run("bk 1000 if RL == 0").endsWith(" condition to: RL == $00\n"));
        assertTrue(run("bk 1000 if 8:A == 1").endsWith(" condition to: 8:A == $01\n"));
    }

    // ---------------------------------------------------------------------------------
    // Hits, matching the VICE session through the KERNAL reset code
    // ---------------------------------------------------------------------------------

    @Test
    void hitsMatchVice() {
        run("bk fd27", "w store 0281 0282", "w load fd51", "tr exec fd44", "bk e000 e010", "bk load 9000",
                "watch 9110", "trace fd2f");

        assertEquals("#1 (Stop on  exec fd27)\n"
                + ".C:fd27  20 3F FD    JSR $FD3F      - A:00 X:FF Y:00 SP:ff N.-..I..\n",
                normalise(resume("x")));
        assertEquals("(C:$fd27) ", engine.getPrompt());

        // The tracepoint at fd44 prints, then the load watchpoint stops the machine,
        // reporting the LDA that made the access, with the registers it left.
        assertEquals("#4 (Trace  exec fd44)\n"
                + ".C:fd44  DD 03 A0    CMP $A003,X    - A:CD X:05 Y:00 SP:fd N.-..I..\n"
                + "#3 (Stop on  load fd51)\n"
                + ".C:fd41  BD 4C FD    LDA $FD4C,X    - A:CD X:05 Y:00 SP:fd N.-..I..\n",
                normalise(resume("x")));
        assertTrue(run("r").contains("\n.;fd44 cd 05 00 fd "));

        // The opcode fetch at fd2f isn't a load, so only the exec trace prints. The carry
        // flag here comes from a CMP of the empty cartridge area at $A003, so it depends
        // on the open bus value; two VICE runs gave different values, and jvic matches
        // the run that gave C set.
        assertEquals("#8 (Trace  exec fd2f)\n"
                + ".C:fd2f  20 8D FD    JSR $FD8D      - A:CD X:05 Y:00 SP:ff N.-..I.C\n"
                + "#2 (Stop on store 0281)\n"
                + ".C:fd92  9D 00 02    STA $0200,X    - A:00 X:81 Y:00 SP:fd N.-..I.C\n",
                normalise(resume("x")));
        assertEquals("#2 (Stop on store 0282)\n"
                + ".C:fd92  9D 00 02    STA $0200,X    - A:00 X:82 Y:00 SP:fd N.-..I.C\n",
                normalise(resume("x")));
    }

    @Test
    void conditionStepStopwatchAndUntilMatchVice() {
        run("bk fd27", "w store 0281 0282", "bk fd44");
        resume("x");
        run("dis 3", "cond 2 if X == $82");
        assertEquals("#2 (Stop on store 0282)\n"
                + ".C:fd92  9D 00 02    STA $0200,X    - A:00 X:82 Y:00 SP:fd N.-..I.C\n",
                normalise(resume("x")));
        assertTrue(run("r").contains("\n.;fd95 00 82 00 fd "));
        assertEquals("Stopwatch reset to 0.\nStopwatch:          0\n", run("sw reset", "sw"));
        assertEquals(".C:fd98  E8          INX            - A:00 X:82 Y:00 SP:fd N.-..I.C          5\n",
                resume("z"), "z shows the stopwatch after STA abs,X (5 cycles)");
        assertEquals("Stopwatch:          5\n", run("sw"));
    }

    @Test
    void untilStepNextAndReturnMatchVice() {
        run("bk fd27");
        resume("x");
        assertEquals("UNTIL: 2  C:$fd2f  (Stop on exec)\n#2 (Stop on  exec fd2f)\n"
                + ".C:fd2f  20 8D FD    JSR $FD8D      - A:CD X:05 Y:00 SP:ff N.-..I.C\n",
                normalise(resume("un fd2f")));
        assertEquals("BREAK: 1  C:$fd27  (Stop on exec)\n", run("bk"), "until's checkpoint is deleted");

        run("bk fd95");
        assertEquals(".C:fd8d  A9 00       LDA #$00       - A:CD X:05 Y:00 SP:fd N.-..I.C\n",
                normalise(resume("z")));
        assertEquals("#3 (Stop on  exec fd95)\n"
                + ".C:fd95  9D 00 03    STA $0300,X    - A:00 X:00 Y:00 SP:fd ..-..IZC\n",
                normalise(resume("x")));
        run("del");
        String[] expected = {
            ".C:fd98  E8          INX            - A:00 X:00 Y:00 SP:fd ..-..IZC\n",
            ".C:fd99  D0 F5       BNE $FD90      - A:00 X:01 Y:00 SP:fd ..-..I.C\n",
            ".C:fd90  95 00       STA $00,X      - A:00 X:01 Y:00 SP:fd ..-..I.C\n",
            ".C:fd92  9D 00 02    STA $0200,X    - A:00 X:01 Y:00 SP:fd ..-..I.C\n",
            ".C:fd95  9D 00 03    STA $0300,X    - A:00 X:01 Y:00 SP:fd ..-..I.C\n",
        };
        for (String line : expected) {
            assertEquals(line, normalise(resume("n")));
        }
        assertEquals("Stepping through the next 3 instruction(s).\n"
                + ".C:fd90  95 00       STA $00,X      - A:00 X:02 Y:00 SP:fd ..-..I.C\n",
                normalise(resume("z 3")));
        assertEquals("Nexting through the next 2 instruction(s).\n"
                + ".C:fd95  9D 00 03    STA $0300,X    - A:00 X:02 Y:00 SP:fd ..-..I.C\n",
                normalise(resume("n 2")));

        // Return from the RAM clearing routine to just after its JSR at fd2f.
        assertTrue(normalise(resume("ret")).startsWith(".C:fd32  20 52 FD    JSR $FD52      - A:"));
        assertEquals("(C:$fd32) ", engine.getPrompt());
    }

    @Test
    void hitColumnsUseJvicsRasterAndStopwatch() {
        run("bk fd27");
        String out = resume("x");
        int line = h.machine.getVic().getRasterLine();
        int cycle = h.machine.getVic().getRasterCycle();
        String expectedHeader = String.format("#1 (Stop on  exec fd27) %4d/$%03x, %3d/$%02x%n", line, line, cycle,
                cycle).replace("\r", "");
        assertTrue(out.startsWith(expectedHeader), out);
        assertTrue(out.endsWith(String.format(" %10d%n", h.machine.getCpu().getTotalCycles()).replace("\r", "")));
    }

    // ---------------------------------------------------------------------------------
    // Hook behaviour (plan section 11)
    // ---------------------------------------------------------------------------------

    /** A small program in RAM, with a recursive subroutine. */
    private void loadRecursion() {
        poke(0x1000, 0x20, 0x10, 0x10, // 1000 JSR $1010
                0xEA,                  // 1003 NOP
                0x4C, 0x04, 0x10);     // 1004 JMP $1004
        poke(0x1010, 0xCA,             // 1010 DEX
                0xF0, 0x03,            // 1011 BEQ $1016
                0x20, 0x10, 0x10,      // 1013 JSR $1010
                0x60);                 // 1016 RTS
    }

    private void stopNow() {
        debugger.requestStop();
        runUntilStopped();
    }

    @Test
    void nextStepsOverARecursiveSubroutine() {
        loadRecursion();
        stopNow();
        run("r pc=1000", "r x=3");
        assertTrue(resume("n").startsWith(".C:1003  EA          NOP "));
        assertEquals(0, h.machine.getCpu().getIndexRegisterX());
    }

    @Test
    void returnLeavesARecursiveSubroutineAtTheOuterLevel() {
        loadRecursion();
        stopNow();
        run("r pc=1000", "r x=3");
        assertTrue(resume("z").startsWith(".C:1010  CA          DEX "));
        assertTrue(resume("ret").startsWith(".C:1003  EA          NOP "));
        assertEquals(0, h.machine.getCpu().getIndexRegisterX());
    }

    @Test
    void breakpointOnTheReturnAddressStopsTheNext() {
        loadRecursion();
        stopNow();
        run("r pc=1000", "r x=3", "bk 1016");
        assertTrue(normalise(resume("n")).startsWith("#1 (Stop on  exec 1016)\n"));
    }

    /** The KERNAL's IRQ handler entry, reached through the $FFFE vector. */
    private static final int KERNAL_IRQ = 0xFF72;

    /**
     * Boots the real KERNAL to the READY prompt, where VIA2's timer 1 raises the 60 Hz
     * IRQ and the KERNAL's handler acknowledges it.
     */
    private void bootToReady() {
        for (int i = 0; i < 150; i++) {
            h.machine.update();
        }
        stopNow();
    }

    @Test
    void stepIntoAnInterruptStopsAtTheHandler() {
        bootToReady();
        String previous = "";
        for (int i = 0; i < 20000; i++) {
            String out = resume("z");
            if (out.startsWith(".C:ff72 ")) {
                assertFalse(previous.startsWith(".C:ff72 "));
                assertFalse(h.machine.getCpu().isInterruptDispatched(), "stopped in the handler, not before it");
                assertTrue(h.machine.getCpu().getInterruptDisableFlag(), "the IRQ sequence has run");
                return;
            }
            previous = out;
        }
        fail("never stepped into the IRQ handler");
    }

    @Test
    void execBreakpointNeverStopsWhereAnInterruptIsTaken() {
        bootToReady();
        // A breakpoint on every ROM address is hit at every boundary in ROM, including
        // those where the IRQ is taken, which must not stop.
        run("bk e000 ffff");
        boolean sawHandler = false;
        for (int i = 0; i < 5000; i++) {
            resume("x");
            assertFalse(h.machine.getCpu().isInterruptDispatched(), "hit " + i);
            sawHandler |= (engine.getMainSpace().getDisplayPc() == KERNAL_IRQ);
        }
        assertTrue(sawHandler, "the IRQ handler ran while stepping through the hits");
    }

    @Test
    void returnFromAnInterruptHandler() {
        bootToReady();
        run("bk ff72");
        resume("x");
        int sp = h.machine.getCpu().getStackPointer();
        run("del");
        String out = resume("ret");
        assertTrue(out.startsWith(".C:"), out);
        assertEquals((sp + 3) & 0xFF, h.machine.getCpu().getStackPointer(), "RTI pulled P and the return address");
    }

    @Test
    void watchpointSeesCpuStoresButNotVicFetches() {
        start(MachineTestHarness.withSyntheticRoms(MachineType.PAL, RamType.RAM_UNEXPANDED));
        run("w load 1e00 1fff"); // screen memory, which the VIC fetches every frame
        for (int i = 0; i < 20; i++) {
            h.machine.update();
        }
        assertFalse(debugger.isStopped());

        run("w store 9005");
        stopNow();
        poke(0x1000, 0xA9, 0xF0, 0x8D, 0x05, 0x90, 0x4C, 0x05, 0x10); // LDA #$F0; STA $9005; JMP $1005
        run("r pc=1000");
        String out = normalise(resume("x"));
        assertTrue(out.startsWith("#2 (Stop on store 9005)\n.C:1002  8D 05 90    STA $9005      - A:F0 "), out);
        assertEquals("(C:$1005) ", engine.getPrompt());
        assertEquals(0xF0, h.machine.getVic().readMemory(0x9005) & 0xF0);
    }

    @Test
    void monitorAccessesDontTriggerWatchpoints() {
        run("w 1000 10ff");
        stopNow();
        run("m 1000 10ff", "> 1000 01", "f 1000 10ff 00", "sfx on", "m 1000 1010");
        assertTrue(async.toString().isEmpty());
        assertFalse(debugger.isStopPending());
    }

    @Test
    void deletingAWatchpointRestoresTheChip() {
        Object original = h.getMemory().getMemoryMap()[0x9005];
        run("w 9005");
        assertFalse(original == h.getMemory().getMemoryMap()[0x9005]);
        run("del 1");
        assertTrue(original == h.getMemory().getMemoryMap()[0x9005]);
        assertFalse(h.machine.getCpu().isDebugHooksActive(), "no checkpoints, no hooks");
    }

    @Test
    void jamStopsOnceAndGotoRecovers() {
        stopNow();
        poke(0x1000, 0x02);                    // JAM
        poke(0x1100, 0xA2, 0x42, 0x02);        // LDX #$42; JAM
        run("r pc=1000");
        String out = resume("x");
        assertTrue(out.startsWith("JAM at C:$1000\n.C:1000  02          JAM "), out);
        assertTrue(run("z").startsWith(".C:1000  02          JAM "), "z on a jammed CPU doesn't run");
        assertTrue(resume("g 1100").startsWith("JAM at C:$1102\n"));
        assertEquals(0x42, h.machine.getCpu().getIndexRegisterX());
    }

    @Test
    void ignoreAndConditionSkipHits() {
        bootToReady();
        run("bk ff72");
        resume("x");
        long first = h.machine.getCpu().getTotalCycles();
        run("ignore 1 3");
        resume("x");
        long elapsed = h.machine.getCpu().getTotalCycles() - first;
        // The PAL KERNAL's IRQ comes every 18472 cycles (timer 1 latch $4826 + 2): 3 hits
        // are ignored, and the 4th stops.
        assertTrue(Math.abs(elapsed - (4 * 18472)) < 100, "elapsed " + elapsed);

        run("cond 1 if SP == $00");
        run("x");
        for (int i = 0; i < 20; i++) {
            h.machine.update();
        }
        assertFalse(debugger.isStopped(), "the condition is false");
        run("cond 1 if SP > $00");
        assertTrue(normalise(runUntilStopped()).startsWith("#1 (Stop on  exec ff72)\n"));
    }
}
