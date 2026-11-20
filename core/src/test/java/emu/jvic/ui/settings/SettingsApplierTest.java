package emu.jvic.ui.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import emu.jvic.config.AppConfigItem;
import emu.jvic.config.AppConfigItem.FileLocation;
import emu.jvic.settings.LaunchOptions;
import emu.jvic.settings.ResolvedSettings;
import emu.jvic.settings.SettingsCatalogue;
import emu.jvic.settings.SettingsResolver;
import emu.jvic.settings.SettingsStore;
import emu.jvic.testutil.InMemoryPreferences;

/**
 * Tests {@link SettingsApplier#getRestartNeeded}: a restart is offered when the TV
 * standard or RAM the program would now get differs from what it was started with,
 * and not for changes a launch option overrides anyway.
 */
public class SettingsApplierTest {

    private final SettingsStore store = new SettingsStore(new InMemoryPreferences());
    private final SettingsResolver resolver = new SettingsResolver(store);
    private final AppConfigItem program = AppConfigItem.createAdhoc("game.prg", FileLocation.LOCAL);

    private ResolvedSettings resolve(LaunchOptions launch) {
        return resolver.resolve(program, launch, "game", false);
    }

    private List<String> restartNeeded(AppConfigItem running, LaunchOptions launch) {
        return SettingsApplier.getRestartNeeded(running, resolve(launch));
    }

    @Test
    public void nothingChangedNeedsNoRestart() {
        AppConfigItem running = resolver.resolveProgram(program, LaunchOptions.NONE, "game", false);

        assertTrue(restartNeeded(running, LaunchOptions.NONE).isEmpty());
    }

    @Test
    public void changedTvAndRamNeedARestart() {
        AppConfigItem running = resolver.resolveProgram(program, LaunchOptions.NONE, "game", false);
        store.setProgram("game", SettingsCatalogue.MACHINE_TV, "NTSC");
        store.setGlobal(SettingsCatalogue.MACHINE_RAM, "RAM_8K");

        assertEquals(Arrays.asList(SettingsCatalogue.MACHINE_TV, SettingsCatalogue.MACHINE_RAM),
                restartNeeded(running, LaunchOptions.NONE));
    }

    @Test
    public void otherSettingsNeedNoRestart() {
        AppConfigItem running = resolver.resolveProgram(program, LaunchOptions.NONE, "game", false);
        store.setProgram("game", SettingsCatalogue.DISPLAY_PALETTE, "MID");
        store.setProgram("game", SettingsCatalogue.MEDIA_DISK_WRITE, "TEMP");

        assertTrue(restartNeeded(running, LaunchOptions.NONE).isEmpty());
    }

    @Test
    public void aChangeALaunchOptionOverridesNeedsNoRestart() {
        Map<String, String> args = new HashMap<String, String>();
        args.put("tv", "pal");
        LaunchOptions launch = LaunchOptions.parse(args);
        AppConfigItem running = resolver.resolveProgram(program, launch, "game", false);
        store.setProgram("game", SettingsCatalogue.MACHINE_TV, "NTSC");

        assertTrue(restartNeeded(running, launch).isEmpty());
    }

    @Test
    public void noRunningProgramNeedsNoRestart() {
        assertTrue(SettingsApplier.getRestartNeeded(null, resolve(LaunchOptions.NONE)).isEmpty());
    }
}
