package emu.jvic.ui.settings;

import java.util.ArrayList;
import java.util.List;

import emu.jvic.JVicRunner;
import emu.jvic.MachineScreen;
import emu.jvic.config.AppConfigItem;
import emu.jvic.settings.ResolvedSettings;
import emu.jvic.settings.SettingDef;
import emu.jvic.settings.SettingsCatalogue;
import emu.jvic.ui.MachineInputProcessor;
import emu.jvic.ui.MachineInputProcessor.JoystickAlignment;
import emu.jvic.ui.MachineInputProcessor.ScreenSize;

/**
 * Applies settings to the machine screen: those that are applied when a program starts,
 * and those that can be changed while it runs.
 */
public final class SettingsApplier {

    private SettingsApplier() {
    }

    /**
     * @param id A setting id.
     *
     * @return true if a change to the setting can be applied while a program runs.
     *         Other settings are used the next time a program starts.
     */
    public static boolean isAppliedLive(String id, JVicRunner runner) {
        if (SettingsCatalogue.DISPLAY_PALETTE.equals(id)) {
            return runner.isMachineSettingSupported("palette");
        }
        return SettingsCatalogue.DISPLAY_FILTER.equals(id)
                || SettingsCatalogue.DISPLAY_SOFT_AMOUNT.equals(id)
                || SettingsCatalogue.DISPLAY_SCREEN_SIZE.equals(id)
                || SettingsCatalogue.DISPLAY_FPS.equals(id)
                || SettingsCatalogue.CONTROLS_JOYSTICK_SIDE.equals(id)
                || SettingsCatalogue.GENERAL_PAUSE_IN_SETTINGS.equals(id)
                || SettingsCatalogue.GENERAL_ADVANCED.equals(id);
    }

    /**
     * Applies the settings that the machine screen (rather than the machine) uses, when
     * a program starts: screen size, FPS overlay, which side the touch joystick is on,
     * and whether sound starts on. The rest are in the program's AppConfigItem.
     *
     * @param settings      The resolved settings for the program.
     * @param machineScreen The machine screen.
     */
    public static void applyAtStart(ResolvedSettings settings, MachineScreen machineScreen) {
        MachineInputProcessor input = machineScreen.getMachineInputProcessor();
        input.setScreenSize(ScreenSize.valueOf(settings.get(SettingsCatalogue.DISPLAY_SCREEN_SIZE)));
        machineScreen.setShowFPS(SettingDef.ON.equals(settings.get(SettingsCatalogue.DISPLAY_FPS)));
        input.setJoystickSide(joystickSide(settings));
        input.setSoundOffAtStart(!SettingDef.ON.equals(settings.get(SettingsCatalogue.SOUND_ON_START)));
    }

    /**
     * Applies one changed setting to the running program, if it can be applied live.
     *
     * @param id            The setting that changed.
     * @param settings      The settings resolved again for the running program, so that a
     *                      program's own value still wins over the user's general setting.
     * @param machineScreen The machine screen.
     */
    public static void applyLive(String id, ResolvedSettings settings, MachineScreen machineScreen) {
        MachineInputProcessor input = machineScreen.getMachineInputProcessor();
        if (SettingsCatalogue.DISPLAY_FILTER.equals(id) || SettingsCatalogue.DISPLAY_SOFT_AMOUNT.equals(id)) {
            machineScreen.changeTextureFilter(settings.getTextureFilter());
        } else if (SettingsCatalogue.DISPLAY_SCREEN_SIZE.equals(id)) {
            input.applyScreenSize(ScreenSize.valueOf(settings.get(SettingsCatalogue.DISPLAY_SCREEN_SIZE)));
        } else if (SettingsCatalogue.DISPLAY_FPS.equals(id)) {
            machineScreen.setShowFPS(SettingDef.ON.equals(settings.get(SettingsCatalogue.DISPLAY_FPS)));
        } else if (SettingsCatalogue.CONTROLS_JOYSTICK_SIDE.equals(id)) {
            input.setJoystickSide(joystickSide(settings));
        } else if (SettingsCatalogue.DISPLAY_PALETTE.equals(id)) {
            JVicRunner runner = machineScreen.getJvicRunner();
            if (runner.isMachineSettingSupported("palette")) {
                runner.applyMachineSetting("palette", settings.getConfigPalette());
                if (runner.isPaused() && runner.isMachineControlSupported()) {
                    // Show the new colours while the settings have the machine paused.
                    // The machine may be paused part way through a frame, and a frame
                    // advance only runs to the end of that frame, so a second one is
                    // needed for a whole frame in the new colours.
                    runner.requestFrameAdvance();
                    runner.requestFrameAdvance();
                }
            }
        }
    }

    /**
     * Lists the settings changed since the program started that need a restart to take
     * effect: TV standard and RAM.
     *
     * @param running  The configuration the program was started with.
     * @param settings The settings resolved for the program now.
     *
     * @return The setting ids, empty if no restart is needed.
     */
    public static List<String> getRestartNeeded(AppConfigItem running, ResolvedSettings settings) {
        List<String> ids = new ArrayList<String>();
        if (running != null) {
            if (!settings.get(SettingsCatalogue.MACHINE_TV).equals(running.getMachineType())) {
                ids.add(SettingsCatalogue.MACHINE_TV);
            }
            if (!settings.get(SettingsCatalogue.MACHINE_RAM).equals(running.getRam())) {
                ids.add(SettingsCatalogue.MACHINE_RAM);
            }
        }
        return ids;
    }

    private static JoystickAlignment joystickSide(ResolvedSettings settings) {
        return "RIGHT".equals(settings.get(SettingsCatalogue.CONTROLS_JOYSTICK_SIDE))
                ? JoystickAlignment.RIGHT : JoystickAlignment.LEFT;
    }
}
