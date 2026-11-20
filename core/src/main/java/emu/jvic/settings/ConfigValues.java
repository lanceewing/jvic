package emu.jvic.settings;

/**
 * Converts between the values {@code AppConfigItem} (and so programs.json and the
 * launch options) uses and the stored values of settings.
 */
final class ConfigValues {

    private ConfigValues() {
    }

    /**
     * Splits an {@code AppConfigItem} texture filter ("nearest", "linear", or a number
     * from 0.0 to 1.0 for the soft filter) into the filter and soft amount settings.
     *
     * @param textureFilter The texture filter. May be null.
     *
     * @return {filter, softAmount}, where softAmount is null unless a number was given;
     *         or null if the value isn't recognised.
     */
    static String[] parseTextureFilter(String textureFilter) {
        if (textureFilter == null) {
            return null;
        }
        String value = textureFilter.trim();
        if (value.equalsIgnoreCase("nearest")) {
            return new String[] { SettingsCatalogue.FILTER_NEAREST, null };
        }
        if (value.equalsIgnoreCase("linear")) {
            return new String[] { SettingsCatalogue.FILTER_LINEAR, null };
        }
        String amount = SettingsCatalogue.get(SettingsCatalogue.DISPLAY_SOFT_AMOUNT).normalise(value);
        if (amount != null) {
            return new String[] { SettingsCatalogue.FILTER_SOFT, amount };
        }
        return null;
    }

    /**
     * @return The {@code AppConfigItem} texture filter for the filter and soft amount
     *         settings.
     */
    static String toTextureFilter(String filter, String softAmount) {
        if (SettingsCatalogue.FILTER_NEAREST.equals(filter)) {
            return "nearest";
        }
        if (SettingsCatalogue.FILTER_LINEAR.equals(filter)) {
            return "linear";
        }
        return softAmount;
    }

    /**
     * @return The palette setting for an {@code AppConfigItem} palette ("MID" is the
     *         midpoint palette; anything else is the alternating one), or null if not set.
     */
    static String parsePalette(String palette) {
        if ((palette == null) || palette.trim().isEmpty()) {
            return null;
        }
        return palette.trim().equalsIgnoreCase("mid") ? SettingsCatalogue.PALETTE_MID
                : SettingsCatalogue.PALETTE_ALTERNATING;
    }

    /**
     * @return The {@code AppConfigItem} palette for the palette setting.
     */
    static String toPalette(String palette) {
        return SettingsCatalogue.PALETTE_MID.equals(palette) ? "MID" : null;
    }

    /**
     * @return The joystick setting for an {@code AppConfigItem} joystick value ("OFF" is
     *         off; anything else is on), or null if not set.
     */
    static String parseJoystick(String joystick) {
        if ((joystick == null) || joystick.trim().isEmpty()) {
            return null;
        }
        return joystick.trim().equalsIgnoreCase("off") ? SettingDef.OFF : SettingDef.ON;
    }

    /**
     * @return The disk write setting for an {@code AppConfigItem} disk write value
     *         ("off", "temp" or "persist"), or null if not set or not recognised.
     */
    static String parseDiskWrite(String diskWrite) {
        if (diskWrite == null) {
            return null;
        }
        String value = diskWrite.trim().toLowerCase();
        if (value.equals("off") || value.equals("temp") || value.equals("persist")) {
            return value.toUpperCase();
        }
        return null;
    }

    /**
     * @return The {@code AppConfigItem} disk write value for the disk write setting, or
     *         null for automatic.
     */
    static String toDiskWrite(String diskWrite) {
        if ((diskWrite == null) || SettingsCatalogue.DISK_WRITE_AUTO.equals(diskWrite)) {
            return null;
        }
        return diskWrite.toLowerCase();
    }

    /**
     * @return The RAM setting for a launch option RAM value (e.g. "16K", "UNEXP"), or
     *         null if not recognised.
     */
    static String parseLaunchRam(String ram) {
        if (ram == null) {
            return null;
        }
        switch (ram.trim().toUpperCase()) {
            case "0K":
            case "UNEXPANDED":
            case "UNEXP":
                return SettingsCatalogue.RAM_UNEXPANDED;
            case "3K":
                return "RAM_3K";
            case "8K":
                return "RAM_8K";
            case "16K":
                return "RAM_16K";
            case "24K":
                return "RAM_24K";
            case "32K":
                return "RAM_32K";
            case "35K":
                return "RAM_35K";
            default:
                return null;
        }
    }
}
