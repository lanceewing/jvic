package emu.jvic.settings;

import emu.jvic.config.AppConfigItem;

/**
 * Works out the effective value of each setting for a launch of a program, going
 * through the layers from lowest to highest (see {@link SettingLayer}):
 * <ol>
 * <li>jvic's built-in default</li>
 * <li>the user's setting for all programs</li>
 * <li>the program's own configuration</li>
 * <li>the user's setting for this program</li>
 * <li>the launch options</li>
 * </ol>
 * A higher layer wins only if it has a value. When saved settings are ignored (an
 * embedded deployment with {@code settings=hidden}), layers 2 and 4 are skipped.
 */
public class SettingsResolver {

    private final SettingsStore store;

    /**
     * Constructor for SettingsResolver.
     *
     * @param store The user's saved settings.
     */
    public SettingsResolver(SettingsStore store) {
        this.store = store;
    }

    /**
     * Resolves every setting for one launch.
     *
     * @param program     The program's own configuration. Not changed.
     * @param launch      The launch options, or {@link LaunchOptions#NONE}.
     * @param programKey  The program's key (see {@link ProgramIdentity}).
     * @param ignoreSaved true to skip the user's saved settings.
     *
     * @return The resolved settings.
     */
    public ResolvedSettings resolve(AppConfigItem program, LaunchOptions launch,
            String programKey, boolean ignoreSaved) {
        ProgramKind kind = ProgramKind.of(program);
        ResolvedSettings resolved = new ResolvedSettings();
        for (SettingDef def : SettingsCatalogue.all()) {
            if (!def.hasValue()) {
                continue;
            }
            String id = def.getId();
            String value = ProgramDefaults.builtIn(def, kind);
            SettingLayer source = SettingLayer.BUILT_IN;

            if (!ignoreSaved) {
                String global = store.getGlobal(id);
                if (global != null) {
                    value = global;
                    source = SettingLayer.ALL_PROGRAMS;
                }
            }

            String fromProgram = ProgramDefaults.fromProgram(def, program, kind);
            if (fromProgram != null) {
                value = fromProgram;
                source = SettingLayer.PROGRAM_CONFIG;
            }

            if (!ignoreSaved) {
                String forProgram = store.getProgram(programKey, id);
                if (forProgram != null) {
                    value = forProgram;
                    source = SettingLayer.THIS_PROGRAM;
                }
            }

            String fromLaunch = launch.getSetting(id);
            if (fromLaunch != null) {
                value = fromLaunch;
                source = SettingLayer.LAUNCH_OPTION;
            }

            resolved.put(id, value, source);
        }
        return resolved;
    }

    /**
     * Resolves the settings for a launch and returns a copy of the program's
     * configuration with them (and the launch options that aren't settings) applied.
     * The program's own configuration isn't changed, so launch options only affect the
     * launch they came with.
     *
     * @param program     The program's own configuration. Not changed.
     * @param launch      The launch options, or {@link LaunchOptions#NONE}.
     * @param programKey  The program's key (see {@link ProgramIdentity}).
     * @param ignoreSaved true to skip the user's saved settings.
     *
     * @return The configuration to start the machine with.
     */
    public AppConfigItem resolveProgram(AppConfigItem program, LaunchOptions launch,
            String programKey, boolean ignoreSaved) {
        AppConfigItem config = program.copy();
        resolve(program, launch, programKey, ignoreSaved).applyTo(config);
        launch.applyProgramFields(config);
        return config;
    }
}
