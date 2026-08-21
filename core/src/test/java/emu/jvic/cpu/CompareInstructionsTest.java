package emu.jvic.cpu;

import emu.jvic.testutil.CpuTestHarness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CMP / CPX / CPY. These compute register-minus-operand without storing the
 * result, setting flags as if by subtraction: C=1 means "no borrow" (register >=
 * operand), Z=1 means equal, N reflects bit 7 of the (unstored) subtraction result.
 */
public class CompareInstructionsTest {

    private CpuTestHarness h;

    @BeforeEach
    void setUp() {
        h = new CpuTestHarness();
    }

    @Test
    void cmpEqualSetsZeroAndCarry() {
        h.prepareInstruction(0x0300, 0xC9, 0x40); // CMP #$40
        h.cpu.setAccumulator(0x40);
        long cycles = h.runInstruction();
        assertTrue(h.cpu.getZeroResultFlag());
        assertTrue(h.cpu.getCarryFlag());
        assertFalse(h.cpu.getNegativeResultFlag());
        assertEquals(2, cycles);
    }

    @Test
    void cmpAccumulatorGreaterSetsCarryOnly() {
        h.prepareInstruction(0x0300, 0xC9, 0x10);
        h.cpu.setAccumulator(0x40);
        h.runInstruction();
        assertTrue(h.cpu.getCarryFlag());
        assertFalse(h.cpu.getZeroResultFlag());
    }

    @Test
    void cmpAccumulatorLessClearsCarry() {
        h.prepareInstruction(0x0300, 0xC9, 0x40);
        h.cpu.setAccumulator(0x10);
        h.runInstruction();
        assertFalse(h.cpu.getCarryFlag());
        assertFalse(h.cpu.getZeroResultFlag());
        assertTrue(h.cpu.getNegativeResultFlag()); // 0x10 - 0x40 = 0xD0, bit 7 set
    }

    @Test
    void cmpDoesNotModifyAccumulator() {
        h.prepareInstruction(0x0300, 0xC9, 0x01);
        h.cpu.setAccumulator(0x02);
        h.runInstruction();
        assertEquals(0x02, h.cpu.getAccumulator());
    }

    @Test
    void cmpAbsoluteAddressingMode() {
        h.writeByte(0x0400, 0x30);
        h.prepareInstruction(0x0300, 0xCD, 0x00, 0x04); // CMP $0400
        h.cpu.setAccumulator(0x30);
        long cycles = h.runInstruction();
        assertTrue(h.cpu.getZeroResultFlag());
        assertEquals(4, cycles);
    }

    @Test
    void cpxEqual() {
        h.prepareInstruction(0x0300, 0xE0, 0x05); // CPX #$05
        h.cpu.setIndexRegisterX(0x05);
        long cycles = h.runInstruction();
        assertTrue(h.cpu.getZeroResultFlag());
        assertTrue(h.cpu.getCarryFlag());
        assertEquals(2, cycles);
    }

    @Test
    void cpyLessThan() {
        h.prepareInstruction(0x0300, 0xC0, 0x10); // CPY #$10
        h.cpu.setIndexRegisterY(0x05);
        long cycles = h.runInstruction();
        assertFalse(h.cpu.getCarryFlag());
        assertFalse(h.cpu.getZeroResultFlag());
        assertEquals(2, cycles);
    }
}
