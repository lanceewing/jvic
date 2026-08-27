package emu.jvic.cpu;

import emu.jvic.testutil.CpuTestHarness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Correctness and cycle-count tests for the illegal/undocumented 6502 opcodes
 * that Cpu6502 implements: SLO, RLA, SRE, RRA, DCP, ISC (the read-modify-write
 * combo opcodes, across every addressing mode each supports), SAX, LAX, ANC, ARR,
 * SBX and ALR. Illegal NOP variants have their own dedicated
 * {@link IllegalNopInstructionsTest}. See {@code OpcodeCoverageTest} /
 * {@code Opcode6502Reference} for the small set of "unstable" illegal opcodes
 * (ANE, LXA, LAS, SHA, SHX, SHY, TAS) and the 12 JAM opcodes that remain
 * deliberately unimplemented.
 */
public class UndocumentedOpcodesTest {

    private CpuTestHarness h;

    @BeforeEach
    void setUp() {
        h = new CpuTestHarness();
    }

    @Test
    void sloZeroPageShiftsMemoryThenOrsIntoAccumulator() {
        h.writeByte(0x0050, 0x40); // 0100 0000, bit7 clear
        h.prepareInstruction(0x0300, 0x07, 0x50); // SLO $50
        h.cpu.setAccumulator(0x01);
        long cycles = h.runInstruction();
        assertEquals(0x80, h.readByte(0x0050)); // memory shifted left
        assertEquals(0x81, h.cpu.getAccumulator()); // A | shifted memory
        assertFalse(h.cpu.getCarryFlag()); // old bit7 of memory was 0
        assertTrue(h.cpu.getNegativeResultFlag());
        assertEquals(5, cycles);
    }

    @Test
    void sloIndexedIndirectXWritesToTheCorrectFullAddress() {
        // SLO (Indirect,X) resolves a full 16-bit effective address (via
        // FETCH_ADL_BAL/FETCH_ADH_BAL into effectiveAddressLow/High), so its decode
        // entry must use EXECUTE_MID_EA/STORE_DATA_EA (the full-address
        // read-modify-write cycle types), not the zero-page-only ADL variants.
        h.writeWord(0x0024, 0x0500); // pointer resolves to $0500
        h.writeByte(0x0500, 0x80);
        h.prepareInstruction(0x0300, 0x03, 0x20); // SLO ($20,X)
        h.cpu.setAccumulator(0x00);
        h.cpu.setIndexRegisterX(0x04);
        long cycles = h.runInstruction();

        assertEquals(0x00, h.readByte(0x0500)); // 0x80 << 1 = 0x00 (mod 256)
        assertEquals(0x00, h.cpu.getAccumulator());
        assertTrue(h.cpu.getCarryFlag()); // old bit7 of memory was 1
        assertTrue(h.cpu.getZeroResultFlag());
        assertEquals(0x00, h.readByte(0x0000), "nothing should be written to $0000 by this instruction");
        assertEquals(8, cycles);
    }

    @Test
    void sloAbsoluteCycleCount() {
        h.writeByte(0x0400, 0x01);
        long cycles = h.execute(0x0300, 0x0F, 0x00, 0x04); // SLO $0400
        assertEquals(6, cycles);
    }

    @Test
    void ancMirrorsNegativeFlagIntoCarry() {
        h.prepareInstruction(0x0300, 0x0B, 0x81); // ANC #$81
        h.cpu.setAccumulator(0xFF);
        long cycles = h.runInstruction();
        assertEquals(0x81, h.cpu.getAccumulator());
        assertTrue(h.cpu.getNegativeResultFlag());
        assertTrue(h.cpu.getCarryFlag());
        assertEquals(2, cycles);
    }

    @Test
    void ancClearsCarryWhenResultNotNegative() {
        h.prepareInstruction(0x0300, 0x0B, 0x7F);
        h.cpu.setAccumulator(0xFF);
        h.runInstruction();
        assertEquals(0x7F, h.cpu.getAccumulator());
        assertFalse(h.cpu.getNegativeResultFlag());
        assertFalse(h.cpu.getCarryFlag());
    }

    @Test
    void saxStoresAccumulatorAndXAndedTogetherWithoutAffectingFlags() {
        h.prepareInstruction(0x0300, 0x8F, 0x00, 0x04); // SAX $0400
        h.cpu.setAccumulator(0xF0);
        h.cpu.setIndexRegisterX(0x3C);
        h.cpu.setZeroResultFlag(true); // must remain untouched
        long cycles = h.runInstruction();
        assertEquals(0x30, h.readByte(0x0400)); // 0xF0 & 0x3C
        assertTrue(h.cpu.getZeroResultFlag());
        assertEquals(4, cycles);
    }

    @Test
    void laxLoadsBothAccumulatorAndXFromMemory() {
        h.writeByte(0x0055, 0x99);
        h.prepareInstruction(0x0300, 0xB7, 0x50); // LAX $50,Y
        h.cpu.setIndexRegisterY(0x05);
        long cycles = h.runInstruction();
        assertEquals(0x99, h.cpu.getAccumulator());
        assertEquals(0x99, h.cpu.getIndexRegisterX());
        assertTrue(h.cpu.getNegativeResultFlag());
        assertEquals(4, cycles);
    }

    @Test
    void arrPerformsAndThenRotatesRightThroughCarry() {
        h.prepareInstruction(0x0300, 0x6B, 0xFF); // ARR #$FF
        h.cpu.setAccumulator(0xFF);
        h.cpu.setCarryFlag(false);
        long cycles = h.runInstruction();
        assertEquals(0x7F, h.cpu.getAccumulator()); // (0xFF & 0xFF) rotated right with carry-in 0
        assertTrue(h.cpu.getCarryFlag()); // old bit0 (1) becomes the new carry
        assertEquals(2, cycles);
    }

    @Test
    void arrSetsCarryAndClearsOverflowWhenResultBits5And6AreBothSet() {
        // 0xC0 & 0xC0 = 0xC0 (1100 0000), rotated right with carry-in 0 -> 0x60
        // (0110 0000): bit6=1, bit5=1 -> C=1, V=0.
        h.prepareInstruction(0x0300, 0x6B, 0xC0);
        h.cpu.setAccumulator(0xC0);
        h.cpu.setCarryFlag(false);
        h.cpu.setOverflowFlag(true); // deliberately "wrong" starting value; must be overwritten
        h.runInstruction();
        assertEquals(0x60, h.cpu.getAccumulator());
        assertTrue(h.cpu.getCarryFlag());
        assertFalse(h.cpu.getOverflowFlag());
    }

    @Test
    void arrClearsCarryAndOverflowWhenResultBits5And6AreBothClear() {
        // 0x03 & 0x03 = 0x03 (0000 0011), rotated right with carry-in 0 -> 0x01
        // (0000 0001): bit6=0, bit5=0 -> C=0, V=0.
        h.prepareInstruction(0x0300, 0x6B, 0x03);
        h.cpu.setAccumulator(0x03);
        h.cpu.setCarryFlag(false);
        h.runInstruction();
        assertEquals(0x01, h.cpu.getAccumulator());
        assertFalse(h.cpu.getCarryFlag());
        assertFalse(h.cpu.getOverflowFlag());
    }

    @Test
    void arrSetsOnlyOverflowWhenOnlyBit5OfResultIsSet() {
        // 0x40 & 0x40 = 0x40 (0100 0000), rotated right with carry-in 0 -> 0x20
        // (0010 0000): bit6=0, bit5=1 -> C=0, V=1.
        h.prepareInstruction(0x0300, 0x6B, 0x40);
        h.cpu.setAccumulator(0x40);
        h.cpu.setCarryFlag(false);
        h.runInstruction();
        assertEquals(0x20, h.cpu.getAccumulator());
        assertFalse(h.cpu.getCarryFlag());
        assertTrue(h.cpu.getOverflowFlag());
    }

    @Test
    void arrSetsCarryAndOverflowWhenOnlyBit6OfResultIsSet() {
        // 0x80 & 0x80 = 0x80 (1000 0000), rotated right with carry-in 0 -> 0x40
        // (0100 0000): bit6=1, bit5=0 -> C=1, V=1.
        h.prepareInstruction(0x0300, 0x6B, 0x80);
        h.cpu.setAccumulator(0x80);
        h.cpu.setCarryFlag(false);
        h.runInstruction();
        assertEquals(0x40, h.cpu.getAccumulator());
        assertTrue(h.cpu.getCarryFlag());
        assertTrue(h.cpu.getOverflowFlag());
    }

    @Test
    void arrAppliesBcdNibbleCorrectionInDecimalMode() {
        // The bit6/bit5 AND+ROR flag logic above is unaffected by decimal mode; only
        // the accumulator gets an additional BCD nibble correction (real hardware's
        // exact formula is only sparsely documented - see the comment on the ARR case
        // in Cpu6502 - so this test pins down the implementation's approximation
        // rather than an independently-verified real-hardware value).
        h.prepareInstruction(0x0300, 0x6B, 0xFF); // ARR #$FF
        h.cpu.setAccumulator(0x99);
        h.cpu.setCarryFlag(false);
        h.cpu.setDecimalModeFlag(true);
        long cycles = h.runInstruction();
        assertEquals(0xA2, h.cpu.getAccumulator());
        assertTrue(h.cpu.getCarryFlag());
        assertEquals(2, cycles);
    }

    @Test
    void arrDecimalModeLeavesLowNibbleUnchangedWhenNoCorrectionNeeded() {
        h.prepareInstruction(0x0300, 0x6B, 0x11); // ARR #$11
        h.cpu.setAccumulator(0x11);
        h.cpu.setCarryFlag(false);
        h.cpu.setDecimalModeFlag(true);
        h.runInstruction();
        // 0x11 & 0x11 = 0x11, rotated right (carry-in 0) -> 0x08; low/high nibble
        // corrections both fall below their thresholds, so 0x08 is unchanged.
        assertEquals(0x08, h.cpu.getAccumulator());
        assertFalse(h.cpu.getCarryFlag());
    }

    @Test
    void rraRotatesMemoryThenAddsToAccumulator() {
        h.writeByte(0x0400, 0x05); // 0000 0101, bit0 = 1
        h.prepareInstruction(0x0300, 0x6F, 0x00, 0x04); // RRA $0400
        h.cpu.setAccumulator(0x00);
        h.cpu.setCarryFlag(true); // rotated into bit 7
        long cycles = h.runInstruction();
        assertEquals(0x82, h.readByte(0x0400)); // (0x05 >> 1) | 0x80
        assertEquals(0x83, h.cpu.getAccumulator()); // 0x00 + 0x82 + carry(1, rotated in from the old bit0=1)
        assertEquals(6, cycles);
    }

    @Test
    void rraFeedsTheRotatedOutBitIntoTheFollowingAddition() {
        // Correct RRA semantics: rotate M right through carry (M's old bit 0
        // becomes the NEW carry), THEN compute A + M' + (that new carry) - the bit
        // rotated out of memory must be fed into the addition, not the stale
        // pre-rotation carry.
        //
        // Setup: M=$01, carry-in=0. Rotating gives M'=$00 and a new carry of 1
        // (M's old bit 0). Result: A($00) + M'($00) + 1 = $01.
        h.writeByte(0x0400, 0x01);
        h.prepareInstruction(0x0300, 0x6F, 0x00, 0x04); // RRA $0400
        h.cpu.setAccumulator(0x00);
        h.cpu.setCarryFlag(false);
        h.runInstruction();

        assertEquals(0x00, h.readByte(0x0400)); // 0x01 >> 1 = 0x00
        assertEquals(0x01, h.cpu.getAccumulator());
        assertFalse(h.cpu.getCarryFlag(), "0x00 + 0x00 + 1 = 0x01 does not carry out");
    }

    @Test
    void iscIncrementsMemoryThenSubtractsFromAccumulator() {
        h.writeByte(0x0055, 0x0F);
        h.prepareInstruction(0x0300, 0xF7, 0x50); // ISC $50,X
        h.cpu.setIndexRegisterX(0x05);
        h.cpu.setAccumulator(0x20);
        h.cpu.setCarryFlag(true); // no borrow going in
        long cycles = h.runInstruction();
        assertEquals(0x10, h.readByte(0x0055)); // memory incremented first
        assertEquals(0x10, h.cpu.getAccumulator()); // 0x20 - 0x10
        assertTrue(h.cpu.getCarryFlag());
        assertEquals(6, cycles);
    }

    // ------------------------------------------------------ SLO (remaining modes) ----

    @Test
    void sloIndirectIndexedY() {
        h.writeWord(0x0020, 0x0500);
        h.writeByte(0x0505, 0x40);
        h.prepareInstruction(0x0300, 0x13, 0x20); // SLO ($20),Y
        h.cpu.setAccumulator(0x01);
        h.cpu.setIndexRegisterY(0x05);
        long cycles = h.runInstruction();
        assertEquals(0x80, h.readByte(0x0505));
        assertEquals(0x81, h.cpu.getAccumulator());
        assertEquals(8, cycles);
    }

    @Test
    void sloZeroPageX() {
        h.writeByte(0x0055, 0x01);
        h.prepareInstruction(0x0300, 0x17, 0x50); // SLO $50,X
        h.cpu.setIndexRegisterX(0x05);
        h.cpu.setAccumulator(0x00);
        long cycles = h.runInstruction();
        assertEquals(0x02, h.readByte(0x0055));
        assertEquals(0x02, h.cpu.getAccumulator());
        assertEquals(6, cycles);
    }

    @Test
    void sloAbsoluteYCycleCount() {
        h.writeByte(0x0405, 0x01);
        h.prepareInstruction(0x0300, 0x1B, 0x00, 0x04); // SLO $0400,Y
        h.cpu.setIndexRegisterY(0x05);
        long cycles = h.runInstruction();
        assertEquals(0x02, h.readByte(0x0405));
        assertEquals(7, cycles);
    }

    @Test
    void sloAbsoluteXCycleCount() {
        h.writeByte(0x0405, 0x01);
        h.prepareInstruction(0x0300, 0x1F, 0x00, 0x04); // SLO $0400,X
        h.cpu.setIndexRegisterX(0x05);
        long cycles = h.runInstruction();
        assertEquals(0x02, h.readByte(0x0405));
        assertEquals(7, cycles);
    }

    // ------------------------------------------------------ RLA ----

    @Test
    void rlaRotatesMemoryLeftThenAndsIntoAccumulator() {
        h.writeByte(0x0400, 0x81); // 1000 0001
        h.prepareInstruction(0x0300, 0x2F, 0x00, 0x04); // RLA $0400
        h.cpu.setAccumulator(0xFF);
        h.cpu.setCarryFlag(true);
        long cycles = h.runInstruction();
        assertEquals(0x03, h.readByte(0x0400)); // (0x81 << 1 | 1) & 0xFF
        assertEquals(0x03, h.cpu.getAccumulator()); // 0xFF & 0x03
        assertTrue(h.cpu.getCarryFlag()); // old bit7 of memory was 1
        assertEquals(6, cycles);
    }

    @Test
    void rlaIndexedIndirectX() {
        h.writeWord(0x0024, 0x0500);
        h.writeByte(0x0500, 0x40);
        h.prepareInstruction(0x0300, 0x23, 0x20); // RLA ($20,X)
        h.cpu.setAccumulator(0xFF);
        h.cpu.setIndexRegisterX(0x04);
        h.cpu.setCarryFlag(false);
        long cycles = h.runInstruction();
        assertEquals(0x80, h.readByte(0x0500));
        assertEquals(0x80, h.cpu.getAccumulator());
        assertFalse(h.cpu.getCarryFlag());
        assertEquals(8, cycles);
    }

    @Test
    void rlaZeroPageCycleCount() {
        h.writeByte(0x0050, 0x01);
        long cycles = h.execute(0x0300, 0x27, 0x50);
        assertEquals(5, cycles);
    }

    @Test
    void rlaIndirectIndexedY() {
        h.writeWord(0x0020, 0x0500);
        h.writeByte(0x0505, 0x81);
        h.prepareInstruction(0x0300, 0x33, 0x20); // RLA ($20),Y
        h.cpu.setAccumulator(0xFF);
        h.cpu.setIndexRegisterY(0x05);
        h.cpu.setCarryFlag(false);
        long cycles = h.runInstruction();
        assertEquals(0x02, h.readByte(0x0505)); // (0x81 << 1 | 0) & 0xFF
        assertEquals(0x02, h.cpu.getAccumulator());
        assertEquals(8, cycles);
    }

    @Test
    void rlaZeroPageXCycleCount() {
        h.prepareInstruction(0x0300, 0x37, 0x50); // RLA $50,X
        h.cpu.setIndexRegisterX(0x05);
        long cycles = h.runInstruction();
        assertEquals(6, cycles);
    }

    @Test
    void rlaAbsoluteYCycleCount() {
        h.prepareInstruction(0x0300, 0x3B, 0x00, 0x04); // RLA $0400,Y
        h.cpu.setIndexRegisterY(0x05);
        long cycles = h.runInstruction();
        assertEquals(7, cycles);
    }

    @Test
    void rlaAbsoluteXCycleCount() {
        h.prepareInstruction(0x0300, 0x3F, 0x00, 0x04); // RLA $0400,X
        h.cpu.setIndexRegisterX(0x05);
        long cycles = h.runInstruction();
        assertEquals(7, cycles);
    }

    // ------------------------------------------------------ SRE ----

    @Test
    void sreShiftsMemoryRightThenEorsIntoAccumulator() {
        h.writeByte(0x0400, 0x03); // 0000 0011
        h.prepareInstruction(0x0300, 0x4F, 0x00, 0x04); // SRE $0400
        h.cpu.setAccumulator(0xFF);
        long cycles = h.runInstruction();
        assertEquals(0x01, h.readByte(0x0400)); // 0x03 >> 1
        assertEquals(0xFE, h.cpu.getAccumulator()); // 0xFF ^ 0x01
        assertTrue(h.cpu.getCarryFlag()); // old bit0 of memory was 1
        assertEquals(6, cycles);
    }

    @Test
    void sreIndexedIndirectXCycleCount() {
        h.writeWord(0x0024, 0x0500);
        h.prepareInstruction(0x0300, 0x43, 0x20); // SRE ($20,X)
        h.cpu.setIndexRegisterX(0x04);
        long cycles = h.runInstruction();
        assertEquals(8, cycles);
    }

    @Test
    void sreZeroPageCycleCount() {
        long cycles = h.execute(0x0300, 0x47, 0x50);
        assertEquals(5, cycles);
    }

    @Test
    void sreIndirectIndexedY() {
        h.writeWord(0x0020, 0x0500);
        h.writeByte(0x0505, 0x03);
        h.prepareInstruction(0x0300, 0x53, 0x20); // SRE ($20),Y
        h.cpu.setAccumulator(0xFF);
        h.cpu.setIndexRegisterY(0x05);
        long cycles = h.runInstruction();
        assertEquals(0x01, h.readByte(0x0505)); // 0x03 >> 1
        assertEquals(0xFE, h.cpu.getAccumulator()); // 0xFF ^ 0x01
        assertEquals(8, cycles);
    }

    @Test
    void sreZeroPageXCycleCount() {
        h.prepareInstruction(0x0300, 0x57, 0x50); // SRE $50,X
        h.cpu.setIndexRegisterX(0x05);
        long cycles = h.runInstruction();
        assertEquals(6, cycles);
    }

    @Test
    void sreAbsoluteYCycleCount() {
        h.prepareInstruction(0x0300, 0x5B, 0x00, 0x04); // SRE $0400,Y
        h.cpu.setIndexRegisterY(0x05);
        long cycles = h.runInstruction();
        assertEquals(7, cycles);
    }

    @Test
    void sreAbsoluteXCycleCount() {
        h.prepareInstruction(0x0300, 0x5F, 0x00, 0x04); // SRE $0400,X
        h.cpu.setIndexRegisterX(0x05);
        long cycles = h.runInstruction();
        assertEquals(7, cycles);
    }

    // ------------------------------------------------------ DCP ----

    @Test
    void dcpDecrementsMemoryThenComparesWithAccumulator() {
        h.writeByte(0x0400, 0x11);
        h.prepareInstruction(0x0300, 0xCF, 0x00, 0x04); // DCP $0400
        h.cpu.setAccumulator(0x10);
        long cycles = h.runInstruction();
        assertEquals(0x10, h.readByte(0x0400)); // 0x11 - 1
        assertEquals(0x10, h.cpu.getAccumulator()); // unaffected
        assertTrue(h.cpu.getZeroResultFlag()); // 0x10 - 0x10 == 0
        assertTrue(h.cpu.getCarryFlag()); // no borrow
        assertEquals(6, cycles);
    }

    @Test
    void dcpIndexedIndirectXCycleCount() {
        h.writeWord(0x0024, 0x0500);
        h.prepareInstruction(0x0300, 0xC3, 0x20); // DCP ($20,X)
        h.cpu.setIndexRegisterX(0x04);
        long cycles = h.runInstruction();
        assertEquals(8, cycles);
    }

    @Test
    void dcpZeroPageCycleCount() {
        long cycles = h.execute(0x0300, 0xC7, 0x50);
        assertEquals(5, cycles);
    }

    @Test
    void dcpIndirectIndexedY() {
        h.writeWord(0x0020, 0x0500);
        h.writeByte(0x0505, 0x11);
        h.prepareInstruction(0x0300, 0xD3, 0x20); // DCP ($20),Y
        h.cpu.setAccumulator(0x10);
        h.cpu.setIndexRegisterY(0x05);
        long cycles = h.runInstruction();
        assertEquals(0x10, h.readByte(0x0505)); // 0x11 - 1
        assertTrue(h.cpu.getZeroResultFlag());
        assertEquals(8, cycles);
    }

    @Test
    void dcpZeroPageXCycleCount() {
        h.prepareInstruction(0x0300, 0xD7, 0x50); // DCP $50,X
        h.cpu.setIndexRegisterX(0x05);
        long cycles = h.runInstruction();
        assertEquals(6, cycles);
    }

    @Test
    void dcpAbsoluteYCycleCount() {
        h.prepareInstruction(0x0300, 0xDB, 0x00, 0x04); // DCP $0400,Y
        h.cpu.setIndexRegisterY(0x05);
        long cycles = h.runInstruction();
        assertEquals(7, cycles);
    }

    @Test
    void dcpAbsoluteXCycleCount() {
        h.prepareInstruction(0x0300, 0xDF, 0x00, 0x04); // DCP $0400,X
        h.cpu.setIndexRegisterX(0x05);
        long cycles = h.runInstruction();
        assertEquals(7, cycles);
    }

    // ------------------------------------------------------ RRA (remaining modes) ----

    @Test
    void rraIndexedIndirectX() {
        h.writeWord(0x0024, 0x0500);
        h.writeByte(0x0500, 0x04);
        h.prepareInstruction(0x0300, 0x63, 0x20); // RRA ($20,X)
        h.cpu.setAccumulator(0x00);
        h.cpu.setIndexRegisterX(0x04);
        h.cpu.setCarryFlag(false);
        long cycles = h.runInstruction();
        assertEquals(0x02, h.readByte(0x0500)); // 0x04 >> 1
        assertEquals(0x02, h.cpu.getAccumulator()); // 0x00 + 0x02 + 0
        assertEquals(8, cycles);
    }

    @Test
    void rraZeroPageXCycleCount() {
        h.prepareInstruction(0x0300, 0x77, 0x50); // RRA $50,X
        h.cpu.setIndexRegisterX(0x05);
        long cycles = h.runInstruction();
        assertEquals(6, cycles);
    }

    @Test
    void rraZeroPageCycleCount() {
        long cycles = h.execute(0x0300, 0x67, 0x50);
        assertEquals(5, cycles);
    }

    @Test
    void rraAbsoluteYCycleCount() {
        h.prepareInstruction(0x0300, 0x7B, 0x00, 0x04); // RRA $0400,Y
        h.cpu.setIndexRegisterY(0x05);
        long cycles = h.runInstruction();
        assertEquals(7, cycles);
    }

    @Test
    void rraAbsoluteXCycleCount() {
        h.prepareInstruction(0x0300, 0x7F, 0x00, 0x04); // RRA $0400,X
        h.cpu.setIndexRegisterX(0x05);
        long cycles = h.runInstruction();
        assertEquals(7, cycles);
    }

    @Test
    void rraIndirectIndexedYWritesToTheCorrectAddress() {
        // Regression test: this addressing mode resolves into baseAddress (via
        // FETCH_BAL_IAL/FETCH_BAH_IAL), not effectiveAddress, so the RMW cycles
        // must use EXECUTE_MID_BA/STORE_DATA_BA, not the _EA variants.
        h.writeWord(0x0020, 0x0500);
        h.writeByte(0x0505, 0x05);
        h.prepareInstruction(0x0300, 0x73, 0x20); // RRA ($20),Y
        h.cpu.setAccumulator(0x00);
        h.cpu.setIndexRegisterY(0x05);
        h.cpu.setCarryFlag(true); // rotated into bit 7
        long cycles = h.runInstruction();
        assertEquals(0x82, h.readByte(0x0505)); // (0x05 >> 1) | 0x80
        assertEquals(0x83, h.cpu.getAccumulator()); // 0x00 + 0x82 + carry(1, from old bit0=1)
        assertEquals(8, cycles);
    }

    // ------------------------------------------------------ SAX (remaining modes) ----

    @Test
    void saxIndexedIndirectX() {
        // X (0x04) does double duty here, exactly as on real hardware: it both
        // indexes the (Indirect,X) pointer AND is the register value ANDed with A.
        h.writeWord(0x0024, 0x0500);
        h.prepareInstruction(0x0300, 0x83, 0x20); // SAX ($20,X)
        h.cpu.setAccumulator(0xF0);
        h.cpu.setIndexRegisterX(0x04);
        long cycles = h.runInstruction();
        assertEquals(0x00, h.readByte(0x0500)); // 0xF0 & 0x04
        assertEquals(6, cycles);
    }

    @Test
    void saxZeroPage() {
        h.prepareInstruction(0x0300, 0x87, 0x50); // SAX $50
        h.cpu.setAccumulator(0xFF);
        h.cpu.setIndexRegisterX(0x3C);
        long cycles = h.runInstruction();
        assertEquals(0x3C, h.readByte(0x0050));
        assertEquals(3, cycles);
    }

    @Test
    void saxZeroPageY() {
        h.prepareInstruction(0x0300, 0x97, 0x50); // SAX $50,Y
        h.cpu.setAccumulator(0x0F);
        h.cpu.setIndexRegisterX(0xFF);
        h.cpu.setIndexRegisterY(0x05);
        long cycles = h.runInstruction();
        assertEquals(0x0F, h.readByte(0x0055));
        assertEquals(4, cycles);
    }

    // ------------------------------------------------------ LAX (remaining modes) ----

    @Test
    void laxIndexedIndirectX() {
        h.writeWord(0x0024, 0x0500);
        h.writeByte(0x0500, 0x99);
        h.prepareInstruction(0x0300, 0xA3, 0x20); // LAX ($20,X)
        h.cpu.setIndexRegisterX(0x04);
        long cycles = h.runInstruction();
        assertEquals(0x99, h.cpu.getAccumulator());
        assertEquals(0x99, h.cpu.getIndexRegisterX());
        assertEquals(6, cycles);
    }

    @Test
    void laxZeroPage() {
        h.writeByte(0x0050, 0x42);
        long cycles = h.execute(0x0300, 0xA7, 0x50);
        assertEquals(0x42, h.cpu.getAccumulator());
        assertEquals(0x42, h.cpu.getIndexRegisterX());
        assertEquals(3, cycles);
    }

    @Test
    void laxAbsolute() {
        h.writeByte(0x0400, 0x77);
        long cycles = h.execute(0x0300, 0xAF, 0x00, 0x04);
        assertEquals(0x77, h.cpu.getAccumulator());
        assertEquals(0x77, h.cpu.getIndexRegisterX());
        assertEquals(4, cycles);
    }

    @Test
    void laxIndirectIndexedYNoPageCross() {
        h.writeWord(0x0020, 0x0500);
        h.writeByte(0x0505, 0x88);
        h.prepareInstruction(0x0300, 0xB3, 0x20); // LAX ($20),Y
        h.cpu.setIndexRegisterY(0x05);
        long cycles = h.runInstruction();
        assertEquals(0x88, h.cpu.getAccumulator());
        assertEquals(5, cycles);
    }

    @Test
    void laxIndirectIndexedYPageCrossCostsExtraCycle() {
        h.writeWord(0x0020, 0x04FF);
        h.writeByte(0x0501, 0x33);
        h.prepareInstruction(0x0300, 0xB3, 0x20);
        h.cpu.setIndexRegisterY(0x02);
        long cycles = h.runInstruction();
        assertEquals(0x33, h.cpu.getAccumulator());
        assertEquals(6, cycles);
    }

    @Test
    void laxAbsoluteYNoPageCross() {
        h.writeByte(0x0405, 0x21);
        h.prepareInstruction(0x0300, 0xBF, 0x00, 0x04); // LAX $0400,Y
        h.cpu.setIndexRegisterY(0x05);
        long cycles = h.runInstruction();
        assertEquals(0x21, h.cpu.getAccumulator());
        assertEquals(4, cycles);
    }

    @Test
    void laxAbsoluteYPageCrossCostsExtraCycle() {
        h.writeByte(0x0501, 0x55);
        h.prepareInstruction(0x0300, 0xBF, 0xFF, 0x04);
        h.cpu.setIndexRegisterY(0x02);
        long cycles = h.runInstruction();
        assertEquals(0x55, h.cpu.getAccumulator());
        assertEquals(5, cycles);
    }

    // ------------------------------------------------------ ISC (remaining modes) ----

    @Test
    void iscIndexedIndirectX() {
        h.writeWord(0x0024, 0x0500);
        h.writeByte(0x0500, 0x0F);
        h.prepareInstruction(0x0300, 0xE3, 0x20); // ISC ($20,X)
        h.cpu.setAccumulator(0x20);
        h.cpu.setIndexRegisterX(0x04);
        h.cpu.setCarryFlag(true);
        long cycles = h.runInstruction();
        assertEquals(0x10, h.readByte(0x0500));
        assertEquals(0x10, h.cpu.getAccumulator());
        assertEquals(8, cycles);
    }

    @Test
    void iscZeroPageCycleCount() {
        long cycles = h.execute(0x0300, 0xE7, 0x50);
        assertEquals(5, cycles);
    }

    @Test
    void iscAbsoluteCycleCount() {
        long cycles = h.execute(0x0300, 0xEF, 0x00, 0x04);
        assertEquals(6, cycles);
    }

    @Test
    void iscIndirectIndexedY() {
        h.writeWord(0x0020, 0x0500);
        h.writeByte(0x0505, 0x0F);
        h.prepareInstruction(0x0300, 0xF3, 0x20); // ISC ($20),Y
        h.cpu.setAccumulator(0x20);
        h.cpu.setIndexRegisterY(0x05);
        h.cpu.setCarryFlag(true);
        long cycles = h.runInstruction();
        assertEquals(0x10, h.readByte(0x0505));
        assertEquals(0x10, h.cpu.getAccumulator());
        assertEquals(8, cycles);
    }

    @Test
    void iscAbsoluteYCycleCount() {
        h.prepareInstruction(0x0300, 0xFB, 0x00, 0x04); // ISC $0400,Y
        h.cpu.setIndexRegisterY(0x05);
        long cycles = h.runInstruction();
        assertEquals(7, cycles);
    }

    @Test
    void iscAbsoluteXCycleCount() {
        h.prepareInstruction(0x0300, 0xFF, 0x00, 0x04); // ISC $0400,X
        h.cpu.setIndexRegisterX(0x05);
        long cycles = h.runInstruction();
        assertEquals(7, cycles);
    }

    // ------------------------------------------------------ ANC / SBC duplicates ----

    @Test
    void ancDuplicateOpcodeBehavesIdenticallyTo0x0B() {
        h.prepareInstruction(0x0300, 0x2B, 0x81); // ANC #$81 (duplicate of 0x0B)
        h.cpu.setAccumulator(0xFF);
        long cycles = h.runInstruction();
        assertEquals(0x81, h.cpu.getAccumulator());
        assertTrue(h.cpu.getNegativeResultFlag());
        assertTrue(h.cpu.getCarryFlag());
        assertEquals(2, cycles);
    }

    @Test
    void sbcDuplicateOpcodeBehavesIdenticallyTo0xE9() {
        h.prepareInstruction(0x0300, 0xEB, 0x30); // SBC #$30 (duplicate of 0xE9)
        h.cpu.setAccumulator(0x50);
        h.cpu.setCarryFlag(true);
        long cycles = h.runInstruction();
        assertEquals(0x20, h.cpu.getAccumulator());
        assertTrue(h.cpu.getCarryFlag());
        assertEquals(2, cycles);
    }

    // ------------------------------------------------------ SBX ----

    @Test
    void sbxSubtractsOperandFromAndedAccumulatorAndX() {
        h.prepareInstruction(0x0300, 0xCB, 0x10); // SBX #$10
        h.cpu.setAccumulator(0xFF);
        h.cpu.setIndexRegisterX(0x3C);
        long cycles = h.runInstruction();
        assertEquals(0x2C, h.cpu.getIndexRegisterX()); // (0xFF & 0x3C) - 0x10 = 0x3C - 0x10
        assertEquals(0xFF, h.cpu.getAccumulator()); // A itself is unaffected
        assertTrue(h.cpu.getCarryFlag()); // no borrow
        assertEquals(2, cycles);
    }

    @Test
    void sbxSetsBorrowWhenOperandExceedsAndedValue() {
        h.prepareInstruction(0x0300, 0xCB, 0x50);
        h.cpu.setAccumulator(0x0F);
        h.cpu.setIndexRegisterX(0x0F);
        h.runInstruction();
        assertEquals(0xBF, h.cpu.getIndexRegisterX()); // (0x0F - 0x50) & 0xFF
        assertFalse(h.cpu.getCarryFlag()); // borrow occurred
        assertTrue(h.cpu.getNegativeResultFlag());
    }

    // ------------------------------------------------------ ALR ----

    @Test
    void alrAndsThenShiftsAccumulatorRight() {
        h.prepareInstruction(0x0300, 0x4B, 0x03); // ALR #$03
        h.cpu.setAccumulator(0xFF);
        long cycles = h.runInstruction();
        assertEquals(0x01, h.cpu.getAccumulator()); // (0xFF & 0x03) >> 1 = 0x03 >> 1
        assertTrue(h.cpu.getCarryFlag()); // old bit0 of the AND result was 1
        assertEquals(2, cycles);
    }

    @Test
    void alrClearsCarryWhenAndResultIsEven() {
        h.prepareInstruction(0x0300, 0x4B, 0x02);
        h.cpu.setAccumulator(0xFF);
        h.runInstruction();
        assertEquals(0x01, h.cpu.getAccumulator());
        assertFalse(h.cpu.getCarryFlag());
    }

    // ------------------------------------------------------ ANE / LXA (unstable) ----
    //
    // These two opcodes are genuinely unstable on real hardware: (A OR magic) AND ...,
    // where "magic" depends on chip batch/temperature/bus capacitance decay and has no
    // single correct value. Cpu6502 approximates it with a fixed UNSTABLE_MAGIC_CONSTANT
    // (0xEE); these tests pin down that approximation rather than "the" real answer.

    @Test
    void aneApproximatesMagicOrAndXAndOperand() {
        h.prepareInstruction(0x0300, 0x8B, 0xFF); // ANE #$FF
        h.cpu.setAccumulator(0x00);
        h.cpu.setIndexRegisterX(0xFF);
        long cycles = h.runInstruction();
        assertEquals(0xEE, h.cpu.getAccumulator()); // (0x00 | 0xEE) & 0xFF & 0xFF
        assertTrue(h.cpu.getNegativeResultFlag());
        assertEquals(2, cycles);
    }

    @Test
    void aneAndsXIntoTheMagicConstant() {
        h.prepareInstruction(0x0300, 0x8B, 0xFF); // ANE #$FF
        h.cpu.setAccumulator(0x00);
        h.cpu.setIndexRegisterX(0x0F);
        h.runInstruction();
        assertEquals(0x0E, h.cpu.getAccumulator()); // (0x00 | 0xEE) & 0x0F & 0xFF
        assertFalse(h.cpu.getNegativeResultFlag());
    }

    @Test
    void lxaLoadsBothAccumulatorAndXFromMagicOrAndOperand() {
        h.prepareInstruction(0x0300, 0xAB, 0xFF); // LXA #$FF
        h.cpu.setAccumulator(0x00);
        long cycles = h.runInstruction();
        assertEquals(0xEE, h.cpu.getAccumulator()); // (0x00 | 0xEE) & 0xFF
        assertEquals(0xEE, h.cpu.getIndexRegisterX());
        assertTrue(h.cpu.getNegativeResultFlag());
        assertEquals(2, cycles);
    }

    // ------------------------------------------------------ SHA / SHX / SHY / TAS (unstable) ----
    //
    // "M AND (highByteOfEffectiveAddress + 1)"-family opcodes. Unstable on real hardware
    // (the "+1" term isn't always reliably applied); these tests pin down Cpu6502's
    // deterministic approximation.

    @Test
    void shaAbsoluteYStoresAAndXAndAddressHighPlusOne() {
        h.writeByte(0x0410, 0x00);
        h.prepareInstruction(0x0300, 0x9F, 0x00, 0x04); // SHA $0400,Y
        h.cpu.setAccumulator(0xFF);
        h.cpu.setIndexRegisterX(0xFF);
        h.cpu.setIndexRegisterY(0x10);
        long cycles = h.runInstruction();
        assertEquals(0x05, h.readByte(0x0410)); // 0xFF & 0xFF & (0x04 + 1)
        assertEquals(5, cycles);
    }

    @Test
    void shaIndirectYStoresAAndXAndAddressHighPlusOne() {
        h.writeWord(0x0020, 0x0400); // pointer resolves to $0400
        h.writeByte(0x0410, 0x00);
        h.prepareInstruction(0x0300, 0x93, 0x20); // SHA ($20),Y
        h.cpu.setAccumulator(0xFF);
        h.cpu.setIndexRegisterX(0xFF);
        h.cpu.setIndexRegisterY(0x10);
        long cycles = h.runInstruction();
        assertEquals(0x05, h.readByte(0x0410)); // 0xFF & 0xFF & (0x04 + 1)
        assertEquals(6, cycles);
    }

    @Test
    void shxAbsoluteYStoresXAndAddressHighPlusOne() {
        h.writeByte(0x0410, 0x00);
        h.prepareInstruction(0x0300, 0x9E, 0x00, 0x04); // SHX $0400,Y
        h.cpu.setIndexRegisterX(0xFF);
        h.cpu.setIndexRegisterY(0x10);
        long cycles = h.runInstruction();
        assertEquals(0x05, h.readByte(0x0410)); // 0xFF & (0x04 + 1)
        assertEquals(5, cycles);
    }

    @Test
    void shyAbsoluteXStoresYAndAddressHighPlusOne() {
        h.writeByte(0x0410, 0x00);
        h.prepareInstruction(0x0300, 0x9C, 0x00, 0x04); // SHY $0400,X
        h.cpu.setIndexRegisterY(0xFF);
        h.cpu.setIndexRegisterX(0x10);
        long cycles = h.runInstruction();
        assertEquals(0x05, h.readByte(0x0410)); // 0xFF & (0x04 + 1)
        assertEquals(5, cycles);
    }

    @Test
    void tasSetsStackPointerToAAndXThenStoresItAndedWithAddressHighPlusOne() {
        h.writeByte(0x0410, 0x00);
        h.prepareInstruction(0x0300, 0x9B, 0x00, 0x04); // TAS $0400,Y
        h.cpu.setAccumulator(0xFF);
        h.cpu.setIndexRegisterX(0x3C);
        h.cpu.setIndexRegisterY(0x10);
        long cycles = h.runInstruction();
        assertEquals(0x3C, h.cpu.getStackPointer()); // S = A & X
        assertEquals(0x04, h.readByte(0x0410)); // 0x3C & (0x04 + 1)
        assertEquals(5, cycles);
    }

    // ------------------------------------------------------ LAS (unstable) ----

    @Test
    void lasAndsMemoryWithStackPointerIntoAXAndS() {
        h.writeByte(0x0410, 0x3C);
        h.prepareInstruction(0x0300, 0xBB, 0x00, 0x04); // LAS $0400,Y
        h.cpu.setIndexRegisterY(0x10);
        h.cpu.setStackPointer(0xFF);
        long cycles = h.runInstruction();
        assertEquals(0x3C, h.cpu.getAccumulator());
        assertEquals(0x3C, h.cpu.getIndexRegisterX());
        assertEquals(0x3C, h.cpu.getStackPointer());
        assertEquals(4, cycles); // no page crossing
    }

    @Test
    void lasCrossesPageBoundaryForOneExtraCycle() {
        h.writeByte(0x0500, 0x0F);
        h.prepareInstruction(0x0300, 0xBB, 0xFF, 0x04); // LAS $04FF,Y
        h.cpu.setIndexRegisterY(0x01); // $04FF + 1 = $0500, crosses page
        h.cpu.setStackPointer(0xFF);
        long cycles = h.runInstruction();
        assertEquals(0x0F, h.cpu.getAccumulator());
        assertEquals(5, cycles);
    }
}
