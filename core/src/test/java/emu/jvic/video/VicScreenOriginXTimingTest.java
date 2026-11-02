package emu.jvic.video;

import emu.jvic.testutil.VicTestHarness;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static emu.jvic.video.VicFrameTrace.DEFAULT_FIRST_LINE;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * When a raster line starts, relative to stores to the screen origin X register ($9000). Covers
 * the same behaviour as test36864 from the VICE VIC test suite, which toggles bit 0 of XPOS at
 * store cycle 19 and restores it at cycle 23, on raster lines 76-91.
 *
 * <p>Rule (Matthies): a raster line starts in cycle n if n is stored to XPOS in cycle n or earlier
 * and isn't changed before cycle n+1. A line that doesn't start doesn't advance the cell depth
 * counter, so if none of lines 76-91 start, the whole screen starts 16 lines (two rows) lower.
 */
public class VicScreenOriginXTimingTest {

    private static final int LAST_STORE_LINE = 91;
    private static final int LINES_AFTER = LAST_STORE_LINE + 1;

    /** HC of the first cell fetch relative to the cycle in which the line starts. */
    private static final int FIRST_FETCH_DELAY = 5;

    private static VicFrameTrace run(int xpos, int changeCycle, int restoreCycle) {
        VicTestHarness h = VicFrameTrace.screen(xpos, VicFrameTrace.DEFAULT_YPOS,
                VicFrameTrace.DEFAULT_COLUMNS_REG, VicFrameTrace.DEFAULT_ROWS_REG);
        RasterStoreSchedule stores = new RasterStoreSchedule()
                .storeOnLines(DEFAULT_FIRST_LINE, LAST_STORE_LINE, changeCycle, 0, xpos ^ 1)
                .storeOnLines(DEFAULT_FIRST_LINE, LAST_STORE_LINE, restoreCycle, 0, xpos);
        return VicFrameTrace.run(h, stores);
    }

    /** The cycle the line starts in, by Matthies' rule, or -1 if it doesn't start at all. */
    private static int expectedStartCycle(int xpos, int changeCycle, int restoreCycle) {
        for (int n = 1; n < 70; n++) {
            // A value stored in store cycle c is seen by the VIC from HC = c + 1.
            int seenAtNextCycle = ((n + 1 > changeCycle) && (n + 1 <= restoreCycle)) ? (xpos ^ 1) : xpos;
            if (seenAtNextCycle == n) {
                return n;
            }
        }
        return -1;
    }

    private static void assertMatchesRule(int xpos, int changeCycle, int restoreCycle) {
        VicFrameTrace trace = run(xpos, changeCycle, restoreCycle);
        String context = "xpos=" + xpos + " stores at c" + changeCycle + "/c" + restoreCycle;
        int start = expectedStartCycle(xpos, changeCycle, restoreCycle);
        if (start < 0) {
            assertEquals(LINES_AFTER, trace.firstLine(), context + ": lines 76-91 never start");
        } else {
            assertEquals(DEFAULT_FIRST_LINE, trace.firstLine(), context);
            for (int line = DEFAULT_FIRST_LINE; line <= LAST_STORE_LINE; line++) {
                assertEquals(start + FIRST_FETCH_DELAY, trace.line(line).firstFetchHc, context + " line " + line);
            }
        }
        assertEquals(xpos + FIRST_FETCH_DELAY, trace.line(LINES_AFTER).firstFetchHc, context + " line 92");
    }

    @ParameterizedTest
    @ValueSource(ints = { 12, 13, 14, 15, 16, 17, 18, 24 })
    void test36864ValuesThatAreUnaffected(int xpos) {
        VicFrameTrace trace = run(xpos, 19, 23);
        assertEquals(DEFAULT_FIRST_LINE, trace.firstLine());
        assertEquals(184, trace.lineCount());
        for (int line = DEFAULT_FIRST_LINE; line <= LINES_AFTER; line++) {
            assertEquals(xpos + FIRST_FETCH_DELAY, trace.line(line).firstFetchHc, "line " + line);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = { 25, 26 })
    void test36864ValuesWhereTheTextWindowReachesTheLineEnd(int xpos) {
        // The window runs into the end of the line here, which is VMC carry behaviour covered by
        // Vic6561VmcCarryTest, so only the start of the lines is checked.
        VicFrameTrace trace = run(xpos, 19, 23);
        assertEquals(DEFAULT_FIRST_LINE, trace.firstLine());
        assertEquals(xpos + FIRST_FETCH_DELAY, trace.line(DEFAULT_FIRST_LINE).firstFetchHc);
        assertEquals(xpos + FIRST_FETCH_DELAY, trace.line(LINES_AFTER).firstFetchHc);
    }

    @ParameterizedTest
    @ValueSource(ints = { 19, 22 })
    void test36864ScreenStartsTwoRowsLowerWhenNoLineCanStart(int xpos) {
        VicFrameTrace trace = run(xpos, 19, 23);
        assertEquals(LINES_AFTER, trace.firstLine());
        assertEquals(184, trace.lineCount());
        assertEquals(xpos + FIRST_FETCH_DELAY, trace.line(LINES_AFTER).firstFetchHc);
    }

    @Test
    void test36864Value20StartsHalfAColumnLater() {
        VicFrameTrace trace = run(20, 19, 23);
        for (int line = DEFAULT_FIRST_LINE; line <= LAST_STORE_LINE; line++) {
            assertEquals(26, trace.line(line).firstFetchHc, "line " + line);
        }
        assertEquals(25, trace.line(LINES_AFTER).firstFetchHc);
    }

    @Test
    void test36864Value21StartsHalfAColumnEarlier() {
        VicFrameTrace trace = run(21, 19, 23);
        for (int line = DEFAULT_FIRST_LINE; line <= LAST_STORE_LINE; line++) {
            assertEquals(25, trace.line(line).firstFetchHc, "line " + line);
        }
        assertEquals(26, trace.line(LINES_AFTER).firstFetchHc);
    }

    @Test
    void test36864Value23StartsHalfAColumnEarlier() {
        VicFrameTrace trace = run(23, 19, 23);
        for (int line = DEFAULT_FIRST_LINE; line <= LAST_STORE_LINE; line++) {
            assertEquals(27, trace.line(line).firstFetchHc, "line " + line);
        }
        assertEquals(28, trace.line(LINES_AFTER).firstFetchHc);
    }

    @Test
    void test36864StoresMatchTheRule() {
        for (int xpos = 12; xpos <= 24; xpos++) {
            assertMatchesRule(xpos, 19, 23);
        }
    }

    @Test
    void fourCycleChangesAtEveryStoreCycleMatchTheRule() {
        for (int xpos = 12; xpos <= 24; xpos++) {
            for (int changeCycle = 10; changeCycle <= 30; changeCycle++) {
                assertMatchesRule(xpos, changeCycle, changeCycle + 4);
            }
        }
    }
}
