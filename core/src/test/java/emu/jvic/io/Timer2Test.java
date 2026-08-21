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
