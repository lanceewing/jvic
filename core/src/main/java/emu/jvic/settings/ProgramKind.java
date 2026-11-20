package emu.jvic.settings;

import emu.jvic.config.AppConfigItem;

/**
 * The kinds of program jvic starts, which have different built-in defaults.
 */
public enum ProgramKind {

    /** A program from the game list (programs.json). */
    GAME_LIST,

    /** The BASIC tile, or the #/basic path. */
    BASIC,

    /**
     * A program the user supplied: ?url=, the open file icon, drag and drop, a desktop
     * file path, or autorun-config.txt.
     */
    ADHOC;

    /**
     * @param program A program's configuration.
     *
     * @return What kind of program it is.
     */
    public static ProgramKind of(AppConfigItem program) {
        String name = program.getName();
        if (AppConfigItem.ADHOC_NAME.equals(name)) {
            return ADHOC;
        }
        String fileType = program.getFileType();
        if (AppConfigItem.BASIC_NAME.equals(name) && ((fileType == null) || fileType.trim().isEmpty())) {
            return BASIC;
        }
        return GAME_LIST;
    }
}
