package emu.jvic.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import emu.jvic.MachineType;
import emu.jvic.memory.RamType;
import emu.jvic.testutil.MachineTestHarness;

/**
 * Tests for dump and undump, the memory map and the profiler. Output layouts are
 * xvic 3.10's.
 */
public class MonitorPhase4CommandsTest {

    private static final class Host implements MonitorHost {
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
        }
    }

    private MachineTestHarness h;
    private MonitorEngine engine;
    private InMemoryMonitorFileSystem fs;

    private void setUp(RamType ramType) {
        h = MachineTestHarness.withSyntheticRoms(MachineType.PAL, ramType);
        engine = new MonitorEngine(h.machine, new Host());
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

    private void poke(int address, int... values) {
        int[] mem = h.getMemory().getMemoryArray();
        for (int i = 0; i < values.length; i++) {
            mem[address + i] = values[i];
        }
    }

    private void runUntilStopped() {
        for (int i = 0; (i < 400) && !h.machine.isDebuggerStopped(); i++) {
            h.machine.update();
        }
        assertTrue(h.machine.isDebuggerStopped(), "never stopped");
    }

    private void stopNow() {
        h.machine.getDebugger().requestStop();
        runUntilStopped();
    }

    // ---------------------------------------------------------------------------------
    // dump and undump
    // ---------------------------------------------------------------------------------

    @Test
    void undumpPutsTheMachineBackAsItWasDumped() {
        setUp(RamType.RAM_UNEXPANDED);
        stopNow();
        poke(0x1000, 0xA9, 0x05, 0xE8, 0x4C, 0x02, 0x10); // LDA #5; loop: INX; JMP loop
        run("r pc=1000", "break exec 1003", "x");
        runUntilStopped();
        String registers = run("r");
        String memory = run("m 1000 1005");
        assertEquals("", run("dump \"s.jvs\""));
        assertTrue(fs.read("s.jvs").length > 65536);

        run("> 1000 0 0 0", "r a=77", "del", "x");
        h.machine.update();
        stopNow();
        assertEquals("", run("undump \"s.jvs\""));
        assertEquals("(C:$1003) ", engine.getPrompt());
        // The same registers (including the PC shown at the boundary) and memory.
        assertEquals(registers, run("r"));
        assertEquals(memory, run("m 1000 1005"));
        assertEquals("Cannot open 'none.jvs'.\n", run("undump \"none.jvs\""));
        fs.write("bad.jvs", new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13 });
        assertEquals("Not a jvic save state.\n", run("undump \"bad.jvs\""));
    }

    @Test
    void undumpRefusesAStateFromADifferentlySetUpMachine() {
        setUp(RamType.RAM_8K);
        run("dump \"s.jvs\"");
        byte[] state = fs.read("s.jvs");
        setUp(RamType.RAM_UNEXPANDED);
        fs.write("s.jvs", state);
        assertEquals("The save state was made with different RAM expansions or cartridge; start jvic with the "
                + "same ones first.\n", run("undump \"s.jvs\""));
    }

    // ---------------------------------------------------------------------------------
    // The memory map
    // ---------------------------------------------------------------------------------

    @Test
    void theMemoryMapRecordsAccessesAsVice() throws IOException {
        setUp(RamType.RAM_UNEXPANDED);
        stopNow();
        // LDA $1100; STA $1101; STA $9113 (VIA1 DDRA); JMP $1000.
        poke(0x1000, 0xAD, 0x00, 0x11, 0x8D, 0x01, 0x11, 0x8D, 0x13, 0x91, 0x4C, 0x00, 0x10);
        String first = run("mmsh");
        assertTrue(first.startsWith("The memory map wasn't being recorded; it is now."), first);
        run("r pc=1000", "x");
        for (int i = 0; i < 3; i++) {
            h.machine.update();
        }
        stopNow();
        assertEquals("addr: IO  ROM RAM\n"
                + "1000: --- --- r-x\n"
                + "1001: --- --- r--\n"
                + "1002: --- --- r--\n", run("mmsh 7 1000 1002"));
        assertEquals("addr: IO  ROM RAM\n1100: --- --- r--\n1101: --- --- -w-\n", run("mmsh 6 1100 1101"));
        assertEquals("addr: IO  ROM RAM\n9113: -w- --- ---\n", run("mmsh 40 9110 911f"));
        // With a memspace on the first number, there is no mask.
        assertEquals("addr: IO  ROM RAM\n1000: --- --- r-x\n", run("mmsh c:1000 1000"));
        // Watchpoints still work while the memmap has a proxy on every address.
        run("watch store 1101", "x");
        runUntilStopped();
        run("del");
        assertEquals("", run("mmsave \"mm.png\" 2"));
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(fs.read("mm.png")));
        assertNotNull(image);
        assertEquals(256, image.getWidth());
        assertEquals("", run("mmzap"));
        assertEquals("addr: IO  ROM RAM\n", run("mmsh 7 1100 1101"));
        assertEquals("", run("mmzap off"));
        assertTrue(run("mmsh").startsWith("The memory map wasn't being recorded"));
    }

    // ---------------------------------------------------------------------------------
    // The profiler
    // ---------------------------------------------------------------------------------

    @Test
    void theProfilerAttributesCyclesToFunctions() {
        setUp(RamType.RAM_UNEXPANDED);
        stopNow();
        // main: JSR sub; JMP main. sub: LDX #10; loop: DEX; BNE loop; RTS.
        poke(0x1000, 0x20, 0x10, 0x10, 0x4C, 0x00, 0x10);
        poke(0x1010, 0xA2, 0x0A, 0xCA, 0xD0, 0xFD, 0x60);
        assertEquals("Profiling not started.\nUse \"help prof\" for more information.\n", run("prof"));
        run("al 1010 .sub", "r pc=1000");
        assertEquals("Profiling restarted.\n", run("prof on"));
        run("x");
        h.machine.update();
        h.machine.update();
        stopNow();
        assertEquals("Profiling running.\nUse \"help prof\" for more information.\n", run("prof"));
        assertEquals("Profiling stopped.\n", run("prof off"));
        String flat = run("prof flat 2");
        String[] lines = flat.split("\n");
        assertEquals("        Total      %          Self      %", lines[0]);
        assertEquals("------------- ------ ------------- ------", lines[1]);
        // The subroutine has most of the time; the caller's total includes it.
        assertTrue(lines[2].endsWith(" .sub" + Format.pad("", 36, ' ')), flat);
        assertTrue(lines[3].contains(" ROOT"), flat);
        String graph = run("prof graph depth 1");
        assertTrue(graph.contains("    [1] START"), graph);
        assertTrue(graph.contains("      [2] 1000 -> .sub"), graph);
        String function = run("prof func .sub");
        assertTrue(function.contains("Callers"), function);
        assertTrue(function.contains("     .sub"), function);
        String disass = run("prof disass .sub");
        assertTrue(disass.contains(".C:1012  CA          DEX"), disass);
        assertTrue(run("prof context 2").startsWith("[2] 1000 -> .sub\n"));
        run("prof clear .sub");
        assertTrue(run("prof flat 1").contains(" ROOT"));
    }

    @Test
    void profilerCountFormatsMatchVice() {
        assertEquals("      336,896", Profiler.count(336896));
        assertEquals("            0", Profiler.count(0));
        assertEquals(" 54.1%", Profiler.percent(336896, 622859));
        assertEquals("100.0%", Profiler.percent(5, 5));
        assertEquals("  nan%", Profiler.percent(0, 0));
    }
}
