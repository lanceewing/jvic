package emu.jvic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.badlogic.gdx.Input.Keys;

import emu.jvic.testutil.TestKeyboardMatrix;

/**
 * Tests the modifier handling in {@link KeyboardMatrix} behind hot keys such as
 * Alt+Shift+P: the host SHIFT state is tracked for the hot keys, keys pressed while ALT
 * is down don't reach the VIC, and a key pressed before ALT is still released.
 *
 * <p>KeyboardMatrix defers any release made within 50ms of the press (and the deferred
 * path needs a live Gdx.input), so keys are held past that before being released.
 */
public class HotKeyModifiersTest {

    // Key matrix row/bit of the VIC's left SHIFT, as per KeyboardMatrix.keyConvMapArr.
    private static final int SHIFT_ROW = 8, LEFT_SHIFT_BIT = 2;

    private TestKeyboardMatrix matrix;

    @BeforeEach
    void setUp() {
        matrix = new TestKeyboardMatrix();
    }

    private void assertNothingPressed() {
        for (int row : new int[] { 1, 2, 4, 8, 16, 32, 64, 128, KeyboardMatrix.JOYSTICK }) {
            assertEquals(0, matrix.getKeyMatrixRow(row), "row " + row + " should be clear");
        }
    }

    @Test
    void hostShiftIsTrackedForTheHotKeys() {
        assertFalse(matrix.isShiftKeyDown());
        matrix.keyDown(Keys.ALT_LEFT);
        matrix.keyDown(Keys.SHIFT_RIGHT);
        assertTrue(matrix.isShiftKeyDown());
        matrix.keyUp(Keys.SHIFT_RIGHT);
        assertFalse(matrix.isShiftKeyDown());
    }

    @Test
    void keysPressedWhileAltIsDownDontReachTheVic() throws InterruptedException {
        matrix.keyDown(Keys.ALT_LEFT);
        matrix.keyDown(Keys.SHIFT_LEFT);
        matrix.keyDown(Keys.P);
        assertNothingPressed();
        Thread.sleep(60);
        matrix.keyUp(Keys.P);
        matrix.keyUp(Keys.SHIFT_LEFT);
        matrix.keyUp(Keys.ALT_LEFT);
        assertNothingPressed();
    }

    @Test
    void shiftPressedBeforeAltIsReleasedWhileAltIsDown() throws InterruptedException {
        matrix.keyDown(Keys.SHIFT_LEFT);
        assertEquals(LEFT_SHIFT_BIT, matrix.getKeyMatrixRow(SHIFT_ROW) & LEFT_SHIFT_BIT);
        matrix.keyDown(Keys.ALT_LEFT);
        Thread.sleep(60);
        // Released before ALT, e.g. after Alt+Shift+P: the VIC's SHIFT must not stay down.
        matrix.keyUp(Keys.SHIFT_LEFT);
        assertNothingPressed();
        assertFalse(matrix.isShiftKeyDown());
    }
}
