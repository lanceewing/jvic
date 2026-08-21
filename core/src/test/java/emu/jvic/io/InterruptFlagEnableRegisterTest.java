package emu.jvic.io;

import emu.jvic.testutil.ViaTestHarness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static emu.jvic.testutil.ViaTestHarness.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IFR (Interrupt Flag Register) and IER (Interrupt Enable Register) semantics: IFR
 * is write-1-to-clear on bits 0-6, with bit 7 (IRQ) a read-only computed summary of
 * "any enabled flag is set"; IER uses bit 7 of the WRITTEN value to select set vs.
 * clear mode for the other bits being written, and always reads back with bit 7 set.
 */
public class InterruptFlagEnableRegisterTest {

    private ViaTestHarness h;

    @BeforeEach
    void setUp() {
        h = new ViaTestHarness();
    }

    private void raiseCa1Flag() {
        h.write(PCR, 0x01); // CA1 positive edge
        h.via.ca1Input = 1;
        h.cycle();
    }

    private void raiseCb1Flag() {
        h.write(PCR, 0x10); // CB1 positive edge
        h.via.cb1Input = 1;
        h.cycle();
    }

    @Test
    void writingIfrBitClearsThatFlag() {
        raiseCa1Flag();
        assertTrue((h.read(IFR) & CA1_BIT) != 0);

        h.write(IFR, CA1_BIT);
        assertEquals(0, h.read(IFR) & CA1_BIT);
    }

    @Test
    void writingIfrOnlyClearsTheBitsSpecified() {
        raiseCa1Flag();
        raiseCb1Flag();
        assertTrue((h.read(IFR) & CA1_BIT) != 0);
        assertTrue((h.read(IFR) & CB1_BIT) != 0);

        h.write(IFR, CA1_BIT); // clear CA1 only
        assertEquals(0, h.read(IFR) & CA1_BIT);
        assertTrue((h.read(IFR) & CB1_BIT) != 0, "CB1 flag must be unaffected");
    }

    @Test
    void ierReadAlwaysHasBit7Set() {
        h.write(IER, 0x00);
        assertEquals(0x80, h.read(IER));
    }

    @Test
    void ierWriteWithBit7SetEnablesTheGivenBits() {
        h.write(IER, 0x80 | CA1_BIT | CB1_BIT);
        assertEquals(0x80 | CA1_BIT | CB1_BIT, h.read(IER));
    }

    @Test
    void ierWriteWithBit7ClearDisablesTheGivenBits() {
        h.write(IER, 0x80 | CA1_BIT | CB1_BIT); // enable both first
        h.write(IER, CA1_BIT); // bit7=0 -> clear mode, disable CA1 only
        assertEquals(0x80 | CB1_BIT, h.read(IER));
    }

    @Test
    void irqTopBitStaysClearWhenFlagIsSetButNotEnabled() {
        h.write(IER, CA1_BIT); // bit7=0 -> explicitly NOT enabling CA1
        raiseCa1Flag();
        assertEquals(0, h.read(IFR) & IRQ_BIT);
    }

    @Test
    void irqTopBitSetsWhenFlagIsSetAndEnabled() {
        h.write(IER, 0x80 | CA1_BIT);
        raiseCa1Flag();
        assertTrue((h.read(IFR) & IRQ_BIT) != 0);
    }

    @Test
    void irqPinCallbackFiresWhenTopBitBecomesSet() {
        h.write(IER, 0x80 | CA1_BIT);
        assertEquals(0, h.via.irqPinState);

        raiseCa1Flag();
        assertEquals(1, h.via.irqPinState);
    }

    @Test
    void irqPinCallbackClearsWhenFlagAcknowledged() {
        h.write(IER, 0x80 | CA1_BIT);
        raiseCa1Flag();
        assertEquals(1, h.via.irqPinState);

        h.write(IFR, CA1_BIT);
        assertEquals(0, h.via.irqPinState);
    }
}
