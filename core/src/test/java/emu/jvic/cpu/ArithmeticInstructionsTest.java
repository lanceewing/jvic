package emu.jvic.cpu;

import emu.jvic.testutil.CpuTestHarness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ADC / SBC in binary mode. Reference behaviour: C is the carry-in/out for ADC and
 * the "not-borrow" flag for SBC (set = no borrow). V is set when the sign of the
 * result is wrong for signed addition/subtraction of the two signed 8-bit operands.
 */
public class ArithmeticInstructionsTest {

    private CpuTestHarness h;

    @BeforeEach
    void setUp() {
        h = new CpuTestHarness();
        h.cpu.setDecimalModeFlag(false);
    }

    private long adc(int a, int operand, boolean carryIn) {
        h.prepareInstruction(0x0300, 0x69, operand); // ADC #imm
        h.cpu.setAccumulator(a);
        h.cpu.setCarryFlag(carryIn);
        h.cpu.setDecimalModeFlag(false);
        return h.runInstruction();
    }

    private long sbc(int a, int operand, boolean carryIn) {
        h.prepareInstruction(0x0300, 0xE9, operand); // SBC #imm
        h.cpu.setAccumulator(a);
        h.cpu.setCarryFlag(carryIn);
        h.cpu.setDecimalModeFlag(false);
        return h.runInstruction();
    }

    @Test
    void adcImmediateCycleCount() {
        long cycles = adc(0x10, 0x20, false);
        assertEquals(2, cycles);
    }

    @Test
    void adcSimpleAddition() {
        adc(0x10, 0x20, false);
        assertEquals(0x30, h.cpu.getAccumulator());
        assertFalse(h.cpu.getCarryFlag());
        assertFalse(h.cpu.getOverflowFlag());
        assertFalse(h.cpu.getNegativeResultFlag());
        assertFalse(h.cpu.getZeroResultFlag());
    }

    @Test
    void adcWithCarryIn() {
        adc(0x01, 0x01, true);
        assertEquals(0x03, h.cpu.getAccumulator());
    }

    @Test
    void adcSetsCarryOutOnUnsignedOverflow() {
        adc(0xFF, 0x01, false);
        assertEquals(0x00, h.cpu.getAccumulator());
        assertTrue(h.cpu.getCarryFlag());
        assertTrue(h.cpu.getZeroResultFlag());
    }

    @Test
    void adcSetsOverflowWhenPositivePlusPositiveGivesNegative() {
        adc(0x7F, 0x01, false); // 127 + 1 = 128 (signed overflow)
        assertEquals(0x80, h.cpu.getAccumulator());
        assertTrue(h.cpu.getOverflowFlag());
        assertTrue(h.cpu.getNegativeResultFlag());
        assertFalse(h.cpu.getCarryFlag());
    }

    @Test
    void adcSetsOverflowWhenNegativePlusNegativeGivesPositive() {
        adc(0x80, 0x80, false); // -128 + -128 = -256 -> wraps to 0, signed overflow
        assertEquals(0x00, h.cpu.getAccumulator());
        assertTrue(h.cpu.getOverflowFlag());
        assertTrue(h.cpu.getCarryFlag());
        assertTrue(h.cpu.getZeroResultFlag());
        assertFalse(h.cpu.getNegativeResultFlag());
    }

    @Test
    void adcPositivePlusNegativeNeverOverflows() {
        adc(0x7F, 0xFF, false); // 127 + -1 = 126, cannot overflow
        assertEquals(0x7E, h.cpu.getAccumulator());
        assertFalse(h.cpu.getOverflowFlag());
    }

    @Test
    void adcAbsoluteAddressingMode() {
        h.writeByte(0x0400, 0x05);
        h.prepareInstruction(0x0300, 0x6D, 0x00, 0x04); // ADC $0400
        h.cpu.setAccumulator(0x02);
        h.cpu.setCarryFlag(false);
        long cycles = h.runInstruction();
        assertEquals(0x07, h.cpu.getAccumulator());
        assertEquals(4, cycles);
    }

    @Test
    void sbcImmediateCycleCount() {
        long cycles = sbc(0x50, 0x30, true);
        assertEquals(2, cycles);
    }

    @Test
    void sbcNoBorrowLeavesCarrySet() {
        sbc(0x50, 0x30, true); // carry set = "no borrow" going in
        assertEquals(0x20, h.cpu.getAccumulator());
        assertTrue(h.cpu.getCarryFlag());
    }

    @Test
    void sbcBorrowClearsCarry() {
        sbc(0x30, 0x50, true);
        assertEquals(0xE0, h.cpu.getAccumulator());
        assertFalse(h.cpu.getCarryFlag());
        assertTrue(h.cpu.getNegativeResultFlag());
    }

    @Test
    void sbcCarryClearAppliesExtraBorrow() {
        sbc(0x50, 0x30, false); // carry clear = borrow already pending
        assertEquals(0x1F, h.cpu.getAccumulator());
    }

    @Test
    void sbcSetsOverflowWhenNegativeMinusPositiveGivesPositive() {
        sbc(0x80, 0x01, true); // -128 - 1 = -129 -> wraps, signed overflow
        assertEquals(0x7F, h.cpu.getAccumulator());
        assertTrue(h.cpu.getOverflowFlag());
        assertFalse(h.cpu.getNegativeResultFlag());
    }

    @Test
    void sbcZeroResult() {
        sbc(0x40, 0x40, true);
        assertEquals(0x00, h.cpu.getAccumulator());
        assertTrue(h.cpu.getZeroResultFlag());
        assertTrue(h.cpu.getCarryFlag());
    }
}
