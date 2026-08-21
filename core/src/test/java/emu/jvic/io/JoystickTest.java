package emu.jvic.io;

import emu.jvic.KeyboardMatrix;
import emu.jvic.testutil.TestKeyboardMatrix;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests {@link Joystick#getJoystickState()}'s bit inversion of the raw
 * keyboard-matrix joystick row.
 */
public class JoystickTest {

    @Test
    void noJoystickInputReadsAsAllOnes() {
        TestKeyboardMatrix matrix = new TestKeyboardMatrix();
        Joystick joystick = new Joystick(matrix);

        assertEquals(0xFF, joystick.getJoystickState() & 0xFF);
    }

    @Test
    void aSetBitInTheMatrixRowReadsAsClearedInTheJoystickState() {
        TestKeyboardMatrix matrix = new TestKeyboardMatrix();
        matrix.setKeyMatrixRow(KeyboardMatrix.JOYSTICK, 0x04); // "Up", per KeyboardMatrix's own mapping.
        Joystick joystick = new Joystick(matrix);

        assertEquals(0xFB, joystick.getJoystickState() & 0xFF);
    }

    @Test
    void multipleSetBitsAreAllInvertedTogether() {
        TestKeyboardMatrix matrix = new TestKeyboardMatrix();
        matrix.setKeyMatrixRow(KeyboardMatrix.JOYSTICK, 0x20 | 0x08); // Fire + Down.
        Joystick joystick = new Joystick(matrix);

        assertEquals((~(0x20 | 0x08)) & 0xFF, joystick.getJoystickState() & 0xFF);
    }
}
