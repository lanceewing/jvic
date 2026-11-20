package emu.jvic.ui;

/**
 * The actions in the machine screen's "..." menu, in menu order. Each one does the
 * same as its hotkey.
 */
public enum MachineAction {

    SETTINGS("Settings", "Alt+O", "png/settings.png"),
    SCREEN_SIZE("Screen size", "Alt+-", "png/screen_icon.png"),
    RESET("Reset", "Alt+F9", "png/reset_icon.png"),
    POWER_CYCLE("Power cycle", "Alt+F12", "png/power_icon.png"),
    QUICK_SAVE("Quick save", "Alt+F11", "png/quick_save_icon.png"),
    QUICK_LOAD("Quick load", "Alt+F10", "png/quick_load_icon.png"),
    SCREENSHOT("Screenshot", "Alt+Y", "png/camera_icon.png"),
    WARP("Warp speed", "Alt+W", "png/warp_speed_icon.png"),
    RESET_DISK("Reset disk", "Alt+Z", "png/disk_icon.png"),
    COPY_TEXT("Copy screen text", "Alt+Del", "png/copy_icon.png"),
    PASTE("Paste text", "Alt+Ins", "png/paste_icon.png"),
    MONITOR("Monitor", "Alt+H", "png/monitor_icon.png"),
    SHORTCUTS("Keyboard shortcuts", "", "png/keyboard_icon.png");

    private final String label;
    private final String hotkey;
    private final String iconPath;

    MachineAction(String label, String hotkey, String iconPath) {
        this.label = label;
        this.hotkey = hotkey;
        this.iconPath = iconPath;
    }

    public String getLabel() {
        return label;
    }

    /**
     * @return The hotkey that does the same thing, shown in the menu; empty if none.
     */
    public String getHotkey() {
        return hotkey;
    }

    public String getIconPath() {
        return iconPath;
    }
}
