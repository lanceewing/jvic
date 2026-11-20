package emu.jvic.settings;

/**
 * The places a setting's value can come from, lowest precedence first. A higher layer
 * wins only if it has a value.
 */
public enum SettingLayer {

    /** jvic's own default for this kind of program. */
    BUILT_IN("default"),

    /** The user's setting for all programs. */
    ALL_PROGRAMS("set for all programs"),

    /** The program's own configuration: its programs.json entry, or detected from the file. */
    PROGRAM_CONFIG("set by this program"),

    /** The user's setting for this program only. */
    THIS_PROGRAM("set for this program"),

    /** URL parameters, autorun-config.txt or desktop arguments. Never saved. */
    LAUNCH_OPTION("set by launch options");

    private final String description;

    SettingLayer(String description) {
        this.description = description;
    }

    /**
     * @return A short description for the settings screen, e.g. "set by this program".
     */
    public String getDescription() {
        return description;
    }
}
