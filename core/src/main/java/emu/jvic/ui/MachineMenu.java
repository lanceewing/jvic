package emu.jvic.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;

/**
 * The machine screen's "..." menu: a popup list of actions next to the menu icon. It
 * opens upward from an icon in a bottom row, and to the left of an icon in a side
 * column. Tapping an entry does the action and closes the menu; tapping outside, Esc
 * or BACK closes it.
 */
public class MachineMenu extends Overlay {

    /**
     * Decides which actions are offered and carries them out.
     */
    public interface Handler {

        /**
         * @return true if the action can be used on this platform, now.
         */
        boolean isAvailable(MachineAction action);

        /**
         * Carries out the action. Called after the menu has closed.
         */
        void perform(MachineAction action);

        /**
         * @return Where the menu icon is, to place the menu next to it; null to centre
         *         the menu.
         */
        MachineIconLayout.Slot getMenuIconSlot();

        /**
         * @return true if hotkeys should be shown next to the entries.
         */
        boolean showHotkeys();
    }

    private static final float ROW_HEIGHT = 104;
    private static final float ICON = 72;
    private static final float GAP = 16;

    private final Handler handler;
    private final Map<String, Texture> icons = new HashMap<String, Texture>();
    private final List<MachineAction> entries = new ArrayList<MachineAction>();
    private final List<Table> rows = new ArrayList<Table>();
    private int focus;
    private ScrollPane scrollPane;

    public MachineMenu(Batch batch, Handler handler) {
        super(batch);
        this.handler = handler;
    }

    @Override
    protected void onOpen() {
        focus = -1;
    }

    private Texture icon(String path) {
        Texture texture = icons.get(path);
        if (texture == null) {
            texture = new Texture(path);
            texture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
            icons.put(path, texture);
        }
        return texture;
    }

    @Override
    protected void build(float width, float height, boolean portrait) {
        OverlayStyles styles = OverlayStyles.get();
        entries.clear();
        rows.clear();
        for (MachineAction action : MachineAction.values()) {
            if (handler.isAvailable(action)) {
                entries.add(action);
            }
        }

        boolean showHotkeys = handler.showHotkeys();
        Table list = new Table();
        for (int i = 0; i < entries.size(); i++) {
            final MachineAction action = entries.get(i);
            final int index = i;
            Table row = new Table();
            row.setTouchable(Touchable.enabled);
            row.pad(GAP, 24, GAP, 32);
            row.add(new Image(icon(action.getIconPath()))).size(ICON).padRight(28);
            row.add(new Label(action.getLabel(), styles.label)).left().expandX().fillX();
            if (showHotkeys && !action.getHotkey().isEmpty()) {
                Label hotkey = new Label(action.getHotkey(), styles.dimLabel);
                hotkey.setFontScale(0.7f);
                row.add(hotkey).right().padLeft(40);
            }
            row.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    choose(index);
                }
            });
            rows.add(row);
            list.add(row).height(ROW_HEIGHT).growX().row();
        }
        updateFocus();

        scrollPane = new ScrollPane(list, styles.scrollPane);
        scrollPane.setFadeScrollBars(false);
        scrollPane.setOverscroll(false, false);
        scrollPane.setScrollingDisabled(true, false);

        Table menu = new Table();
        menu.setTouchable(Touchable.enabled);
        menu.setBackground(styles.panel);
        menu.pad(12);
        float menuWidth = Math.min(list.getPrefWidth() + 24, width - 32);
        float maxHeight = height - 48;
        float menuHeight = Math.min((entries.size() * ROW_HEIGHT) + 24, maxHeight);
        menu.add(scrollPane).grow();
        menu.setSize(menuWidth, menuHeight);

        // Next to the menu icon: above it when it is in a bottom row, otherwise to its left.
        float x = (width - menuWidth) / 2;
        float y = (height - menuHeight) / 2;
        MachineIconLayout.Slot slot = handler.getMenuIconSlot();
        if ((slot != null) && slot.isVisible()) {
            float iconSize = MachineIconLayout.ICON_SIZE;
            if (slot.getY() < iconSize) {
                x = slot.getX();
                y = slot.getY() + iconSize + GAP;
            } else {
                x = slot.getX() - GAP - menuWidth;
                y = slot.getY() + (iconSize / 2) - (menuHeight / 2);
            }
        }
        x = Math.max(16, Math.min(x, width - menuWidth - 16));
        y = Math.max(16, Math.min(y, height - menuHeight - 16));
        menu.setPosition(x, y);
        stage.addActor(menu);
    }

    private void updateFocus() {
        OverlayStyles styles = OverlayStyles.get();
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).setBackground((i == focus) ? styles.rowFocus : null);
        }
        if ((scrollPane != null) && (focus >= 0) && (focus < rows.size())) {
            Table row = rows.get(focus);
            scrollPane.scrollTo(row.getX(), row.getY(), row.getWidth(), row.getHeight());
        }
    }

    private void choose(int index) {
        if ((index >= 0) && (index < entries.size())) {
            MachineAction action = entries.get(index);
            close();
            handler.perform(action);
        }
    }

    @Override
    protected void keyPressed(int keycode) {
        switch (keycode) {
            case Keys.UP:
                focus = (focus <= 0) ? entries.size() - 1 : focus - 1;
                updateFocus();
                break;
            case Keys.DOWN:
                focus = (focus + 1) % Math.max(1, entries.size());
                updateFocus();
                break;
            case Keys.ENTER:
            case Keys.NUMPAD_ENTER:
            case Keys.SPACE:
                choose(focus);
                break;
            case Keys.ESCAPE:
            case Keys.BACK:
                close();
                break;
            default:
                break;
        }
    }

    @Override
    protected void touchedOutside() {
        close();
    }

    @Override
    public void dispose() {
        super.dispose();
        for (Texture texture : icons.values()) {
            texture.dispose();
        }
        icons.clear();
    }
}
