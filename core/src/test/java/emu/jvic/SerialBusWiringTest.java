package emu.jvic;

import emu.jvic.cpu.Cpu6502;
import emu.jvic.io.Joystick;
import emu.jvic.io.Keyboard;
import emu.jvic.io.SerialBus;
import emu.jvic.io.Via1;
import emu.jvic.io.Via2;
import emu.jvic.io.tape.C1530Datasette;
import emu.jvic.memory.Memory;
import emu.jvic.testutil.TestKeyboardMatrix;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the real, documented VIC-20 IEC serial bus wiring, which is
 * asymmetric across the two VIAs: VIA1 provides Clock IN (PA0), Data IN (PA1)
 * and ATN OUT (PA7); VIA2 provides Clock OUT (CA2) and Data OUT (CB2). Unlike
 * the Phase 2 VIA control-line suite (which drove each VIA's control lines in
 * isolation), this wires a real {@link Via1} and {@link Via2} to one shared
 * {@link SerialBus}, confirming what one VIA drives is observable by the
 * other exactly as the real hardware's shared bus lines would show it.
 */
public class SerialBusWiringTest {

    private static final int ORA = 1;
    private static final int DDRA = 3;
    private static final int PCR = 12;

    // CA2/CB2 "manual output" PCR bit patterns, per the MOS 6522 PCR layout
    // (confirmed against the existing ControlLineCA1CA2Test/ControlLineCB1CB2Test suites).
    // Confirmed empirically (not just assumed) that Via2.updateControlLines() treats
    // ca2==1/cb2==1 ("manual output high") as the condition that PULLS the shared bus
    // line down/true, and ca2==0/cb2==0 ("manual output low") as RELEASING it - i.e.
    // the VIA2 pin and the electrical bus line are inverted relative to each other,
    // consistent with the open-collector-with-inverting-driver arrangement real 6522s
    // like this are wired through on the IEC bus.
    private static final int CA2_ASSERT = 0x0E; // Manual output high -> pulls clock down.
    private static final int CA2_RELEASE = 0x0C; // Manual output low -> releases clock.
    private static final int CB2_ASSERT = 0xE0; // Manual output high -> pulls data down.
    private static final int CB2_RELEASE = 0xC0; // Manual output low -> releases data.

    private SerialBus serialBus;
    private Via1 via1;
    private Via2 via2;

    @BeforeEach
    void setUp() {
        Cpu6502 cpu = new Cpu6502(null);
        Memory memory = new Memory(cpu, null, true);
        TestKeyboardMatrix matrix = new TestKeyboardMatrix();
        Keyboard keyboard = new Keyboard(matrix);
        Joystick joystick = new Joystick(matrix);
        serialBus = new SerialBus();
        C1530Datasette datasette = new C1530Datasette();

        via1 = new Via1(cpu, keyboard, joystick, serialBus, datasette, null);
        via1.setMemory(memory);
        via2 = new Via2(cpu, keyboard, joystick, serialBus, datasette, null);
        via2.setMemory(memory);

        // Start both VIA2 control lines released, matching the bus's idle state.
        via2.writeMemory(PCR, CA2_RELEASE | CB2_RELEASE);
    }

    @Test
    void via2PullingClockLowIsSeenAsClockInOnVia1() {
        assertEquals(0x01, via1.readMemory(ORA) & 0x01, "clock line idle (released) should read as 1 on PA0");

        via2.writeMemory(PCR, CA2_ASSERT | CB2_RELEASE);

        assertEquals(0x00, via1.readMemory(ORA) & 0x01, "VIA2 pulling clock low should read as 0 on VIA1's PA0");
    }

    @Test
    void via2PullingDataLowIsSeenAsDataInOnVia1() {
        assertEquals(0x02, via1.readMemory(ORA) & 0x02, "data line idle (released) should read as 1 on PA1");

        via2.writeMemory(PCR, CA2_RELEASE | CB2_ASSERT);

        assertEquals(0x00, via1.readMemory(ORA) & 0x02, "VIA2 pulling data low should read as 0 on VIA1's PA1");
    }

    @Test
    void releasingTheLinesAgainReturnsThemToIdleOnVia1() {
        via2.writeMemory(PCR, CA2_ASSERT | CB2_ASSERT);
        assertEquals(0x00, via1.readMemory(ORA) & 0x03);

        via2.writeMemory(PCR, CA2_RELEASE | CB2_RELEASE);
        assertEquals(0x03, via1.readMemory(ORA) & 0x03, "both lines released should read as 1 again");
    }

    @Test
    void via1DrivingAtnOutIsObservableOnTheSharedBus() {
        assertFalse(serialBus.getAtn(), "ATN should be idle/released before VIA1 drives it");

        via1.writeMemory(DDRA, 0x80); // PA7 = output.
        via1.writeMemory(ORA, 0x80); // Drive PA7 (ATN OUT) high -> pulls ATN down.

        assertTrue(serialBus.getAtn(), "VIA1 driving PA7 high should pull the shared ATN line down (true)");

        via1.writeMemory(ORA, 0x00); // Drive PA7 low -> releases ATN.

        assertFalse(serialBus.getAtn(), "VIA1 driving PA7 low should release the shared ATN line");
    }
}
