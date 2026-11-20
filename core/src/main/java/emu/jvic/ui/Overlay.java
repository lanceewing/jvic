package emu.jvic.ui;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Stage;

/**
 * Something drawn over the current screen with scene2d (the settings panel, the
 * machine screen's menu). It is an InputProcessor that goes first in the screen's
 * InputMultiplexer: while open it takes all touches and keys, so nothing reaches the
 * VIC-20, the touch joystick or the icons underneath.
 *
 * <p>Modifier keys (Alt, Shift, Control) are passed on, so that the keyboard matrix's
 * idea of which modifiers are held stays right when the overlay closes.
 *
 * <p>The content is built for the current viewport size and orientation, and built
 * again when either changes.
 */
public abstract class Overlay extends InputAdapter {

    protected final ViewportManager viewportManager;
    protected final Stage stage;

    private boolean open;
    private boolean rebuildRequested;
    private float builtWidth;
    private float builtHeight;
    private boolean builtPortrait;

    /** Whether an Alt key is held, for Alt+key hotkeys while the overlay is open. */
    protected boolean altDown;

    /**
     * A key pressed while the overlay was open, whose release is taken too, even if the
     * overlay has closed by then. Otherwise the release of the key that closed it (e.g.
     * the O of Alt+O) would reach the screen underneath and act there.
     */
    private int swallowKeyUp = -1;

    /**
     * @param batch The batch to draw with.
     */
    protected Overlay(Batch batch) {
        this.viewportManager = ViewportManager.getInstance();
        this.stage = new Stage(viewportManager.getCurrentViewport(), batch);
    }

    public boolean isOpen() {
        return open;
    }

    /**
     * Opens the overlay, building its content.
     */
    public void open() {
        open = true;
        rebuildRequested = true;
        onOpen();
    }

    /**
     * Closes the overlay.
     */
    public void close() {
        if (open) {
            open = false;
            stage.unfocusAll();
            onClose();
        }
    }

    /** Called after the overlay opens. */
    protected void onOpen() {
    }

    /** Called after the overlay closes. */
    protected void onClose() {
    }

    /**
     * Asks for the content to be built again before it is next drawn, e.g. after a
     * value it shows has changed.
     */
    public void requestRebuild() {
        rebuildRequested = true;
    }

    /**
     * Builds the content. The stage has been cleared.
     *
     * @param width    The viewport's world width.
     * @param height   The viewport's world height.
     * @param portrait true in portrait orientation.
     */
    protected abstract void build(float width, float height, boolean portrait);

    /**
     * Handles a key while the overlay is open. All keys are taken whether or not this
     * handles them.
     *
     * @param keycode The key that was pressed.
     */
    protected void keyPressed(int keycode) {
    }

    /**
     * Called when a touch lands outside the overlay's content.
     */
    protected void touchedOutside() {
    }

    /**
     * Draws the overlay, if open. Call after the screen underneath has been drawn.
     *
     * @param delta Seconds since the last frame.
     */
    public void draw(float delta) {
        if (!open) {
            return;
        }
        syncViewport();
        float width = viewportManager.getWidth();
        float height = viewportManager.getHeight();
        boolean portrait = viewportManager.isPortrait();
        if (rebuildRequested || (width != builtWidth) || (height != builtHeight) || (portrait != builtPortrait)) {
            rebuildRequested = false;
            builtWidth = width;
            builtHeight = height;
            builtPortrait = portrait;
            stage.clear();
            build(width, height, portrait);
        }
        stage.act(delta);
        stage.draw();
    }

    private void syncViewport() {
        if (stage.getViewport() != viewportManager.getCurrentViewport()) {
            stage.setViewport(viewportManager.getCurrentViewport());
        }
    }

    public void dispose() {
        stage.dispose();
    }

    private static boolean isModifier(int keycode) {
        return (keycode == Keys.ALT_LEFT) || (keycode == Keys.ALT_RIGHT)
                || (keycode == Keys.SHIFT_LEFT) || (keycode == Keys.SHIFT_RIGHT)
                || (keycode == Keys.CONTROL_LEFT) || (keycode == Keys.CONTROL_RIGHT);
    }

    @Override
    public boolean keyDown(int keycode) {
        if ((keycode == Keys.ALT_LEFT) || (keycode == Keys.ALT_RIGHT)) {
            altDown = true;
        }
        if (!open || isModifier(keycode)) {
            return false;
        }
        swallowKeyUp = keycode;
        keyPressed(keycode);
        return true;
    }

    @Override
    public boolean keyUp(int keycode) {
        if ((keycode == Keys.ALT_LEFT) || (keycode == Keys.ALT_RIGHT)) {
            altDown = false;
        }
        if (keycode == swallowKeyUp) {
            swallowKeyUp = -1;
            return true;
        }
        return open && !isModifier(keycode);
    }

    @Override
    public boolean keyTyped(char character) {
        return open;
    }

    private boolean isOverContent(int screenX, int screenY) {
        Vector2 point = stage.screenToStageCoordinates(new Vector2(screenX, screenY));
        return stage.hit(point.x, point.y, true) != null;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (!open) {
            return false;
        }
        syncViewport();
        if (isOverContent(screenX, screenY)) {
            stage.touchDown(screenX, screenY, pointer, button);
        } else {
            touchedOutside();
        }
        return true;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        if (!open) {
            return false;
        }
        syncViewport();
        stage.touchUp(screenX, screenY, pointer, button);
        return true;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        if (!open) {
            return false;
        }
        syncViewport();
        stage.touchDragged(screenX, screenY, pointer);
        return true;
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        if (!open) {
            return false;
        }
        syncViewport();
        stage.mouseMoved(screenX, screenY);
        return true;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        if (!open) {
            return false;
        }
        stage.scrolled(amountX, amountY);
        return true;
    }
}
