package emu.jvic.testutil;

import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.Preferences;

/**
 * A libGDX Preferences kept in a map, so settings code can be tested without a libGDX
 * backend. Counts flushes so tests can check that changes are saved.
 */
public class InMemoryPreferences implements Preferences {

    private final Map<String, Object> values = new HashMap<String, Object>();

    public int flushCount;

    @Override
    public Preferences putBoolean(String key, boolean val) {
        values.put(key, val);
        return this;
    }

    @Override
    public Preferences putInteger(String key, int val) {
        values.put(key, val);
        return this;
    }

    @Override
    public Preferences putLong(String key, long val) {
        values.put(key, val);
        return this;
    }

    @Override
    public Preferences putFloat(String key, float val) {
        values.put(key, val);
        return this;
    }

    @Override
    public Preferences putString(String key, String val) {
        values.put(key, val);
        return this;
    }

    @Override
    public Preferences put(Map<String, ?> vals) {
        values.putAll(vals);
        return this;
    }

    @Override
    public boolean getBoolean(String key) {
        return getBoolean(key, false);
    }

    @Override
    public int getInteger(String key) {
        return getInteger(key, 0);
    }

    @Override
    public long getLong(String key) {
        return getLong(key, 0);
    }

    @Override
    public float getFloat(String key) {
        return getFloat(key, 0);
    }

    @Override
    public String getString(String key) {
        return getString(key, "");
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        Object value = values.get(key);
        return (value instanceof Boolean) ? (Boolean) value : defValue;
    }

    @Override
    public int getInteger(String key, int defValue) {
        Object value = values.get(key);
        return (value instanceof Integer) ? (Integer) value : defValue;
    }

    @Override
    public long getLong(String key, long defValue) {
        Object value = values.get(key);
        return (value instanceof Long) ? (Long) value : defValue;
    }

    @Override
    public float getFloat(String key, float defValue) {
        Object value = values.get(key);
        return (value instanceof Float) ? (Float) value : defValue;
    }

    @Override
    public String getString(String key, String defValue) {
        Object value = values.get(key);
        return (value != null) ? String.valueOf(value) : defValue;
    }

    @Override
    public Map<String, ?> get() {
        return new HashMap<String, Object>(values);
    }

    @Override
    public boolean contains(String key) {
        return values.containsKey(key);
    }

    @Override
    public void clear() {
        values.clear();
    }

    @Override
    public void remove(String key) {
        values.remove(key);
    }

    @Override
    public void flush() {
        flushCount++;
    }
}
