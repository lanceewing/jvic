package emu.jvic.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import emu.jvic.config.AppConfigItem;
import emu.jvic.config.AppConfigItem.FileLocation;
import emu.jvic.memory.RamType;
import emu.jvic.MachineType;

/**
 * Checks the setting definitions are consistent, that their values match the enums
 * the machine uses, and that program keys are worked out as quick saves always did.
 */
public class SettingsCatalogueTest {

    @Test
    public void idsAreUniqueAndDefaultsAreValid() {
        Set<String> ids = new HashSet<String>();
        for (SettingDef def : SettingsCatalogue.all()) {
            assertTrue(ids.add(def.getId()), "duplicate id " + def.getId());
            assertTrue(def.getId().indexOf('.') > 0, def.getId());
            assertNotNull(def.getLabel());
            if (def.hasValue()) {
                assertEquals(def.getDefaultValue(), def.normalise(def.getDefaultValue()), def.getId());
            } else {
                assertNull(def.getDefaultValue(), def.getId());
            }
        }
    }

    @Test
    public void ramAndTvChoicesAreMachineEnumNames() {
        for (SettingDef.Choice choice : SettingsCatalogue.get(SettingsCatalogue.MACHINE_RAM).getChoices()) {
            assertNotNull(RamType.valueOf(choice.getValue()));
        }
        for (SettingDef.Choice choice : SettingsCatalogue.get(SettingsCatalogue.MACHINE_TV).getChoices()) {
            assertNotNull(MachineType.valueOf(choice.getValue()));
        }
    }

    @Test
    public void vic44MachinesAreAdvancedChoices() {
        for (SettingDef.Choice choice : SettingsCatalogue.get(SettingsCatalogue.MACHINE_TV).getChoices()) {
            assertEquals(choice.getValue().startsWith("VIC44"), choice.isAdvanced(), choice.getValue());
        }
    }

    @Test
    public void toggleAndSliderNormalisation() {
        SettingDef toggle = SettingsCatalogue.get(SettingsCatalogue.SOUND_ON_START);
        assertEquals("ON", toggle.normalise("true"));
        assertEquals("OFF", toggle.normalise("0"));
        assertNull(toggle.normalise("maybe"));

        SettingDef slider = SettingsCatalogue.get(SettingsCatalogue.DISPLAY_SOFT_AMOUNT);
        assertEquals("0.0", slider.normalise("0"));
        assertEquals("1.0", slider.normalise(" 1 "));
        assertNull(slider.normalise("-0.1"));
    }

    @Test
    public void inCategoryKeepsDisplayOrder() {
        assertEquals(SettingsCatalogue.MACHINE_TV,
                SettingsCatalogue.inCategory(SettingCategory.MACHINE).get(0).getId());
        assertTrue(SettingsCatalogue.inCategory(SettingCategory.SOUND).size() >= 1);
    }

    @Test
    public void programKindAndKeyName() {
        AppConfigItem game = new AppConfigItem();
        game.setName("Pitfall");
        game.setFileType("CART");
        AppConfigItem basic = new AppConfigItem();
        basic.setName(AppConfigItem.BASIC_NAME);
        basic.setFileType("");
        AppConfigItem adhoc = AppConfigItem.createAdhoc("C:\\vic\\games\\Gorf.zip", FileLocation.ABSOLUTE);

        assertEquals(ProgramKind.GAME_LIST, ProgramKind.of(game));
        assertEquals(ProgramKind.BASIC, ProgramKind.of(basic));
        assertEquals(ProgramKind.ADHOC, ProgramKind.of(adhoc));

        assertEquals("Pitfall", ProgramIdentity.keyName(game));
        assertEquals("adhoc Gorf.zip", ProgramIdentity.keyName(adhoc));
        assertEquals("adhoc game.prg",
                ProgramIdentity.keyName(AppConfigItem.createAdhoc("https://x.org/a/game.prg", FileLocation.EXTERNAL)));
        assertNull(ProgramIdentity.keyName(null));
        assertEquals("default", ProgramIdentity.key(""));
        assertEquals("pitfall", ProgramIdentity.key("pitfall"));
        assertFalse(ProgramIdentity.key(null).isEmpty());
    }
}
