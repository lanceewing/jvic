package emu.jvic.settings;

import static emu.jvic.settings.SettingDef.advancedChoice;
import static emu.jvic.settings.SettingDef.choice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Every setting jvic has, in the order the settings screen shows them. Adding a
 * setting means adding its definition here (plus a hook where it is applied).
 *
 * <p>Ids are stored in the user's preferences, so an id must never be renamed or
 * reused for a different setting.
 */
public final class SettingsCatalogue {

    public static final String MACHINE_TV = "machine.tv";
    public static final String MACHINE_RAM = "machine.ram";
    public static final String MEDIA_DISK_WRITE = "media.diskWrite";
    public static final String DISPLAY_FILTER = "display.filter";
    public static final String DISPLAY_SOFT_AMOUNT = "display.softAmount";
    public static final String DISPLAY_PALETTE = "display.palette";
    public static final String DISPLAY_SCREEN_SIZE = "display.screenSize";
    public static final String DISPLAY_FPS = "display.fps";
    public static final String CONTROLS_JOYSTICK = "controls.joystick";
    public static final String CONTROLS_JOYSTICK_SIDE = "controls.joystickSide";
    public static final String SOUND_ON_START = "sound.onStart";
    public static final String GENERAL_PAUSE_IN_SETTINGS = "general.pauseInSettings";
    public static final String GENERAL_ADVANCED = "general.advanced";
    public static final String GENERAL_RESET_PROGRAM = "general.resetProgram";
    public static final String GENERAL_RESET_ALL = "general.resetAll";

    // Values that code outside the catalogue needs to know about.
    public static final String TV_PAL = "PAL";
    public static final String TV_NTSC = "NTSC";
    public static final String RAM_AUTO = "RAM_AUTO";
    public static final String RAM_UNEXPANDED = "RAM_UNEXPANDED";
    public static final String DISK_WRITE_AUTO = "AUTO";
    public static final String FILTER_NEAREST = "NEAREST";
    public static final String FILTER_LINEAR = "LINEAR";
    public static final String FILTER_SOFT = "SOFT";
    public static final float DEFAULT_SOFT_AMOUNT = 0.4f;
    public static final String PALETTE_ALTERNATING = "ALTERNATING";
    public static final String PALETTE_MID = "MID";

    private static final List<SettingDef> ALL;

    static {
        List<SettingDef> all = new ArrayList<SettingDef>();

        all.add(SettingDef.choices(MACHINE_TV, SettingCategory.MACHINE, "TV standard",
                "PAL or NTSC VIC-20.", TV_PAL, true, ApplyMode.RESTART,
                choice(TV_PAL, "PAL"),
                choice(TV_NTSC, "NTSC"),
                advancedChoice("VIC44", "VIC44"),
                advancedChoice("VIC44K", "VIC44K")));

        all.add(SettingDef.choices(MACHINE_RAM, SettingCategory.MACHINE, "RAM expansion",
                "Programs in the game list use their own RAM unless you set it for that program.",
                RAM_AUTO, true, ApplyMode.RESTART,
                choice(RAM_AUTO, "Auto"),
                choice(RAM_UNEXPANDED, "Unexpanded"),
                choice("RAM_3K", "3K"),
                choice("RAM_8K", "8K"),
                choice("RAM_16K", "16K"),
                choice("RAM_24K", "24K"),
                choice("RAM_32K", "32K"),
                choice("RAM_35K", "35K")));

        all.add(SettingDef.choices(DISPLAY_FILTER, SettingCategory.DISPLAY, "Screen filter",
                "How the VIC-20 screen is scaled up.", FILTER_SOFT, true, ApplyMode.LIVE,
                choice(FILTER_NEAREST, "Sharp"),
                choice(FILTER_LINEAR, "Smooth"),
                choice(FILTER_SOFT, "Soft")));

        all.add(SettingDef.slider(DISPLAY_SOFT_AMOUNT, SettingCategory.DISPLAY, "Soft amount",
                "How much the soft filter blends sharp and smooth.", DEFAULT_SOFT_AMOUNT,
                0.0f, 1.0f, 0.05f, true, ApplyMode.LIVE));

        all.add(SettingDef.choices(DISPLAY_PALETTE, SettingCategory.DISPLAY, "PAL palette",
                "Alternating uses the odd and even line colours; midpoint averages them.",
                PALETTE_ALTERNATING, true, ApplyMode.LIVE,
                choice(PALETTE_ALTERNATING, "Alternating"),
                choice(PALETTE_MID, "Midpoint")));

        all.add(SettingDef.choices(DISPLAY_SCREEN_SIZE, SettingCategory.DISPLAY, "Screen size",
                "Only sizes that fit the window are used.", "FIT", false, ApplyMode.LIVE,
                choice("FIT", "Fit"),
                choice("X2", "2x"),
                choice("X3", "3x"),
                choice("X4", "4x"),
                choice("X5", "5x"),
                choice("X6", "6x"),
                choice("X7", "7x"),
                choice("X8", "8x")));

        all.add(SettingDef.toggle(DISPLAY_FPS, SettingCategory.DISPLAY, "Show FPS",
                "The frame rate and performance overlay (Alt+B).", false, false, ApplyMode.LIVE));

        all.add(SettingDef.toggle(SOUND_ON_START, SettingCategory.SOUND, "Sound at start",
                "Browsers still need a tap before sound can play.", true, false,
                ApplyMode.NEXT_START));

        all.add(SettingDef.choices(CONTROLS_JOYSTICK, SettingCategory.CONTROLS, "Joystick at start",
                "Programs in the game list use their own setting unless you set it for that program.",
                SettingDef.OFF, true, ApplyMode.NEXT_START,
                choice(SettingDef.ON, "On"),
                choice(SettingDef.OFF, "Off")));

        all.add(SettingDef.choices(CONTROLS_JOYSTICK_SIDE, SettingCategory.CONTROLS,
                "Touch joystick side", "Which side the touch joystick is on.", "LEFT", false,
                ApplyMode.LIVE,
                choice("LEFT", "Left"),
                choice("RIGHT", "Right")));

        all.add(SettingDef.choices(MEDIA_DISK_WRITE, SettingCategory.MEDIA, "Disk writes",
                "Whether changes to a disk are kept.", DISK_WRITE_AUTO, true, ApplyMode.NEXT_START,
                choice(DISK_WRITE_AUTO, "Automatic"),
                choice("OFF", "Off"),
                choice("TEMP", "This session"),
                choice("PERSIST", "Save")));

        all.add(SettingDef.toggle(GENERAL_PAUSE_IN_SETTINGS, SettingCategory.GENERAL,
                "Pause while settings are open", "Stops the machine while this screen is open.",
                true, false, ApplyMode.LIVE));

        all.add(SettingDef.toggle(GENERAL_ADVANCED, SettingCategory.GENERAL, "Show advanced options",
                "Shows rarely needed choices, such as the VIC44 machines.", false, false,
                ApplyMode.LIVE));

        all.add(SettingDef.action(GENERAL_RESET_PROGRAM, SettingCategory.GENERAL,
                "Reset this program's settings", "Goes back to the settings for all programs.",
                true));

        all.add(SettingDef.action(GENERAL_RESET_ALL, SettingCategory.GENERAL, "Reset all settings",
                "Forgets every saved setting.", false));

        ALL = Collections.unmodifiableList(all);
    }

    private SettingsCatalogue() {
    }

    /**
     * @return Every setting, in display order.
     */
    public static List<SettingDef> all() {
        return ALL;
    }

    /**
     * @param id A setting id.
     *
     * @return The setting with that id, or null if there isn't one.
     */
    public static SettingDef get(String id) {
        for (SettingDef def : ALL) {
            if (def.getId().equals(id)) {
                return def;
            }
        }
        return null;
    }

    /**
     * @param category A category.
     *
     * @return The settings in that category, in display order.
     */
    public static List<SettingDef> inCategory(SettingCategory category) {
        List<SettingDef> result = new ArrayList<SettingDef>();
        for (SettingDef def : ALL) {
            if (def.getCategory() == category) {
                result.add(def);
            }
        }
        return result;
    }
}
