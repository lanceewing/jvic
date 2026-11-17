package emu.jvic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.utils.Json;

import emu.jvic.config.AppConfigItem;
import emu.jvic.testutil.TestKeyboardMatrix;

/**
 * Tests the joystick key mode in {@link KeyboardMatrix}: while the joystick is ON the
 * arrow keys and INSERT act only as the joystick, and while it is OFF they act only as
 * the VIC cursor keys and INST, so that games which read both (e.g. Moon Patrol, which
 * uses the cursor keys to centre the screen) don't see the cursor keys during play.
 * Delete and the numpad are the joystick in both modes.
 *
 * <p>Drives the matrix directly via libgdx keycodes. KeyboardMatrix defers any release
 * made within 50ms of the press (and the deferred path needs a live Gdx.input), so keys
 * are held past that before being released.
 */
public class JoystickKeysTest {

    // Key matrix rows/bits, as per KeyboardMatrix.keyConvMapArr.
    private static final int SHIFT_ROW = 8, LEFT_SHIFT_BIT = 2;
    private static final int CURSOR_RIGHT_ROW = 4, CURSOR_RIGHT_BIT = 128;
    private static final int CURSOR_DOWN_ROW = 8, CURSOR_DOWN_BIT = 128;
    private static final int DELETE_ROW = 1, DELETE_BIT = 128;

    private static final int JOY_UP = 0x04, JOY_DOWN = 0x08, JOY_LEFT = 0x10;
    private static final int JOY_FIRE = 0x20, JOY_RIGHT = 0x80;

    private TestKeyboardMatrix matrix;

    @BeforeEach
    void setUp() {
        matrix = new TestKeyboardMatrix();
    }

    private int joystick() {
        return matrix.getKeyMatrixRow(KeyboardMatrix.JOYSTICK);
    }

    private void holdThenRelease(int keycode) throws InterruptedException {
        Thread.sleep(60);
        matrix.keyUp(keycode);
    }

    private void assertNothingPressed() {
        for (int row : new int[] { 1, 2, 4, 8, 16, 32, 64, 128, KeyboardMatrix.JOYSTICK }) {
            assertEquals(0, matrix.getKeyMatrixRow(row), "row " + row + " should be clear");
        }
    }

    @Test
    void joystickIsOffByDefault() {
        assertFalse(matrix.isJoystickKeysActive());
    }

    @Test
    void joystickOffArrowKeysAreCursorKeysOnly() throws InterruptedException {
        matrix.keyDown(Keys.LEFT);
        assertEquals(LEFT_SHIFT_BIT, matrix.getKeyMatrixRow(SHIFT_ROW) & LEFT_SHIFT_BIT);
        assertEquals(CURSOR_RIGHT_BIT, matrix.getKeyMatrixRow(CURSOR_RIGHT_ROW));
        assertEquals(0, joystick());
        holdThenRelease(Keys.LEFT);
        assertNothingPressed();

        matrix.keyDown(Keys.DOWN);
        assertEquals(CURSOR_DOWN_BIT, matrix.getKeyMatrixRow(CURSOR_DOWN_ROW));
        assertEquals(0, joystick());
        holdThenRelease(Keys.DOWN);
        assertNothingPressed();
    }

    @Test
    void joystickOnArrowKeysAreJoystickOnly() throws InterruptedException {
        matrix.setJoystickKeysActive(true);
        int[][] cases = {
            { Keys.LEFT, JOY_LEFT }, { Keys.RIGHT, JOY_RIGHT },
            { Keys.UP, JOY_UP }, { Keys.DOWN, JOY_DOWN },
        };
        for (int[] c : cases) {
            matrix.keyDown(c[0]);
            assertEquals(c[1], joystick(), "keycode " + c[0]);
            // No cursor keys and, importantly, no SHIFT.
            for (int row = 1; row <= 128; row <<= 1) {
                assertEquals(0, matrix.getKeyMatrixRow(row), "keycode " + c[0] + " row " + row);
            }
            holdThenRelease(c[0]);
            assertNothingPressed();
        }
    }

    @Test
    void insertIsInstWhenOffAndFireOnlyWhenOn() throws InterruptedException {
        matrix.keyDown(Keys.INSERT);
        assertEquals(LEFT_SHIFT_BIT, matrix.getKeyMatrixRow(SHIFT_ROW));
        assertEquals(DELETE_BIT, matrix.getKeyMatrixRow(DELETE_ROW));
        assertEquals(0, joystick());
        holdThenRelease(Keys.INSERT);
        assertNothingPressed();

        matrix.setJoystickKeysActive(true);
        matrix.keyDown(Keys.INSERT);
        assertEquals(JOY_FIRE, joystick());
        assertEquals(0, matrix.getKeyMatrixRow(SHIFT_ROW));
        assertEquals(0, matrix.getKeyMatrixRow(DELETE_ROW));
        holdThenRelease(Keys.INSERT);
        assertNothingPressed();
    }

    @Test
    void deleteIsFireInBothModes() throws InterruptedException {
        for (boolean joystickOn : new boolean[] { false, true }) {
            matrix.setJoystickKeysActive(joystickOn);
            assertTrue(matrix.keyDown(Keys.FORWARD_DEL));
            assertEquals(JOY_FIRE, joystick(), "joystick on: " + joystickOn);
            assertEquals(0, matrix.getKeyMatrixRow(DELETE_ROW), "must not be VIC INST/DEL");
            holdThenRelease(Keys.FORWARD_DEL);
            assertNothingPressed();
        }
    }

    @Test
    void backspaceIsStillVicDelete() {
        matrix.setJoystickKeysActive(true);
        matrix.keyDown(Keys.BACKSPACE);
        assertEquals(DELETE_BIT, matrix.getKeyMatrixRow(DELETE_ROW));
        assertEquals(0, joystick());
    }

    @Test
    void numpadIsJoystickInBothModes() throws InterruptedException {
        for (boolean joystickOn : new boolean[] { false, true }) {
            matrix.setJoystickKeysActive(joystickOn);
            matrix.keyDown(Keys.NUMPAD_4);
            assertEquals(JOY_LEFT, joystick(), "joystick on: " + joystickOn);
            holdThenRelease(Keys.NUMPAD_4);
            assertNothingPressed();
        }
    }

    @Test
    void cursorKeyHeldWhenJoystickTurnedOnIsReleasedOnKeyUp() throws InterruptedException {
        matrix.keyDown(Keys.LEFT);
        matrix.setJoystickKeysActive(true);
        // Still physically held, so the cursor key stays down until released.
        assertEquals(CURSOR_RIGHT_BIT, matrix.getKeyMatrixRow(CURSOR_RIGHT_ROW));
        holdThenRelease(Keys.LEFT);
        // The release must undo what was pressed (SHIFT + cursor), not the new mapping.
        assertNothingPressed();
    }

    @Test
    void joystickDirectionHeldWhenJoystickTurnedOffIsCleared() throws InterruptedException {
        matrix.setJoystickKeysActive(true);
        matrix.keyDown(Keys.RIGHT);
        assertEquals(JOY_RIGHT, joystick());
        matrix.setJoystickKeysActive(false);
        assertEquals(0, joystick(), "turning the joystick off clears held directions");
        holdThenRelease(Keys.RIGHT);
        // And the key up must not press or release any cursor key.
        assertNothingPressed();
    }

    @Test
    void onScreenJoystickDirectionIsClearedWhenJoystickTurnedOff() {
        // The on-screen joystick stops being processed once hidden, so a direction it
        // left held would otherwise be stuck.
        matrix.setJoystickKeysActive(true);
        matrix.vicKeyDown(VicKeys.JOYSTICK_UP);
        matrix.setJoystickKeysActive(false);
        assertEquals(0, joystick());
    }

    @Test
    void appConfigJoystickDefaultsToOnWhenAbsent() {
        Json json = new Json();
        AppConfigItem absent = json.fromJson(AppConfigItem.class, "{\"name\":\"Moon Patrol\"}");
        assertTrue(absent.isJoystickOnByDefault());

        AppConfigItem off = json.fromJson(AppConfigItem.class, "{\"name\":\"Adventure\",\"joystick\":\"OFF\"}");
        assertFalse(off.isJoystickOnByDefault());

        AppConfigItem on = json.fromJson(AppConfigItem.class, "{\"name\":\"Game\",\"joystick\":\"ON\"}");
        assertTrue(on.isJoystickOnByDefault());

        AppConfigItem lowerOff = new AppConfigItem();
        lowerOff.setJoystick("off");
        assertFalse(lowerOff.isJoystickOnByDefault());
    }
}
