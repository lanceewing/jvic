package emu.jvic.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.badlogic.gdx.Preferences;

/**
 * Saves the user's settings in libGDX Preferences (a file on desktop and Android,
 * localStorage on the web).
 *
 * <p>Keys:
 * <ul>
 * <li>{@code g.<id>}: the setting for all programs.</li>
 * <li>{@code p.<programKey>.<id>}: the setting for one program. The program key is the
 * same one quick saves use (see {@link ProgramIdentity}).</li>
 * <li>{@code settings.version}: the storage format, so keys can be migrated later.</li>
 * </ul>
 *
 * <p>Values are checked against the setting's definition when read, so a value that is
 * no longer valid (or a hand-edited one) is treated as not set.
 *
 * <p>A cleared setting is stored as an empty value rather than removed. On the TeaVM
 * web build, gdx-teavm's WebPreferences.flush() (1.5.5) removes localStorage items
 * while stepping through them by index, which skips the item after each one removed,
 * so a removed setting could survive and come back on the next page load. Writing an
 * empty value always reaches storage, and an empty value reads as not set.
 */
public class SettingsStore {

    /** The name of the Preferences that hold the settings. */
    public static final String PREFERENCES_NAME = "jvic.settings";

    /** The current storage format. */
    public static final int VERSION = 1;

    static final String VERSION_KEY = "settings.version";
    private static final String GLOBAL_PREFIX = "g.";
    private static final String PROGRAM_PREFIX = "p.";

    private final Preferences preferences;

    /**
     * Constructor for SettingsStore.
     *
     * @param preferences The Preferences to keep the settings in.
     */
    public SettingsStore(Preferences preferences) {
        this.preferences = preferences;
    }

    /**
     * @param id A setting id.
     *
     * @return The user's value for all programs, or null if not set.
     */
    public String getGlobal(String id) {
        return read(GLOBAL_PREFIX + id, id);
    }

    /**
     * Saves the user's value for all programs.
     *
     * @param id    A setting id.
     * @param value The value. Null or an invalid value clears the setting.
     */
    public void setGlobal(String id, String value) {
        write(GLOBAL_PREFIX + id, id, value);
    }

    /**
     * Clears the user's value for all programs.
     */
    public void clearGlobal(String id) {
        remove(GLOBAL_PREFIX + id);
    }

    /**
     * @param programKey The program's key.
     * @param id         A setting id.
     *
     * @return The user's value for that program, or null if not set (or if the setting
     *         can't be set per program).
     */
    public String getProgram(String programKey, String id) {
        SettingDef def = SettingsCatalogue.get(id);
        if ((def == null) || !def.isPerProgram()) {
            return null;
        }
        return read(programPrefix(programKey) + id, id);
    }

    /**
     * Saves the user's value for one program. Ignored for settings that can't be set
     * per program.
     *
     * @param programKey The program's key.
     * @param id         A setting id.
     * @param value      The value. Null or an invalid value clears the setting.
     */
    public void setProgram(String programKey, String id, String value) {
        SettingDef def = SettingsCatalogue.get(id);
        if ((def != null) && def.isPerProgram()) {
            write(programPrefix(programKey) + id, id, value);
        }
    }

    /**
     * Clears the user's value for one program.
     */
    public void clearProgram(String programKey, String id) {
        remove(programPrefix(programKey) + id);
    }

    /**
     * Clears every setting saved for one program.
     *
     * @param programKey The program's key.
     */
    public void clearAllForProgram(String programKey) {
        clearKeysStartingWith(programPrefix(programKey));
    }

    /**
     * Clears every saved setting, for all programs and for each program.
     */
    public void resetAll() {
        clearKeysStartingWith("");
    }

    private String programPrefix(String programKey) {
        return PROGRAM_PREFIX + programKey + ".";
    }

    private String read(String key, String id) {
        SettingDef def = SettingsCatalogue.get(id);
        if ((def == null) || !def.hasValue() || !preferences.contains(key)) {
            return null;
        }
        return def.normalise(preferences.getString(key, null));
    }

    private void write(String key, String id, String value) {
        SettingDef def = SettingsCatalogue.get(id);
        String normalised = (def != null) ? def.normalise(value) : null;
        if (normalised == null) {
            remove(key);
            return;
        }
        preferences.putString(key, normalised);
        preferences.putInteger(VERSION_KEY, VERSION);
        preferences.flush();
    }

    private void remove(String key) {
        if (preferences.contains(key) && !preferences.getString(key, "").isEmpty()) {
            preferences.putString(key, "");
            preferences.flush();
        }
    }

    private void clearKeysStartingWith(String prefix) {
        Map<String, ?> all = preferences.get();
        List<String> toClear = new ArrayList<String>();
        for (String key : all.keySet()) {
            if (key.startsWith(prefix) && (key.startsWith(GLOBAL_PREFIX) || key.startsWith(PROGRAM_PREFIX))) {
                toClear.add(key);
            }
        }
        for (String key : toClear) {
            preferences.putString(key, "");
        }
        if (!toClear.isEmpty()) {
            preferences.flush();
        }
    }
}
