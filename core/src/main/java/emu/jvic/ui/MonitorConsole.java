package emu.jvic.ui;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Matrix4;

import emu.jvic.debug.MonitorChannel;

/**
 * The in-app monitor console, opened with Alt+H (or Alt+M) as in VICE. It is drawn
 * over the bottom half of the window and talks to the monitor engine through a
 * {@link MonitorChannel}, so it works the same on desktop and in the browser.
 *
 * <p>Text is drawn with the VIC-20's own character ROM (its lower case set), which
 * gives a fixed-width font without any extra assets. While the console is open, it
 * takes all keyboard input, so the emulated keyboard sees none of it.
 *
 * <p>Keys: Enter runs the line; Up and Down go through the command history; Tab
 * completes a command name; Page Up and Page Down (or the mouse wheel) scroll; Ctrl+V
 * pastes; Ctrl+L clears; Alt+H closes. Typing x (exit) also closes it.
 *
 * <p>Plain Java and libGDX only, as this package is translated by GWT, where the
 * console is never created (Alt+H is ignored there).
 */
public class MonitorConsole extends InputAdapter implements MonitorChannel.Listener {

    /** How many lines of output are kept for scrolling back. */
    static final int SCROLLBACK_LINES = 5000;

    private static final int MAX_HISTORY = 200;

    private static final int GLYPH = 8;

    /** The command names that Tab completes, VICE's full list. */
    private static final String[] COMMANDS = {
        "help", "print", "device", "exit", "quit", "radix", "sidefx", "log", "logname", "dump", "undump", "bank",
        "cpu", "backtrace", "cpuhistory", "registers", "stopwatch", "goto", "next", "return", "step", "profile",
        "reset", "export", "cartfreeze", "updb", "jpdb", "keybuf", "warp", "add_label", "delete_label",
        "load_labels", "save_labels", "show_labels", "clear_labels", "compare", "disass", "fill", "hunt", "mem",
        "memchar", "memmapsave", "memmapshow", "memmapzap", "memsprite", "move", "io", "screen", "break",
        "command", "condition", "delete", "disable", "enable", "ignore", "until", "watch", "trace", "dummy",
        "cd", "pwd", "mkdir", "rmdir", "attach", "autostart", "autoload", "bload", "block_read", "bsave",
        "bverify", "block_write", "detach", "dir", "list", "load", "loadbasic", "save", "verify", "screenshot",
        "tapectrl", "tapeoffs", "playback", "record", "stop", "resourceget", "resourceset", "load_resources",
        "save_resources",
    };

    private final MonitorChannel channel;

    // Output from the emulation thread, waiting for the UI thread.
    private final List<String> pendingOutput = new ArrayList<>();
    private String pendingPrompt;
    /** Whether the machine has stopped in the monitor since the console last looked. */
    private boolean stopSeen;

    // UI thread state.
    private final List<String> lines = new ArrayList<>();
    private final StringBuilder partialLine = new StringBuilder();
    private final List<String> history = new ArrayList<>();
    private final StringBuilder input = new StringBuilder();
    private int cursor;
    private int historyIndex;
    private int scrollOffset;
    private String prompt = "";
    private boolean open;
    private boolean altDown;
    private boolean controlDown;
    /** A key whose release the console takes, after Alt+H closed it. */
    private int swallowKeyUp = -1;

    private Texture glyphTexture;
    private TextureRegion[] glyphs;
    private Texture white;
    private final Matrix4 projection = new Matrix4();
    private float blink;

    /**
     * @param channel The monitor channel of the runner.
     */
    public MonitorConsole(MonitorChannel channel) {
        this.channel = channel;
        channel.addListener(this);
    }

    public boolean isOpen() {
        return open;
    }

    /**
     * Opens the console and stops the machine in the monitor, or closes it and lets
     * the machine run on.
     */
    public void toggle() {
        if (open) {
            close(true);
        } else {
            open = true;
            altDown = false;
            controlDown = false;
            scrollOffset = 0;
            channel.requestStop();
        }
    }

    private void close(boolean resume) {
        open = false;
        altDown = false;
        controlDown = false;
        if (resume) {
            channel.execute("x");
        }
    }

    // ------------------------------------------------------------------------------
    // MonitorChannel.Listener, called on the emulation thread.
    // ------------------------------------------------------------------------------

    @Override
    public void onOutput(String text) {
        synchronized (pendingOutput) {
            pendingOutput.add(text);
        }
    }

    @Override
    public void onPrompt(String prompt) {
        synchronized (pendingOutput) {
            pendingPrompt = prompt;
            stopSeen = true;
        }
    }

    /** Moves output from the emulation thread into the scrollback. UI thread. */
    private void takeOutput() {
        List<String> texts;
        String newPrompt;
        synchronized (pendingOutput) {
            if (stopSeen && !open) {
                // The machine stopped in the monitor (a breakpoint, initbreak, the
                // remote monitor): open, as VICE opens its monitor window.
                open = true;
                altDown = false;
                controlDown = false;
                scrollOffset = 0;
            }
            stopSeen = false;
            if (pendingOutput.isEmpty() && (pendingPrompt == null)) {
                return;
            }
            texts = new ArrayList<>(pendingOutput);
            pendingOutput.clear();
            newPrompt = pendingPrompt;
            pendingPrompt = null;
        }
        for (String text : texts) {
            append(text);
        }
        if (newPrompt != null) {
            prompt = newPrompt;
        }
    }

    private void append(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                addLine(partialLine.toString());
                partialLine.setLength(0);
            } else if (c != '\r') {
                partialLine.append(c);
            }
        }
    }

    private void addLine(String line) {
        lines.add(line);
        if (lines.size() > SCROLLBACK_LINES) {
            lines.remove(0);
        }
        scrollOffset = 0;
    }

    // ------------------------------------------------------------------------------
    // Input, UI thread.
    // ------------------------------------------------------------------------------

    @Override
    public boolean keyDown(int keycode) {
        if ((keycode == Keys.ALT_LEFT) || (keycode == Keys.ALT_RIGHT)) {
            altDown = true;
            return open;
        }
        if ((keycode == Keys.CONTROL_LEFT) || (keycode == Keys.CONTROL_RIGHT)) {
            controlDown = true;
            return open;
        }
        if (!open) {
            return false;
        }
        if (altDown) {
            if ((keycode == Keys.H) || (keycode == Keys.M)) {
                // Take the key's release too, or Alt+H would open the console again.
                swallowKeyUp = keycode;
                close(true);
            }
            return true;
        }
        if (controlDown) {
            if (keycode == Keys.V) {
                paste();
            } else if (keycode == Keys.L) {
                lines.clear();
                scrollOffset = 0;
            }
            return true;
        }
        switch (keycode) {
            case Keys.LEFT:
                cursor = Math.max(0, cursor - 1);
                break;
            case Keys.RIGHT:
                cursor = Math.min(input.length(), cursor + 1);
                break;
            case Keys.HOME:
                cursor = 0;
                break;
            case Keys.END:
                cursor = input.length();
                break;
            case Keys.UP:
                recall(-1);
                break;
            case Keys.DOWN:
                recall(1);
                break;
            case Keys.PAGE_UP:
                scroll(visibleLines() - 1);
                break;
            case Keys.PAGE_DOWN:
                scroll(-(visibleLines() - 1));
                break;
            case Keys.FORWARD_DEL:
                if (cursor < input.length()) {
                    input.deleteCharAt(cursor);
                }
                break;
            default:
                break;
        }
        return true;
    }

    @Override
    public boolean keyUp(int keycode) {
        if (keycode == swallowKeyUp) {
            swallowKeyUp = -1;
            return true;
        }
        if ((keycode == Keys.ALT_LEFT) || (keycode == Keys.ALT_RIGHT)) {
            altDown = false;
        } else if ((keycode == Keys.CONTROL_LEFT) || (keycode == Keys.CONTROL_RIGHT)) {
            controlDown = false;
        }
        return open;
    }

    @Override
    public boolean keyTyped(char c) {
        if (!open) {
            return false;
        }
        if (altDown || controlDown) {
            return true;
        }
        if ((c == '\r') || (c == '\n')) {
            enter();
        } else if (c == '\b') {
            if (cursor > 0) {
                input.deleteCharAt(--cursor);
            }
        } else if (c == '\t') {
            complete();
        } else if ((c >= ' ') && (c < 0x7F)) {
            input.insert(cursor++, c);
        }
        return true;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        if (!open) {
            return false;
        }
        scroll(-Math.round(amountY * 3));
        return true;
    }

    private void scroll(int lineCount) {
        int max = Math.max(0, lines.size() - visibleLines() + 1);
        scrollOffset = Math.max(0, Math.min(max, scrollOffset + lineCount));
    }

    private void enter() {
        String line = input.toString();
        input.setLength(0);
        cursor = 0;
        if (!line.trim().isEmpty() && (history.isEmpty() || !history.get(history.size() - 1).equals(line))) {
            history.add(line);
            if (history.size() > MAX_HISTORY) {
                history.remove(0);
            }
        }
        historyIndex = history.size();
        // Echo the line after the prompt, as the monitor log shows it.
        append(prompt + line + "\n");
        channel.execute(line);
        String word = line.trim().toLowerCase();
        if (word.equals("x") || word.equals("exit")) {
            close(false);
        }
    }

    private void recall(int direction) {
        if (history.isEmpty()) {
            return;
        }
        historyIndex = Math.max(0, Math.min(history.size(), historyIndex + direction));
        input.setLength(0);
        if (historyIndex < history.size()) {
            input.append(history.get(historyIndex));
        }
        cursor = input.length();
    }

    /** Completes the command name at the start of the line, or lists the choices. */
    private void complete() {
        String text = input.toString();
        if (text.indexOf(' ') >= 0) {
            return;
        }
        String start = text.toLowerCase();
        List<String> matches = new ArrayList<>();
        for (String command : COMMANDS) {
            if (command.startsWith(start)) {
                matches.add(command);
            }
        }
        if (matches.isEmpty()) {
            return;
        }
        String common = matches.get(0);
        for (String match : matches) {
            int i = 0;
            while ((i < common.length()) && (i < match.length()) && (common.charAt(i) == match.charAt(i))) {
                i++;
            }
            common = common.substring(0, i);
        }
        if (matches.size() == 1) {
            common += " ";
        } else if (common.length() == start.length()) {
            StringBuilder list = new StringBuilder();
            for (String match : matches) {
                list.append(match).append("  ");
            }
            append(list.toString().trim() + "\n");
        }
        input.setLength(0);
        input.append(common);
        cursor = input.length();
    }

    private void paste() {
        String contents = (Gdx.app.getClipboard() != null) ? Gdx.app.getClipboard().getContents() : null;
        if (contents == null) {
            return;
        }
        String[] parts = contents.split("\r?\n", -1);
        for (int i = 0; i < parts.length; i++) {
            for (int j = 0; j < parts[i].length(); j++) {
                char c = parts[i].charAt(j);
                if ((c >= ' ') && (c < 0x7F)) {
                    input.insert(cursor++, c);
                }
            }
            if (i < parts.length - 1) {
                enter();
            }
        }
    }

    // ------------------------------------------------------------------------------
    // Drawing, UI thread.
    // ------------------------------------------------------------------------------

    private int scale() {
        return (Gdx.graphics.getWidth() >= 1200) ? 2 : 1;
    }

    private int charSize() {
        return GLYPH * scale();
    }

    private int heightInPixels() {
        return Gdx.graphics.getHeight() / 2;
    }

    private int visibleLines() {
        return Math.max(2, (heightInPixels() - 8) / (charSize() + scale()));
    }

    /**
     * Draws the console over the bottom half of the window, if open. Takes any new
     * output first. Call after everything else has been drawn.
     *
     * @param batch A batch that isn't drawing.
     * @param delta The time since the last frame, for the cursor blink.
     */
    public void draw(SpriteBatch batch, float delta) {
        takeOutput();
        if (!open) {
            return;
        }
        ensureTextures();
        blink += delta;

        int width = Gdx.graphics.getWidth();
        int height = heightInPixels();
        int size = charSize();
        int lineHeight = size + scale();
        int columns = Math.max(10, (width - 16) / size);

        projection.setToOrtho2D(0, 0, width, Gdx.graphics.getHeight());
        Matrix4 oldProjection = batch.getProjectionMatrix().cpy();
        Color oldColor = batch.getColor().cpy();
        batch.setProjectionMatrix(projection);
        batch.begin();
        batch.setColor(0f, 0f, 0f, 0.94f);
        batch.draw(white, 0, 0, width, height);
        batch.setColor(0.55f, 0.55f, 0.95f, 1f);
        batch.draw(white, 0, height - scale(), width, scale());

        // The lines to show: the scrollback (wrapped to the width), then the input line.
        List<String> rows = new ArrayList<>();
        int needed = visibleLines() + scrollOffset;
        for (int i = lines.size() - 1; (i >= 0) && (rows.size() < needed); i--) {
            List<String> wrapped = wrap(lines.get(i), columns);
            for (int w = wrapped.size() - 1; w >= 0; w--) {
                rows.add(0, wrapped.get(w));
            }
        }
        if (partialLine.length() > 0) {
            rows.add(partialLine.toString());
        }
        String inputLine = prompt + input;
        List<String> inputRows = wrap(inputLine, columns);
        int bottomRows = (scrollOffset == 0) ? inputRows.size() : 0;
        int outputRows = visibleLines() - bottomRows;
        int end = Math.max(0, rows.size() - scrollOffset);
        int start = Math.max(0, end - outputRows);

        float y = height - lineHeight - 4;
        batch.setColor(0.8f, 0.8f, 0.8f, 1f);
        for (int i = start; i < end; i++) {
            drawText(batch, rows.get(i), 8, y, size);
            y -= lineHeight;
        }
        if (scrollOffset == 0) {
            batch.setColor(1f, 1f, 1f, 1f);
            for (String row : inputRows) {
                drawText(batch, row, 8, y, size);
                y -= lineHeight;
            }
            // The cursor, blinking.
            if ((blink % 1f) < 0.6f) {
                int position = prompt.length() + cursor;
                int cursorRow = position / columns;
                int cursorColumn = position % columns;
                float cursorY = (y + lineHeight * inputRows.size()) - lineHeight * cursorRow;
                batch.setColor(0.55f, 0.55f, 0.95f, 0.8f);
                batch.draw(white, 8 + cursorColumn * size, cursorY, size, size);
            }
        }
        batch.end();
        batch.setColor(oldColor);
        batch.setProjectionMatrix(oldProjection);
    }

    private static List<String> wrap(String line, int columns) {
        List<String> rows = new ArrayList<>();
        if (line.isEmpty()) {
            rows.add("");
            return rows;
        }
        for (int i = 0; i < line.length(); i += columns) {
            rows.add(line.substring(i, Math.min(line.length(), i + columns)));
        }
        return rows;
    }

    private void drawText(SpriteBatch batch, String text, float x, float y, int size) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != ' ') {
                batch.draw(glyphs[(c < 128) ? c : '.'], x + i * size, y, size, size);
            }
        }
    }

    /**
     * Builds the glyph texture from the character ROM's lower case set: 128 glyphs,
     * one per ASCII code.
     */
    private void ensureTextures() {
        if (glyphTexture != null) {
            return;
        }
        byte[] charRom = Gdx.files.internal("roms/char.rom").readBytes();
        Pixmap pixmap = new Pixmap(128, 64, Pixmap.Format.RGBA8888);
        pixmap.setColor(0, 0, 0, 0);
        pixmap.fill();
        for (int c = 0; c < 128; c++) {
            int code = screenCode((char) c);
            int base = 0x800 + code * 8;
            int gx = (c % 16) * GLYPH;
            int gy = (c / 16) * GLYPH;
            for (int row = 0; row < 8; row++) {
                int bits = charRom[base + row] & 0xFF;
                for (int bit = 0; bit < 8; bit++) {
                    if ((bits & (0x80 >> bit)) != 0) {
                        pixmap.drawPixel(gx + bit, gy + row, 0xFFFFFFFF);
                    }
                }
            }
        }
        glyphTexture = new Texture(pixmap);
        pixmap.dispose();
        glyphs = new TextureRegion[128];
        for (int c = 0; c < 128; c++) {
            glyphs[c] = new TextureRegion(glyphTexture, (c % 16) * GLYPH, (c / 16) * GLYPH, GLYPH, GLYPH);
        }
        Pixmap one = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        one.setColor(1, 1, 1, 1);
        one.fill();
        white = new Texture(one);
        one.dispose();
    }

    /**
     * The screen code in the VIC-20's lower case character set that looks most like
     * the ASCII character.
     */
    static int screenCode(char c) {
        if ((c >= 'a') && (c <= 'z')) {
            return c - 'a' + 1;
        }
        if ((c >= 'A') && (c <= 'Z')) {
            return c - 'A' + 0x41;
        }
        if ((c >= ' ') && (c <= '?')) {
            return c;
        }
        switch (c) {
            case '@': return 0x00;
            case '[': return 0x1B;
            case ']': return 0x1D;
            case '^': return 0x1E;
            case '_': return 0x64; // A low line.
            case '|': return 0x5D; // A vertical line.
            case '\\': return 0x1C; // The pound sign, which VICE shows for a backslash.
            case '`': return 0x27;
            case '{': return 0x28;
            case '}': return 0x29;
            case '~': return 0x2D;
            default: return 0x20;
        }
    }

    /** Frees the textures. */
    public void dispose() {
        channel.removeListener(this);
        if (glyphTexture != null) {
            glyphTexture.dispose();
            white.dispose();
            glyphTexture = null;
        }
    }
}
