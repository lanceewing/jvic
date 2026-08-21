package emu.jvic.cpu;

import emu.jvic.testutil.CpuTestHarness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * NOP (0xEA), implied, 2 cycles, no register/flag/memory side effects beyond
 * advancing the program counter past its own opcode byte.
 */
public class NopInstructionsTest {

    private CpuTestHarness h;

    @BeforeEach
    void setUp() {
        h = new CpuTestHarness();
    }

    @Test
    void nopTakesTwoCyclesAndOnlyAdvancesProgramCounter() {
        h.prepareInstruction(0x0300, 0xEA);
        h.cpu.setAccumulator(0x12);
        h.cpu.setIndexRegisterX(0x34);
        h.cpu.setIndexRegisterY(0x56);
        h.cpu.setStackPointer(0x78);
        int statusBefore = h.cpu.getProcessorStatus();
        long cycles = h.runInstruction();

        assertEquals(0x12, h.cpu.getAccumulator());
        assertEquals(0x34, h.cpu.getIndexRegisterX());
        assertEquals(0x56, h.cpu.getIndexRegisterY());
        assertEquals(0x78, h.cpu.getStackPointer());
        assertEquals(statusBefore, h.cpu.getProcessorStatus());
        assertEquals(2, cycles);
    }
}
