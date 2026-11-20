package emu.jvic.settings;

import emu.jvic.config.AppConfigItem;

/**
 * The two layers that come from jvic itself rather than from the user: the built-in
 * defaults (which can differ by kind of program) and the program's own configuration.
 *
 * <p>Only values a program really specifies count as its own configuration. Defaults
 * that jvic used to write into a program's {@code AppConfigItem} (PAL, Auto RAM and
 * joystick off for programs the user opens, PAL and unexpanded for BASIC) are built-in
 * defaults instead, so that the user's settings for all programs can change them.
 */
final class ProgramDefaults {

    private ProgramDefaults() {
    }

    /**
     * @param def  A setting.
     * @param kind The kind of program.
     *
     * @return jvic's built-in default for that setting and kind of program.
     */
    static String builtIn(SettingDef def, ProgramKind kind) {
        String id = def.getId();
        if (SettingsCatalogue.MACHINE_RAM.equals(id) && (kind == ProgramKind.BASIC)) {
            return SettingsCatalogue.RAM_UNEXPANDED;
        }
        if (SettingsCatalogue.CONTROLS_JOYSTICK.equals(id) && (kind == ProgramKind.GAME_LIST)) {
            // Most programs in the game list are joystick games.
            return SettingDef.ON;
        }
        return def.getDefaultValue();
    }

    /**
     * @param def     A setting.
     * @param program The program's configuration.
     * @param kind    The kind of program.
     *
     * @return The value the program's own configuration gives the setting, or null if
     *         it doesn't give one.
     */
    static String fromProgram(SettingDef def, AppConfigItem program, ProgramKind kind) {
        String id = def.getId();
        String value = null;
        if (SettingsCatalogue.MACHINE_TV.equals(id)) {
            // For programs the user opens, this is set only if "NTSC" is in the file name.
            value = (kind == ProgramKind.BASIC) ? null : program.getMachineType();
        } else if (SettingsCatalogue.MACHINE_RAM.equals(id)) {
            // A game list entry without "ram" relies on auto detection, which is the
            // curated choice for that game. Other kinds of program never specify RAM.
            value = (kind == ProgramKind.GAME_LIST) ? program.getRam() : null;
        } else if (SettingsCatalogue.MEDIA_DISK_WRITE.equals(id)) {
            value = ConfigValues.parseDiskWrite(program.getDiskWrite());
        } else if (SettingsCatalogue.DISPLAY_FILTER.equals(id)) {
            String[] filter = ConfigValues.parseTextureFilter(program.getTextureFilter());
            value = (filter != null) ? filter[0] : null;
        } else if (SettingsCatalogue.DISPLAY_SOFT_AMOUNT.equals(id)) {
            String[] filter = ConfigValues.parseTextureFilter(program.getTextureFilter());
            value = (filter != null) ? filter[1] : null;
        } else if (SettingsCatalogue.DISPLAY_PALETTE.equals(id)) {
            value = ConfigValues.parsePalette(program.getPalette());
        } else if (SettingsCatalogue.CONTROLS_JOYSTICK.equals(id)) {
            value = ConfigValues.parseJoystick(program.getJoystick());
            if ((value == null) && (kind == ProgramKind.GAME_LIST)) {
                // In programs.json, no "joystick" means ON; the entries that aren't
                // joystick games say "OFF".
                value = SettingDef.ON;
            }
        }
        return def.normalise(value);
    }
}
