package emu.jvic.io;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link SerialBus}'s own wired-together pull-down/release semantics in
 * isolation: a line reads true (pulled down) if *any* device is pulling it
 * down, and only reads false (released) once *every* device has released it -
 * relevant once more than two devices can drive the same line (VIA2 and a
 * {@code C1541Drive}, for instance).
 */
public class SerialBusTest {

    @Test
    void allLinesAreReleasedInitially() {
        SerialBus bus = new SerialBus();

        assertFalse(bus.getAtn());
        assertFalse(bus.getData());
        assertFalse(bus.getClock());
    }

    @Test
    void oneDevicePullingDownIsEnoughToAssertTheLine() {
        SerialBus bus = new SerialBus();
        Object device = new Object();

        bus.pullDownClock(device);

        assertTrue(bus.getClock());
    }

    @Test
    void lineStaysAssertedWhileAnyOfSeveralDevicesStillPullsItDown() {
        SerialBus bus = new SerialBus();
        Object deviceA = new Object();
        Object deviceB = new Object();
        Object deviceC = new Object();

        bus.pullDownData(deviceA);
        bus.pullDownData(deviceB);
        bus.pullDownData(deviceC);

        bus.releaseData(deviceA);
        assertTrue(bus.getData(), "still pulled down by B and C");

        bus.releaseData(deviceB);
        assertTrue(bus.getData(), "still pulled down by C");

        bus.releaseData(deviceC);
        assertFalse(bus.getData(), "released by every device");
    }

    @Test
    void releasingADeviceThatNeverPulledDownHasNoEffect() {
        SerialBus bus = new SerialBus();
        Object deviceA = new Object();
        Object deviceB = new Object();

        bus.pullDownAtn(deviceA);
        bus.releaseAtn(deviceB); // Never pulled it down.

        assertTrue(bus.getAtn(), "deviceA's pull-down should be unaffected");
    }

    @Test
    void resetClearsEveryLineRegardlessOfHowManyDevicesWerePullingItDown() {
        SerialBus bus = new SerialBus();
        bus.pullDownAtn(new Object());
        bus.pullDownData(new Object());
        bus.pullDownData(new Object());
        bus.pullDownClock(new Object());

        bus.reset();

        assertFalse(bus.getAtn());
        assertFalse(bus.getData());
        assertFalse(bus.getClock());
    }

    @Test
    void theThreeLinesAreIndependentOfEachOther() {
        SerialBus bus = new SerialBus();
        Object device = new Object();

        bus.pullDownClock(device);

        assertTrue(bus.getClock());
        assertFalse(bus.getData());
        assertFalse(bus.getAtn());
    }
}
