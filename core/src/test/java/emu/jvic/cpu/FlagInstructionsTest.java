package emu.jvic.cpu;

import emu.jvic.testutil.CpuTestHarness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CLC / SEC / CLD / SED / CLI / SEI / CLV. Single-flag, 2-cycle implied
 * instructions. Each must affect only its own flag.
 */
public class FlagInstructionsTest {

    private CpuTestHarness h;

    @BeforeEach
    void setUp() {
        h = new CpuTestHarness();
    }

    @Test
    void clc() {
        h.prepareInstruction(0x0300, 0x18);
        h.cpu.setCarryFlag(true);
        long cycles = h.runInstruction();
        assertFalse(h.cpu.getCarryFlag());
        assertEquals(2, cycles);
    }

    @Test
    void sec() {
        h.prepareInstruction(0x0300, 0x38);
        h.cpu.setCarryFlag(false);
        long cycles = h.runInstruction();
        assertTrue(h.cpu.getCarryFlag());
        assertEquals(2, cycles);
    }

    @Test
    void cld() {
        h.prepareInstruction(0x0300, 0xD8);
        h.cpu.setDecimalModeFlag(true);
        h.runInstruction();
        assertFalse(h.cpu.getDecimalModeFlag());
    }

    @Test
    void sed() {
        h.prepareInstruction(0x0300, 0xF8);
        h.cpu.setDecimalModeFlag(false);
        h.runInstruction();
        assertTrue(h.cpu.getDecimalModeFlag());
    }

    @Test
    void cli() {
        h.prepareInstruction(0x0300, 0x58);
        h.cpu.setInterruptDisableFlag(true);
        h.runInstruction();
        assertFalse(h.cpu.getInterruptDisableFlag());
    }

    @Test
    void sei() {
        h.prepareInstruction(0x0300, 0x78);
        h.cpu.setInterruptDisableFlag(false);
        h.runInstruction();
        assertTrue(h.cpu.getInterruptDisableFlag());
    }

    @Test
    void clv() {
        h.prepareInstruction(0x0300, 0xB8);
        h.cpu.setOverflowFlag(true);
        h.runInstruction();
        assertFalse(h.cpu.getOverflowFlag());
    }

    @Test
    void secOnlyAffectsCarry() {
        h.prepareInstruction(0x0300, 0x38);
        h.cpu.setZeroResultFlag(true);
        h.cpu.setNegativeResultFlag(true);
        h.cpu.setOverflowFlag(true);
        h.cpu.setDecimalModeFlag(true);
        h.cpu.setInterruptDisableFlag(true);
        h.runInstruction();
        assertTrue(h.cpu.getZeroResultFlag());
        assertTrue(h.cpu.getNegativeResultFlag());
        assertTrue(h.cpu.getOverflowFlag());
        assertTrue(h.cpu.getDecimalModeFlag());
        assertTrue(h.cpu.getInterruptDisableFlag());
    }
}
