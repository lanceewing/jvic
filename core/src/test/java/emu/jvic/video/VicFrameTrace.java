package emu.jvic.video;

import emu.jvic.MachineType;
import emu.jvic.testutil.VicTestHarness;

import java.util.ArrayList;
import java.util.List;

/**
 * Records what the PAL VIC did during one frame while a {@link RasterStoreSchedule} is applied:
 * per matrix line, when the first cell was fetched and what each cell fetched, plus the rendered
 * pixels. Used by the tests that cover the same behaviour as the VICE VIC test suite (see
 * VIC_TEST_SUITE_PLAN.md in the project root).
 *
 * <p>The schedule is applied for a whole frame before the traced one, and during it. This matters
 * for stores on line 0, which is the last line of a jvic frame: they only affect the frame after.
 */
class VicFrameTrace {

    /** Standard unexpanded PAL screen: XPOS 12, YPOS 38, 22 columns, 23 rows of height 8. */
    static final int DEFAULT_XPOS = 12;
    static final int DEFAULT_YPOS = 38;
    static final int DEFAULT_COLUMNS_REG = 0x96;  // 22 columns, screen memory bit 9 set
    static final int DEFAULT_ROWS_REG = 0x2E;     // 23 rows, height 8
    static final int DEFAULT_MEMORY_REG = 0xF0;   // screen at $1E00 (with reg 2 bit 7), chars at $8000
    static final int DEFAULT_COLOUR_REG = 0x1B;   // background white, non-reverse, border cyan

    /** First raster line of the matrix for the default YPOS. */
    static final int DEFAULT_FIRST_LINE = 76;

    private static final int PAL_WIDTH = MachineType.PAL.getTotalScreenWidth();

    /** What the VIC did on one matrix line. */
    static class MatrixLine {
        final int line;
        int firstFetchHc = -1;
        int startVmc = -1;
        final List<Integer> screenCodes = new ArrayList<>();
        final List<Integer> charData = new ArrayList<>();
        final List<Integer> charAddresses = new ArrayList<>();

        MatrixLine(int line) {
            this.line = line;
        }

        int fetches() {
            return screenCodes.size();
        }
    }

    final List<MatrixLine> lines = new ArrayList<>();
    final int[] pixels;

    private VicFrameTrace(List<MatrixLine> lines, int[] pixels) {
        this.lines.addAll(lines);
        this.pixels = pixels;
    }

    /** Creates a PAL harness set up with the standard unexpanded screen. */
    static VicTestHarness defaultScreen() {
        return screen(DEFAULT_XPOS, DEFAULT_YPOS, DEFAULT_COLUMNS_REG, DEFAULT_ROWS_REG);
    }

    static VicTestHarness screen(int xpos, int ypos, int columnsReg, int rowsReg) {
        VicTestHarness h = new VicTestHarness(MachineType.PAL);
        h.writeRegister(0, xpos);
        h.writeRegister(1, ypos);
        h.writeRegister(2, columnsReg);
        h.writeRegister(3, rowsReg);
        h.writeRegister(5, DEFAULT_MEMORY_REG);
        h.writeRegister(15, DEFAULT_COLOUR_REG);
        return h;
    }

    /** Runs the harness from reset and traces the second full frame with the stores applied. */
    static VicFrameTrace run(VicTestHarness h, RasterStoreSchedule stores) {
        h.runToEndOfFrame(); // discard the short startup fragment
        runFrame(h, stores, null);
        List<MatrixLine> traced = new ArrayList<>();
        runFrame(h, stores, traced);
        int[] pixels = h.pixelData.pixels.clone();
        return new VicFrameTrace(traced, pixels);
    }

    private static void runFrame(VicTestHarness h, RasterStoreSchedule stores, List<MatrixLine> out) {
        Vic vic = h.vic;
        MatrixLine current = null;
        boolean frameDone = false;
        while (!frameDone) {
            int hc = vic.horizontalCounter;
            int vc = vic.verticalCounter;
            // Cell fetches only happen from HC=4 to HC=69 on lines outside vertical blanking.
            boolean fetchCycle = (hc >= 4) && (hc < 70) && ((vc == 0) || (vc > 9));
            boolean screenCodeFetch = fetchCycle && (vic.fetchState == Vic.FETCH_SCREEN_CODE)
                    && (vic.horizontalCellCounter > 0);
            boolean charDataFetch = fetchCycle && (vic.fetchState == Vic.FETCH_CHAR_DATA);
            if ((out != null) && screenCodeFetch && ((current == null) || (current.line != vc))) {
                current = new MatrixLine(vc);
                current.firstFetchHc = hc;
                current.startVmc = vic.videoMatrixCounter;
                out.add(current);
            }

            frameDone = h.cycle();

            if ((out != null) && (current != null) && (current.line == vc)) {
                if (screenCodeFetch) {
                    current.screenCodes.add(vic.cellIndex);
                }
                if (charDataFetch) {
                    current.charData.add(vic.charDataLatch);
                    current.charAddresses.add(vic.charDataOffset);
                }
            }
            stores.applyAfterCycle(h);
        }
    }

    /** The given matrix line, or null if the matrix wasn't active on that line. */
    MatrixLine line(int line) {
        for (MatrixLine l : lines) {
            if (l.line == line) {
                return l;
            }
        }
        return null;
    }

    int lineCount() {
        return lines.size();
    }

    /** The first matrix line, or -1 if the matrix never opened. */
    int firstLine() {
        return lines.isEmpty() ? -1 : lines.get(0).line;
    }

    MatrixLine lastLine() {
        return lines.get(lines.size() - 1);
    }

    /** The rendered RGBA8888 colour at frame buffer column x of the given raster line. */
    int pixel(int x, int line) {
        int y = (line + RasterStoreSchedule.PAL_LINES - 1) % RasterStoreSchedule.PAL_LINES;
        return pixels[y * PAL_WIDTH + x];
    }

    /** Frame buffer column of the first pixel of the given text column. */
    static int columnX(int xpos, int column) {
        return (4 * xpos) + 27 + (8 * column);
    }

    /** Frame buffer column where a colour register store in the given store cycle first shows. */
    static int colourChangeX(int storeCycle) {
        return (4 * (storeCycle - 7)) + 28;
    }
}
