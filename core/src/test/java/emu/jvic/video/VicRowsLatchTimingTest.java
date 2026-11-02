package emu.jvic.video;

import emu.jvic.testutil.VicTestHarness;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * When the number of rows ($9003 bits 1-6) is read for a frame. Covers the same behaviour as
 * test36867-1 and -2 from the VICE VIC test suite, which reduce the rows from 23 to 7 at store
 * cycle 3 (too late) or cycle 2 (in time) of raster line 0, and restore them 4 cycles later.
 *
 * <p>Rule (Matthies): the number of rows is determined after cycle 2 of raster line 0. Line 0 is the
 * last line of a jvic frame, so the effect shows in the frame after the store.
 */
public class VicRowsLatchTimingTest {

    private static final int ROWS_7 = VicFrameTrace.DEFAULT_ROWS_REG ^ 0x20;

    private static VicFrameTrace run(int changeCycle) {
        VicTestHarness h = VicFrameTrace.defaultScreen();
        RasterStoreSchedule stores = new RasterStoreSchedule()
                .store(0, changeCycle, 3, ROWS_7)
                .store(0, changeCycle + 4, 3, VicFrameTrace.DEFAULT_ROWS_REG);
        return VicFrameTrace.run(h, stores);
    }

    @Test
    void test36867_1StoreAtCycle3IsTooLate() {
        assertEquals(23 * 8, run(3).lineCount());
    }

    @Test
    void test36867_2StoreAtCycle2IsInTime() {
        assertEquals(7 * 8, run(2).lineCount());
    }

    @Test
    void storeInEachCycleAtTheStartOfLine0() {
        for (int cycle = 0; cycle <= 8; cycle++) {
            assertEquals(((cycle <= 2) ? 7 : 23) * 8, run(cycle).lineCount(), "store at c" + cycle);
        }
    }
}
