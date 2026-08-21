package emu.jvic.io;

import emu.jvic.testutil.ViaTestHarness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static emu.jvic.testutil.ViaTestHarness.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Timer 1: one-shot mode (ACR bit6=0) and free-run mode (ACR bit6=1), plus the PB7
 * square-wave output (ACR bit7).
 *
 * <p>Empirically confirmed timing (matches Via6522's own comments, which cite
 * testing against real hardware): writing T1C-H with latch value N loads the
 * counter, consuming one {@code emulateCycle()} call that does NOT decrement (the
 * load itself). The interrupt flag reads false for the first N+2 calls after that
 * write and becomes true from the (N+3)th call onward - i.e. it takes N+3 total
 * cycles, not N+2, to OBSERVE the flag as set via a read. Once a free-running timer
 * has fired once, subsequent auto-reloads are exactly N+2 cycles apart (the "load
 * consumes an extra cycle" quirk only applies to a CPU-issued write, not to the
 * internal auto-reload).
 */
public class Timer1Test {

    private ViaTestHarness h;

    @BeforeEach
    void setUp() {
        h = new ViaTestHarness();
    }

    private boolean t1Fired() {
        return (h.read(IFR) & TIMER1_BIT) != 0;
    }

    @Test
    void oneShotFiresOnTheLatchPlusThreeCycleAfterLoad() {
        h.write(ACR, 0x00); // one-shot, no PB7
        h.writeWord(T1L_L, T1C_H, 2); // latch=2

        h.cycles(4); // N+2
        assertFalse(t1Fired(), "must not have fired yet");
        h.cycle(); // N+3
        assertTrue(t1Fired(), "must have fired by the (latch+3)th cycle");
    }

    @Test
    void oneShotWithZeroLatchFiresOnTheThirdCycle() {
        h.write(ACR, 0x00);
        h.writeWord(T1L_L, T1C_H, 0);

        h.cycles(2);
        assertFalse(t1Fired());
        h.cycle();
        assertTrue(t1Fired());
    }

    @Test
    void oneShotFiresOnlyOnceEvenIfLeftUnacknowledged() {
        h.write(ACR, 0x00);
        h.writeWord(T1L_L, T1C_H, 0);
        h.cycles(3);
        assertTrue(t1Fired());

        // Flag stays set (nothing has acknowledged it), but keeps counting down and
        // wrapping without ever firing again in one-shot mode.
        h.cycles(20);
        assertTrue(t1Fired());
    }

    @Test
    void freeRunModeRefiresEveryLatchPlusTwoCycles() {
        h.write(ACR, 0x40); // free-run, no PB7
        h.writeWord(T1L_L, T1C_H, 2); // first fire at cycle 5, then every 4 cycles

        int firings = 0;
        for (int i = 0; i < 13; i++) {
            h.cycle();
            if (t1Fired()) {
                firings++;
                h.write(IFR, TIMER1_BIT); // acknowledge (write-1-to-clear)
            }
        }
        assertEquals(3, firings, "13 cycles should fire at cycles 5, 9 and 13");
    }

    @Test
    void readingCounterLowByteClearsInterruptFlag() {
        h.write(ACR, 0x00);
        h.writeWord(T1L_L, T1C_H, 0);
        h.cycles(3);
        assertTrue(t1Fired());

        h.read(T1C_L);
        assertFalse(t1Fired());
    }

    @Test
    void writingCounterHighByteReloadsAndClearsInterruptFlag() {
        h.write(ACR, 0x00);
        h.writeWord(T1L_L, T1C_H, 0);
        h.cycles(3);
        assertTrue(t1Fired());

        h.writeWord(T1L_L, T1C_H, 5); // rewrite -> reload + clear
        assertFalse(t1Fired());
    }

    @Test
    void pb7GoesLowImmediatelyAfterLoadInOneShotPb7Mode() {
        h.write(ACR, 0x80); // one-shot, PB7 mode enabled
        h.write(DDRB, 0x80); // PB7 as output (so it appears in the port pins)
        h.write(ORB, 0xFF);  // would normally make PB7 high, but PB7 mode overrides bit 7
        h.writeWord(T1L_L, T1C_H, 5);
        h.cycle(); // the delayed pulse-start clears PB7 at the end of the load cycle

        assertEquals(0, h.via.getPortBPins() & 0x80);
    }

    @Test
    void pb7GoesHighWhenOneShotFires() {
        h.write(ACR, 0x80);
        h.write(DDRB, 0x80);
        h.writeWord(T1L_L, T1C_H, 2); // fires on cycle 5

        h.cycles(4);
        assertEquals(0, h.via.getPortBPins() & 0x80, "still low before firing");
        h.cycle(); // 5th cycle: fires now
        assertEquals(0x80, h.via.getPortBPins() & 0x80, "PB7 goes high on the one-shot firing");
    }

    @Test
    void pb7TogglesEachPeriodInFreeRunPb7Mode() {
        h.write(ACR, 0xC0); // free-run + PB7 mode
        h.write(DDRB, 0x80);
        h.writeWord(T1L_L, T1C_H, 2); // first fire at cycle 5, then every 4 cycles

        h.cycles(5);
        int firstEdge = h.via.getPortBPins() & 0x80;
        assertEquals(0x80, firstEdge, "toggles high on the first firing");

        h.cycles(4); // second firing, 4 cycles later
        int secondEdge = h.via.getPortBPins() & 0x80;
        assertEquals(0, secondEdge, "toggles back low on the second firing");
    }
}
