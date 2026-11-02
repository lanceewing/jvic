package emu.jvic.video;

import emu.jvic.testutil.VicTestHarness;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Changing the character height ($9003 bit 0) on the last line of a character cell. Covers the same
 * behaviour as testcharheigh-1 and -2 from the VICE VIC test suite, which toggle the height at store
 * cycle 20 of raster line 83 (the last line of row 0 when the height is 8) and restore it at cycle 0
 * (-1) or cycle 1 (-2) of line 84. Each program runs this with 8 rows of height 8, 8 rows of height
 * 16, and 23 rows of height 8.
 *
 * <p>Rules (Matthies):
 * <ul>
 * <li>A change of height immediately affects the character fetch, so on line 83 columns 0 and 1 are
 *     fetched with the old height and columns 2 onwards with the new one.</li>
 * <li>Whether the row ends is checked after cycle 0 of a raster line, against the height at that
 *     point: the cell depth counter must equal the height, or twice the height.</li>
 * <li>The character fetch uses the cell depth counter modulo the height.</li>
 * <li>Independently of that, when a character is completely displayed (fetched on the last line of
 *     its cell for the current height), the memory pointer for the next row advances to at least
 *     the column after it.</li>
 * </ul>
 *
 * <p>Expected values: the line counts and the row start of 2 are stated by Matthies; the other row
 * starts were worked out from VICE's screens for the same programs.
 */
public class VicCharHeightTimingTest {

    private static final int SCREEN_CODE = 0x41;

    private static final int HEIGHT_8_ROWS_8 = 0x10;
    private static final int HEIGHT_16_ROWS_8 = 0x11;
    private static final int HEIGHT_8_ROWS_23 = 0x2E;

    /**
     * Character addresses for the screen code on its last line with height 8 and height 16, and on
     * its first line with height 8 (the cell depth counter is masked to 3 bits).
     */
    private static final int LAST_LINE_HEIGHT_8 = (SCREEN_CODE << 3) + 7;
    private static final int LAST_LINE_OF_TOP_HALF_HEIGHT_16 = (SCREEN_CODE << 4) + 7;
    private static final int FIRST_LINE_HEIGHT_8 = SCREEN_CODE << 3;

    private static VicFrameTrace run(int rowsReg, int restoreCycle) {
        VicTestHarness h = VicFrameTrace.screen(VicFrameTrace.DEFAULT_XPOS, VicFrameTrace.DEFAULT_YPOS,
                VicFrameTrace.DEFAULT_COLUMNS_REG, rowsReg);
        for (int i = 0; i < 0x200; i++) {
            h.poke(0x1E00 + i, SCREEN_CODE);
        }
        RasterStoreSchedule stores = new RasterStoreSchedule()
                .store(83, 20, 3, rowsReg ^ 1)
                .store(84, restoreCycle, 3, rowsReg);
        return VicFrameTrace.run(h, stores);
    }

    /**
     * Arguments: program and screen, rows register, restore cycle, matrix lines, row start (VMC) on
     * line 84, row start on line 92, last row start, character address for column 0 on line 84.
     */
    static Stream<Arguments> passingCases() {
        return Stream.of(
                Arguments.of("-1 screens 1-2: 8 to 16 to 8", HEIGHT_8_ROWS_8, 0, 64, 22, 44, 154, FIRST_LINE_HEIGHT_8),
                Arguments.of("-1 screen 4: 23 rows, 8 to 16 to 8", HEIGHT_8_ROWS_23, 0, 184, 22, 44, 484, FIRST_LINE_HEIGHT_8),
                Arguments.of("-2 screen 3: 16 to 8 to 16", HEIGHT_16_ROWS_8, 1, 120, 22, 22, 154, SCREEN_CODE << 4));
    }

    /**
     * Cases where jvic currently differs from the rules and from VICE. See section 6 of
     * VIC_TEST_SUITE_PLAN.md in the project root.
     */
    static Stream<Arguments> knownFailingCases() {
        return Stream.of(
                Arguments.of("-1 screen 3: 16 to 8 to 16", HEIGHT_16_ROWS_8, 0, 128, 22, 44, 176, (SCREEN_CODE << 4) + 8),
                Arguments.of("-2 screens 1-2: 8 to 16 to 8", HEIGHT_8_ROWS_8, 1, 72, 2, 24, 156, FIRST_LINE_HEIGHT_8),
                Arguments.of("-2 screen 4: 23 rows, 8 to 16 to 8", HEIGHT_8_ROWS_23, 1, 192, 2, 24, 486, FIRST_LINE_HEIGHT_8));
    }

    private static void assertCase(int rowsReg, int restoreCycle, int lines, int rowStart84, int rowStart92,
            int lastRowStart, int line84Column0Address) {
        VicFrameTrace trace = run(rowsReg, restoreCycle);
        assertEquals(lines, trace.lineCount(), "matrix lines");

        // Line 83: the height change at c20 affects the character fetch from column 2.
        boolean startsHeight8 = (rowsReg & 1) == 0;
        int oldAddress = startsHeight8 ? LAST_LINE_HEIGHT_8 : LAST_LINE_OF_TOP_HALF_HEIGHT_16;
        int newAddress = startsHeight8 ? LAST_LINE_OF_TOP_HALF_HEIGHT_16 : LAST_LINE_HEIGHT_8;
        VicFrameTrace.MatrixLine line83 = trace.line(83);
        for (int column = 0; column < 22; column++) {
            int expected = (column < 2) ? oldAddress : newAddress;
            assertEquals(expected, (int) line83.charAddresses.get(column), "line 83 column " + column);
        }

        assertEquals(rowStart84, trace.line(84).startVmc, "row start on line 84");
        assertEquals(rowStart92, trace.line(92).startVmc, "row start on line 92");
        assertEquals(lastRowStart, trace.lastLine().startVmc, "last row start");
        assertEquals(line84Column0Address, (int) trace.line(84).charAddresses.get(0), "line 84 column 0 address");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("passingCases")
    void heightChangeOnTheLastCellLine(String name, int rowsReg, int restoreCycle, int lines, int rowStart84,
            int rowStart92, int lastRowStart, int line84Column0Address) {
        assertCase(rowsReg, restoreCycle, lines, rowStart84, rowStart92, lastRowStart, line84Column0Address);
    }

    @Test
    void heightChangeAffectsTheCharacterFetchFromTheNextColumnFetched() {
        // The character fetch for column X follows cycle XPOS + 2X + 4, so, as for the character
        // pointer, a height stored in cycle c is first used for column (c - XPOS - 4) div 2.
        for (int cycle = 14; cycle <= 30; cycle++) {
            VicTestHarness h = VicFrameTrace.defaultScreen();
            h.writeRegister(3, HEIGHT_8_ROWS_8);
            for (int i = 0; i < 0x200; i++) {
                h.poke(0x1E00 + i, SCREEN_CODE);
            }
            RasterStoreSchedule stores = new RasterStoreSchedule()
                    .store(83, cycle, 3, HEIGHT_16_ROWS_8)
                    .store(84, 0, 3, HEIGHT_8_ROWS_8);
            VicFrameTrace.MatrixLine line83 = VicFrameTrace.run(h, stores).line(83);
            int firstNewColumn = Math.max(0, Math.floorDiv(cycle - VicFrameTrace.DEFAULT_XPOS - 4, 2));
            for (int column = 0; column < 22; column++) {
                int expected = (column < firstNewColumn) ? LAST_LINE_HEIGHT_8 : LAST_LINE_OF_TOP_HALF_HEIGHT_16;
                assertEquals(expected, (int) line83.charAddresses.get(column), "store at c" + cycle + " column " + column);
            }
        }
    }

    @Disabled("Known jvic difference from the VIC chip: the next row start is only latched at HC=1, and the "
            + "character fetch doesn't mask the cell depth to the height. See section 6 of VIC_TEST_SUITE_PLAN.md.")
    @ParameterizedTest(name = "{0}")
    @MethodSource("knownFailingCases")
    void heightChangeOnTheLastCellLineKnownFailures(String name, int rowsReg, int restoreCycle, int lines,
            int rowStart84, int rowStart92, int lastRowStart, int line84Column0Address) {
        assertCase(rowsReg, restoreCycle, lines, rowStart84, rowStart92, lastRowStart, line84Column0Address);
    }
}
