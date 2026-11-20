package emu.jvic.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import emu.jvic.ui.MachineIconLayout.Icon;
import emu.jvic.ui.MachineIconLayout.Slot;
import emu.jvic.ui.MachineInputProcessor.JoystickAlignment;

/**
 * Tests {@link MachineIconLayout}: icons are where the machine screen drew them before
 * the layout table existed (with the menu icon in the screen size icon's place), each
 * icon is hit at its centre, and no two tap areas overlap.
 */
public class MachineIconLayoutTest {

    private final MachineIconLayout layout = new MachineIconLayout();

    private void assertAt(Icon icon, float x, float y) {
        Slot slot = layout.getSlot(icon);
        assertTrue(slot.isVisible(), icon.name());
        assertEquals(x, slot.getX(), 0.01f, icon + " x");
        assertEquals(y, slot.getY(), 0.01f, icon + " y");
    }

    private void assertHitsAndNoOverlap() {
        Slot[] slots = layout.getSlots();
        for (Slot slot : slots) {
            if (!slot.isVisible()) {
                continue;
            }
            float half = MachineIconLayout.ICON_SIZE / 2;
            assertEquals(slot.getIcon(), layout.hitTest(slot.getX() + half, slot.getY() + half));
            for (Slot other : slots) {
                if ((other == slot) || !other.isVisible()) {
                    continue;
                }
                float reach = MachineIconLayout.ICON_SIZE + (2 * MachineIconLayout.HIT_MARGIN);
                boolean overlap = (Math.abs(slot.getX() - other.getX()) < reach)
                        && (Math.abs(slot.getY() - other.getY()) < reach);
                assertFalse(overlap, slot.getIcon() + " overlaps " + other.getIcon());
            }
        }
    }

    @Test
    public void portraitRowAlongTheBottom() {
        float w = 1080;
        layout.update(true, w, 2340, 1528, 0, 0, JoystickAlignment.LEFT);

        assertAt(Icon.FULL_SCREEN, 20, 20);
        assertAt(Icon.MENU, (w / 6) - 16, 20);
        assertAt(Icon.SPEAKER, (w / 3) - 32, 20);
        assertAt(Icon.PAUSE_PLAY, (w / 2) - 48, 20);
        assertAt(Icon.KEYBOARD, (w - (w / 3)) - 64, 20);
        assertAt(Icon.JOYSTICK, (w - (w / 6)) - 80, 20);
        assertAt(Icon.BACK, w - 116, 20);
        assertHitsAndNoOverlap();
        assertNull(layout.hitTest(540, 1200));
    }

    @Test
    public void landscapeRowBetweenLandscapeAndPortrait() {
        float w = 1920;
        layout.update(false, w, 1500, 56, 0, 0, JoystickAlignment.OFF);

        float leftAdjustment = (w / 4) - 48;
        assertAt(Icon.FULL_SCREEN, ((w - ((w * 6) / 12)) - 96) - leftAdjustment, 16);
        assertAt(Icon.MENU, ((w - ((w * 5) / 12)) - 96) - leftAdjustment, 16);
        assertAt(Icon.BACK, (w - 96) - leftAdjustment, 16);
        assertHitsAndNoOverlap();
    }

    @Test
    public void normalLandscapeColumnsWithJoystickIconAwayFromTheJoystick() {
        float w = 1920;
        float h = 1080;
        layout.update(false, w, h, -363, 242, 0, JoystickAlignment.LEFT);

        assertAt(Icon.SPEAKER, 16, h - 112);
        assertAt(Icon.PAUSE_PLAY, 16, (h - (h / 3)) - 64);
        assertAt(Icon.KEYBOARD, 16, 0);
        assertAt(Icon.FULL_SCREEN, w - 112, h - 112);
        assertAt(Icon.MENU, w - 112, (h - (h / 3)) - 64);
        assertAt(Icon.JOYSTICK, w - 112, (h / 3) - 32);
        assertAt(Icon.BACK, w - 112, 16);
        assertHitsAndNoOverlap();

        layout.update(false, w, h, -363, 242, 0, JoystickAlignment.RIGHT);
        assertAt(Icon.JOYSTICK, 16, (h / 3) - 32);
        assertHitsAndNoOverlap();
    }

    @Test
    public void columnOnTheRightWhenTheScreenIsPushedLeft() {
        float w = 1920;
        float h = 1250;
        layout.update(false, w, h, -194, 100, 20, JoystickAlignment.LEFT);

        assertAt(Icon.FULL_SCREEN, w - 112, h - 112);
        assertAt(Icon.MENU, w - 112, (h - (h / 6)) - 80);
        assertAt(Icon.SPEAKER, w - 112, (h - (h / 3)) - 64);
        assertAt(Icon.PAUSE_PLAY, w - 112, (h / 2) - 48);
        assertAt(Icon.JOYSTICK, w - 112, (h / 3) - 32);
        assertAt(Icon.KEYBOARD, w - 112, (h / 6) - 16);
        assertAt(Icon.BACK, w - 112, 16);
        assertHitsAndNoOverlap();
    }
}
