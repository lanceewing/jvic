package emu.jvic.video;

import emu.jvic.testutil.VicTestHarness;
import org.junit.jupiter.api.Test;

import static emu.jvic.video.VicFrameTrace.DEFAULT_FIRST_LINE;
import static emu.jvic.video.VicFrameTrace.DEFAULT_XPOS;
import static emu.jvic.video.VicFrameTrace.colourChangeX;
import static emu.jvic.video.VicFrameTrace.columnX;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Where stores to the colour registers first show within a raster line. The background and border
 * test covers the same behaviour as testback from the VICE VIC test suite (Marko Mäkelä's original
 * raster routine), which alternates $900F between $1B and $EC every 4 cycles from store cycle 19,
 * on lines 76-91.
 *
 * <p>Rule (Matthies): a store to the auxiliary, background or border colour in cycle c first shows
 * at pixel 4 * (c - 7), and a store to the reverse bit at pixel 4 * (c - 7) + 3. Pixel 4 * (c - 7)
 * is on the character grid, i.e. a column start when c - 7 = XPOS + 2 * column. Measured in both
 * jvic and VICE, the colour changes actually show one pixel to the right of the grid
 * ({@link VicFrameTrace#colourChangeX}), while the reverse bit change is exactly 3 pixels after the
 * grid position, which is 2 pixels after the colour change.
 *
 * <p>No program in the suite tests the auxiliary colour or the reverse bit. The reverse bit position
 * was checked against VICE using a variant of testback that toggles only bit 3 of $900F. The
 * auxiliary colour position has not been checked against VICE.
 *
 * <p>Raster line 76 is an even line, so it uses the even line PAL palette.
 */
public class VicColourChangeTimingTest {

    private static final int BLACK = 0x000000FF;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int RED = 0x982D18FF;
    private static final int CYAN = 0x73D5FFFF;
    private static final int PURPLE = 0xB63BA7FF;
    private static final int LIGHT_BLUE = 0xA0A4D8FF;

    /** Background white, border cyan. */
    private static final int COLOURS_A = 0x1B;

    /** Background light blue, border purple. */
    private static final int COLOURS_B = 0xEC;

    private static final int FIRST_STORE_CYCLE = 19;
    private static final int STORES_PER_LINE = 12;
    private static final int LAST_STORE_LINE = 91;

    private static final int WINDOW_START = columnX(DEFAULT_XPOS, 0);
    private static final int WINDOW_END = columnX(DEFAULT_XPOS, 22);

    private static int storeCycle(int store) {
        return FIRST_STORE_CYCLE + (4 * store);
    }

    /** Runs the testback store pattern for the given register, alternating between two values. */
    private static VicFrameTrace runAlternating(VicTestHarness h, int reg, int first, int second, int stores) {
        RasterStoreSchedule schedule = new RasterStoreSchedule();
        for (int store = 0; store < stores; store++) {
            schedule.storeOnLines(DEFAULT_FIRST_LINE, LAST_STORE_LINE, storeCycle(store), reg,
                    ((store & 1) == 0) ? first : second);
        }
        return VicFrameTrace.run(h, schedule);
    }

    private static int backgroundOrBorder(int x, int colourReg) {
        boolean inWindow = (x >= WINDOW_START) && (x < WINDOW_END);
        if (colourReg == COLOURS_A) {
            return inWindow ? WHITE : CYAN;
        }
        return inWindow ? LIGHT_BLUE : PURPLE;
    }

    @Test
    void testbackBackgroundAndBorderChanges() {
        VicFrameTrace trace = runAlternating(VicFrameTrace.defaultScreen(), 15, COLOURS_B, COLOURS_A, STORES_PER_LINE);
        int previous = COLOURS_A;
        for (int store = 0; store < STORES_PER_LINE; store++) {
            int value = ((store & 1) == 0) ? COLOURS_B : COLOURS_A;
            int x = colourChangeX(storeCycle(store));
            assertEquals(backgroundOrBorder(x - 1, previous), trace.pixel(x - 1, DEFAULT_FIRST_LINE),
                    "pixel before the store at c" + storeCycle(store) + " (x=" + (x - 1) + ")");
            assertEquals(backgroundOrBorder(x, value), trace.pixel(x, DEFAULT_FIRST_LINE),
                    "first pixel of the store at c" + storeCycle(store) + " (x=" + x + ")");
            previous = value;
        }
    }

    @Test
    void testbackFirstChangesAreAt76And92() {
        VicFrameTrace trace = runAlternating(VicFrameTrace.defaultScreen(), 15, COLOURS_B, COLOURS_A, STORES_PER_LINE);
        assertEquals(WHITE, trace.pixel(75, DEFAULT_FIRST_LINE));
        assertEquals(LIGHT_BLUE, trace.pixel(76, DEFAULT_FIRST_LINE));
        assertEquals(LIGHT_BLUE, trace.pixel(91, DEFAULT_FIRST_LINE));
        assertEquals(WHITE, trace.pixel(92, DEFAULT_FIRST_LINE));
    }

    @Test
    void testbackLeavesTheLineAfterTheStoresUnchanged() {
        VicFrameTrace trace = runAlternating(VicFrameTrace.defaultScreen(), 15, COLOURS_B, COLOURS_A, STORES_PER_LINE);
        int line = LAST_STORE_LINE + 1;
        for (int x = WINDOW_START; x < WINDOW_END; x++) {
            assertEquals(WHITE, trace.pixel(x, line), "x=" + x);
        }
    }

    @Test
    void auxiliaryColourChangesLikeTheBackground() {
        VicTestHarness h = VicFrameTrace.defaultScreen();
        for (int i = 0; i < 0x200; i++) {
            h.poke(0x9600 + i, 0x08); // multicolour mode, foreground black
        }
        for (int i = 0; i < 8; i++) {
            h.poke(0x8000 + i, 0xFF); // screen code 0: every pixel pair is the auxiliary colour
        }
        // An even number of stores leaves each line on the starting value. Stores past c59 would
        // show outside the text window, where the auxiliary colour isn't used.
        int stores = 10;
        VicFrameTrace trace = runAlternating(h, 14, 0x20, 0x00, stores);
        int previous = BLACK;
        for (int store = 0; store < stores; store++) {
            int colour = ((store & 1) == 0) ? RED : BLACK;
            int x = colourChangeX(storeCycle(store));
            assertEquals(previous, trace.pixel(x - 1, DEFAULT_FIRST_LINE), "pixel before the store at c" + storeCycle(store));
            assertEquals(colour, trace.pixel(x, DEFAULT_FIRST_LINE), "first pixel of the store at c" + storeCycle(store));
            previous = colour;
        }
    }

    @Test
    void reverseBitChangesThreePixelsAfterTheCharacterGridPosition() {
        VicTestHarness h = VicFrameTrace.defaultScreen();
        for (int i = 0; i < 0x200; i++) {
            h.poke(0x9600 + i, 0x02); // hires, foreground red; character data is all zero
        }
        int stores = 10;
        VicFrameTrace trace = runAlternating(h, 15, COLOURS_A & ~0x08, COLOURS_A, stores);
        int previous = WHITE;
        for (int store = 0; store < stores; store++) {
            int colour = ((store & 1) == 0) ? RED : WHITE;
            int x = colourChangeX(storeCycle(store)) - 1 + 3;
            assertEquals(previous, trace.pixel(x - 1, DEFAULT_FIRST_LINE), "pixel before the store at c" + storeCycle(store));
            assertEquals(colour, trace.pixel(x, DEFAULT_FIRST_LINE), "first pixel of the store at c" + storeCycle(store));
            previous = colour;
        }
    }
}
