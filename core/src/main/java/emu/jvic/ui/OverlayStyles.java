package emu.jvic.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane.ScrollPaneStyle;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;

/**
 * The look of the overlays (settings panel and machine menu), built from jvic's
 * existing skin (data/uiskin.json, whose font is Droid Sans at 60 pixels). Loaded once
 * and shared.
 */
public final class OverlayStyles {

    public static final Color PANEL = new Color(0.11f, 0.11f, 0.12f, 1f);
    public static final Color ROW_FOCUS = new Color(0.22f, 0.24f, 0.30f, 1f);
    public static final Color BUTTON = new Color(0.24f, 0.24f, 0.26f, 1f);
    public static final Color BUTTON_DOWN = new Color(0.34f, 0.34f, 0.38f, 1f);
    public static final Color SELECTED = new Color(0.20f, 0.42f, 0.70f, 1f);
    public static final Color TEXT = new Color(0.93f, 0.93f, 0.93f, 1f);
    public static final Color TEXT_DIM = new Color(0.62f, 0.62f, 0.65f, 1f);
    public static final Color NOTE = new Color(0.95f, 0.78f, 0.35f, 1f);
    public static final Color DIVIDER = new Color(1f, 1f, 1f, 0.08f);

    private static OverlayStyles instance;

    public final Skin skin;
    public final BitmapFont font;
    public final LabelStyle label;
    public final LabelStyle dimLabel;
    public final LabelStyle noteLabel;
    public final TextButtonStyle button;
    public final TextButtonStyle choice;
    public final TextButtonStyle plain;
    public final ScrollPaneStyle scrollPane;
    public final Drawable panel;
    public final Drawable rowFocus;
    public final Drawable divider;

    private OverlayStyles() {
        skin = new Skin(Gdx.files.internal("data/uiskin.json"));
        font = skin.getFont("default-font");

        label = new LabelStyle(font, TEXT);
        dimLabel = new LabelStyle(font, TEXT_DIM);
        noteLabel = new LabelStyle(font, NOTE);

        panel = tint(PANEL);
        rowFocus = tint(ROW_FOCUS);
        divider = tint(DIVIDER);

        button = new TextButtonStyle(tint(BUTTON), tint(BUTTON_DOWN), null, font);
        button.fontColor = TEXT;

        // A choice button: grey, or blue when it is the selected value.
        choice = new TextButtonStyle(tint(BUTTON), tint(BUTTON_DOWN), tint(SELECTED), font);
        choice.fontColor = TEXT;

        // A button with no background, e.g. a row of the category list.
        plain = new TextButtonStyle(null, tint(BUTTON_DOWN), null, font);
        plain.fontColor = TEXT;

        scrollPane = new ScrollPaneStyle();
        scrollPane.vScrollKnob = tint(new Color(1f, 1f, 1f, 0.25f));
        scrollPane.vScrollKnob.setMinWidth(10);
    }

    private Drawable tint(Color color) {
        // A 1x1 texture of its own: the skin's "white" region blends with its transparent
        // neighbours in the atlas when stretched, which makes backgrounds see-through.
        if (white == null) {
            Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
            pixmap.setColor(Color.WHITE);
            pixmap.fill();
            white = new TextureRegion(new Texture(pixmap));
            pixmap.dispose();
        }
        return new TextureRegionDrawable(white).tint(color);
    }

    private TextureRegion white;

    /**
     * @return The shared styles, loading them the first time. Call on the render thread.
     */
    public static OverlayStyles get() {
        if (instance == null) {
            instance = new OverlayStyles();
        }
        return instance;
    }
}
