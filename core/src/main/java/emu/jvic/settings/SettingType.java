package emu.jvic.settings;

/**
 * The kind of value a setting holds, which also decides how its row is drawn.
 */
public enum SettingType {

    /** One value from a fixed list of choices. */
    CHOICE,

    /** On or off. Stored as {@link SettingDef#ON} or {@link SettingDef#OFF}. */
    TOGGLE,

    /** A number between a minimum and a maximum. */
    SLIDER,

    /** A button that does something (e.g. reset all settings). Holds no value. */
    ACTION
}
