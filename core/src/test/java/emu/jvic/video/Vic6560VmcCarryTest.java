package emu.jvic.video;

import emu.jvic.MachineType;
import emu.jvic.testutil.VicTestHarness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NTSC (6560) behaviour when the text window is still active at the end of the raster
 * line (screen origin X + 2 * columns >= 62, matching Tokra's measured NTSC onset values):
 * <ul>
 * <li>The Video Matrix Counter "stale nibble carry" effect.</li>
 * <li>For screen origin X = 0, the bogus one-cycle matrix enable pulse that makes the
 *     following line start one cycle (half a character) early.</li>
 * </ul>
 * See the vmc_carry_bug folder in the project root for the analysis, and the Python
 * version of the reference model that {@link Model} is a port of (extended with the sx=0
 * rule). Unlike the PAL test, there are no NTSC real-hardware captures to check against
 * yet, so this only checks against the reference model.
 */
public class Vic6560VmcCarryTest {

    private static final int ROWS = 23;
    private static final int LINES = ROWS * 8;

    /** Matrix counting cycles available from a normal start to the end of the line. */
    private static final int C = 62;

    /** HC at which the first screen code fetch of a line normally happens for sx=0. */
    private static final int SX0_FIRST_FETCH_HC = 5;

    private VicTestHarness h;

    @BeforeEach
    void setUp() {
        h = new VicTestHarness(MachineType.NTSC);
    }

    /** What jvic did on each matrix raster line of one stable frame. */
    private static class Frame {
        /** VMC just after it is reloaded from the latch at HC=2. */
        int[] lineStarts;
        /** HC of the first screen code fetch on the line. */
        int[] firstFetchHc;
    }

    private Frame runFrame(int screenOriginX, int columns) {
        h.writeRegister(0, screenOriginX);
        h.writeRegister(1, 25);
        h.writeRegister(2, 0x80 | columns);
        h.writeRegister(3, ROWS << 1);
        h.writeRegister(5, 0xF0);
        h.runToEndOfFrame();
        h.runToEndOfFrame();

        List<Integer> starts = new ArrayList<>();
        List<Integer> fetches = new ArrayList<>();
        boolean frameDone = false;
        while (!frameDone) {
            if ((h.vic.fetchState == Vic.FETCH_SCREEN_CODE) && !fetches.isEmpty()
                    && (fetches.get(fetches.size() - 1) < 0)) {
                fetches.set(fetches.size() - 1, h.vic.horizontalCounter);
            }
            frameDone = h.cycle();
            if ((h.vic.horizontalCounter == 3) && (h.vic.fetchState != Vic.FETCH_OUTSIDE_MATRIX)) {
                if (starts.isEmpty() && (h.vic.cellDepthCounter == 1)) {
                    // On the 6560 the screen origin Y match happens at HC=29, when VC changes,
                    // so for screen origin X >= 28 the first matrix line opens part way through
                    // the line, after the HC=3 sample point. Its start address is the latch
                    // value just after vsync, i.e. 0. Its fetch timing isn't checked.
                    starts.add(0);
                    fetches.add(0);
                }
                starts.add(h.vic.videoMatrixCounter);
                fetches.add(-1);
            }
        }

        if ((screenOriginX == 0) && (fetches.get(0) < 0)) {
            // With sx=0, X can't match on the line where Y first matches, so the matrix
            // starts one line lower. That first line has no fetches and isn't a matrix line.
            starts.remove(0);
            fetches.remove(0);
        }

        Frame frame = new Frame();
        frame.lineStarts = starts.stream().mapToInt(Integer::intValue).toArray();
        frame.firstFetchHc = fetches.stream().mapToInt(Integer::intValue).toArray();
        return frame;
    }

    /**
     * The first LINES matrix lines. With sx=0 the matrix is kept open for an extra line
     * after the last row (see the "screen_origin_x > 0" check at HC=1 in Vic6560), which
     * isn't part of what is being tested here.
     */
    private int[] firstLines(int[] values) {
        assertTrue(values.length >= LINES, "only " + values.length + " matrix lines");
        return Arrays.copyOf(values, LINES);
    }

    /**
     * Port of the reference model in vmc_carry_bug/sim/vmc_carry_model.py, extended with
     * the sx=0 rule: if the previous line was still counting in its final cycle, the
     * line starts one cycle early, so it has one more counting cycle available.
     */
    private static class Model {
        final int[] lineStarts = new int[LINES];
        final boolean[] early = new boolean[LINES];

        Model(int screenOriginX, int columns) {
            int full = columns * 2;
            int vb = 0;
            boolean c4 = false;
            boolean c8 = false;
            boolean hitEnd = false;
            for (int line = 0; line < LINES; line++) {
                early[line] = (screenOriginX == 0) && hitEnd;
                int avail = C - screenOriginX + (early[line] ? 1 : 0);
                int incr = (avail >= full) ? full : Math.max(avail, 0);
                hitEnd = (avail >= full) ? (avail == full) : (avail > 0);

                int vm = vb;
                if (c8) {
                    vm = (vm & 0x0FF) | ((vm + 0x100) & 0xF00);
                }
                if (c4) {
                    vm = (vm & 0xF0F) | ((vm + 0x010) & 0x0F0);
                }
                lineStarts[line] = (vm + 1) >> 1;
                vm = (vm + incr) & 0xFFF;
                if ((line & 7) == 7) {
                    vb = vm;
                }
                c4 = hitEnd && ((vm & 0x00F) == 0);
                c8 = hitEnd && ((vm & 0x0FF) == 0);
            }
        }
    }

    @Test
    void normalScreenIsUnaffected() {
        int[] starts = runFrame(5, 22).lineStarts;
        assertEquals(LINES, starts.length);
        for (int line = 0; line < LINES; line++) {
            assertEquals((line >> 3) * 22, starts[line], "line " + line);
        }
    }



    @Test
    void sx0Columns30HasNoEarlyLines() {
        int[] fetchHc = firstLines(runFrame(0, 30).firstFetchHc);
        for (int line = 0; line < LINES; line++) {
            assertEquals(SX0_FIRST_FETCH_HC, fetchHc[line], "line " + line);
        }
    }

    @Test
    void sx0Columns31AlternatesNormalAndEarlyLines() {
        // The NTSC equivalent of Example_2.png: alternate lines one cycle left.
        int[] fetchHc = firstLines(runFrame(0, 31).firstFetchHc);
        for (int line = 0; line < LINES; line++) {
            int expected = SX0_FIRST_FETCH_HC - (((line & 1) == 1) ? 1 : 0);
            assertEquals(expected, fetchHc[line], "line " + line);
        }
    }

    @Test
    void sx0Columns32HasEveryLineEarlyAfterTheFirst() {
        int[] fetchHc = firstLines(runFrame(0, 32).firstFetchHc);
        assertEquals(SX0_FIRST_FETCH_HC, fetchHc[0], "line 0");
        for (int line = 1; line < LINES; line++) {
            assertEquals(SX0_FIRST_FETCH_HC - 1, fetchHc[line], "line " + line);
        }
    }

    @Test
    void matchesReferenceModelAcrossOriginAndWidth() {
        for (int columns = 1; columns <= 31; columns++) {
            for (int sx = 1; sx <= C; sx++) {
                Model model = new Model(sx, columns);
                Frame frame = runFrame(sx, columns);
                assertArrayEquals(model.lineStarts, frame.lineStarts, "sx=" + sx + " columns=" + columns);
            }
        }
    }

    @Test
    void sx0MatchesReferenceModelAcrossWidth() {
        for (int columns = 1; columns <= 63; columns++) {
            Model model = new Model(0, columns);
            Frame frame = runFrame(0, columns);
            int[] fetchHc = firstLines(frame.firstFetchHc);
            assertArrayEquals(model.lineStarts, firstLines(frame.lineStarts), "columns=" + columns);
            for (int line = 0; line < LINES; line++) {
                int expected = SX0_FIRST_FETCH_HC - (model.early[line] ? 1 : 0);
                assertEquals(expected, fetchHc[line], "columns=" + columns + " line " + line);
            }
        }
    }
}
