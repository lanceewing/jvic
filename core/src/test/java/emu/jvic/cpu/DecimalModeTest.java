package emu.jvic.cpu;

import emu.jvic.testutil.CpuTestHarness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ADC / SBC in BCD (decimal) mode, operating on valid packed-BCD operands
 * (both nibbles 0-9).
 *
 * <p><b>Known documented deviation from real NMOS hardware:</b> this
 * implementation unconditionally clears N and V and derives Z from the final
 * (adjusted) accumulator value. Real NMOS 6502 decimal mode has well-known flag
 * quirks where N/V/Z actually reflect the pre-adjustment binary result rather than
 * the final decimal one. The tests below lock in the emulator's simplified,
 * current behaviour (which is correct for the accumulator RESULT and carry, just
 * not for N/V/Z in every case) rather than asserting real-hardware N/V/Z quirks
 * that this emulator does not attempt to reproduce.
 */
public class DecimalModeTest {

    private CpuTestHarness h;

    @BeforeEach
    void setUp() {
        h = new CpuTestHarness();
    }

    private void adcDecimal(int a, int operand, boolean carryIn) {
        h.prepareInstruction(0x0300, 0x69, operand); // ADC #imm
        h.cpu.setAccumulator(a);
        h.cpu.setCarryFlag(carryIn);
        h.cpu.setDecimalModeFlag(true);
        h.runInstruction();
    }

    private void sbcDecimal(int a, int operand, boolean carryIn) {
        h.prepareInstruction(0x0300, 0xE9, operand); // SBC #imm
        h.cpu.setAccumulator(a);
        h.cpu.setCarryFlag(carryIn);
        h.cpu.setDecimalModeFlag(true);
        h.runInstruction();
    }

    @Test
    void adcAddsAsPackedBcd() {
        adcDecimal(0x05, 0x05, false); // 5 + 5 = 10 (decimal)
        assertEquals(0x10, h.cpu.getAccumulator());
        assertFalse(h.cpu.getCarryFlag());
    }

    @Test
    void adcHandlesCarryIn() {
        adcDecimal(0x05, 0x05, true); // 5 + 5 + 1 = 11
        assertEquals(0x11, h.cpu.getAccumulator());
    }

    @Test
    void adcHandlesTensDigitCarry() {
        adcDecimal(0x50, 0x50, false); // 50 + 50 = 100 -> wraps to 00, carry set
        assertEquals(0x00, h.cpu.getAccumulator());
        assertTrue(h.cpu.getCarryFlag());
        assertTrue(h.cpu.getZeroResultFlag());
    }

    @Test
    void adcMaxValuesWrapWithCarry() {
        adcDecimal(0x99, 0x01, false); // 99 + 1 = 100 -> wraps to 00, carry set
        assertEquals(0x00, h.cpu.getAccumulator());
        assertTrue(h.cpu.getCarryFlag());
    }

    @Test
    void adcArbitraryTwoDigitAddition() {
        adcDecimal(0x12, 0x34, false); // 12 + 34 = 46
        assertEquals(0x46, h.cpu.getAccumulator());
        assertFalse(h.cpu.getCarryFlag());
    }

    @Test
    void adcAlwaysClearsOverflowAndNegativeInThisImplementation() {
        adcDecimal(0x99, 0x99, false);
        assertFalse(h.cpu.getOverflowFlag());
        assertFalse(h.cpu.getNegativeResultFlag());
    }

    @Test
    void sbcSubtractsAsPackedBcdWithNoBorrow() {
        sbcDecimal(0x10, 0x05, true); // carry set = no borrow in; 10 - 5 = 5
        assertEquals(0x05, h.cpu.getAccumulator());
        assertTrue(h.cpu.getCarryFlag()); // no borrow out
    }

    @Test
    void sbcBorrowWrapsAndClearsCarry() {
        sbcDecimal(0x00, 0x01, true); // 0 - 1 -> wraps to 99, borrow out
        assertEquals(0x99, h.cpu.getAccumulator());
        assertFalse(h.cpu.getCarryFlag());
    }

    @Test
    void sbcCarryClearAppliesExtraBorrow() {
        sbcDecimal(0x50, 0x30, false); // carry clear = borrow of 1 already pending; 50 - 30 - 1 = 19
        assertEquals(0x19, h.cpu.getAccumulator());
        assertTrue(h.cpu.getCarryFlag());
    }

    @Test
    void sbcArbitraryTwoDigitSubtraction() {
        sbcDecimal(0x46, 0x12, true); // 46 - 12 = 34
        assertEquals(0x34, h.cpu.getAccumulator());
    }

    @Test
    void adcDecimalModeCycleCountMatchesBinaryMode() {
        // Decimal mode is not documented as costing any extra cycles on NMOS 6502.
        h.prepareInstruction(0x0300, 0x69, 0x01);
        h.cpu.setAccumulator(0x01);
        h.cpu.setDecimalModeFlag(true);
        long cycles = h.runInstruction();
        assertEquals(2, cycles);
    }
}
