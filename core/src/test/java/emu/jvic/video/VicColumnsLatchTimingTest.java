package emu.jvic.video;

import emu.jvic.testutil.VicTestHarness;
import org.junit.jupiter.api.Test;

import static emu.jvic.video.VicFrameTrace.DEFAULT_FIRST_LINE;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * When the number of columns ($9002 bits 0-6) is read for a raster line. Covers the same behaviour
 * as test36866-1 and -2 from the VICE VIC test suite, which reduce the columns from 22 to 18 at
 * store cycle 19 of lines 76-91, and restore them at cycle 1 (in time) or cycle 2 (too late) of the
 * following line.
 *
 * <p>Rule (Matthies): a raster line has n columns if n is stored to $9002 in cycle 1 of that line
 * or earlier, and isn't changed before cycle 2.
 */
public class VicColumnsLatchTimingTest {

    private static final int COLUMNS_18 = VicFrameTrace.DEFAULT_COLUMNS_REG ^ 0x04;

    private static VicFrameTrace runTest36866(int restoreCycle) {
        VicTestHarness h = VicFrameTrace.defaultScreen();
        RasterStoreSchedule stores = new RasterStoreSchedule()
                .storeOnLines(DEFAULT_FIRST_LINE, 91, 19, 2, COLUMNS_18)
                .storeOnLines(DEFAULT_FIRST_LINE + 1, 92, restoreCycle, 2, VicFrameTrace.DEFAULT_COLUMNS_REG);
        return VicFrameTrace.run(h, stores);
    }

    @Test
    void test36866_1RestoreAtCycle1IsInTime() {
        VicFrameTrace trace = runTest36866(1);
        assertEquals(184, trace.lineCount());
        for (VicFrameTrace.MatrixLine line : trace.lines) {
            assertEquals(22, line.fetches(), "line " + line.line);
        }
        assertEquals(0, trace.line(76).startVmc);
        assertEquals(22, trace.line(84).startVmc);
        assertEquals(44, trace.line(92).startVmc);
        assertEquals(484, trace.lastLine().startVmc);
    }

    @Test
    void test36866_2RestoreAtCycle2IsTooLate() {
        VicFrameTrace trace = runTest36866(2);
        assertEquals(184, trace.lineCount());
        assertEquals(22, trace.line(76).fetches());
        for (int line = 77; line <= 92; line++) {
            assertEquals(18, trace.line(line).fetches(), "line " + line);
        }
        assertEquals(22, trace.line(93).fetches());
        // Rows 0 and 1 are 18 cells long, so every later row starts 8 cells earlier.
        assertEquals(0, trace.line(76).startVmc);
        assertEquals(18, trace.line(84).startVmc);
        assertEquals(36, trace.line(92).startVmc);
        assertEquals(476, trace.lastLine().startVmc);
    }

    @Test
    void storeInEachCycleAtTheStartOfALine() {
        for (int cycle = 0; cycle <= 10; cycle++) {
            VicTestHarness h = VicFrameTrace.defaultScreen();
            RasterStoreSchedule stores = new RasterStoreSchedule()
                    .store(80, cycle, 2, COLUMNS_18)
                    .store(80, 40, 2, VicFrameTrace.DEFAULT_COLUMNS_REG);
            VicFrameTrace trace = VicFrameTrace.run(h, stores);
            assertEquals((cycle <= 1) ? 18 : 22, trace.line(80).fetches(), "store at c" + cycle);
            assertEquals(22, trace.line(79).fetches(), "store at c" + cycle + ", line 79");
            assertEquals(22, trace.line(81).fetches(), "store at c" + cycle + ", line 81");
        }
    }
}
