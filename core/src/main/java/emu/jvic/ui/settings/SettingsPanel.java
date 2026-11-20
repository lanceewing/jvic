package emu.jvic.ui.settings;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;

import emu.jvic.settings.ApplyMode;
import emu.jvic.settings.ResolvedSettings;
import emu.jvic.settings.SettingCategory;
import emu.jvic.settings.SettingDef;
import emu.jvic.settings.SettingLayer;
import emu.jvic.settings.SettingType;
import emu.jvic.settings.SettingsCatalogue;
import emu.jvic.settings.SettingsStore;
import emu.jvic.ui.ConfirmResponseHandler;
import emu.jvic.ui.DialogHandler;
import emu.jvic.ui.Overlay;
import emu.jvic.ui.OverlayStyles;

/**
 * The settings screen: a panel drawn over the home screen (filling it) or over the
 * running machine (beside the VIC screen in landscape, below it in portrait, so the
 * effect of display changes can be seen). It has two levels: the list of categories,
 * and a page of settings for one category.
 *
 * <p>Over a running program it has two scopes: "This program" (the default), which
 * shows what the program uses and saves changes for that program only, and "All
 * programs". From the home screen it edits the settings for all programs. Each row
 * says where its value comes from, with a Reset to drop a saved value.
 *
 * <p>Changes that need a restart (TV standard, RAM) are collected in a banner with
 * "Restart now". Other settings that can't be applied to a running program say they
 * are used the next time a program starts.
 *
 * <p>Keys: Up/Down move between rows, Left/Right change the value, Enter or Space
 * changes a toggle, opens a category or presses a button, Tab switches the scope,
 * Esc/Backspace/BACK go back a level or close, and Alt+O closes.
 */
public class SettingsPanel extends Overlay {

    /**
     * What the panel needs from the screen it is shown on.
     */
    public interface Host {

        /**
         * @return true if a program is running under the panel (the machine screen),
         *         false on the home screen.
         */
        boolean isMachineRunning();

        /**
         * @return The settings resolved for the running program, launch options
         *         included, or null if none.
         */
        ResolvedSettings resolveRunningProgram();

        /**
         * @return The settings resolved for the running program without its launch
         *         options, or null if none.
         */
        ResolvedSettings resolveRunningProgramWithoutLaunchOptions();

        /**
         * @return The running program's key for per-program settings, or null if none.
         */
        String getProgramKey();

        /**
         * @return The running program's name, to show with "This program".
         */
        String getProgramName();

        /**
         * @return true if a change to the setting is applied to the running program.
         */
        boolean isAppliedLive(String id);

        /**
         * Applies a changed setting to the running program.
         */
        void applyLive(String id, ResolvedSettings settings);

        /**
         * @return The ids of the changed settings that need a restart; empty if none.
         */
        List<String> getRestartNeeded();

        /**
         * @return true if the program can be restarted with new settings.
         */
        boolean isRestartSupported();

        /**
         * Restarts the running program with its settings as they are now.
         */
        void restart();

        /**
         * @return The world y of the bottom of the VIC screen in portrait, so the panel
         *         can sit below it.
         */
        float getVicScreenBase();

        /**
         * Pauses or resumes the machine for the "pause while settings are open" setting.
         */
        void setPausedForSettings(boolean paused);
    }

    /** Which settings the panel edits. */
    private enum Scope {
        /** The running program's own settings. */
        PROGRAM,
        /** The settings for all programs. */
        GLOBAL
    }

    private static final float PAD = 28;
    private static final float SMALL = 0.62f;
    private static final float ROW_LABEL = 0.82f;
    private static final float BUTTON_HEIGHT = 92;
    private static final int CHOICES_PER_LINE = 4;

    private final SettingsStore store;
    private final DialogHandler dialogHandler;
    private Host host;
    private Scope scope = Scope.GLOBAL;

    /** For the running program: without and with its launch options. */
    private ResolvedSettings programSettings;
    private ResolvedSettings runningSettings;

    /** The category shown, or null for the list of categories. */
    private SettingCategory page;
    private final List<Row> rows = new ArrayList<Row>();
    private int focus;
    private ScrollPane scrollPane;
    private String note;
    private boolean pausedByPanel;
    private float pendingScrollY = -1;

    /** The width a row's content has, worked out when the panel is built. */
    private float contentWidth;

    private final GlyphLayout glyphLayout = new GlyphLayout();

    /**
     * One focusable row: a category in the list, or a setting on a page.
     */
    private abstract class Row {
        Table actor;

        void left() {
        }

        void right() {
        }

        void activate() {
            right();
        }
    }

    public SettingsPanel(Batch batch, SettingsStore store, DialogHandler dialogHandler) {
        super(batch);
        this.store = store;
        this.dialogHandler = dialogHandler;
    }

    /**
     * Opens the panel on the list of categories: for the running program if there is
     * one, otherwise for all programs.
     *
     * @param host The screen the panel is shown on.
     */
    public void open(Host host) {
        this.host = host;
        this.page = null;
        this.focus = 0;
        this.note = null;
        this.scope = (host.isMachineRunning() && (host.getProgramKey() != null)) ? Scope.PROGRAM : Scope.GLOBAL;
        refreshResolved();
        open();
    }

    @Override
    protected void onOpen() {
        pausedByPanel = false;
        if (host.isMachineRunning() && isOn(SettingsCatalogue.GENERAL_PAUSE_IN_SETTINGS)) {
            host.setPausedForSettings(true);
            pausedByPanel = true;
        }
    }

    @Override
    protected void onClose() {
        if (pausedByPanel) {
            pausedByPanel = false;
            host.setPausedForSettings(false);
        }
    }

    // ---------------------------------------------------------------------------------
    // Values.
    // ---------------------------------------------------------------------------------

    private void refreshResolved() {
        if (host.isMachineRunning()) {
            programSettings = host.resolveRunningProgramWithoutLaunchOptions();
            runningSettings = host.resolveRunningProgram();
        } else {
            programSettings = null;
            runningSettings = null;
        }
    }

    private boolean isProgramScope() {
        return (scope == Scope.PROGRAM) && (programSettings != null);
    }

    /** The value for all programs: saved, or the built-in default. */
    private String getGlobalValue(SettingDef def) {
        String value = store.getGlobal(def.getId());
        return (value != null) ? value : def.getDefaultValue();
    }

    /** The value shown: in "This program", what the program uses (launch options aside). */
    private String getValue(SettingDef def) {
        if (isProgramScope() && def.isPerProgram()) {
            return programSettings.get(def.getId());
        }
        return getGlobalValue(def);
    }

    private boolean isOn(String id) {
        return SettingDef.ON.equals(getGlobalValue(SettingsCatalogue.get(id)));
    }

    private void setValue(SettingDef def, String value) {
        if ((value == null) || value.equals(getValue(def))) {
            return;
        }
        String id = def.getId();
        if (isProgramScope() && def.isPerProgram()) {
            store.setProgram(host.getProgramKey(), id, value);
        } else {
            store.setGlobal(id, value);
        }
        afterChange(def);
    }

    /** Drops the saved value shown in a row, so it inherits again. */
    private void clearValue(SettingDef def) {
        if (isProgramScope() && def.isPerProgram()) {
            store.clearProgram(host.getProgramKey(), def.getId());
        } else {
            store.clearGlobal(def.getId());
        }
        afterChange(def);
    }

    private void afterChange(SettingDef def) {
        String id = def.getId();
        note = null;
        refreshResolved();

        if (host.isMachineRunning()) {
            if (host.isAppliedLive(id)) {
                if (runningSettings != null) {
                    host.applyLive(id, runningSettings);
                }
            } else if (def.getApplyMode() != ApplyMode.RESTART) {
                note = def.getLabel() + ": used the next time a program starts.";
            }
        }

        if (SettingsCatalogue.GENERAL_PAUSE_IN_SETTINGS.equals(id) && host.isMachineRunning()) {
            boolean pause = isOn(id);
            if (pause != pausedByPanel) {
                pausedByPanel = pause;
                host.setPausedForSettings(pause);
            }
        }

        // Rebuild the page, since a change can show or hide other rows (the soft amount,
        // the advanced choices). The scroll position and focus are kept.
        rebuildKeepingPlace();
    }

    /** After values have changed by other means, applies every live setting again. */
    private void reapplyLiveSettings() {
        refreshResolved();
        if (host.isMachineRunning() && (runningSettings != null)) {
            for (SettingDef def : SettingsCatalogue.all()) {
                if (def.hasValue() && host.isAppliedLive(def.getId())) {
                    host.applyLive(def.getId(), runningSettings);
                }
            }
        }
    }

    private void rebuildKeepingPlace() {
        pendingScrollY = (scrollPane != null) ? scrollPane.getScrollY() : 0;
        requestRebuild();
    }

    private void setScope(Scope newScope) {
        if (scope != newScope) {
            scope = newScope;
            note = null;
            focus = 0;
            if ((page != null) && visibleSettings(page).isEmpty()) {
                page = null;
            }
            requestRebuild();
        }
    }

    // ---------------------------------------------------------------------------------
    // Layout.
    // ---------------------------------------------------------------------------------

    @Override
    protected void build(float width, float height, boolean portrait) {
        OverlayStyles styles = OverlayStyles.get();
        rows.clear();

        // Where the panel goes.
        float x = 0;
        float y = 0;
        float panelWidth = width;
        float panelHeight = height;
        if (host.isMachineRunning()) {
            if (portrait) {
                panelHeight = Math.max(host.getVicScreenBase(), height * 0.5f);
                panelHeight = Math.min(panelHeight, height);
            } else {
                panelWidth = Math.max(Math.min(width * 0.5f, 1100), Math.min(900, width));
                x = width - panelWidth;
            }
        }

        Table panel = new Table();
        panel.setTouchable(Touchable.enabled);
        panel.setBackground(styles.panel);
        panel.setBounds(x, y, panelWidth, panelHeight);
        panel.top().pad(PAD);
        stage.addActor(panel);

        // Header: back, title, close.
        Table header = new Table();
        if (page != null) {
            TextButton back = new TextButton("<", styles.button);
            back.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float cx, float cy) {
                    goBack();
                }
            });
            header.add(back).size(BUTTON_HEIGHT * 1.2f, BUTTON_HEIGHT).padRight(PAD);
        }
        Label title = new Label((page != null) ? page.getLabel() : "Settings", styles.label);
        header.add(title).left().expandX();
        TextButton closeButton = new TextButton("Close", styles.button);
        closeButton.getLabel().setFontScale(ROW_LABEL);
        closeButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float cx, float cy) {
                close();
            }
        });
        header.add(closeButton).height(BUTTON_HEIGHT).width(220);
        panel.add(header).growX().row();

        contentWidth = panelWidth - (2 * PAD) - 40 - 16;
        panel.add(buildScope()).growX().padTop(10).padBottom(14).row();

        // The rows.
        Table list = new Table();
        list.top();
        if (page == null) {
            buildCategoryList(list);
        } else {
            buildPage(list);
        }
        scrollPane = new ScrollPane(list, styles.scrollPane);
        scrollPane.setFadeScrollBars(false);
        scrollPane.setOverscroll(false, false);
        scrollPane.setScrollingDisabled(true, false);
        panel.add(scrollPane).grow().row();

        Actor footer = buildFooter();
        if (footer != null) {
            panel.add(footer).growX().padTop(12).row();
        }

        if (focus >= rows.size()) {
            focus = rows.size() - 1;
        }
        if (focus < 0) {
            focus = 0;
        }
        panel.validate();
        if (pendingScrollY >= 0) {
            scrollPane.layout();
            scrollPane.setScrollY(pendingScrollY);
            scrollPane.updateVisualScroll();
            pendingScrollY = -1;
            updateFocus(false);
        } else {
            updateFocus(true);
        }
    }

    /** "This program | All programs" over a running program, otherwise a label. */
    private Actor buildScope() {
        OverlayStyles styles = OverlayStyles.get();
        if ((programSettings == null) || (host.getProgramKey() == null)) {
            return smallLabel("For all programs", styles.dimLabel);
        }
        Table table = new Table();
        table.defaults().height(BUTTON_HEIGHT * 0.85f).expandX().fillX().uniformX().pad(0, 0, 0, 8);
        final TextButton thisProgram = new TextButton("This program", styles.choice);
        final TextButton allPrograms = new TextButton("All programs", styles.choice);
        thisProgram.getLabel().setFontScale(0.72f);
        allPrograms.getLabel().setFontScale(0.72f);
        thisProgram.setChecked(scope == Scope.PROGRAM);
        allPrograms.setChecked(scope == Scope.GLOBAL);
        thisProgram.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float cx, float cy) {
                thisProgram.setChecked(true);
                allPrograms.setChecked(false);
                setScope(Scope.PROGRAM);
            }
        });
        allPrograms.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float cx, float cy) {
                thisProgram.setChecked(false);
                allPrograms.setChecked(true);
                setScope(Scope.GLOBAL);
            }
        });
        table.add(thisProgram);
        table.add(allPrograms).row();
        if (scope == Scope.PROGRAM) {
            Label name = smallLabel(host.getProgramName(), styles.dimLabel);
            name.setWrap(false);
            name.setEllipsis(true);
            table.add(name).colspan(2).left().width(contentWidth).padTop(8);
        }
        return table;
    }

    /** The restart banner, or the latest note. */
    private Actor buildFooter() {
        OverlayStyles styles = OverlayStyles.get();
        List<String> restartIds = host.isMachineRunning() ? host.getRestartNeeded() : new ArrayList<String>();
        if (!restartIds.isEmpty()) {
            StringBuilder text = new StringBuilder("Restart needed: ");
            for (int i = 0; i < restartIds.size(); i++) {
                if (i > 0) {
                    text.append(", ");
                }
                text.append(SettingsCatalogue.get(restartIds.get(i)).getLabel());
            }
            text.append('.');
            Table banner = new Table();
            if (host.isRestartSupported()) {
                banner.add(smallLabel(text.toString(), styles.noteLabel)).growX().left();
                TextButton restart = new TextButton("Restart now", styles.choice);
                restart.setChecked(true);
                restart.getLabel().setFontScale(0.72f);
                restart.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float cx, float cy) {
                        host.restart();
                    }
                });
                banner.add(restart).height(BUTTON_HEIGHT * 0.85f).width(340).padLeft(16);
                banner.setTouchable(Touchable.enabled);
                banner.pad(8);
                // The last row for the keyboard: Down from the last setting, then Enter.
                Row row = new Row() {
                    @Override
                    void activate() {
                        host.restart();
                    }

                    @Override
                    void right() {
                    }
                };
                row.actor = banner;
                rows.add(row);
            } else {
                text.append(" Used the next time the program starts.");
                banner.add(smallLabel(text.toString(), styles.noteLabel)).growX().left();
            }
            return banner;
        }
        if (note != null) {
            return smallLabel(note, styles.noteLabel);
        }
        return null;
    }

    private Label smallLabel(String text, Label.LabelStyle style) {
        Label label = new Label(text, style);
        label.setFontScale(SMALL);
        label.setWrap(true);
        return label;
    }

    private void buildCategoryList(Table list) {
        OverlayStyles styles = OverlayStyles.get();
        for (final SettingCategory category : SettingCategory.values()) {
            List<SettingDef> defs = visibleSettings(category);
            if (defs.isEmpty()) {
                continue;
            }
            Row row = new Row() {
                @Override
                void activate() {
                    openPage(category);
                }

                @Override
                void right() {
                    openPage(category);
                }
            };
            Table table = new Table();
            table.setTouchable(Touchable.enabled);
            table.pad(20, 20, 20, 20);
            Table text = new Table();
            text.add(new Label(category.getLabel(), styles.label)).left().row();
            text.add(smallLabel(summary(defs), styles.dimLabel)).left().growX();
            table.add(text).growX().left();
            table.add(new Label(">", styles.dimLabel)).right().padLeft(20);
            table.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float cx, float cy) {
                    openPage(category);
                }
            });
            addRow(list, row, table);
        }
    }

    private String summary(List<SettingDef> defs) {
        StringBuilder text = new StringBuilder();
        int count = 0;
        for (SettingDef def : defs) {
            if ((def.getType() == SettingType.CHOICE) && (count < 3)) {
                if (count > 0) {
                    text.append(", ");
                }
                text.append(def.getValueLabel(getValue(def)));
                count++;
            }
        }
        if (count == 0) {
            for (SettingDef def : defs) {
                if (count > 0) {
                    text.append(", ");
                }
                text.append(def.getLabel());
                if (++count == 2) {
                    break;
                }
            }
        }
        return text.toString();
    }

    /**
     * @return The settings shown for a category in the current scope. The reset
     *         actions belong to one scope each, and the soft amount only shows with the
     *         soft filter.
     */
    private List<SettingDef> visibleSettings(SettingCategory category) {
        List<SettingDef> result = new ArrayList<SettingDef>();
        for (SettingDef def : SettingsCatalogue.inCategory(category)) {
            if (def.getType() == SettingType.ACTION) {
                if (def.isPerProgram() != isProgramScope()) {
                    continue;
                }
            }
            if (SettingsCatalogue.DISPLAY_SOFT_AMOUNT.equals(def.getId())
                    && !SettingsCatalogue.FILTER_SOFT.equals(getValue(SettingsCatalogue.get(SettingsCatalogue.DISPLAY_FILTER)))) {
                continue;
            }
            result.add(def);
        }
        return result;
    }

    private void buildPage(Table list) {
        for (SettingDef def : visibleSettings(page)) {
            switch (def.getType()) {
                case CHOICE:
                case TOGGLE:
                    addChoiceRow(list, def);
                    break;
                case SLIDER:
                    addStepperRow(list, def);
                    break;
                case ACTION:
                    addActionRow(list, def);
                    break;
                default:
                    break;
            }
        }
    }

    private Table rowTable(SettingDef def) {
        OverlayStyles styles = OverlayStyles.get();
        Table table = new Table();
        table.setTouchable(Touchable.enabled);
        table.pad(18, 20, 22, 20);
        table.defaults().left();
        Label label = new Label(def.getLabel(), styles.label);
        label.setFontScale(ROW_LABEL);
        table.add(label).growX().row();
        table.add(smallLabel(def.getHelp(), styles.dimLabel)).growX().padBottom(14).row();
        return table;
    }

    /**
     * Adds the line under a row's control that says where the value comes from, with a
     * Reset button when the value is saved in the scope shown.
     */
    private void addSourceLine(Table table, final SettingDef def) {
        OverlayStyles styles = OverlayStyles.get();
        String text;
        boolean saved;
        if (isProgramScope() && def.isPerProgram()) {
            SettingLayer source = programSettings.getSource(def.getId());
            saved = (source == SettingLayer.THIS_PROGRAM);
            text = describe(source);
        } else if (isProgramScope()) {
            saved = false;
            text = "The same for all programs.";
        } else {
            saved = (store.getGlobal(def.getId()) != null);
            text = saved ? "Set for all programs." : "Default.";
        }
        Table line = new Table();
        line.add(smallLabel(text, styles.dimLabel)).growX().left();
        if (saved) {
            TextButton reset = new TextButton("Reset", styles.button);
            reset.getLabel().setFontScale(SMALL);
            reset.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float cx, float cy) {
                    clearValue(def);
                }
            });
            line.add(reset).height(BUTTON_HEIGHT * 0.7f).width(180).padLeft(16);
        }
        table.add(line).growX().padTop(10).row();

        // When this run of the program uses a different value (from its launch options,
        // or, for all programs, from the program itself), say so.
        if (runningSettings != null) {
            String used = runningSettings.get(def.getId());
            SettingLayer usedSource = runningSettings.getSource(def.getId());
            boolean differs = (used != null) && !used.equals(getValue(def));
            boolean fromAbove = isProgramScope() ? (usedSource == SettingLayer.LAUNCH_OPTION)
                    : ((usedSource != null) && (usedSource.ordinal() > SettingLayer.ALL_PROGRAMS.ordinal()));
            if (differs && fromAbove) {
                table.add(smallLabel("This program uses " + def.getValueLabel(used) + " ("
                        + usedSource.getDescription() + ").", styles.noteLabel)).growX().padTop(8).row();
            }
        }
    }

    private static String describe(SettingLayer source) {
        if (source == null) {
            return "";
        }
        switch (source) {
            case THIS_PROGRAM:
                return "Set for this program.";
            case PROGRAM_CONFIG:
                return "Set by this program.";
            case ALL_PROGRAMS:
                return "From the settings for all programs.";
            case LAUNCH_OPTION:
                return "Set by launch options.";
            case BUILT_IN:
            default:
                return "Default.";
        }
    }

    private List<SettingDef.Choice> visibleChoices(SettingDef def) {
        List<SettingDef.Choice> choices = new ArrayList<SettingDef.Choice>();
        boolean advanced = isOn(SettingsCatalogue.GENERAL_ADVANCED);
        String current = getValue(def);
        for (SettingDef.Choice choice : def.getChoices()) {
            if (!choice.isAdvanced() || advanced || choice.getValue().equals(current)) {
                choices.add(choice);
            }
        }
        return choices;
    }

    /**
     * @return How many choice buttons fit on a line, given the widest label, from 1 up
     *         to {@link #CHOICES_PER_LINE}.
     */
    private int choicesPerLine(List<String> labels) {
        float widest = 0;
        for (String label : labels) {
            glyphLayout.setText(OverlayStyles.get().font, label);
            widest = Math.max(widest, glyphLayout.width * ROW_LABEL);
        }
        int perLine = (int) (contentWidth / (widest + 56));
        return Math.max(1, Math.min(CHOICES_PER_LINE, perLine));
    }

    private void addChoiceRow(Table list, final SettingDef def) {
        OverlayStyles styles = OverlayStyles.get();
        final List<String> values = new ArrayList<String>();
        final List<String> labels = new ArrayList<String>();
        if (def.getType() == SettingType.TOGGLE) {
            values.add(SettingDef.ON);
            labels.add("On");
            values.add(SettingDef.OFF);
            labels.add("Off");
        } else {
            for (SettingDef.Choice choice : visibleChoices(def)) {
                values.add(choice.getValue());
                labels.add(choice.getLabel());
            }
        }

        Table table = rowTable(def);
        Table buttons = new Table();
        buttons.defaults().height(BUTTON_HEIGHT).expandX().fillX().uniformX().pad(4);
        final String current = getValue(def);
        final List<TextButton> buttonList = new ArrayList<TextButton>();
        int perLine = choicesPerLine(labels);
        for (int i = 0; i < values.size(); i++) {
            if ((i > 0) && ((i % perLine) == 0)) {
                buttons.row();
            }
            final String value = values.get(i);
            TextButton button = new TextButton(labels.get(i), styles.choice);
            button.getLabel().setFontScale(ROW_LABEL);
            button.setChecked(value.equals(current));
            button.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float cx, float cy) {
                    // The button has toggled itself; put the checked states right.
                    for (int j = 0; j < buttonList.size(); j++) {
                        buttonList.get(j).setChecked(values.get(j).equals(value));
                    }
                    setValue(def, value);
                }
            });
            buttonList.add(button);
            buttons.add(button);
        }
        table.add(buttons).growX().row();
        addSourceLine(table, def);

        Row row = new Row() {
            private int index() {
                return values.indexOf(getValue(def));
            }

            @Override
            void left() {
                int i = index();
                setValue(def, values.get((i <= 0) ? values.size() - 1 : i - 1));
            }

            @Override
            void right() {
                int i = index();
                setValue(def, values.get((i + 1) % values.size()));
            }
        };
        addRow(list, row, table);
    }

    private void addStepperRow(Table list, final SettingDef def) {
        OverlayStyles styles = OverlayStyles.get();
        Table table = rowTable(def);
        Table stepper = new Table();
        final Row row = new Row() {
            private void step(float direction) {
                float value = Float.parseFloat(getValue(def)) + (direction * def.getStep());
                value = Math.max(def.getMin(), Math.min(def.getMax(), value));
                // Round to the step, so repeated steps don't drift.
                value = Math.round(value / def.getStep()) * def.getStep();
                setValue(def, def.normalise(formatAmount(value)));
            }

            @Override
            void left() {
                step(-1);
            }

            @Override
            void right() {
                step(1);
            }
        };
        TextButton minus = new TextButton("-", styles.button);
        minus.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float cx, float cy) {
                row.left();
            }
        });
        TextButton plus = new TextButton("+", styles.button);
        plus.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float cx, float cy) {
                row.right();
            }
        });
        Label value = new Label(formatAmount(Float.parseFloat(getValue(def))), styles.label);
        value.setAlignment(Align.center);
        stepper.add(minus).size(BUTTON_HEIGHT * 1.4f, BUTTON_HEIGHT);
        stepper.add(value).width(220);
        stepper.add(plus).size(BUTTON_HEIGHT * 1.4f, BUTTON_HEIGHT);
        table.add(stepper).left().row();
        addSourceLine(table, def);
        addRow(list, row, table);
    }

    /** Two decimal places, without String.format (not available on GWT). */
    private static String formatAmount(float value) {
        int hundredths = Math.round(value * 100);
        String digits = String.valueOf(hundredths % 100);
        return (hundredths / 100) + "." + ((digits.length() < 2) ? "0" + digits : digits);
    }

    private void addActionRow(Table list, final SettingDef def) {
        OverlayStyles styles = OverlayStyles.get();
        Table table = rowTable(def);
        TextButton button = new TextButton("Reset", styles.button);
        button.getLabel().setFontScale(ROW_LABEL);
        final Row row = new Row() {
            @Override
            void activate() {
                runAction(def);
            }

            @Override
            void right() {
            }
        };
        button.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float cx, float cy) {
                row.activate();
            }
        });
        table.add(button).height(BUTTON_HEIGHT).width(300).left().row();
        addRow(list, row, table);
    }

    private void runAction(SettingDef def) {
        if (SettingsCatalogue.GENERAL_RESET_ALL.equals(def.getId())) {
            dialogHandler.confirm("Forget all saved settings?", new ConfirmResponseHandler() {
                @Override
                public void yes() {
                    store.resetAll();
                    note = "All settings are back to their defaults.";
                    reapplyLiveSettings();
                    rebuildKeepingPlace();
                }

                @Override
                public void no() {
                    // Nothing to do.
                }
            });
        } else if (SettingsCatalogue.GENERAL_RESET_PROGRAM.equals(def.getId()) && (host.getProgramKey() != null)) {
            dialogHandler.confirm("Forget the settings saved for this program?", new ConfirmResponseHandler() {
                @Override
                public void yes() {
                    store.clearAllForProgram(host.getProgramKey());
                    note = "This program uses the settings for all programs again.";
                    reapplyLiveSettings();
                    rebuildKeepingPlace();
                }

                @Override
                public void no() {
                    // Nothing to do.
                }
            });
        }
    }

    private void addRow(Table list, final Row row, Table table) {
        final int index = rows.size();
        row.actor = table;
        table.addListener(new ClickListener() {
            @Override
            public void touchUp(InputEvent event, float cx, float cy, int pointer, int button) {
                // Focus first: the tap may open another page, which sets its own focus.
                focus = index;
                updateFocus(false);
                super.touchUp(event, cx, cy, pointer, button);
            }
        });
        rows.add(row);
        if (index > 0) {
            Table divider = new Table();
            divider.setBackground(OverlayStyles.get().divider);
            list.add(divider).height(2).growX().row();
        }
        list.add(table).growX().row();
    }

    private void updateFocus(boolean scroll) {
        OverlayStyles styles = OverlayStyles.get();
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).actor.setBackground((i == focus) ? styles.rowFocus : null);
        }
        if (scroll && (scrollPane != null) && (focus >= 0) && (focus < rows.size())) {
            Actor actor = rows.get(focus).actor;
            // The restart banner is below the scrolling list, so isn't scrolled to.
            if (actor.isDescendantOf(scrollPane)) {
                scrollPane.scrollTo(actor.getX(), actor.getY(), actor.getWidth(), actor.getHeight());
            }
        }
    }

    // ---------------------------------------------------------------------------------
    // Navigation.
    // ---------------------------------------------------------------------------------

    private void openPage(SettingCategory category) {
        page = category;
        focus = 0;
        note = null;
        requestRebuild();
    }

    private void goBack() {
        if (page == null) {
            close();
            return;
        }
        // Back to the list, with the category just left in focus.
        int index = 0;
        for (SettingCategory category : SettingCategory.values()) {
            if (category == page) {
                break;
            }
            if (!visibleSettings(category).isEmpty()) {
                index++;
            }
        }
        page = null;
        focus = index;
        note = null;
        requestRebuild();
    }

    @Override
    protected void keyPressed(int keycode) {
        if (altDown) {
            if (keycode == Keys.O) {
                close();
            }
            return;
        }
        Row row = ((focus >= 0) && (focus < rows.size())) ? rows.get(focus) : null;
        switch (keycode) {
            case Keys.UP:
                if (focus > 0) {
                    focus--;
                    updateFocus(true);
                }
                break;
            case Keys.DOWN:
                if (focus < rows.size() - 1) {
                    focus++;
                    updateFocus(true);
                }
                break;
            case Keys.LEFT:
                if (row != null) {
                    row.left();
                }
                break;
            case Keys.RIGHT:
                if (row != null) {
                    row.right();
                }
                break;
            case Keys.ENTER:
            case Keys.NUMPAD_ENTER:
            case Keys.SPACE:
                if (row != null) {
                    row.activate();
                }
                break;
            case Keys.TAB:
                if (programSettings != null) {
                    setScope((scope == Scope.PROGRAM) ? Scope.GLOBAL : Scope.PROGRAM);
                }
                break;
            case Keys.ESCAPE:
            case Keys.BACK:
            case Keys.BACKSPACE:
                goBack();
                break;
            default:
                break;
        }
    }
}
