package emu.jvic.video;

import emu.jvic.testutil.VicTestHarness;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * When the screen opens, relative to stores to the screen origin Y register ($9001). Covers the
 * same behaviour as test36865-1, -2 and -3 from the VICE VIC test suite, and adds the early edge
 * of the matching window, which none of those programs prove.
 *
 * <p>Rule (Matthies): the screen opens if, from cycle 1 of raster line 2n to cycle 0 of raster line
 * 2n+2, there is at least one cycle in which n is stored in YPOS. It then starts at the first raster
 * line that starts after that cycle. Raster lines start in cycle XPOS (see
 * {@link VicScreenOriginXTimingTest}), so a match by that cycle still opens the same line.
 */
public class VicScreenOriginYTimingTest {

    /** A YPOS value that never matches: it would need raster line 400. */
    private static final int NEVER_MATCHES = 200;

    private static final int PULSED_YPOS = 39;
    private static final int WINDOW_FIRST_LINE = 2 * PULSED_YPOS;

    private static VicFrameTrace run(int ypos, RasterStoreSchedule stores) {
        VicTestHarness h = VicFrameTrace.screen(VicFrameTrace.DEFAULT_XPOS, ypos,
                VicFrameTrace.DEFAULT_COLUMNS_REG, VicFrameTrace.DEFAULT_ROWS_REG);
        return VicFrameTrace.run(h, stores);
    }

    /** YPOS bit 0 toggled at the first store position and restored at the second. */
    private static VicFrameTrace runToggle(int ypos, int changeLine, int changeCycle, int restoreLine, int restoreCycle) {
        RasterStoreSchedule stores = new RasterStoreSchedule()
                .store(changeLine, changeCycle, 1, ypos ^ 1)
                .store(restoreLine, restoreCycle, 1, ypos);
        return run(ypos, stores);
    }

    @ParameterizedTest(name = "YPOS {0}")
    @CsvSource({ "38, 76", "39, -1" })
    void test36865_1ChangeAtCycle1OfLine78IsTooLate(int ypos, int expectedFirstLine) {
        VicFrameTrace trace = runToggle(ypos, 78, 1, 80, 1);
        assertEquals(expectedFirstLine, trace.firstLine());
    }

    @ParameterizedTest(name = "YPOS {0}")
    @CsvSource({ "38, 76", "39, 78", "40, 80" })
    void test36865_2ChangeAtCycle0OfLine78IsTheLastChance(int ypos, int expectedFirstLine) {
        VicFrameTrace trace = runToggle(ypos, 78, 0, 80, 0);
        assertEquals(expectedFirstLine, trace.firstLine());
        assertEquals(184, trace.lineCount());
    }

    @ParameterizedTest(name = "YPOS {0}")
    @CsvSource({ "38, 76", "39, 80", "40, 80" })
    void test36865_3OneCycleMatchAtTheEndOfTheWindow(int ypos, int expectedFirstLine) {
        VicFrameTrace trace = runToggle(ypos, 78, 1, 80, 0);
        assertEquals(expectedFirstLine, trace.firstLine());
        assertEquals(184, trace.lineCount());
    }

    @ParameterizedTest(name = "pulse at c{1} of line {0}")
    @CsvSource({ "78, 0, -1", "78, 1, 78", "79, 69, 80", "80, 0, 80", "80, 1, -1" })
    void oneCyclePulseAtTheWindowEdges(int line, int cycle, int expectedFirstLine) {
        VicFrameTrace trace = run(NEVER_MATCHES,
                new RasterStoreSchedule().pulse(line, cycle, 1, PULSED_YPOS, NEVER_MATCHES));
        assertEquals(expectedFirstLine, trace.firstLine());
    }

    @Test
    void oneCyclePulseInEveryCycleAroundTheWindow() {
        for (int line = WINDOW_FIRST_LINE - 1; line <= WINDOW_FIRST_LINE + 3; line++) {
            for (int cycle = 0; cycle < RasterStoreSchedule.PAL_CYCLES_PER_LINE; cycle++) {
                boolean afterStart = (line > WINDOW_FIRST_LINE) || ((line == WINDOW_FIRST_LINE) && (cycle >= 1));
                boolean beforeEnd = (line < WINDOW_FIRST_LINE + 2) || ((line == WINDOW_FIRST_LINE + 2) && (cycle == 0));
                int expectedFirstLine = -1;
                if (afterStart && beforeEnd) {
                    expectedFirstLine = (cycle <= VicFrameTrace.DEFAULT_XPOS) ? line : line + 1;
                }
                VicFrameTrace trace = run(NEVER_MATCHES,
                        new RasterStoreSchedule().pulse(line, cycle, 1, PULSED_YPOS, NEVER_MATCHES));
                assertEquals(expectedFirstLine, trace.firstLine(), "pulse at c" + cycle + " of line " + line);
            }
        }
    }
}
