package emu.jvic.settings;

/**
 * The groups that settings are shown under in the settings screen, in display order.
 */
public enum SettingCategory {

    MACHINE("Machine"),
    DISPLAY("Display"),
    SOUND("Sound"),
    CONTROLS("Controls"),
    MEDIA("Media"),
    GENERAL("General");

    private final String label;

    SettingCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
