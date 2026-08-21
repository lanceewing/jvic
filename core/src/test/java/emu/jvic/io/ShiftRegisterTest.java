package emu.jvic.io;

import emu.jvic.testutil.ViaTestHarness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static emu.jvic.testutil.ViaTestHarness.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The 8 shift register modes selected by ACR bits 2-4: disabled, shift-in under
 * Timer 2 / system clock / external clock, and free-running / Timer-2 / system
 * clock / external clock shift-OUT.
 *
 * <p>This emulator implements the shift register as a circular (rotating) shifter
 * in output mode: the bit shifted out onto CB2 is simultaneously shifted back in
 * at bit 0. So after a full 8-bit rotation the register reads back its ORIGINAL
 * value - a convenient, precise way to verify a complete shift-out cycle.
 *
 * <p>Empirically confirmed: writing SR suppresses shifting for exactly the next
 * {@code emulateCycle()} call (real hardware also does not shift on the write's
 * own cycle); the system clock mode then shifts exactly once per cycle, so the
 * SHIFT_SET interrupt flag reads false through the 8th call after the write and
 * becomes true from the 9th call onward (8 shifts total).
 */
public class ShiftRegisterTest {

    private ViaTestHarness h;

    @BeforeEach
    void setUp() {
        h = new ViaTestHarness();
    }

    private boolean shiftFired() {
        return (h.read(IFR) & SHIFT_BIT) != 0;
    }

    @Test
    void disabledModeNeverShiftsOrFires() {
        h.write(ACR, 0x00); // shift register disabled
        h.write(SR, 0xAA);

        h.cycles(20);
        assertFalse(shiftFired());
        assertEquals(0xAA, h.read(SR));
    }

    @Test
    void systemClockShiftOutRotatesOneBitPerCycle() {
        h.write(ACR, 0x18); // shift out, system clock
        h.write(SR, 0x81);

        h.cycles(8);
        assertFalse(shiftFired(), "must not have completed 8 shifts yet");
        h.cycle(); // 9th cycle since the write: the 8th actual shift
        assertTrue(shiftFired());
    }

    @Test
    void systemClockShiftOutIsACircularRotate() {
        h.write(ACR, 0x18);
        h.write(SR, 0x81);
        h.cycles(9); // a full 8-bit rotation

        // Read via the harness's raw memory array equivalent (readMemory) without
        // triggering the "read clears flag and resets counter" side effect twice;
        // a single read here is fine.
        assertEquals(0x81, h.read(SR), "a full 8-shift rotation returns the register to its original value");
    }

    @Test
    void systemClockShiftOutProducesExpectedCb2Sequence() {
        h.write(ACR, 0x18);
        h.write(SR, 0x81); // 1000 0001

        h.cycle(); // write-suppression cycle: no shift yet
        assertEquals(0, h.via.getCb2());

        int[] expectedCb2 = {1, 0, 0, 0, 0, 0, 0, 1}; // MSB-first, then the low 7 zero bits, then the wrapped bit again
        for (int expected : expectedCb2) {
            h.cycle();
            assertEquals(expected, h.via.getCb2());
        }
    }

    @Test
    void systemClockShiftInAssemblesExternalBitsMsbFirst() {
        h.write(ACR, 0x08); // shift in, system clock
        h.write(SR, 0x00);

        h.cycle(); // write-suppression cycle
        int[] bitsToShiftIn = {1, 0, 1, 1, 0, 0, 1, 0}; // -> 0xB2 once fully shifted in
        for (int bit : bitsToShiftIn) {
            h.via.setCb2(bit);
            h.cycle();
        }

        assertTrue(shiftFired());
        assertEquals(0xB2, h.read(SR));
    }

    @Test
    void writingShiftRegisterWhileFlagIsSetClearsFlagAndResetsCounter() {
        h.write(ACR, 0x18);
        h.write(SR, 0x81);
        h.cycles(9);
        assertTrue(shiftFired());

        h.write(SR, 0x00); // fresh write while the flag is still set
        assertFalse(shiftFired());
    }

    @Test
    void externalClockShiftOutRespondsToCb1Transitions() {
        h.write(ACR, 0x1C); // shift out, external clock
        h.write(SR, 0x81);

        // Toggle the external clock (CB1) pin repeatedly; a shift happens on
        // every transition. Run generously past the 8 shifts needed.
        for (int i = 0; i < 20 && !shiftFired(); i++) {
            h.via.cb1Input ^= 1;
            h.cycle();
        }

        assertTrue(shiftFired(), "external-clock shift-out should complete well within 20 clock edges");
        assertEquals(0x81, h.read(SR), "a full rotation returns the register to its original value");
    }

    @Test
    void timer2ControlledShiftOutEventuallyCompletesARotation() {
        h.write(ACR, 0x14); // shift out, Timer 2 control
        h.writeWord(T2C_L, T2C_H, 1); // small latch for a fast, easily-bounded test
        h.write(SR, 0x81);

        h.cycles(40); // generous upper bound for 8 shifts at this rate
        assertTrue(shiftFired(), "Timer-2-controlled shift-out should have completed by now");
        assertEquals(0x81, h.read(SR), "a full rotation returns the register to its original value");
    }
}
