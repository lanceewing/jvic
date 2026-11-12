package emu.jvic.io;

import emu.jvic.testutil.ViaTestHarness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static emu.jvic.testutil.ViaTestHarness.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Timer 2: one-shot mode (ACR bit5=0, interrupt-on-timeout) and the PB6
 * pulse-counting mode (ACR bit5=1).
 *
 * <p>Unlike Timer 1, Timer 2's one-shot interrupt fires when the counter reaches
 * exactly 0 (not 0xFFFF) - per Via6522's own comment, this was confirmed against
 * real hardware behaviour. Empirically confirmed here: after writing T2C-H with
 * latch value N, the flag reads false for the first N+1 calls and becomes true
 * from the (N+2)th call onward (one fewer "extra" cycle than Timer 1's N+2/N+3).
 */
public class Timer2Test {

    private ViaTestHarness h;

    @BeforeEach
    void setUp() {
        h = new ViaTestHarness();
    }

    private boolean t2Fired() {
        return (h.read(IFR) & TIMER2_BIT) != 0;
    }

    private void loadT2(int value) {
        h.write(T2C_L, value & 0xFF);
        h.write(T2C_H, (value >> 8) & 0xFF);
    }

    @Test
    void oneShotFiresOnTheLatchPlusTwoCycleAfterLoad() {
        h.write(ACR, 0x00); // T2 one-shot (bit5=0)
        loadT2(2);

        h.cycles(3); // N+1
        assertFalse(t2Fired());
        h.cycle(); // N+2
        assertTrue(t2Fired());
    }

    @Test
    void oneShotWithZeroLatchFiresOnTheSecondCycle() {
        h.write(ACR, 0x00);
        loadT2(0);

        h.cycle();
        assertFalse(t2Fired());
        h.cycle();
        assertTrue(t2Fired());
    }

    @Test
    void oneShotFiresOnlyOnceEvenIfLeftUnacknowledged() {
        h.write(ACR, 0x00);
        loadT2(0);
        h.cycles(2);
        assertTrue(t2Fired());

        h.cycles(20);
        assertTrue(t2Fired());
    }

    /**
     * More than a full 16-bit wrap of the counter, so it is certain to pass through
     * zero at least once whatever value it started at.
     */
    private static final int MORE_THAN_A_FULL_WRAP = 0x10000 + 16;

    // On the real chip, T2_active_ff (set by a T2C-H write, cleared by an underflow)
    // also has RES ORed into its reset input (6522 netlist, "15d) T2H control"), so
    // the chip comes out of reset with Timer 2 counting but disarmed.

    @Test
    void timer2IsDisarmedAtPowerOnAndDoesNotFireUntilT2HighIsWritten() {
        h.write(ACR, 0x00);

        h.cycles(MORE_THAN_A_FULL_WRAP);
        assertFalse(t2Fired(), "T2 must not fire after power-on before T2C-H has been written");

        loadT2(0);
        h.cycle();
        assertFalse(t2Fired());
        h.cycle();
        assertTrue(t2Fired(), "the T2C-H write arms T2, so it must now fire as normal");
    }

    @Test
    void writingOnlyTheT2LowLatchAfterPowerOnDoesNotArmTimer2() {
        h.write(ACR, 0x00);
        h.write(T2C_L, 0x00);

        h.cycles(MORE_THAN_A_FULL_WRAP);
        assertFalse(t2Fired(), "only a T2C-H write arms T2");
    }

    @Test
    void timer2IsDisarmedAtPowerOnInPb6PulseCountingModeToo() {
        h.write(ACR, 0x20);

        for (int i = 0; i < MORE_THAN_A_FULL_WRAP; i++) {
            pulsePb6();
        }
        assertFalse(t2Fired(), "T2 must not fire after power-on before T2C-H has been written");
    }

    @Test
    void readingCounterLowByteClearsInterruptFlag() {
        h.write(ACR, 0x00);
        loadT2(0);
        h.cycles(2);
        assertTrue(t2Fired());

        h.read(T2C_L);
        assertFalse(t2Fired());
    }

    @Test
    void writingCounterHighByteReloadsAndClearsInterruptFlag() {
        h.write(ACR, 0x00);
        loadT2(0);
        h.cycles(2);
        assertTrue(t2Fired());

        loadT2(5);
        assertFalse(t2Fired());
    }

    // ------------------------------------------------------- ACR5 mode changes ----

    @Test
    void changingFromPulseCountingToTimedStartsCountingTheCycleAfterTheWrite() {
        h.write(ACR, 0x20);
        h.writeWord(T2C_L, T2C_H, 0x4000);
        h.cycles(3);
        assertEquals(0x00, h.read(T2C_L), "frozen in pulse counting mode");

        h.write(ACR, 0x00);
        h.cycle(); // The cycle of the ACR write: the count enable hasn't changed yet.
        assertEquals(0x00, h.read(T2C_L));

        h.cycle();
        assertEquals(0xFF, h.read(T2C_L));
    }

    @Test
    void changingFromTimedToPulseCountingStopsCountingTheCycleAfterTheWrite() {
        h.write(ACR, 0x00);
        h.writeWord(T2C_L, T2C_H, 0x4000);
        h.cycles(2); // The cycle of the load doesn't decrement, the next one does.
        assertEquals(0xFF, h.read(T2C_L));

        h.write(ACR, 0x20);
        h.cycle(); // The cycle of the ACR write: the count enable hasn't changed yet.
        assertEquals(0xFE, h.read(T2C_L));

        h.cycles(3);
        assertEquals(0xFE, h.read(T2C_L), "frozen in pulse counting mode");
    }

    @Test
    void rewritingAcrWithoutChangingBit5DoesNotDisturbCounting() {
        h.write(ACR, 0x00);
        h.writeWord(T2C_L, T2C_H, 0x4000);
        h.cycles(2);
        assertEquals(0xFF, h.read(T2C_L));

        h.write(ACR, 0x00);
        h.cycle();
        assertEquals(0xFE, h.read(T2C_L));
    }

    // ------------------------------------------------------ PB6 pulse counting ----

    private void pulsePb6() {
        h.via.setPortBPins(0x40); // PB6 high
        h.cycle();
        h.via.setPortBPins(0x00); // PB6 low: a negative-going pulse
        h.cycle();
    }

    @Test
    void pb6PulseCountingModeDoesNotDecrementFromClockCyclesAlone() {
        h.write(ACR, 0x20); // PB6 pulse-counting mode (ACR bit5=1)
        loadT2(2);

        // With PB6 never touched, plain cycling must never decrement or fire -
        // this mode's clock source is external pulses, not PHI2.
        h.cycles(50);
        assertFalse(t2Fired());
    }

    @Test
    void pb6PulseCountingModeDecrementsOnceForEachNegativeGoingPulse() {
        h.write(ACR, 0x20);
        loadT2(2);

        pulsePb6();
        assertFalse(t2Fired(), "one pulse should not yet be enough to reach 0 from a latch of 2");

        for (int i = 0; i < 10 && !t2Fired(); i++) {
            pulsePb6();
        }
        assertTrue(t2Fired(), "should have fired well within a handful of further pulses");
    }

    @Test
    void pb6PulseCountingModeIgnoresPositiveGoingTransitions() {
        h.write(ACR, 0x20);
        loadT2(0); // counter already at 0; only a negative-going pulse should move it

        h.via.setPortBPins(0x00); // PB6 low
        h.cycle();
        h.via.setPortBPins(0x40); // PB6 low -> high: positive-going, must be ignored
        h.cycle();

        assertFalse(t2Fired());
    }
}
