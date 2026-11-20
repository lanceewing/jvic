package emu.jvic.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The definition of one setting: its id, how it is shown, its allowed values and
 * default, which scopes it can be set in, and when a change takes effect.
 *
 * <p>Values are always held as strings, so that they can be stored in libGDX
 * Preferences and passed through to {@code AppConfigItem} unchanged. Definitions are
 * immutable and all live in {@link SettingsCatalogue}.
 */
public final class SettingDef {

    /** The stored value of a TOGGLE setting that is on. */
    public static final String ON = "ON";

    /** The stored value of a TOGGLE setting that is off. */
    public static final String OFF = "OFF";

    /**
     * One allowed value of a CHOICE setting.
     */
    public static final class Choice {

        private final String value;
        private final String label;
        private final boolean advanced;

        private Choice(String value, String label, boolean advanced) {
            this.value = value;
            this.label = label;
            this.advanced = advanced;
        }

        /**
         * @return The stored value, e.g. "RAM_16K".
         */
        public String getValue() {
            return value;
        }

        /**
         * @return The text shown to the user, e.g. "16K".
         */
        public String getLabel() {
            return label;
        }

        /**
         * @return true if this choice is only offered when advanced options are shown.
         */
        public boolean isAdvanced() {
            return advanced;
        }
    }

    /**
     * Creates a normal choice.
     */
    public static Choice choice(String value, String label) {
        return new Choice(value, label, false);
    }

    /**
     * Creates a choice that is only offered when advanced options are shown.
     */
    public static Choice advancedChoice(String value, String label) {
        return new Choice(value, label, true);
    }

    private final String id;
    private final SettingCategory category;
    private final String label;
    private final String help;
    private final SettingType type;
    private final List<Choice> choices;
    private final String defaultValue;
    private final float min;
    private final float max;
    private final float step;
    private final boolean perProgram;
    private final ApplyMode applyMode;

    private SettingDef(String id, SettingCategory category, String label, String help,
            SettingType type, List<Choice> choices, String defaultValue, float min, float max,
            float step, boolean perProgram, ApplyMode applyMode) {
        this.id = id;
        this.category = category;
        this.label = label;
        this.help = help;
        this.type = type;
        this.choices = Collections.unmodifiableList(choices);
        this.defaultValue = defaultValue;
        this.min = min;
        this.max = max;
        this.step = step;
        this.perProgram = perProgram;
        this.applyMode = applyMode;
    }

    /**
     * Defines a setting whose value is one of a fixed list of choices.
     */
    public static SettingDef choices(String id, SettingCategory category, String label,
            String help, String defaultValue, boolean perProgram, ApplyMode applyMode,
            Choice... choices) {
        List<Choice> list = new ArrayList<Choice>();
        for (Choice choice : choices) {
            list.add(choice);
        }
        return new SettingDef(id, category, label, help, SettingType.CHOICE, list,
                defaultValue, 0, 0, 0, perProgram, applyMode);
    }

    /**
     * Defines an on/off setting.
     */
    public static SettingDef toggle(String id, SettingCategory category, String label,
            String help, boolean defaultOn, boolean perProgram, ApplyMode applyMode) {
        return new SettingDef(id, category, label, help, SettingType.TOGGLE,
                new ArrayList<Choice>(), defaultOn ? ON : OFF, 0, 0, 0, perProgram, applyMode);
    }

    /**
     * Defines a setting whose value is a number from min to max.
     */
    public static SettingDef slider(String id, SettingCategory category, String label,
            String help, float defaultValue, float min, float max, float step,
            boolean perProgram, ApplyMode applyMode) {
        return new SettingDef(id, category, label, help, SettingType.SLIDER,
                new ArrayList<Choice>(), String.valueOf(defaultValue), min, max, step,
                perProgram, applyMode);
    }

    /**
     * Defines a button. Actions hold no value and are never stored.
     */
    public static SettingDef action(String id, SettingCategory category, String label,
            String help, boolean perProgram) {
        return new SettingDef(id, category, label, help, SettingType.ACTION,
                new ArrayList<Choice>(), null, 0, 0, 0, perProgram, ApplyMode.LIVE);
    }

    /**
     * Checks a value and puts it in its stored form. Choice values are matched
     * ignoring case; toggle values accept ON/OFF, TRUE/FALSE, YES/NO and 1/0; slider
     * values must be numbers within range.
     *
     * @param raw The value to check. May be null.
     *
     * @return The stored form of the value, or null if it isn't valid for this setting.
     */
    public String normalise(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        switch (type) {
            case CHOICE:
                for (Choice choice : choices) {
                    if (choice.getValue().equalsIgnoreCase(value)) {
                        return choice.getValue();
                    }
                }
                return null;
            case TOGGLE:
                String upper = value.toUpperCase();
                if (upper.equals(ON) || upper.equals("TRUE") || upper.equals("YES") || upper.equals("1")) {
                    return ON;
                }
                if (upper.equals(OFF) || upper.equals("FALSE") || upper.equals("NO") || upper.equals("0")) {
                    return OFF;
                }
                return null;
            case SLIDER:
                try {
                    float number = Float.parseFloat(value);
                    if ((number >= min) && (number <= max)) {
                        return String.valueOf(number);
                    }
                } catch (NumberFormatException e) {
                    // Not a number.
                }
                return null;
            case ACTION:
            default:
                return null;
        }
    }

    /**
     * @param value A stored value.
     *
     * @return The label for the value, e.g. "16K" for "RAM_16K", or the value itself if
     *         this setting has no label for it.
     */
    public String getValueLabel(String value) {
        for (Choice choice : choices) {
            if (choice.getValue().equals(value)) {
                return choice.getLabel();
            }
        }
        return value;
    }

    public String getId() {
        return id;
    }

    public SettingCategory getCategory() {
        return category;
    }

    public String getLabel() {
        return label;
    }

    public String getHelp() {
        return help;
    }

    public SettingType getType() {
        return type;
    }

    public List<Choice> getChoices() {
        return choices;
    }

    /**
     * @return The built-in default value, or null for an ACTION.
     */
    public String getDefaultValue() {
        return defaultValue;
    }

    public float getMin() {
        return min;
    }

    public float getMax() {
        return max;
    }

    public float getStep() {
        return step;
    }

    /**
     * @return true if this setting can be set for one program as well as for all
     *         programs.
     */
    public boolean isPerProgram() {
        return perProgram;
    }

    public ApplyMode getApplyMode() {
        return applyMode;
    }

    /**
     * @return true if this setting holds a value that can be stored.
     */
    public boolean hasValue() {
        return type != SettingType.ACTION;
    }
}
