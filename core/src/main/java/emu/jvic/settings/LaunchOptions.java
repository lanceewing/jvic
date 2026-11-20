package emu.jvic.settings;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import emu.jvic.config.AppConfigItem;

/**
 * The options a program was launched with: URL parameters and #/ path parts, the
 * lines of autorun-config.txt, or desktop command line arguments. They are the highest
 * layer of settings and are never saved.
 *
 * <p>The parameter names and values are the ones jvic has always accepted:
 * <ul>
 * <li>{@code ram}: 0K/UNEXPANDED/UNEXP, 3K, 8K, 16K, 24K, 32K, 35K</li>
 * <li>{@code tv}: PAL, NTSC, VIC44, VIC44K</li>
 * <li>{@code diskwrite} (or {@code dskwrt}): off, temp, persist</li>
 * <li>{@code filter}: nearest, linear, or 0.0 to 1.0 for the soft filter</li>
 * <li>{@code palette}: MID for the midpoint palette</li>
 * <li>{@code joystick}: on, off</li>
 * <li>{@code type}: TAPE, DISK, PRG, CART, PCV</li>
 * <li>{@code entry}, {@code addr}, {@code cmd}: passed through as they are</li>
 * <li>{@code settings}: hidden, to hide the settings screen and ignore saved settings
 * (for embedded deployments)</li>
 * </ul>
 * Unrecognised values are ignored.
 */
public final class LaunchOptions {

    /** No launch options, for programs started from inside jvic. */
    public static final LaunchOptions NONE = new LaunchOptions(new HashMap<String, String>(),
            null, null, null, null, false);

    private final Map<String, String> settings;
    private final String fileType;
    private final String entryName;
    private final String loadAddress;
    private final String autoRunCommand;
    private final boolean settingsHidden;

    private LaunchOptions(Map<String, String> settings, String fileType, String entryName,
            String loadAddress, String autoRunCommand, boolean settingsHidden) {
        this.settings = Collections.unmodifiableMap(settings);
        this.fileType = fileType;
        this.entryName = entryName;
        this.loadAddress = loadAddress;
        this.autoRunCommand = autoRunCommand;
        this.settingsHidden = settingsHidden;
    }

    /**
     * Reads the launch options from the arguments map built by each platform's launcher.
     *
     * @param args The arguments. May be null.
     *
     * @return The launch options.
     */
    public static LaunchOptions parse(Map<String, String> args) {
        if ((args == null) || args.isEmpty()) {
            return NONE;
        }

        Map<String, String> settings = new HashMap<String, String>();

        putIfValid(settings, SettingsCatalogue.MACHINE_RAM, ConfigValues.parseLaunchRam(args.get("ram")));
        putIfValid(settings, SettingsCatalogue.MACHINE_TV, parseTv(args.get("tv")));

        String diskWrite = args.get("diskwrite");
        if (diskWrite == null) {
            diskWrite = args.get("dskwrt");
        }
        putIfValid(settings, SettingsCatalogue.MEDIA_DISK_WRITE, ConfigValues.parseDiskWrite(diskWrite));

        String[] filter = ConfigValues.parseTextureFilter(args.get("filter"));
        if (filter != null) {
            putIfValid(settings, SettingsCatalogue.DISPLAY_FILTER, filter[0]);
            putIfValid(settings, SettingsCatalogue.DISPLAY_SOFT_AMOUNT, filter[1]);
        }

        putIfValid(settings, SettingsCatalogue.DISPLAY_PALETTE, ConfigValues.parsePalette(args.get("palette")));
        putIfValid(settings, SettingsCatalogue.CONTROLS_JOYSTICK, ConfigValues.parseJoystick(args.get("joystick")));

        String settingsOption = args.get("settings");
        boolean hidden = (settingsOption != null) && settingsOption.trim().equalsIgnoreCase("hidden");

        return new LaunchOptions(settings, parseFileType(args.get("type")), args.get("entry"),
                args.get("addr"), args.get("cmd"), hidden);
    }

    private static void putIfValid(Map<String, String> settings, String id, String value) {
        String normalised = SettingsCatalogue.get(id).normalise(value);
        if (normalised != null) {
            settings.put(id, normalised);
        }
    }

    private static String parseTv(String tv) {
        if (tv == null) {
            return null;
        }
        switch (tv.trim().toUpperCase()) {
            case "PAL":
            case "NTSC":
            case "VIC44":
            case "VIC44K":
                return tv.trim().toUpperCase();
            default:
                return null;
        }
    }

    private static String parseFileType(String type) {
        if (type == null) {
            return null;
        }
        switch (type.trim().toUpperCase()) {
            case "TAPE":
            case "DISK":
            case "PRG":
            case "CART":
            case "PCV":
                return type.trim().toUpperCase();
            default:
                return null;
        }
    }

    /**
     * @param id A setting id.
     *
     * @return The value the launch options give the setting, or null if none.
     */
    public String getSetting(String id) {
        return settings.get(id);
    }

    /**
     * @return true if the settings screen should be hidden and saved settings ignored.
     */
    public boolean isSettingsHidden() {
        return settingsHidden;
    }

    /**
     * Copies the launch options that aren't settings (type, entry, addr, cmd) into a
     * program's configuration.
     *
     * @param program The program's configuration, which is changed.
     */
    public void applyProgramFields(AppConfigItem program) {
        if (fileType != null) {
            program.setFileType(fileType);
        }
        if (entryName != null) {
            program.setEntryName(entryName);
        }
        if (loadAddress != null) {
            program.setLoadAddress(loadAddress);
        }
        if (autoRunCommand != null) {
            program.setAutoRunCommand(autoRunCommand);
        }
    }
}
