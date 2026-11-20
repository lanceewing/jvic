package emu.jvic.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import emu.jvic.testutil.InMemoryPreferences;

/**
 * Tests {@link SettingsStore}: values for all programs and for one program are kept
 * apart, values are checked against their definitions, and changes are flushed.
 */
public class SettingsStoreTest {

    private InMemoryPreferences preferences;
    private SettingsStore store;

    @BeforeEach
    public void setUp() {
        preferences = new InMemoryPreferences();
        store = new SettingsStore(preferences);
    }

    @Test
    public void globalValueRoundTripsInItsStoredForm() {
        store.setGlobal(SettingsCatalogue.MACHINE_TV, "ntsc");

        assertEquals("NTSC", store.getGlobal(SettingsCatalogue.MACHINE_TV));
        assertEquals("NTSC", preferences.getString("g.machine.tv", null));
        assertEquals(SettingsStore.VERSION, preferences.getInteger(SettingsStore.VERSION_KEY, 0));
        assertTrue(preferences.flushCount > 0);
    }

    @Test
    public void programValueIsSeparateFromGlobalValueAndOtherPrograms() {
        store.setGlobal(SettingsCatalogue.MACHINE_RAM, "RAM_8K");
        store.setProgram("pitfall", SettingsCatalogue.MACHINE_RAM, "RAM_16K");

        assertEquals("RAM_8K", store.getGlobal(SettingsCatalogue.MACHINE_RAM));
        assertEquals("RAM_16K", store.getProgram("pitfall", SettingsCatalogue.MACHINE_RAM));
        assertNull(store.getProgram("pitfall-2", SettingsCatalogue.MACHINE_RAM));
    }

    @Test
    public void invalidValuesAreNotStored() {
        store.setGlobal(SettingsCatalogue.MACHINE_RAM, "RAM_1000K");
        store.setGlobal(SettingsCatalogue.DISPLAY_SOFT_AMOUNT, "1.5");
        store.setGlobal("no.such.setting", "x");

        assertTrue(preferences.get().isEmpty());
    }

    @Test
    public void settingANullValueClearsIt() {
        store.setGlobal(SettingsCatalogue.MACHINE_TV, "NTSC");
        store.setGlobal(SettingsCatalogue.MACHINE_TV, null);

        assertNull(store.getGlobal(SettingsCatalogue.MACHINE_TV));
        // Cleared by writing an empty value, not by removing the key (see SettingsStore).
        assertEquals("", preferences.getString("g.machine.tv", null));
    }

    @Test
    public void storedValueThatIsNoLongerValidReadsAsNotSet() {
        preferences.putString("g.machine.tv", "SECAM");
        preferences.putString("g.display.softAmount", "abc");

        assertNull(store.getGlobal(SettingsCatalogue.MACHINE_TV));
        assertNull(store.getGlobal(SettingsCatalogue.DISPLAY_SOFT_AMOUNT));
    }

    @Test
    public void globalOnlySettingsCannotBeSetPerProgram() {
        store.setProgram("pitfall", SettingsCatalogue.DISPLAY_SCREEN_SIZE, "X2");
        preferences.putString("p.pitfall.display.fps", "ON");

        assertFalse(preferences.contains("p.pitfall.display.screenSize"));
        assertNull(store.getProgram("pitfall", SettingsCatalogue.DISPLAY_FPS));
    }

    @Test
    public void actionsHoldNoValue() {
        store.setGlobal(SettingsCatalogue.GENERAL_RESET_ALL, "ON");

        assertNull(store.getGlobal(SettingsCatalogue.GENERAL_RESET_ALL));
        assertTrue(preferences.get().isEmpty());
    }

    @Test
    public void clearAllForProgramLeavesOtherProgramsAndGlobalValues() {
        store.setGlobal(SettingsCatalogue.MACHINE_TV, "NTSC");
        store.setProgram("pitfall", SettingsCatalogue.MACHINE_TV, "PAL");
        store.setProgram("pitfall", SettingsCatalogue.MACHINE_RAM, "RAM_8K");
        store.setProgram("pitfall-2", SettingsCatalogue.MACHINE_TV, "PAL");

        store.clearAllForProgram("pitfall");

        assertNull(store.getProgram("pitfall", SettingsCatalogue.MACHINE_TV));
        assertNull(store.getProgram("pitfall", SettingsCatalogue.MACHINE_RAM));
        assertEquals("PAL", store.getProgram("pitfall-2", SettingsCatalogue.MACHINE_TV));
        assertEquals("NTSC", store.getGlobal(SettingsCatalogue.MACHINE_TV));
    }

    @Test
    public void resetAllForgetsEverything() {
        store.setGlobal(SettingsCatalogue.MACHINE_TV, "NTSC");
        store.setProgram("pitfall", SettingsCatalogue.MACHINE_RAM, "RAM_8K");

        store.resetAll();

        assertNull(store.getGlobal(SettingsCatalogue.MACHINE_TV));
        assertNull(store.getProgram("pitfall", SettingsCatalogue.MACHINE_RAM));
        for (Object value : preferences.get().values()) {
            if (value instanceof String) {
                assertEquals("", value);
            }
        }
    }

    @Test
    public void clearingNeverRemovesKeys() {
        // gdx-teavm's WebPreferences can leave removed keys in localStorage, so the store
        // must clear settings by writing empty values instead.
        InMemoryPreferences noRemove = new InMemoryPreferences() {
            @Override
            public void remove(String key) {
                throw new AssertionError("remove(" + key + ")");
            }

            @Override
            public void clear() {
                throw new AssertionError("clear()");
            }
        };
        SettingsStore noRemoveStore = new SettingsStore(noRemove);
        noRemoveStore.setGlobal(SettingsCatalogue.MACHINE_TV, "NTSC");
        noRemoveStore.setProgram("pitfall", SettingsCatalogue.MACHINE_RAM, "RAM_8K");

        noRemoveStore.clearGlobal(SettingsCatalogue.MACHINE_TV);
        noRemoveStore.setGlobal(SettingsCatalogue.MACHINE_RAM, null);
        noRemoveStore.clearAllForProgram("pitfall");
        noRemoveStore.setGlobal(SettingsCatalogue.DISPLAY_FILTER, "NEAREST");
        noRemoveStore.resetAll();

        assertNull(noRemoveStore.getGlobal(SettingsCatalogue.MACHINE_TV));
        assertNull(noRemoveStore.getProgram("pitfall", SettingsCatalogue.MACHINE_RAM));
        assertNull(noRemoveStore.getGlobal(SettingsCatalogue.DISPLAY_FILTER));
    }

    @Test
    public void clearGlobalAndClearProgramRemoveOneValue() {
        store.setGlobal(SettingsCatalogue.MACHINE_TV, "NTSC");
        store.setGlobal(SettingsCatalogue.MACHINE_RAM, "RAM_8K");
        store.setProgram("pitfall", SettingsCatalogue.MACHINE_TV, "PAL");
        store.setProgram("pitfall", SettingsCatalogue.MACHINE_RAM, "RAM_16K");

        store.clearGlobal(SettingsCatalogue.MACHINE_TV);
        store.clearProgram("pitfall", SettingsCatalogue.MACHINE_RAM);

        assertNull(store.getGlobal(SettingsCatalogue.MACHINE_TV));
        assertEquals("RAM_8K", store.getGlobal(SettingsCatalogue.MACHINE_RAM));
        assertEquals("PAL", store.getProgram("pitfall", SettingsCatalogue.MACHINE_TV));
        assertNull(store.getProgram("pitfall", SettingsCatalogue.MACHINE_RAM));
    }
}
