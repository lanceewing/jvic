package emu.jvic.ui;

import emu.jvic.ui.MachineInputProcessor.JoystickAlignment;

/**
 * Where the machine screen's icons go, for each of its four layouts. The drawing
 * code and the touch handling both read the slots worked out here, so an icon is
 * always tapped where it is drawn, and moving or adding an icon is a change in one
 * place.
 *
 * <p>The layouts:
 * <ul>
 * <li>Portrait: one row of seven along the bottom.</li>
 * <li>Landscape, between landscape and portrait (the VIC screen doesn't reach the
 * bottom, or the side padding is narrow): one row of seven along the bottom.</li>
 * <li>Landscape, normal: a column of icons on each side. The joystick icon goes on the
 * side away from the touch joystick.</li>
 * <li>Landscape, with the VIC screen pushed left: one column of seven on the right.</li>
 * </ul>
 *
 * <p>Slots are reused between frames, so working out the layout makes no garbage.
 */
public class MachineIconLayout {

    /** The icons on the machine screen. */
    public enum Icon {
        FULL_SCREEN, MENU, SPEAKER, PAUSE_PLAY, KEYBOARD, JOYSTICK, BACK
    }

    /** The size of every icon, in world units. */
    public static final float ICON_SIZE = 96;

    /**
     * How far a tap can be outside an icon's image and still count as a tap on it. Less
     * than half the smallest gap between icons (48, between the first two in portrait),
     * so tap areas never overlap.
     */
    public static final float HIT_MARGIN = 22;

    /** One icon's position. */
    public static final class Slot {
        Icon icon;
        float x;
        float y;
        boolean visible;

        public Icon getIcon() {
            return icon;
        }

        public float getX() {
            return x;
        }

        public float getY() {
            return y;
        }

        public boolean isVisible() {
            return visible;
        }
    }

    private final Slot[] slots;

    public MachineIconLayout() {
        Icon[] icons = Icon.values();
        slots = new Slot[icons.length];
        for (int i = 0; i < icons.length; i++) {
            slots[i] = new Slot();
            slots[i].icon = icons[i];
        }
    }

    private void place(Icon icon, float x, float y) {
        Slot slot = slots[icon.ordinal()];
        slot.x = x;
        slot.y = y;
        slot.visible = true;
    }

    /**
     * Works out the slots for the current screen.
     *
     * @param portrait          true for the portrait layout.
     * @param width             The viewport's world width.
     * @param height            The viewport's world height.
     * @param vicScreenBase     {@link ViewportManager#getVICScreenBase()}.
     * @param sidePaddingWidth  {@link ViewportManager#getSidePaddingWidth()}.
     * @param cameraXOffset     How far the VIC screen is pushed left (0 when centred).
     * @param joystickAlignment Where the touch joystick is.
     */
    public void update(boolean portrait, float width, float height, int vicScreenBase,
            float sidePaddingWidth, float cameraXOffset, JoystickAlignment joystickAlignment) {
        for (Slot slot : slots) {
            slot.visible = false;
        }

        if (portrait) {
            place(Icon.FULL_SCREEN, 20, 20);
            place(Icon.MENU, (width / 6) - 16, 20);
            place(Icon.SPEAKER, (width / 3) - 32, 20);
            place(Icon.PAUSE_PLAY, (width / 2) - 48, 20);
            place(Icon.KEYBOARD, (width - (width / 3)) - 64, 20);
            place(Icon.JOYSTICK, (width - (width / 6)) - 80, 20);
            place(Icon.BACK, width - 116, 20);

        } else if (cameraXOffset == 0) {
            if ((vicScreenBase > 0) || (sidePaddingWidth <= 64)) {
                // Between full landscape and full portrait: a row along the bottom.
                float leftAdjustment = (width / 4) - 48;
                Icon[] order = { Icon.FULL_SCREEN, Icon.MENU, Icon.SPEAKER, Icon.PAUSE_PLAY,
                        Icon.KEYBOARD, Icon.JOYSTICK, Icon.BACK };
                for (int i = 0; i < order.length; i++) {
                    int twelfths = 6 - i;
                    place(order[i], ((width - ((width * twelfths) / 12)) - 96) - leftAdjustment, 16);
                }
            } else {
                // Normal landscape: icons both sides.
                place(Icon.SPEAKER, 16, height - 112);
                place(Icon.PAUSE_PLAY, 16, (height - (height / 3)) - 64);
                place(Icon.KEYBOARD, 16, 0);
                place(Icon.FULL_SCREEN, width - 112, height - 112);
                place(Icon.MENU, width - 112, (height - (height / 3)) - 64);
                if (joystickAlignment == JoystickAlignment.RIGHT) {
                    place(Icon.JOYSTICK, 16, (height / 3) - 32);
                } else {
                    place(Icon.JOYSTICK, width - 112, (height / 3) - 32);
                }
                place(Icon.BACK, width - 112, 16);
            }

        } else if (cameraXOffset > 0) {
            // The VIC screen is pushed left: one column on the right.
            place(Icon.FULL_SCREEN, width - 112, height - 112);
            place(Icon.MENU, width - 112, (height - (height / 6)) - 80);
            place(Icon.SPEAKER, width - 112, (height - (height / 3)) - 64);
            place(Icon.PAUSE_PLAY, width - 112, (height / 2) - 48);
            place(Icon.JOYSTICK, width - 112, (height / 3) - 32);
            place(Icon.KEYBOARD, width - 112, (height / 6) - 16);
            place(Icon.BACK, width - 112, 16);
        }
    }

    /**
     * @return The slots, one per icon, in {@link Icon} order. Check
     *         {@link Slot#isVisible()} before using one.
     */
    public Slot[] getSlots() {
        return slots;
    }

    /**
     * @param icon An icon.
     *
     * @return That icon's slot.
     */
    public Slot getSlot(Icon icon) {
        return slots[icon.ordinal()];
    }

    /**
     * @param x A world x position.
     * @param y A world y position.
     *
     * @return The icon whose tap area contains the position, or null if none.
     */
    public Icon hitTest(float x, float y) {
        for (Slot slot : slots) {
            if (slot.visible
                    && (x >= slot.x - HIT_MARGIN) && (x <= slot.x + ICON_SIZE + HIT_MARGIN)
                    && (y >= slot.y - HIT_MARGIN) && (y <= slot.y + ICON_SIZE + HIT_MARGIN)) {
                return slot.icon;
            }
        }
        return null;
    }
}
