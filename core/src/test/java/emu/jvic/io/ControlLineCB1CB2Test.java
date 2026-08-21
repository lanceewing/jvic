package emu.jvic.io;

import emu.jvic.testutil.ViaTestHarness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static emu.jvic.testutil.ViaTestHarness.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CB1 (PCR bit 4: active edge select) and CB2 (PCR bits 5-7: input/output mode).
 */
public class ControlLineCB1CB2Test {

    private ViaTestHarness h;

    @BeforeEach
    void setUp() {
        h = new ViaTestHarness();
    }

    private boolean cb1Fired() {
        return (h.read(IFR) & CB1_BIT) != 0;
    }

    @Test
    void negativeEdgeDefaultTriggersOnHighToLowTransition() {
        h.via.cb1Input = 1;
        h.cycle();
        assertFalse(cb1Fired());

        h.via.cb1Input = 0;
        h.cycle();
        assertTrue(cb1Fired());
    }

    @Test
    void positiveEdgeTriggersOnLowToHighTransition() {
        h.write(PCR, 0x10); // CB1 positive edge
        h.via.cb1Input = 1;
        h.cycle();
        assertTrue(cb1Fired());
    }

    @Test
    void cb2ManualLowModeHoldsLow() {
        h.write(PCR, 0xC0); // CB2 manual output, low (bits5-7 = 110)
        assertEquals(0, h.via.getCb2());
    }

    @Test
    void cb2ManualHighModeHoldsHigh() {
        h.write(PCR, 0xE0); // CB2 manual output, high (bits5-7 = 111)
        assertEquals(1, h.via.getCb2());
    }

    @Test
    void cb2HandshakeModeGoesLowOnOrbWrite() {
        h.write(PCR, 0xE0); // start high
        h.write(PCR, 0x80); // CB2 handshake output (bits5-7 = 100)
        h.write(ORB, 0x00);

        assertEquals(0, h.via.getCb2());
    }

    @Test
    void cb2HandshakeModeDoesNotGoLowOnOrbRead() {
        // Unlike ORA/CA2 (which triggers on both read and write), CB2
        // handshake/pulse triggers only on a write of ORB.
        h.write(PCR, 0xE0);
        h.write(PCR, 0x80);
        h.read(ORB);

        assertEquals(1, h.via.getCb2());
    }

    @Test
    void cb2HandshakeModeReturnsHighOnCb1ActiveEdge() {
        h.write(PCR, 0xE0); // start high
        h.write(PCR, 0x80 | 0x10); // CB2 handshake output AND CB1 positive edge
        h.write(ORB, 0x00);
        assertEquals(0, h.via.getCb2());

        h.via.cb1Input = 1; // the documented active edge that resets CB2 high
        h.cycle();

        assertEquals(1, h.via.getCb2());
    }

    @Test
    void cb2PulseModeGoesLowThenReturnsHighOneCycleLater() {
        h.write(PCR, 0xE0); // start high
        h.write(PCR, 0xA0); // CB2 pulse output (bits5-7 = 101)
        h.write(ORB, 0x00); // access ORB -> arms the pulse

        h.cycle();
        assertEquals(0, h.via.getCb2(), "goes low on the cycle following the access");
        h.cycle();
        assertEquals(1, h.via.getCb2(), "returns high exactly one cycle later");
        h.cycle();
        assertEquals(1, h.via.getCb2(), "stays high afterwards");
    }

    @Test
    void cb2InputModePositiveEdgeRaisesAnInterruptFlag() {
        h.write(PCR, 0x60); // CB2 positive-edge input mode (cb2ControlMode = 3, POSITIVE_EDGE_INDEPENDENT)
        assertEquals(0, h.read(IFR) & CB2_BIT);

        h.via.cb2Input = 1; // 0 -> 1, the configured active edge
        h.cycle();

        assertTrue((h.read(IFR) & CB2_BIT) != 0);
    }

    @Test
    void cb2InputModeNegativeEdgeIgnoresARisingTransition() {
        h.write(PCR, 0x00); // CB2 negative-edge input mode, the power-on default
        h.via.cb2Input = 1; // 0 -> 1, NOT the active edge in negative-edge mode
        h.cycle();
        assertEquals(0, h.read(IFR) & CB2_BIT);
    }

    @Test
    void cb2InputModeIndependentFlagStillClearsOnOrbAccess_knownLimitation() {
        // Same deliberately-scoped-out limitation as CA2 (see
        // ControlLineCA1CA2Test): VIA_REG_0's read/write handlers unconditionally
        // clear CB1_SET/CB2_SET on every ORB access, regardless of whether CB2 is
        // in "independent" input mode (which per the datasheet should NOT clear on
        // access).
        h.write(PCR, 0x70); // CB2 positive-edge INDEPENDENT input mode (cb2ControlMode = 3)
        h.via.cb2Input = 1;
        h.cycle();
        assertTrue((h.read(IFR) & CB2_BIT) != 0);

        h.read(ORB); // per the datasheet, this should NOT clear CB2_SET in independent mode

        assertEquals(0, h.read(IFR) & CB2_BIT,
                "KNOWN LIMITATION: independent CB2 input mode's flag is still (incorrectly) cleared by ORB access");
    }

    @Test
    void shiftRegisterOutputOverridesCb2RegardlessOfPcrMode() {
        // While the shift register is actively shifting out, it drives CB2 with
        // serial data directly (see ShiftRegisterTest), independent of whatever
        // PCR CB2 control mode is configured - this matches real hardware, where
        // the shift register takes over the CB2 pin in output mode.
        h.write(PCR, 0xE0); // CB2 manual-high, which would otherwise hold it at 1
        h.write(ACR, 0x18); // shift out, system clock
        h.write(SR, 0x00); // top bit 0 -> first shifted-out bit should pull CB2 low

        h.cycle(); // write-suppression cycle
        h.cycle(); // first actual shift
        assertEquals(0, h.via.getCb2());
    }
}
