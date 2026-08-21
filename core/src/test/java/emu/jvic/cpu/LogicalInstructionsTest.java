package emu.jvic.cpu;

import emu.jvic.testutil.CpuTestHarness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AND / ORA / EOR / BIT. The first three set N/Z from the accumulator result. BIT
 * is unusual: it computes Z from (A & M) but sets N and V directly from bits 7 and 6
 * of the memory operand, regardless of A's value in those bits.
 */
public class LogicalInstructionsTest {

    private CpuTestHarness h;

    @BeforeEach
    void setUp() {
        h = new CpuTestHarness();
    }

    @Test
    void andImmediate() {
        h.prepareInstruction(0x0300, 0x29, 0x0F); // AND #$0F
        h.cpu.setAccumulator(0xFF);
        long cycles = h.runInstruction();
        assertEquals(0x0F, h.cpu.getAccumulator());
        assertEquals(2, cycles);
    }

    @Test
    void andResultingInZeroSetsZeroFlag() {
        h.prepareInstruction(0x0300, 0x29, 0x00);
        h.cpu.setAccumulator(0xFF);
        h.runInstruction();
        assertTrue(h.cpu.getZeroResultFlag());
    }

    @Test
    void oraImmediate() {
        h.prepareInstruction(0x0300, 0x09, 0xF0); // ORA #$F0
        h.cpu.setAccumulator(0x0F);
        long cycles = h.runInstruction();
        assertEquals(0xFF, h.cpu.getAccumulator());
        assertTrue(h.cpu.getNegativeResultFlag());
        assertEquals(2, cycles);
    }

    @Test
    void eorImmediate() {
        h.prepareInstruction(0x0300, 0x49, 0xFF); // EOR #$FF
        h.cpu.setAccumulator(0xAA);
        long cycles = h.runInstruction();
        assertEquals(0x55, h.cpu.getAccumulator());
        assertEquals(2, cycles);
    }

    @Test
    void eorWithSelfClearsAccumulator() {
        h.prepareInstruction(0x0300, 0x49, 0x77);
        h.cpu.setAccumulator(0x77);
        h.runInstruction();
        assertEquals(0x00, h.cpu.getAccumulator());
        assertTrue(h.cpu.getZeroResultFlag());
    }

    @Test
    void andAbsoluteAddressingMode() {
        h.writeByte(0x0400, 0x0F);
        h.prepareInstruction(0x0300, 0x2D, 0x00, 0x04); // AND $0400
        h.cpu.setAccumulator(0xFF);
        long cycles = h.runInstruction();
        assertEquals(0x0F, h.cpu.getAccumulator());
        assertEquals(4, cycles);
    }

    @Test
    void bitZeroPageSetsNegativeAndOverflowFromMemoryOperand() {
        h.writeByte(0x0050, 0xC0); // bits 7 and 6 both set
        h.prepareInstruction(0x0300, 0x24, 0x50); // BIT $50
        h.cpu.setAccumulator(0xFF); // A & M != 0, so Z should be clear
        long cycles = h.runInstruction();
        assertTrue(h.cpu.getNegativeResultFlag());
        assertTrue(h.cpu.getOverflowFlag());
        assertFalse(h.cpu.getZeroResultFlag());
        assertEquals(3, cycles);
    }

    @Test
    void bitSetsZeroWhenAndIsZeroRegardlessOfMemoryBits() {
        h.writeByte(0x0050, 0xC0);
        h.prepareInstruction(0x0300, 0x24, 0x50);
        h.cpu.setAccumulator(0x00); // A & M == 0 -> Z set
        h.runInstruction();
        assertTrue(h.cpu.getZeroResultFlag());
        // N/V still reflect the memory operand's bits 7/6, not A.
        assertTrue(h.cpu.getNegativeResultFlag());
        assertTrue(h.cpu.getOverflowFlag());
    }

    @Test
    void bitAbsoluteDoesNotAffectCarry() {
        h.writeByte(0x0400, 0x00);
        h.prepareInstruction(0x0300, 0x2C, 0x00, 0x04); // BIT $0400
        h.cpu.setAccumulator(0xFF);
        h.cpu.setCarryFlag(true);
        long cycles = h.runInstruction();
        assertTrue(h.cpu.getCarryFlag());
        assertEquals(4, cycles);
    }
}
