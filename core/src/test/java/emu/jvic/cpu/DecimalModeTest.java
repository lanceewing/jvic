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
 * <p><b>Real NMOS hardware flag quirks, now modeled here</b> (see 6502.org's
 * Decimal Mode tutorial, "Appendix A"):
 * <ul>
 *   <li>ADC: Z is set as if the addition had been done in binary, not from the
 *       BCD-corrected accumulator result - so a decimal add that wraps to a
 *       BCD-corrected $00 does NOT necessarily set Z (see
 *       {@link #adcHandlesTensDigitCarry}, the canonical example of this).</li>
 *   <li>ADC: N and V reflect the result before the final high-nibble decimal
 *       correction is applied, not the BCD-corrected accumulator.</li>
 *   <li>SBC: N, V, Z and C are ALL computed as if the subtraction had been done
 *       in binary - none of them reflect the BCD-corrected result.</li>
 * </ul>
 * In both cases the accumulator (and, for ADC, the carry) result itself is
 * still the properly BCD-corrected value.
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
        // Classic NMOS decimal-mode Z quirk: Z is set from the BINARY sum
        // ($50 + $50 = $A0, non-zero), not from the BCD-corrected accumulator
        // ($00) - so despite the accumulator reading zero, Z is clear.
        assertFalse(h.cpu.getZeroResultFlag());
    }

    @Test
    void adcMaxValuesWrapWithCarry() {
        adcDecimal(0x99, 0x01, false); // 99 + 1 = 100 -> wraps to 00, carry set
        assertEquals(0x00, h.cpu.getAccumulator());
        assertTrue(h.cpu.getCarryFlag());
        // Same Z quirk as adcHandlesTensDigitCarry: binary sum $99 + $01 = $9A is
        // non-zero, so Z is clear despite the BCD-corrected accumulator being $00.
        assertFalse(h.cpu.getZeroResultFlag());
    }

    @Test
    void adcArbitraryTwoDigitAddition() {
        adcDecimal(0x12, 0x34, false); // 12 + 34 = 46
        assertEquals(0x46, h.cpu.getAccumulator());
        assertFalse(h.cpu.getCarryFlag());
    }

    @Test
    void adcNegativeAndOverflowReflectPreCorrectionResultNotFinalAccumulator() {
        // $99 + $99 (decimal 99+99=198, BCD-corrected accumulator becomes $98,
        // carry set). The pre-correction intermediate is $138 truncated to $38
        // (0011 1000): bit 7 clear, so N is clear even though the final
        // accumulator ($98) has bit 7 set. V compares operand signs against that
        // same pre-correction byte, and is set here.
        adcDecimal(0x99, 0x99, false);
        assertEquals(0x98, h.cpu.getAccumulator());
        assertTrue(h.cpu.getCarryFlag());
        assertFalse(h.cpu.getNegativeResultFlag());
        assertTrue(h.cpu.getOverflowFlag());
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
    void sbcFlagsReflectBinarySubtractionNotBcdCorrectedResult() {
        // $00 - $01 (borrow in): BCD-corrected accumulator wraps to $99 with carry
        // clear (borrow out) - but N, V, Z all come from the BINARY subtraction
        // $00 - $01 - 0 = -1 ($FF as a byte): N set (bit 7 of $FF), Z clear, and
        // no signed overflow (V clear, since operands don't have matching signs
        // per the standard overflow test).
        sbcDecimal(0x00, 0x01, true);
        assertEquals(0x99, h.cpu.getAccumulator());
        assertFalse(h.cpu.getCarryFlag());
        assertTrue(h.cpu.getNegativeResultFlag());
        assertFalse(h.cpu.getZeroResultFlag());
        assertFalse(h.cpu.getOverflowFlag());
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
