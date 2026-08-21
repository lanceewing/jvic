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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the real, documented VIC-20 keyboard-scanning convention: the whole
 * matrix is scanned through a single VIA (VIA2) using both of its ports
 * together - one port selects a row or column (driven low/active), the other
 * reads back the result - rather than being split across VIA1 and VIA2.
 *
 * <p>Confirmed empirically (not just by reading the source) via
 * {@code Via2.getPortAPins()}/{@code getPortBPins()}: reading ORA (register 1)
 * returns the column state for whatever row is currently selected via ORB
 * (register 0), and reading ORB returns the row state for whatever column is
 * selected via ORA - both active-low, matching {@code Keyboard.java}'s own
 * scan implementation. This is different from the per-chip Phase 2 VIA suite,
 * which drove a real {@code Via6522} against a fake {@code TestKeyboardMatrix}
 * signal in total isolation - this test instead wires a real {@code Via2} to a
 * real {@code Keyboard} to confirm the two ports correctly cooperate.
 */
public class KeyboardScanIntegrationTest {

    private static final int ORB = 0;
    private static final int ORA = 1;
    private static final int DDRB = 2;
    private static final int DDRA = 3;

    private Via2 via2;
    private TestKeyboardMatrix matrix;

    private void buildVia2() {
        Cpu6502 cpu = new Cpu6502(null);
        Memory memory = new Memory(cpu, null, true);
        matrix = new TestKeyboardMatrix();
        Keyboard keyboard = new Keyboard(matrix);
        Joystick joystick = new Joystick(matrix);
        SerialBus serialBus = new SerialBus();
        C1530Datasette datasette = new C1530Datasette();
        via2 = new Via2(cpu, keyboard, joystick, serialBus, datasette, null);
        via2.setMemory(memory);
    }

    @Test
    void selectingARowThroughOrbReflectsThePressedKeyOnOra() {
        buildVia2();
        // Key at row-select bit 0x01, column bit 0x80 - matches how
        // KeyboardMatrix.vicKeyDown ORs a column bit into the row's bitmask.
        matrix.setKeyMatrixRow(0x01, 0x80);

        via2.writeMemory(DDRA, 0x00); // Port A = input (columns read back here).
        via2.writeMemory(DDRB, 0xFF); // Port B = output (row select).
        via2.writeMemory(ORB, ~0x01 & 0xFF); // Select row 0x01 (active low).

        assertEquals(0x7F, via2.readMemory(ORA) & 0xFF, "column 0x80 should read low (pressed)");
    }

    @Test
    void deselectingAllRowsShowsNoKeysPressedOnOra() {
        buildVia2();
        matrix.setKeyMatrixRow(0x01, 0x80);

        via2.writeMemory(DDRA, 0x00);
        via2.writeMemory(DDRB, 0xFF);
        via2.writeMemory(ORB, 0xFF); // No row selected.

        assertEquals(0xFF, via2.readMemory(ORA) & 0xFF, "no row selected means nothing should read as pressed");
    }

    @Test
    void selectingAColumnThroughOraReflectsThePressedKeyOnOrb() {
        buildVia2();
        matrix.setKeyMatrixRow(0x01, 0x80);

        via2.writeMemory(DDRA, 0xFF); // Port A = output (column select).
        via2.writeMemory(DDRB, 0x00); // Port B = input (rows read back here).
        via2.writeMemory(ORA, ~0x80 & 0xFF); // Select column 0x80 (active low).

        assertEquals(0xFE, via2.readMemory(ORB) & 0xFF, "row 0x01 should read low (pressed)");
    }

    @Test
    void restoreKeyIsCurrentlyAlwaysReportedAsNotPressedOnVia1() {
        // Documents CURRENT, deliberately-left-alone behaviour: Keyboard.getRestoreKeyState()
        // is hard-coded to return 0 regardless of the actual matrix state, per its own TODO
        // ("pressing the RESTORE key hangs the machine") - this was investigated and
        // intentionally excluded from the earlier VIA bug-fix pass. This test pins down
        // that current behaviour so a future change to it is a deliberate, visible decision,
        // not an accidental regression caught only here.
        Cpu6502 cpu = new Cpu6502(null);
        Memory memory = new Memory(cpu, null, true);
        TestKeyboardMatrix restoreMatrix = new TestKeyboardMatrix();
        Keyboard keyboard = new Keyboard(restoreMatrix);
        Joystick joystick = new Joystick(restoreMatrix);
        SerialBus serialBus = new SerialBus();
        C1530Datasette datasette = new C1530Datasette();
        Via1 via1 = new Via1(cpu, keyboard, joystick, serialBus, datasette, null);
        via1.setMemory(memory);

        restoreMatrix.setKeyMatrixRow(emu.jvic.KeyboardMatrix.RESTORE, 0x01); // "Press" RESTORE.

        assertEquals(0, keyboard.getRestoreKeyState());
    }
}
