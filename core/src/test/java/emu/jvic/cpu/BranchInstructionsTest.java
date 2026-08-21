package emu.jvic.cpu;

import emu.jvic.testutil.CpuTestHarness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * BPL / BMI / BVC / BVS / BCC / BCS / BNE / BEQ. All are relative branches: 2 bytes,
 * signed 8-bit offset relative to the address of the FOLLOWING instruction. Cycle
 * cost: 2 if not taken, 3 if taken within the same page, 4 if taken across a page
 * boundary.
 *
 * <p>NOTE on the expected PC values below: {@link CpuTestHarness#runInstruction()}
 * runs until the CPU has fetched the opcode of the FOLLOWING instruction (this is
 * how the CPU pipelines fetch/execute; see the harness Javadoc), so by the time it
 * returns, PC has already been advanced one further byte past wherever the branch
 * actually landed. Expected PCs are therefore "branch target + 1", not the target
 * itself.
 */
public class BranchInstructionsTest {

    private CpuTestHarness h;

    @BeforeEach
    void setUp() {
        h = new CpuTestHarness();
    }

    @Test
    void bplTakenWhenNotNegative() {
        h.prepareInstruction(0x0300, 0x10, 0x05); // BPL +5, target = 0x0300+2+5 = 0x0307
        h.cpu.setNegativeResultFlag(false);
        long cycles = h.runInstruction();
        assertEquals(0x0308, h.cpu.getProgramCounter());
        assertEquals(3, cycles);
    }

    @Test
    void bplNotTakenWhenNegative() {
        h.prepareInstruction(0x0300, 0x10, 0x05);
        h.cpu.setNegativeResultFlag(true);
        long cycles = h.runInstruction();
        assertEquals(0x0303, h.cpu.getProgramCounter());
        assertEquals(2, cycles);
    }

    @Test
    void bmiTakenWhenNegative() {
        h.prepareInstruction(0x0300, 0x30, 0x05);
        h.cpu.setNegativeResultFlag(true);
        h.runInstruction();
        assertEquals(0x0308, h.cpu.getProgramCounter());
    }

    @Test
    void bvcTakenWhenOverflowClear() {
        h.prepareInstruction(0x0300, 0x50, 0x05);
        h.cpu.setOverflowFlag(false);
        h.runInstruction();
        assertEquals(0x0308, h.cpu.getProgramCounter());
    }

    @Test
    void bvsTakenWhenOverflowSet() {
        h.prepareInstruction(0x0300, 0x70, 0x05);
        h.cpu.setOverflowFlag(true);
        h.runInstruction();
        assertEquals(0x0308, h.cpu.getProgramCounter());
    }

    @Test
    void bccTakenWhenCarryClear() {
        h.prepareInstruction(0x0300, 0x90, 0x05);
        h.cpu.setCarryFlag(false);
        h.runInstruction();
        assertEquals(0x0308, h.cpu.getProgramCounter());
    }

    @Test
    void bcsTakenWhenCarrySet() {
        h.prepareInstruction(0x0300, 0xB0, 0x05);
        h.cpu.setCarryFlag(true);
        h.runInstruction();
        assertEquals(0x0308, h.cpu.getProgramCounter());
    }

    @Test
    void bneTakenWhenZeroClear() {
        h.prepareInstruction(0x0300, 0xD0, 0x05);
        h.cpu.setZeroResultFlag(false);
        h.runInstruction();
        assertEquals(0x0308, h.cpu.getProgramCounter());
    }

    @Test
    void beqNotTakenWhenZeroClear() {
        h.prepareInstruction(0x0300, 0xF0, 0x05);
        h.cpu.setZeroResultFlag(false);
        long cycles = h.runInstruction();
        assertEquals(0x0303, h.cpu.getProgramCounter());
        assertEquals(2, cycles);
    }

    @Test
    void beqTakenWhenZeroSet() {
        h.prepareInstruction(0x0300, 0xF0, 0x05);
        h.cpu.setZeroResultFlag(true);
        long cycles = h.runInstruction();
        assertEquals(0x0308, h.cpu.getProgramCounter());
        assertEquals(3, cycles);
    }

    @Test
    void beqTakenBackwardsBranchSamePage() {
        h.prepareInstruction(0x0310, 0xF0, 0xFB); // offset -5
        h.cpu.setZeroResultFlag(true);
        long cycles = h.runInstruction();
        // Target = (0x0310 + 2) - 5 = 0x030D, same page as 0x0312 -> 3 cycles.
        assertEquals(0x030E, h.cpu.getProgramCounter());
        assertEquals(3, cycles);
    }

    @Test
    void beqTakenBackwardsBranchAcrossPageBoundaryCostsFourCycles() {
        h.prepareInstruction(0x0300, 0xF0, 0xFB); // offset -5
        h.cpu.setZeroResultFlag(true);
        long cycles = h.runInstruction();
        // Target = (0x0300 + 2) - 5 = 0x02FD, DIFFERENT page from 0x0302 -> 4 cycles.
        assertEquals(0x02FE, h.cpu.getProgramCounter());
        assertEquals(4, cycles);
    }

    @Test
    void beqTakenAcrossPageBoundaryCostsFourCycles() {
        h.prepareInstruction(0x02F0, 0xF0, 0x20); // BEQ +$20 from $02F0 -> target $0312 (crosses page)
        h.cpu.setZeroResultFlag(true);
        long cycles = h.runInstruction();
        assertEquals(0x0313, h.cpu.getProgramCounter());
        assertEquals(4, cycles);
    }

    @Test
    void beqNotTakenNeverCostsExtraEvenNearPageBoundary() {
        h.prepareInstruction(0x02F0, 0xF0, 0x20);
        h.cpu.setZeroResultFlag(false);
        long cycles = h.runInstruction();
        assertEquals(2, cycles);
    }
}
