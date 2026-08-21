package emu.jvic.cpu;

import emu.jvic.testutil.CpuTestHarness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TAX / TAY / TXA / TYA / TSX / TXS. All are 2-cycle implied instructions. All set
 * N/Z from the transferred value EXCEPT TXS, which per the 6502 reference does not
 * affect any flags (it just loads the stack pointer).
 */
public class TransferInstructionsTest {

    private CpuTestHarness h;

    @BeforeEach
    void setUp() {
        h = new CpuTestHarness();
    }

    @Test
    void tax() {
        h.prepareInstruction(0x0300, 0xAA);
        h.cpu.setAccumulator(0x55);
        long cycles = h.runInstruction();
        assertEquals(0x55, h.cpu.getIndexRegisterX());
        assertEquals(2, cycles);
    }

    @Test
    void taxSetsNegativeFlag() {
        h.prepareInstruction(0x0300, 0xAA);
        h.cpu.setAccumulator(0x80);
        h.runInstruction();
        assertTrue(h.cpu.getNegativeResultFlag());
    }

    @Test
    void taxSetsZeroFlag() {
        h.prepareInstruction(0x0300, 0xAA);
        h.cpu.setAccumulator(0x00);
        h.runInstruction();
        assertTrue(h.cpu.getZeroResultFlag());
    }

    @Test
    void tay() {
        h.prepareInstruction(0x0300, 0xA8);
        h.cpu.setAccumulator(0x66);
        long cycles = h.runInstruction();
        assertEquals(0x66, h.cpu.getIndexRegisterY());
        assertEquals(2, cycles);
    }

    @Test
    void txa() {
        h.prepareInstruction(0x0300, 0x8A);
        h.cpu.setIndexRegisterX(0x77);
        long cycles = h.runInstruction();
        assertEquals(0x77, h.cpu.getAccumulator());
        assertEquals(2, cycles);
    }

    @Test
    void tya() {
        h.prepareInstruction(0x0300, 0x98);
        h.cpu.setIndexRegisterY(0x88);
        long cycles = h.runInstruction();
        assertEquals(0x88, h.cpu.getAccumulator());
        assertTrue(h.cpu.getNegativeResultFlag());
        assertEquals(2, cycles);
    }

    @Test
    void tsx() {
        h.prepareInstruction(0x0300, 0xBA);
        h.cpu.setStackPointer(0x99);
        long cycles = h.runInstruction();
        assertEquals(0x99, h.cpu.getIndexRegisterX());
        assertTrue(h.cpu.getNegativeResultFlag());
        assertEquals(2, cycles);
    }

    @Test
    void txsLoadsStackPointerWithoutAffectingFlags() {
        h.prepareInstruction(0x0300, 0x9A); // TXS
        h.cpu.setIndexRegisterX(0x80); // would set N if flags were (incorrectly) touched
        h.cpu.setNegativeResultFlag(false);
        h.cpu.setZeroResultFlag(true);
        long cycles = h.runInstruction();
        assertEquals(0x80, h.cpu.getStackPointer());
        assertFalse(h.cpu.getNegativeResultFlag());
        assertTrue(h.cpu.getZeroResultFlag());
        assertEquals(2, cycles);
    }
}
