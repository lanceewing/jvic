package emu.jvic.settings;

import java.util.HashMap;
import java.util.Map;

import emu.jvic.config.AppConfigItem;

/**
 * The effective value of every setting for one launch of a program, and the layer
 * each value came from.
 */
public final class ResolvedSettings {

    private final Map<String, String> values = new HashMap<String, String>();
    private final Map<String, SettingLayer> sources = new HashMap<String, SettingLayer>();

    void put(String id, String value, SettingLayer source) {
        values.put(id, value);
        sources.put(id, source);
    }

    /**
     * @param id A setting id.
     *
     * @return The effective value, or null for an ACTION or an unknown id.
     */
    public String get(String id) {
        return values.get(id);
    }

    /**
     * @param id A setting id.
     *
     * @return The layer the effective value came from, or null for an ACTION or an
     *         unknown id.
     */
    public SettingLayer getSource(String id) {
        return sources.get(id);
    }

    /**
     * @return The {@code AppConfigItem} texture filter ("nearest", "linear", or the soft
     *         filter's amount) for the filter and soft amount settings.
     */
    public String getTextureFilter() {
        return ConfigValues.toTextureFilter(get(SettingsCatalogue.DISPLAY_FILTER),
                get(SettingsCatalogue.DISPLAY_SOFT_AMOUNT));
    }

    /**
     * @return The {@code AppConfigItem} palette ("MID", or null for alternating).
     */
    public String getConfigPalette() {
        return ConfigValues.toPalette(get(SettingsCatalogue.DISPLAY_PALETTE));
    }

    /**
     * Writes the settings that {@code AppConfigItem} carries into a program's
     * configuration: TV standard, RAM, disk writes, filter, palette and joystick.
     *
     * @param program The program's configuration, which is changed.
     */
    public void applyTo(AppConfigItem program) {
        program.setMachineType(get(SettingsCatalogue.MACHINE_TV));
        program.setRam(get(SettingsCatalogue.MACHINE_RAM));
        program.setDiskWrite(ConfigValues.toDiskWrite(get(SettingsCatalogue.MEDIA_DISK_WRITE)));
        program.setTextureFilter(getTextureFilter());
        program.setPalette(ConfigValues.toPalette(get(SettingsCatalogue.DISPLAY_PALETTE)));
        program.setJoystick(get(SettingsCatalogue.CONTROLS_JOYSTICK));
    }
}
