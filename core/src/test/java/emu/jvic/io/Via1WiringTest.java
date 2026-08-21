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
 * VIA #1's VIC-20-specific pin wiring (see {@code Via1.java}): most of the
 * joystick on port A, the RESTORE key on CA1 (wired to the CPU's NMI), the serial
 * bus IN lines and ATN OUT on port A, and the datasette motor control on CA2.
 */
public class Via1WiringTest {

    private static final int ORA = 1;
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
    private Via1 via1;

    @BeforeEach
    void setUp() {
        cpu = new Cpu6502(null);
        Memory memory = new Memory(cpu, null, true);
        keyboardMatrix = new TestKeyboardMatrix();
        keyboard = new Keyboard(keyboardMatrix);
        joystick = new Joystick(keyboardMatrix);
        serialBus = new SerialBus();
        datasette = new C1530Datasette();
        via1 = new Via1(cpu, keyboard, joystick, serialBus, datasette, null);
        via1.setMemory(memory);
    }

    @Test
    void joystickUpDownLeftFireAppearOnPortABits2Through5() {
        assertEquals(0x3C, via1.getPortAPins() & 0x3C, "idle (nothing pressed) reads all high");

        keyboardMatrix.setKeyMatrixRow(KeyboardMatrix.JOYSTICK, 0x04); // UP
        assertEquals(0x38, via1.getPortAPins() & 0x3C, "UP pulls PA2 low (active low)");
    }

    @Test
    void joystickRightIsNotWiredToVia1() {
        keyboardMatrix.setKeyMatrixRow(KeyboardMatrix.JOYSTICK, 0x80); // RIGHT
        assertEquals(0x3C, via1.getPortAPins() & 0x3C, "RIGHT is wired to VIA2, not VIA1");
    }

    @Test
    void tapeSenseDefaultsToNoButtonPressedOnPa6() {
        assertEquals(0x40, via1.getPortAPins() & 0x40);
    }

    @Test
    void serialClockAndDataInputsAreInvertedOntoPa0Pa1() {
        Object otherDevice = new Object();

        assertEquals(0x03, via1.getPortAPins() & 0x03, "both lines released -> both PA bits high");

        serialBus.pullDownClock(otherDevice);
        assertEquals(0x02, via1.getPortAPins() & 0x03, "clock pulled down -> PA0 low");

        serialBus.pullDownData(otherDevice);
        assertEquals(0x00, via1.getPortAPins() & 0x03, "both pulled down -> both PA bits low");
    }

    @Test
    void writingPa7HighPullsDownAtnOnSerialBus() {
        via1.writeMemory(DDRA, 0x80); // PA7 as output
        via1.writeMemory(ORA, 0x80);
        assertTrue(serialBus.getAtn());

        via1.writeMemory(ORA, 0x00);
        assertFalse(serialBus.getAtn());
    }

    @Test
    void ca2ControlsCassetteMotorWhenTapeButtonIsPressed() {
        datasette.insertTape(minimalTapeImage());
        datasette.play(); // simulates the PLAY button, closing the physical switch

        via1.writeMemory(PCR, 0x0E); // CA2 manual high -> motor control released (off)
        assertFalse(datasette.isMotorOn());

        via1.writeMemory(PCR, 0x0C); // CA2 manual low (active low) -> motor on
        assertTrue(datasette.isMotorOn());
    }

    @Test
    void restoreKeyNeverTriggersNmi_knownGap() {
        // Keyboard.getRestoreKeyState() has its real logic commented out with a
        // "TODO: For some reason pressing the RESTORE key hangs the machine" note,
        // and unconditionally returns 0. So CA1 (wired to RESTORE) never sees an
        // active transition here no matter what the RESTORE key matrix entry says,
        // meaning RESTORE can never trigger an NMI in this emulator.
        via1.writeMemory(IER, 0x80 | CA1_BIT); // enable the CA1 interrupt so a real transition WOULD be observable
        keyboardMatrix.setKeyMatrixRow(KeyboardMatrix.RESTORE, 0x01); // "pressed"
        via1.emulateCycle();
        assertFalse(cpu.isNmiAsserted(),
                "KNOWN GAP: RESTORE key press never reaches CA1/NMI (Keyboard.getRestoreKeyState() is stubbed to 0)");
    }

    private byte[] minimalTapeImage() {
        String header = "C64-TAPE-RAW";
        byte[] data = new byte[header.length() + 4 + 4 + 2];
        int i = 0;
        for (char c : header.toCharArray()) {
            data[i++] = (byte) c;
        }
        data[i++] = 0; // version 0
        data[i++] = 1; // machine = VIC-20
        data[i++] = 0; // video standard = PAL
        data[i++] = 0; // reserved
        int size = 2;
        data[i++] = (byte) (size & 0xFF);
        data[i++] = (byte) ((size >> 8) & 0xFF);
        data[i++] = (byte) ((size >> 16) & 0xFF);
        data[i++] = (byte) ((size >> 24) & 0xFF);
        data[i++] = 10; // pulse delay byte
        data[i] = 10;
        return data;
    }
}
