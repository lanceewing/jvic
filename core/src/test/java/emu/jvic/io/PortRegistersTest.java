package emu.jvic.io;

import emu.jvic.testutil.ViaTestHarness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static emu.jvic.testutil.ViaTestHarness.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ORA/ORB/DDRA/DDRB and the IRA/IRB input-latching behaviour controlled by ACR
 * bits 0 (port A) and 1 (port B). Per the 6522 datasheet: reg $01 (ORA/IRA) has
 * CA1/CA2 handshake side effects on read/write; reg $0F is the same register with
 * NO handshake side effects; reg $00 (ORB/IRB) always clears CB1/CB2 flags.
 */
public class PortRegistersTest {

    private ViaTestHarness h;

    @BeforeEach
    void setUp() {
        h = new ViaTestHarness();
    }

    @Test
    void writingDdraThenOraDrivesOutputPins() {
        h.write(DDRA, 0xFF); // all output
        h.write(ORA, 0x5A);
        assertEquals(0x5A, h.via.getPortAPins());
    }

    @Test
    void inputPinsPassThroughWhenNotLatched() {
        h.write(DDRA, 0x00); // all input
        h.via.setPortAPins(0x3C); // simulates external hardware driving the pins
        assertEquals(0x3C, h.read(ORA));
    }

    @Test
    void mixedInputOutputPinsCombineOraAndExternalPins() {
        h.write(DDRA, 0x0F); // low nibble output, high nibble input
        h.write(ORA, 0xAB);  // only the low nibble (0xB) takes effect as output
        h.via.setPortAPins(0x50); // external hardware drives the high nibble
        assertEquals(0x5B, h.via.getPortAPins());
    }

    @Test
    void inputLatchingCapturesPinsAtCa1ActiveEdge() {
        h.write(PCR, 0x01);  // CA1 positive-edge active transition
        h.write(ACR, 0x01);  // port A input latching enabled
        h.write(DDRA, 0x00); // all input

        h.via.setPortAPins(0x3C);
        h.via.ca1Input = 1; // rising edge (starts at 0)
        h.cycle();

        assertEquals(0x3C, h.read(ORA));

        // Pins change afterwards, but the latched value must not.
        h.via.setPortAPins(0xFF);
        assertEquals(0x3C, h.read(ORA));
    }

    @Test
    void oraNoHandshakeReadReturnsSameValueAsOra() {
        h.write(DDRA, 0xFF);
        h.write(ORA, 0x77);
        assertEquals(h.read(ORA), h.read(ORA_NO_HANDSHAKE));
    }

    @Test
    void readingOraClearsCa1AndCa2InterruptFlags() {
        h.write(PCR, 0x01); // CA1 positive edge
        h.via.ca1Input = 1;
        h.cycle(); // raises the CA1 interrupt flag

        assertTrue((h.read(IFR) & CA1_BIT) != 0);
        h.read(ORA); // must clear it
        assertEquals(0, h.read(IFR) & CA1_BIT);
    }

    @Test
    void readingOraNoHandshakeDoesNotClearCa1InterruptFlag() {
        h.write(PCR, 0x01);
        h.via.ca1Input = 1;
        h.cycle();

        assertTrue((h.read(IFR) & CA1_BIT) != 0);
        h.read(ORA_NO_HANDSHAKE);
        assertTrue((h.read(IFR) & CA1_BIT) != 0, "reg $0F must not clear CA1/CA2 flags");
    }

    @Test
    void writingOrbClearsCb1AndCb2InterruptFlags() {
        h.write(PCR, 0x10); // CB1 positive edge
        h.via.cb1Input = 1;
        h.cycle();

        assertTrue((h.read(IFR) & CB1_BIT) != 0);
        h.write(ORB, 0x00);
        assertEquals(0, h.read(IFR) & CB1_BIT);
    }

    @Test
    void ddrbControlsPortBOutputIndependentlyOfPortA() {
        h.write(DDRB, 0xF0); // high nibble output
        h.write(ORB, 0x90);
        h.via.setPortBPins(0x0D); // external hardware drives the low nibble
        assertEquals(0x9D, h.via.getPortBPins());
    }
}
