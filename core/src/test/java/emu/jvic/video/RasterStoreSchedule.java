package emu.jvic.video;

import emu.jvic.testutil.VicTestHarness;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * VIC register stores at exact raster positions, as a 6502 raster routine would make them, but
 * applied directly to a {@link VicTestHarness} with no CPU involved.
 *
 * <p>Positions use the notation of Andreas Matthies' "VIC 6561 - cycle exact emulation" notes:
 * "store cycle c of rasterline L" is the first cycle that holds the new value. In jvic, that is a
 * write made straight after the VIC has run with HC = c, which the VIC then sees at HC = c + 1
 * (hence all the comparisons against prevHorizontalCounter in Vic6561). Since the vertical
 * counter only increments at HC = 1, store cycle 0 of line L happens while the VIC is still on
 * line L - 1. Applying the store after {@code cycle()} returns is the same as a CPU store in that
 * cycle, because Machine runs the VIC before the CPU.
 */
class RasterStoreSchedule {

    /** Cycles per PAL raster line (HC = 0 to 70). */
    static final int PAL_CYCLES_PER_LINE = 71;

    /** Number of PAL raster lines (0 to 311). */
    static final int PAL_LINES = 312;

    private final Map<Integer, List<int[]>> stores = new HashMap<>();

    /**
     * Schedules a store of the given value to the given VIC register.
     *
     * @param line  The PAL raster line (0-311) that the store cycle belongs to.
     * @param cycle The store cycle within that line (0-70).
     * @param reg   The VIC register number (0-15).
     * @param value The value to store.
     */
    RasterStoreSchedule store(int line, int cycle, int reg, int value) {
        int vc = (cycle == 0) ? ((line + PAL_LINES - 1) % PAL_LINES) : line;
        stores.computeIfAbsent(key(vc, cycle), k -> new ArrayList<>()).add(new int[] { reg, value });
        return this;
    }

    /** Schedules the same store on every line from firstLine to lastLine inclusive. */
    RasterStoreSchedule storeOnLines(int firstLine, int lastLine, int cycle, int reg, int value) {
        for (int line = firstLine; line <= lastLine; line++) {
            store(line, cycle, reg, value);
        }
        return this;
    }

    /**
     * Schedules a value that is held for exactly one cycle: stored in the given store cycle, then
     * the restore value stored in the very next cycle.
     */
    RasterStoreSchedule pulse(int line, int cycle, int reg, int value, int restoreValue) {
        store(line, cycle, reg, value);
        if (cycle == PAL_CYCLES_PER_LINE - 1) {
            return store((line + 1) % PAL_LINES, 0, reg, restoreValue);
        }
        return store(line, cycle + 1, reg, restoreValue);
    }

    /** Applies any stores due in the cycle that the VIC has just run. */
    void applyAfterCycle(VicTestHarness h) {
        List<int[]> due = stores.get(key(h.vic.verticalCounter, h.vic.prevHorizontalCounter));
        if (due != null) {
            for (int[] store : due) {
                h.writeRegister(store[0], store[1]);
            }
        }
    }

    private static int key(int vc, int hc) {
        return (vc << 7) | hc;
    }
}
