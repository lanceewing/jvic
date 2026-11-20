package emu.jvic.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import emu.jvic.config.AppConfigItem;

/**
 * Tests that {@link LaunchOptions} accepts every spelling of every launch option jvic
 * has always accepted (URL parameters, #/ path parts, autorun-config.txt and desktop
 * arguments all end up in the same map), and ignores values it doesn't recognise.
 */
public class LaunchOptionsTest {

    private static LaunchOptions parse(String... nameValuePairs) {
        Map<String, String> args = new HashMap<String, String>();
        for (int i = 0; i < nameValuePairs.length; i += 2) {
            args.put(nameValuePairs[i], nameValuePairs[i + 1]);
        }
        return LaunchOptions.parse(args);
    }

    private static String ram(String value) {
        return parse("ram", value).getSetting(SettingsCatalogue.MACHINE_RAM);
    }

    @Test
    public void ramSpellings() {
        assertEquals("RAM_UNEXPANDED", ram("0K"));
        assertEquals("RAM_UNEXPANDED", ram("UNEXPANDED"));
        assertEquals("RAM_UNEXPANDED", ram("unexp"));
        assertEquals("RAM_3K", ram("3K"));
        assertEquals("RAM_8K", ram("8k"));
        assertEquals("RAM_16K", ram("16K"));
        assertEquals("RAM_24K", ram("24K"));
        assertEquals("RAM_32K", ram("32K"));
        assertEquals("RAM_35K", ram("35K"));
        assertNull(ram("64K"));
        assertNull(ram("RAM_16K"));
    }

    @Test
    public void tvSpellings() {
        assertEquals("PAL", parse("tv", "pal").getSetting(SettingsCatalogue.MACHINE_TV));
        assertEquals("NTSC", parse("tv", "NTSC").getSetting(SettingsCatalogue.MACHINE_TV));
        assertEquals("VIC44", parse("tv", "vic44").getSetting(SettingsCatalogue.MACHINE_TV));
        assertEquals("VIC44K", parse("tv", "VIC44K").getSetting(SettingsCatalogue.MACHINE_TV));
        assertNull(parse("tv", "SECAM").getSetting(SettingsCatalogue.MACHINE_TV));
    }

    @Test
    public void diskWriteSpellingsIncludingTheShortName() {
        assertEquals("OFF", parse("diskwrite", "off").getSetting(SettingsCatalogue.MEDIA_DISK_WRITE));
        assertEquals("TEMP", parse("diskwrite", "TEMP").getSetting(SettingsCatalogue.MEDIA_DISK_WRITE));
        assertEquals("PERSIST", parse("dskwrt", "persist").getSetting(SettingsCatalogue.MEDIA_DISK_WRITE));
        assertEquals("OFF", parse("diskwrite", "off", "dskwrt", "persist")
                .getSetting(SettingsCatalogue.MEDIA_DISK_WRITE));
        assertNull(parse("diskwrite", "always").getSetting(SettingsCatalogue.MEDIA_DISK_WRITE));
    }

    @Test
    public void filterNamesAndNumbers() {
        LaunchOptions nearest = parse("filter", "nearest");
        assertEquals("NEAREST", nearest.getSetting(SettingsCatalogue.DISPLAY_FILTER));
        assertNull(nearest.getSetting(SettingsCatalogue.DISPLAY_SOFT_AMOUNT));

        assertEquals("LINEAR", parse("filter", "LINEAR").getSetting(SettingsCatalogue.DISPLAY_FILTER));

        LaunchOptions soft = parse("filter", "0.35");
        assertEquals("SOFT", soft.getSetting(SettingsCatalogue.DISPLAY_FILTER));
        assertEquals("0.35", soft.getSetting(SettingsCatalogue.DISPLAY_SOFT_AMOUNT));

        assertNull(parse("filter", "1.5").getSetting(SettingsCatalogue.DISPLAY_FILTER));
        assertNull(parse("filter", "blurry").getSetting(SettingsCatalogue.DISPLAY_FILTER));
    }

    @Test
    public void paletteMidOrAnythingElseIsAlternating() {
        assertEquals("MID", parse("palette", "mid").getSetting(SettingsCatalogue.DISPLAY_PALETTE));
        assertEquals("ALTERNATING", parse("palette", "normal").getSetting(SettingsCatalogue.DISPLAY_PALETTE));
    }

    @Test
    public void joystickOffOrAnythingElseIsOn() {
        assertEquals("OFF", parse("joystick", "off").getSetting(SettingsCatalogue.CONTROLS_JOYSTICK));
        assertEquals("ON", parse("joystick", "on").getSetting(SettingsCatalogue.CONTROLS_JOYSTICK));
        assertEquals("ON", parse("joystick", "yes").getSetting(SettingsCatalogue.CONTROLS_JOYSTICK));
    }

    @Test
    public void settingsHidden() {
        assertTrue(parse("settings", "hidden").isSettingsHidden());
        assertTrue(parse("settings", "HIDDEN").isSettingsHidden());
        assertFalse(parse("settings", "shown").isSettingsHidden());
        assertFalse(parse("tv", "pal").isSettingsHidden());
    }

    @Test
    public void programFieldsArePassedThrough() {
        LaunchOptions launch = parse("type", "disk", "entry", "GAME", "addr", "4609", "cmd", "SYS 4609");
        AppConfigItem program = new AppConfigItem();

        launch.applyProgramFields(program);

        assertEquals("DISK", program.getFileType());
        assertEquals("GAME", program.getEntryName());
        assertEquals("4609", program.getLoadAddress());
        assertEquals("SYS 4609", program.getAutoRunCommand());
    }

    @Test
    public void unrecognisedTypeLeavesTheProgramsTypeAlone() {
        AppConfigItem program = new AppConfigItem();
        program.setFileType("PRG");

        parse("type", "floppy").applyProgramFields(program);

        assertEquals("PRG", program.getFileType());
    }

    @Test
    public void noArgumentsMeansNoOptions() {
        assertNull(LaunchOptions.parse(null).getSetting(SettingsCatalogue.MACHINE_TV));
        assertFalse(LaunchOptions.parse(new HashMap<String, String>()).isSettingsHidden());
    }
}
