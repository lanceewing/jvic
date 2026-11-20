package emu.jvic.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import emu.jvic.config.AppConfigItem;
import emu.jvic.config.AppConfigItem.FileLocation;
import emu.jvic.testutil.InMemoryPreferences;

/**
 * Tests {@link SettingsResolver}.
 *
 * <p>The first group checks that with nothing saved, programs start exactly as they
 * did before settings existed. The expected values are the ones the old code in
 * {@code JVic.create()}, {@code JVic.applyConfigArgs()} and {@code HomeScreen} wrote
 * into {@code AppConfigItem}. The second group checks the order of the five layers.
 */
public class SettingsResolverTest {

    private static final String KEY = "a-game";

    private SettingsStore store;
    private SettingsResolver resolver;

    @BeforeEach
    public void setUp() {
        store = new SettingsStore(new InMemoryPreferences());
        resolver = new SettingsResolver(store);
    }

    private static AppConfigItem gameListItem(String machineType, String ram) {
        AppConfigItem item = new AppConfigItem();
        item.setName("A Game");
        item.setFilePath("games/A/agame.prg");
        item.setFileType("PRG");
        item.setMachineType(machineType);
        if (ram != null) {
            item.setRam(ram);
        }
        return item;
    }

    private static AppConfigItem basicItem() {
        // As HomeScreen.buildBasicAppConfigItem() builds it.
        AppConfigItem item = new AppConfigItem();
        item.setName(AppConfigItem.BASIC_NAME);
        item.setFilePath("");
        item.setFileType("");
        item.setDiskWrite("persist");
        item.setJoystick("OFF");
        return item;
    }

    private static LaunchOptions launch(String... nameValuePairs) {
        Map<String, String> args = new HashMap<String, String>();
        for (int i = 0; i < nameValuePairs.length; i += 2) {
            args.put(nameValuePairs[i], nameValuePairs[i + 1]);
        }
        return LaunchOptions.parse(args);
    }

    private AppConfigItem resolve(AppConfigItem program, LaunchOptions launch) {
        return resolver.resolveProgram(program, launch, KEY, false);
    }

    // ---- Unchanged behaviour with nothing saved ----

    @Test
    public void gameListItemKeepsItsMachineAndRam() {
        AppConfigItem config = resolve(gameListItem("NTSC", "RAM_16K"), LaunchOptions.NONE);

        assertEquals("NTSC", config.getMachineType());
        assertEquals("RAM_16K", config.getRam());
        assertNull(config.getDiskWrite());
        assertNull(config.getPalette());
        assertTrue(config.isJoystickOnByDefault());
    }

    @Test
    public void defaultFilterIsTheSoftFilterAtItsDefaultAmount() {
        // MachineScreen treats a null filter and "0.4" the same: soft, blend 0.4.
        AppConfigItem config = resolve(gameListItem("PAL", null), LaunchOptions.NONE);

        assertEquals(String.valueOf(SettingsCatalogue.DEFAULT_SOFT_AMOUNT), config.getTextureFilter());
    }

    @Test
    public void gameListItemWithoutRamUsesAutoDetection() {
        assertEquals("RAM_AUTO", resolve(gameListItem("PAL", null), LaunchOptions.NONE).getRam());
    }

    @Test
    public void gameListItemCanTurnTheJoystickOff() {
        AppConfigItem item = gameListItem("PAL", null);
        item.setJoystick("OFF");

        assertFalse(resolve(item, LaunchOptions.NONE).isJoystickOnByDefault());
    }

    @Test
    public void basicIsPalUnexpandedWithSavedDiskAndCursorKeys() {
        AppConfigItem config = resolve(basicItem(), LaunchOptions.NONE);

        assertEquals("PAL", config.getMachineType());
        assertEquals("RAM_UNEXPANDED", config.getRam());
        assertEquals("persist", config.getDiskWrite());
        assertFalse(config.isJoystickOnByDefault());
    }

    @Test
    public void adhocProgramIsPalAutoRamWithCursorKeys() {
        AppConfigItem config = resolve(AppConfigItem.createAdhoc("C:/vic/game.prg", FileLocation.ABSOLUTE),
                LaunchOptions.NONE);

        assertEquals("PAL", config.getMachineType());
        assertEquals("RAM_AUTO", config.getRam());
        assertNull(config.getDiskWrite());
        assertFalse(config.isJoystickOnByDefault());
    }

    @Test
    public void adhocProgramWithNtscInItsNameIsNtsc() {
        AppConfigItem config = resolve(AppConfigItem.createAdhoc("https://x.org/Game_NTSC.zip",
                FileLocation.EXTERNAL), LaunchOptions.NONE);

        assertEquals("NTSC", config.getMachineType());
    }

    @Test
    public void basicPathWithRamTvAndPalette() {
        // #/basic/16k/pal/mid
        AppConfigItem config = resolve(basicItem(), launch("uri", "basic", "ram", "16K", "tv", "PAL",
                "palette", "MID"));

        assertEquals("RAM_16K", config.getRam());
        assertEquals("PAL", config.getMachineType());
        assertEquals("MID", config.getPalette());
    }

    @Test
    public void urlWithTvFilterJoystickAndProgramFields() {
        // ?url=...&tv=ntsc&filter=nearest&joystick=on&type=cart&cmd=SYS 40960
        AppConfigItem config = resolve(AppConfigItem.createAdhoc("https://x.org/game.zip", FileLocation.EXTERNAL),
                launch("url", "https://x.org/game.zip", "tv", "ntsc", "filter", "nearest", "joystick", "on",
                        "type", "cart", "cmd", "SYS 40960"));

        assertEquals("NTSC", config.getMachineType());
        assertEquals("nearest", config.getTextureFilter());
        assertTrue(config.isJoystickOnByDefault());
        assertEquals("CART", config.getFileType());
        assertEquals("SYS 40960", config.getAutoRunCommand());
    }

    @Test
    public void numericFilterAndDiskWriteLaunchOptions() {
        AppConfigItem config = resolve(gameListItem("PAL", null), launch("filter", "0.25", "dskwrt", "temp"));

        assertEquals("0.25", config.getTextureFilter());
        assertEquals("temp", config.getDiskWrite());
    }

    @Test
    public void programsOwnConfigurationIsNotChanged() {
        AppConfigItem item = gameListItem("PAL", null);

        AppConfigItem config = resolve(item, launch("tv", "NTSC", "ram", "8K", "type", "DISK"));

        assertNotSame(item, config);
        assertEquals("PAL", item.getMachineType());
        assertEquals("RAM_AUTO", item.getRam());
        assertEquals("PRG", item.getFileType());
        assertEquals("NTSC", config.getMachineType());
        // A later launch without options is back to the program's own settings.
        assertEquals("PAL", resolve(item, LaunchOptions.NONE).getMachineType());
    }

    @Test
    public void copyKeepsNameFileAndData() {
        AppConfigItem item = AppConfigItem.createAdhoc("dropped.prg", FileLocation.LOCAL);
        byte[] data = new byte[] { 1, 2, 3 };
        item.setFileData(data);

        AppConfigItem config = resolve(item, LaunchOptions.NONE);

        assertEquals(AppConfigItem.ADHOC_NAME, config.getName());
        assertEquals("dropped.prg", config.getFilePath());
        assertEquals(FileLocation.LOCAL, config.getFileLocation());
        assertSame(data, config.getFileData());
    }

    // ---- Precedence of the layers ----

    @Test
    public void builtInDefaultWhenNothingElseSaysOtherwise() {
        ResolvedSettings settings = resolver.resolve(AppConfigItem.createAdhoc("game.prg", FileLocation.LOCAL),
                LaunchOptions.NONE, KEY, false);

        assertEquals("PAL", settings.get(SettingsCatalogue.MACHINE_TV));
        assertEquals(SettingLayer.BUILT_IN, settings.getSource(SettingsCatalogue.MACHINE_TV));
        assertEquals("FIT", settings.get(SettingsCatalogue.DISPLAY_SCREEN_SIZE));
    }

    @Test
    public void allProgramsSettingBeatsBuiltInDefault() {
        store.setGlobal(SettingsCatalogue.MACHINE_TV, "NTSC");
        store.setGlobal(SettingsCatalogue.MACHINE_RAM, "RAM_35K");

        ResolvedSettings adhoc = resolver.resolve(AppConfigItem.createAdhoc("game.prg", FileLocation.LOCAL),
                LaunchOptions.NONE, KEY, false);
        ResolvedSettings basic = resolver.resolve(basicItem(), LaunchOptions.NONE, KEY, false);

        assertEquals("NTSC", adhoc.get(SettingsCatalogue.MACHINE_TV));
        assertEquals(SettingLayer.ALL_PROGRAMS, adhoc.getSource(SettingsCatalogue.MACHINE_TV));
        assertEquals("RAM_35K", adhoc.get(SettingsCatalogue.MACHINE_RAM));
        // BASIC's PAL and unexpanded are built-in defaults, so they follow the user too.
        assertEquals("NTSC", basic.get(SettingsCatalogue.MACHINE_TV));
        assertEquals("RAM_35K", basic.get(SettingsCatalogue.MACHINE_RAM));
    }

    @Test
    public void programConfigurationBeatsAllProgramsSetting() {
        store.setGlobal(SettingsCatalogue.MACHINE_TV, "NTSC");
        store.setGlobal(SettingsCatalogue.MACHINE_RAM, "RAM_35K");
        store.setGlobal(SettingsCatalogue.CONTROLS_JOYSTICK, "OFF");
        store.setGlobal(SettingsCatalogue.DISPLAY_PALETTE, "MID");

        AppConfigItem item = gameListItem("PAL", null);
        item.setPalette("normal");
        ResolvedSettings settings = resolver.resolve(item, LaunchOptions.NONE, KEY, false);

        assertEquals("PAL", settings.get(SettingsCatalogue.MACHINE_TV));
        assertEquals(SettingLayer.PROGRAM_CONFIG, settings.getSource(SettingsCatalogue.MACHINE_TV));
        // No "ram" in programs.json means auto detection, which is the program's choice.
        assertEquals("RAM_AUTO", settings.get(SettingsCatalogue.MACHINE_RAM));
        // No "joystick" in programs.json means ON.
        assertEquals("ON", settings.get(SettingsCatalogue.CONTROLS_JOYSTICK));
        assertEquals("ALTERNATING", settings.get(SettingsCatalogue.DISPLAY_PALETTE));
        assertEquals(SettingLayer.PROGRAM_CONFIG, settings.getSource(SettingsCatalogue.DISPLAY_PALETTE));
    }

    @Test
    public void allProgramsSettingAppliesWhereTheProgramIsSilent() {
        store.setGlobal(SettingsCatalogue.DISPLAY_PALETTE, "MID");
        store.setGlobal(SettingsCatalogue.DISPLAY_FILTER, "NEAREST");

        ResolvedSettings settings = resolver.resolve(gameListItem("PAL", null), LaunchOptions.NONE, KEY, false);

        assertEquals("MID", settings.get(SettingsCatalogue.DISPLAY_PALETTE));
        assertEquals("NEAREST", settings.get(SettingsCatalogue.DISPLAY_FILTER));
        assertEquals(SettingLayer.ALL_PROGRAMS, settings.getSource(SettingsCatalogue.DISPLAY_FILTER));
    }

    @Test
    public void thisProgramSettingBeatsProgramConfiguration() {
        store.setProgram(KEY, SettingsCatalogue.MACHINE_TV, "NTSC");
        store.setProgram(KEY, SettingsCatalogue.MACHINE_RAM, "RAM_8K");
        store.setProgram("other-game", SettingsCatalogue.MACHINE_RAM, "RAM_24K");

        ResolvedSettings settings = resolver.resolve(gameListItem("PAL", "RAM_16K"), LaunchOptions.NONE, KEY, false);

        assertEquals("NTSC", settings.get(SettingsCatalogue.MACHINE_TV));
        assertEquals("RAM_8K", settings.get(SettingsCatalogue.MACHINE_RAM));
        assertEquals(SettingLayer.THIS_PROGRAM, settings.getSource(SettingsCatalogue.MACHINE_RAM));
    }

    @Test
    public void launchOptionsBeatEverything() {
        store.setGlobal(SettingsCatalogue.MACHINE_TV, "PAL");
        store.setProgram(KEY, SettingsCatalogue.MACHINE_TV, "PAL");

        ResolvedSettings settings = resolver.resolve(gameListItem("PAL", null), launch("tv", "NTSC"), KEY, false);

        assertEquals("NTSC", settings.get(SettingsCatalogue.MACHINE_TV));
        assertEquals(SettingLayer.LAUNCH_OPTION, settings.getSource(SettingsCatalogue.MACHINE_TV));
    }

    @Test
    public void ignoringSavedSettingsSkipsBothUserLayers() {
        store.setGlobal(SettingsCatalogue.MACHINE_RAM, "RAM_35K");
        store.setGlobal(SettingsCatalogue.DISPLAY_FILTER, "NEAREST");
        store.setProgram(KEY, SettingsCatalogue.MACHINE_TV, "NTSC");

        ResolvedSettings settings = resolver.resolve(AppConfigItem.createAdhoc("game.prg", FileLocation.LOCAL),
                launch("settings", "hidden", "palette", "mid"), KEY, true);

        assertEquals("RAM_AUTO", settings.get(SettingsCatalogue.MACHINE_RAM));
        assertEquals("SOFT", settings.get(SettingsCatalogue.DISPLAY_FILTER));
        assertEquals("PAL", settings.get(SettingsCatalogue.MACHINE_TV));
        // Launch options still apply.
        assertEquals("MID", settings.get(SettingsCatalogue.DISPLAY_PALETTE));
    }

    @Test
    public void softAmountCombinesWithFilterFromAnotherLayer() {
        store.setGlobal(SettingsCatalogue.DISPLAY_SOFT_AMOUNT, "0.7");

        AppConfigItem config = resolve(gameListItem("PAL", null), LaunchOptions.NONE);
        AppConfigItem sharp = resolve(gameListItem("PAL", null), launch("filter", "nearest"));

        assertEquals("0.7", config.getTextureFilter());
        assertEquals("nearest", sharp.getTextureFilter());
    }

    @Test
    public void everyValueSettingIsResolvedAndActionsAreNot() {
        ResolvedSettings settings = resolver.resolve(gameListItem("PAL", null), LaunchOptions.NONE, KEY, false);

        for (SettingDef def : SettingsCatalogue.all()) {
            if (def.hasValue()) {
                assertNotNull(settings.get(def.getId()), def.getId());
                assertEquals(def.normalise(settings.get(def.getId())), settings.get(def.getId()), def.getId());
            } else {
                assertNull(settings.get(def.getId()), def.getId());
            }
        }
    }
}
