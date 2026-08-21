package emu.jvic.cpu;

import emu.jvic.testutil.CpuTestHarness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ASL / LSR / ROL / ROR, both accumulator and memory (read-modify-write) forms.
 * RMW forms on this emulator perform a dummy write of the unmodified value before
 * writing the final result (real 6502 behaviour), so zero-page RMW is 5 cycles and
 * absolute RMW is 6.
 */
public class ShiftRotateInstructionsTest {

    private CpuTestHarness h;

    @BeforeEach
    void setUp() {
        h = new CpuTestHarness();
    }

    @Test
    void aslAccumulator() {
        h.prepareInstruction(0x0300, 0x0A); // ASL A
        h.cpu.setAccumulator(0x40);
        long cycles = h.runInstruction();
        assertEquals(0x80, h.cpu.getAccumulator());
        assertFalse(h.cpu.getCarryFlag());
        assertTrue(h.cpu.getNegativeResultFlag());
        assertEquals(2, cycles);
    }

    @Test
    void aslAccumulatorSetsCarryFromBit7() {
        h.prepareInstruction(0x0300, 0x0A);
        h.cpu.setAccumulator(0x80);
        h.runInstruction();
        assertEquals(0x00, h.cpu.getAccumulator());
        assertTrue(h.cpu.getCarryFlag());
        assertTrue(h.cpu.getZeroResultFlag());
    }

    @Test
    void aslZeroPage() {
        h.writeByte(0x0050, 0x01);
        long cycles = h.execute(0x0300, 0x06, 0x50); // ASL $50
        assertEquals(0x02, h.readByte(0x0050));
        assertEquals(5, cycles);
    }

    @Test
    void aslAbsolute() {
        h.writeByte(0x0400, 0x01);
        long cycles = h.execute(0x0300, 0x0E, 0x00, 0x04); // ASL $0400
        assertEquals(0x02, h.readByte(0x0400));
        assertEquals(6, cycles);
    }

    @Test
    void lsrAccumulatorSetsCarryFromBit0() {
        h.prepareInstruction(0x0300, 0x4A); // LSR A
        h.cpu.setAccumulator(0x01);
        long cycles = h.runInstruction();
        assertEquals(0x00, h.cpu.getAccumulator());
        assertTrue(h.cpu.getCarryFlag());
        assertTrue(h.cpu.getZeroResultFlag());
        assertEquals(2, cycles);
    }

    @Test
    void lsrNeverSetsNegativeFlag() {
        // Bit 7 is always shifted in as 0, so LSR can never produce a negative result.
        h.prepareInstruction(0x0300, 0x4A);
        h.cpu.setAccumulator(0xFF);
        h.runInstruction();
        assertFalse(h.cpu.getNegativeResultFlag());
    }

    @Test
    void rolAccumulatorRotatesCarryIntoBit0() {
        h.prepareInstruction(0x0300, 0x2A); // ROL A
        h.cpu.setAccumulator(0x00);
        h.cpu.setCarryFlag(true);
        long cycles = h.runInstruction();
        assertEquals(0x01, h.cpu.getAccumulator());
        assertFalse(h.cpu.getCarryFlag());
        assertEquals(2, cycles);
    }

    @Test
    void rolAccumulatorSetsCarryFromOldBit7() {
        h.prepareInstruction(0x0300, 0x2A);
        h.cpu.setAccumulator(0x80);
        h.cpu.setCarryFlag(false);
        h.runInstruction();
        assertEquals(0x00, h.cpu.getAccumulator());
        assertTrue(h.cpu.getCarryFlag());
    }

    @Test
    void rorAccumulatorRotatesCarryIntoBit7() {
        h.prepareInstruction(0x0300, 0x6A); // ROR A
        h.cpu.setAccumulator(0x00);
        h.cpu.setCarryFlag(true);
        long cycles = h.runInstruction();
        assertEquals(0x80, h.cpu.getAccumulator());
        assertTrue(h.cpu.getNegativeResultFlag());
        assertFalse(h.cpu.getCarryFlag());
        assertEquals(2, cycles);
    }

    @Test
    void rorZeroPageX() {
        h.writeByte(0x0055, 0x01);
        h.prepareInstruction(0x0300, 0x76, 0x50); // ROR $50,X
        h.cpu.setIndexRegisterX(0x05);
        h.cpu.setCarryFlag(false);
        long cycles = h.runInstruction();
        assertEquals(0x00, h.readByte(0x0055));
        assertTrue(h.cpu.getCarryFlag());
        assertEquals(6, cycles);
    }

    @Test
    void aslAbsoluteXCostsSevenCyclesRegardlessOfPageCross() {
        h.writeByte(0x0405, 0x01);
        h.prepareInstruction(0x0300, 0x1E, 0x00, 0x04); // ASL $0400,X
        h.cpu.setIndexRegisterX(0x05);
        long cycles = h.runInstruction();
        assertEquals(0x02, h.readByte(0x0405));
        assertEquals(7, cycles);
    }
}
