package emu.jvic.io.tape;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link C1530Datasette}'s motor/switch-sense/pulse-timing state
 * machine. Real VIC-20 wiring (documented in the class's own field comments,
 * confirmed against {@code Via1.java}/{@code Via2.java} this session):
 * cassette switch sense on VIA1 PA6, motor control via VIA1 CA2 (active low -
 * false means "motor commanded on"), pulse read via VIA2 CA1.
 */
public class C1530DatasetteTest {

    private C1530Datasette datasette;

    @BeforeEach
    void setUp() throws IOException {
        datasette = new C1530Datasette();
        datasette.insertTape(buildTapFile(new byte[] { 0x10, 0x10, 0x10, 0x10 }));
    }

    private static byte[] buildTapFile(byte[] payload) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("C64-TAPE-RAW".getBytes("ASCII"));
        out.write(0); // version 0
        out.write(1); // VIC-20
        out.write(0); // PAL
        out.write(0); // reserved
        int size = payload.length;
        out.write(size & 0xFF);
        out.write((size >> 8) & 0xFF);
        out.write((size >> 16) & 0xFF);
        out.write((size >> 24) & 0xFF);
        out.write(payload);
        return out.toByteArray();
    }

    @Test
    void motorStaysOffIfOnlyPlayIsPressedWithoutSoftwareCommandingItOn() {
        // Motor control defaults to true (off, active low), matching real post-reset state.
        datasette.play();

        assertFalse(datasette.isMotorOn(), "pressing PLAY alone shouldn't spin the motor without CA2 also commanding it on");
    }

    @Test
    void motorStaysOffIfOnlyMotorControlIsCommandedOnWithoutTheButtonPressed() {
        datasette.setCassetteMotorControl(false); // Command motor on (active low).

        assertFalse(datasette.isMotorOn(), "commanding the motor on without a button pressed shouldn't spin it");
    }

    @Test
    void motorTurnsOnOnlyWhenBothPlayIsPressedAndMotorControlCommandsItOn() {
        datasette.setCassetteMotorControl(false); // Command motor on.
        datasette.play(); // Press PLAY.

        assertTrue(datasette.isMotorOn());
    }

    @Test
    void cassetteSwitchSenseReflectsWhetherAButtonIsCurrentlyPressed() {
        assertTrue(datasette.getCassetteSwitchSense(), "no button pressed initially (active low = true = released)");

        datasette.play();

        assertFalse(datasette.getCassetteSwitchSense(), "PLAY pressed should read as active low (false)");
    }

    @Test
    void stopDoesNotImmediatelyTurnTheMotorOffButStartsATimeout() {
        datasette.setCassetteMotorControl(false);
        datasette.play();
        assertTrue(datasette.isMotorOn());

        datasette.stop();

        // Real hardware behaviour: releasing the button doesn't instantly stop the motor -
        // it keeps spinning for a short tail (modelled here as a pulse-count timeout).
        assertTrue(datasette.isMotorOn(), "motor should still be spinning immediately after stop()");
    }

    @Test
    void motorEventuallyStopsAfterStopViaThePulseCountdown() {
        datasette.setCassetteMotorControl(false);
        datasette.play();
        datasette.stop();

        boolean stoppedEventually = false;
        for (int i = 0; i < 40_000 && !stoppedEventually; i++) {
            datasette.getPulse();
            if (!datasette.isMotorOn()) {
                stoppedEventually = true;
            }
        }

        assertTrue(stoppedEventually, "motor should turn off within a bounded number of pulses after stop()");
    }
}
