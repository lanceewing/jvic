package emu.jvic.io;

import emu.jvic.testutil.ViaTestHarness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static emu.jvic.testutil.ViaTestHarness.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CA1 (PCR bit 0: active edge select) and CA2 (PCR bits 1-3: input/output mode).
 */
public class ControlLineCA1CA2Test {

    private ViaTestHarness h;

    @BeforeEach
    void setUp() {
        h = new ViaTestHarness();
    }

    private boolean ca1Fired() {
        return (h.read(IFR) & CA1_BIT) != 0;
    }

    @Test
    void negativeEdgeDefaultTriggersOnHighToLowTransition() {
        h.via.ca1Input = 1;
        h.cycle(); // establish the starting high level (0->1 is not the active edge here)
        assertFalse(ca1Fired());

        h.via.ca1Input = 0; // 1 -> 0
        h.cycle();
        assertTrue(ca1Fired());
    }

    @Test
    void negativeEdgeDoesNotTriggerOnLowToHighTransition() {
        h.via.ca1Input = 1; // 0 -> 1, not the active edge in negative-edge mode
        h.cycle();
        assertFalse(ca1Fired());
    }

    @Test
    void positiveEdgeTriggersOnLowToHighTransition() {
        h.write(PCR, 0x01); // CA1 positive edge
        h.via.ca1Input = 1; // 0 -> 1
        h.cycle();
        assertTrue(ca1Fired());
    }

    @Test
    void positiveEdgeDoesNotTriggerOnHighToLowTransition() {
        h.write(PCR, 0x01);
        h.via.ca1Input = 1;
        h.cycle(); // establish high
        h.write(IFR, CA1_BIT); // acknowledge the flag this raised

        h.via.ca1Input = 0; // 1 -> 0, not the active edge in positive-edge mode
        h.cycle();
        assertFalse(ca1Fired());
    }

    @Test
    void ca2ManualLowModeHoldsLow() {
        h.write(PCR, 0x0C); // CA2 manual output, low (bits1-3 = 110)
        assertEquals(0, h.via.getCa2());
    }

    @Test
    void ca2ManualHighModeHoldsHigh() {
        h.write(PCR, 0x0E); // CA2 manual output, high (bits1-3 = 111)
        assertEquals(1, h.via.getCa2());
    }

    @Test
    void ca2PulseModeGoesLowThenReturnsHighOneCycleLater() {
        h.write(PCR, 0x0E); // establish a known-high starting point first
        h.write(PCR, 0x0A); // CA2 pulse output (bits1-3 = 101)
        h.write(ORA, 0x00); // access ORA -> arms the pulse

        h.cycle();
        assertEquals(0, h.via.getCa2(), "goes low on the cycle following the access");
        h.cycle();
        assertEquals(1, h.via.getCa2(), "returns high exactly one cycle later");
        h.cycle();
        assertEquals(1, h.via.getCa2(), "stays high afterwards");
    }

    @Test
    void ca2HandshakeModeGoesLowOnOraAccess() {
        h.write(PCR, 0x0E); // start high
        h.write(PCR, 0x08); // CA2 handshake output (bits1-3 = 100)
        h.write(ORA, 0x00);
        assertEquals(0, h.via.getCa2());
    }

    @Test
    void ca2HandshakeModeReturnsHighOnCa1ActiveEdge() {
        // Per the PCR documentation (also mirrored in Via6522's own PCR write
        // comment block): "Handshake output mode - Set CA2 output low on a read or
        // write of the Peripheral A Output Register. Reset CA2 high with an active
        // transition on CA1."
        h.write(PCR, 0x0E); // start high
        h.write(PCR, 0x08 | 0x01); // handshake output AND CA1 positive edge
        h.write(ORA, 0x00);
        assertEquals(0, h.via.getCa2());

        h.via.ca1Input = 1; // the documented active edge that resets CA2 high
        h.cycle();

        assertEquals(1, h.via.getCa2());
    }

    @Test
    void ca2HandshakeModeIgnoresAnInactiveCa1Edge() {
        h.write(PCR, 0x0E);
        h.write(PCR, 0x08 | 0x01); // handshake output AND CA1 positive edge
        h.via.ca1Input = 1;
        h.cycle(); // establish CA1 high, so the next transition is 1->0 (inactive here)
        h.write(ORA, 0x00);
        assertEquals(0, h.via.getCa2());

        h.via.ca1Input = 0; // NOT the active edge (positive-edge mode selected)
        h.cycle();

        assertEquals(0, h.via.getCa2(), "CA2 must stay low; 1->0 is not the configured active edge");
    }

    @Test
    void ca2InputModePositiveEdgeRaisesAnInterruptFlag() {
        h.write(PCR, 0x06); // CA2 positive-edge input mode (ca2ControlMode = 3, POSITIVE_EDGE_INDEPENDENT)
        assertEquals(0, h.read(IFR) & CA2_BIT);

        h.via.ca2Input = 1; // 0 -> 1, the configured active edge
        h.cycle();

        assertTrue((h.read(IFR) & CA2_BIT) != 0);
    }

    @Test
    void ca2InputModeNegativeEdgeIgnoresARisingTransition() {
        h.write(PCR, 0x00); // CA2 negative-edge input mode (bits1-3 = 000), the power-on default
        h.via.ca2Input = 1; // 0 -> 1, NOT the active edge in negative-edge mode
        h.cycle();
        assertEquals(0, h.read(IFR) & CA2_BIT);
    }

    @Test
    void ca2InputModeIndependentFlagStillClearsOnOraAccess_knownLimitation() {
        // Real hardware distinguishes "dependent" input modes (PCR bit stays 0:
        // reading/writing ORA clears the CA2 flag, same as CA1) from "independent"
        // ones (PCR bit set to 1: ORA access must NOT clear it). VIA_REG_1's
        // read/write handlers unconditionally clear both CA1_SET and CA2_SET on
        // every access, regardless of ca2ControlMode - so independent mode's
        // "don't clear on access" guarantee isn't honoured. Documented here as a
        // deliberate remaining scope limitation (see the VIA fix-pass plan) rather
        // than silently left untested.
        h.write(PCR, 0x07); // CA2 positive-edge INDEPENDENT input mode (ca2ControlMode = 3)
        h.via.ca2Input = 1;
        h.cycle();
        assertTrue((h.read(IFR) & CA2_BIT) != 0);

        h.read(ORA); // per the datasheet, this should NOT clear CA2_SET in independent mode

        assertEquals(0, h.read(IFR) & CA2_BIT,
                "KNOWN LIMITATION: independent CA2 input mode's flag is still (incorrectly) cleared by ORA access");
    }
}
