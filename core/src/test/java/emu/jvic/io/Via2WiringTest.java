package emu.jvic.io;

import emu.jvic.KeyboardMatrix;
import emu.jvic.cpu.Cpu6502;
import emu.jvic.io.tape.C1530Datasette;
import emu.jvic.memory.Memory;
import emu.jvic.testutil.TestKeyboardMatrix;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * VIA #2's VIC-20-specific pin wiring (see {@code Via2.java}): the full keyboard
 * matrix (columns via port A, rows via port B), the joystick RIGHT signal on PB7,
 * the serial bus OUT lines (CLOCK via CA2, DATA via CB2), tape read data on CA1,
 * and the IRQ line.
 */
public class Via2WiringTest {

    private static final int ORB = 0;
    private static final int DDRB = 2;
    private static final int DDRA = 3;
    private static final int PCR = 12;
    private static final int IER = 14;
    private static final int CA1_BIT = 0x02;

    private Cpu6502 cpu;
    private TestKeyboardMatrix keyboardMatrix;
    private Keyboard keyboard;
    private Joystick joystick;
    private SerialBus serialBus;
    private C1530Datasette datasette;
    private Via2 via2;

    @BeforeEach
    void setUp() {
        cpu = new Cpu6502(null);
        Memory memory = new Memory(cpu, null, true);
        keyboardMatrix = new TestKeyboardMatrix();
        keyboard = new Keyboard(keyboardMatrix);
        joystick = new Joystick(keyboardMatrix);
        serialBus = new SerialBus();
        datasette = new C1530Datasette();
        via2 = new Via2(cpu, keyboard, joystick, serialBus, datasette, null);
        via2.setMemory(memory);
    }

    @Test
    void keyboardRowColumnScanDetectsAPressedKey() {
        // The 'A' key is wired to row $04, column bit $02 (see KeyboardMatrix's
        // key conversion table). Selecting row 4 means driving PB bit 0x04 low.
        via2.writeMemory(DDRB, 0xFF); // PB all output
        via2.writeMemory(ORB, 0xFF & ~0x04); // select row 4 (active low)

        assertEquals(0xFF, via2.getPortAPins(), "nothing pressed -> all column bits high");

        keyboardMatrix.setKeyMatrixRow(0x04, 0x02); // 'A' is down
        assertEquals(0xFF & ~0x02, via2.getPortAPins(), "column bit 0x02 pulled low");
    }

    @Test
    void keyboardScanIgnoresUnselectedRows() {
        via2.writeMemory(DDRB, 0xFF);
        via2.writeMemory(ORB, 0xFF & ~0x08); // select row 8, NOT row 4

        keyboardMatrix.setKeyMatrixRow(0x04, 0x02); // 'A' (row 4) is down, but row 4 isn't selected
        assertEquals(0xFF, via2.getPortAPins());
    }

    @Test
    void joystickRightAppearsOnPb7() {
        assertEquals(0x80, via2.getPortBPins() & 0x80, "idle (not pressed) reads high");

        keyboardMatrix.setKeyMatrixRow(KeyboardMatrix.JOYSTICK, 0x80); // RIGHT
        assertEquals(0x00, via2.getPortBPins() & 0x80, "RIGHT pulls PB7 low (active low)");
    }

    @Test
    void ca2DrivesSerialBusClockOut() {
        // Via2.updateControlLines() pulls the CLOCK line down (true) when ca2==1
        // and releases it (false) when ca2==0 - an inverting driver, where the VIA
        // output pin HIGH turns on the pull-down transistor.
        via2.writeMemory(PCR, 0x0E); // CA2 manual high
        assertTrue(serialBus.getClock());

        via2.writeMemory(PCR, 0x0C); // CA2 manual low
        assertFalse(serialBus.getClock());
    }

    @Test
    void cb2DrivesSerialBusDataOut() {
        // Same inverting relationship as CA2/CLOCK above, but for CB2/DATA.
        via2.writeMemory(PCR, 0xE0); // CB2 manual high
        assertTrue(serialBus.getData());

        via2.writeMemory(PCR, 0xC0); // CB2 manual low
        assertFalse(serialBus.getData());
    }

    @Test
    void tapeReadNeverReachesCa1WhileMotorIsOff() {
        via2.writeMemory(IER, 0x80 | CA1_BIT);
        via2.emulateCycle();
        assertFalse(cpu.isIrqAsserted());
    }

    @Test
    void irqPinIsWiredToCpuIrqNotNmi() {
        // Drive an interrupt via Timer 1 (simpler and more direct than wiring up a
        // real tape pulse through CA1) to confirm VIA2's IRQ pin lands on the
        // CPU's IRQ line, not NMI (that's VIA1's job). Per Timer1Test, a latch of
        // 0 fires from the 3rd emulateCycle() call after the T1C-H write onward.
        via2.writeMemory(IER, 0x80 | 0x40); // enable Timer 1
        via2.writeMemory(6, 0); // T1L-L
        via2.writeMemory(5, 0); // T1C-H -> loads latch=0
        via2.emulateCycle();
        via2.emulateCycle();
        via2.emulateCycle();

        assertTrue(cpu.isIrqAsserted());
        assertFalse(cpu.isNmiAsserted());
    }
}
