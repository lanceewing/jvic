package emu.jvic.cpu;

import emu.jvic.testutil.CpuTestHarness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PHA / PHP / PLA / PLP. Stack lives at $0100-$01FF, addressed by the 8-bit stack
 * pointer (push = write then decrement; pull = increment then read). PHP (like BRK)
 * always pushes the status byte with bits 4 (B) and 5 (unused) set to 1, even though
 * neither is a real, physically-stored processor flag.
 */
public class StackInstructionsTest {

    private CpuTestHarness h;

    @BeforeEach
    void setUp() {
        h = new CpuTestHarness();
    }

    @Test
    void phaPushesAccumulatorAndDecrementsStackPointer() {
        h.prepareInstruction(0x0300, 0x48); // PHA
        h.cpu.setAccumulator(0x42);
        h.cpu.setStackPointer(0xFF);
        long cycles = h.runInstruction();
        assertEquals(0x42, h.readByte(0x01FF));
        assertEquals(0xFE, h.cpu.getStackPointer());
        assertEquals(3, cycles);
    }

    @Test
    void phaDoesNotAffectFlags() {
        h.prepareInstruction(0x0300, 0x48);
        h.cpu.setAccumulator(0x00);
        h.cpu.setZeroResultFlag(false);
        h.runInstruction();
        assertFalse(h.cpu.getZeroResultFlag());
    }

    @Test
    void stackPointerWrapsFromZeroToFF() {
        h.prepareInstruction(0x0300, 0x48);
        h.cpu.setAccumulator(0x99);
        h.cpu.setStackPointer(0x00);
        h.runInstruction();
        assertEquals(0x99, h.readByte(0x0100));
        assertEquals(0xFF, h.cpu.getStackPointer());
    }

    @Test
    void phpPushesStatusWithBreakAndUnusedBitsSet() {
        h.prepareInstruction(0x0300, 0x08); // PHP
        h.cpu.setStackPointer(0xFF);
        h.cpu.setCarryFlag(true);
        h.cpu.setZeroResultFlag(false);
        h.cpu.setInterruptDisableFlag(false);
        h.cpu.setDecimalModeFlag(false);
        h.cpu.setOverflowFlag(false);
        h.cpu.setNegativeResultFlag(false);
        long cycles = h.runInstruction();
        int pushed = h.readByte(0x01FF);
        assertEquals(0x31, pushed); // carry(0x01) | unused(0x20) | break(0x10)
        assertEquals(3, cycles);
    }

    @Test
    void plaPullsAccumulatorAndSetsFlags() {
        h.writeByte(0x01FF, 0x00);
        h.prepareInstruction(0x0300, 0x68); // PLA
        h.cpu.setStackPointer(0xFE);
        long cycles = h.runInstruction();
        assertEquals(0x00, h.cpu.getAccumulator());
        assertTrue(h.cpu.getZeroResultFlag());
        assertEquals(0xFF, h.cpu.getStackPointer());
        assertEquals(4, cycles);
    }

    @Test
    void plaSetsNegativeFlag() {
        h.writeByte(0x01FF, 0x80);
        h.prepareInstruction(0x0300, 0x68);
        h.cpu.setStackPointer(0xFE);
        h.runInstruction();
        assertEquals(0x80, h.cpu.getAccumulator());
        assertTrue(h.cpu.getNegativeResultFlag());
    }

    @Test
    void plpRestoresFlagsFromStack() {
        h.writeByte(0x01FF, 0x81); // N + C set, everything else clear
        h.prepareInstruction(0x0300, 0x28); // PLP
        h.cpu.setStackPointer(0xFE);
        long cycles = h.runInstruction();
        assertTrue(h.cpu.getNegativeResultFlag());
        assertTrue(h.cpu.getCarryFlag());
        assertFalse(h.cpu.getZeroResultFlag());
        assertFalse(h.cpu.getOverflowFlag());
        assertEquals(4, cycles);
    }

    @Test
    void plpIgnoresBreakBitSincePhysicallyItDoesNotExist() {
        // Push a byte with the break bit (0x10) set; after PLP + re-reading status
        // via getProcessorStatus() (which repacks from the individual flags), bit 4
        // must NOT reappear, because B is not a real, stored processor flag.
        h.writeByte(0x01FF, 0x10);
        h.prepareInstruction(0x0300, 0x28);
        h.cpu.setStackPointer(0xFE);
        h.runInstruction();
        int repacked = h.cpu.getProcessorStatus();
        assertEquals(0, repacked & 0x10);
    }

    @Test
    void phaThenPlaRoundTrips() {
        // Both instructions are written up-front since the CPU pipelines the next
        // opcode fetch into the previous instruction's last cycle; priming/running
        // must therefore flow through the whole sequence without an intervening
        // reset() (unlike prepareInstruction(), which is for isolated instructions).
        h.loadBytes(0x0300, 0x48); // PHA
        h.loadBytes(0x0301, 0x68); // PLA
        h.cpu.reset();
        h.cpu.setProgramCounter(0x0300);
        h.cpu.setAccumulator(0x5A);
        h.cpu.setStackPointer(0xFF);

        h.cpu.step(); // primes: fetches PHA's opcode
        h.cpu.step(); // runs PHA to completion, fetches PLA's opcode
        h.cpu.setAccumulator(0x00);
        h.cpu.step(); // runs PLA to completion

        assertEquals(0x5A, h.cpu.getAccumulator());
        assertEquals(0xFF, h.cpu.getStackPointer());
    }
}
