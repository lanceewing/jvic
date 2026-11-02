package emu.jvic.video;

import emu.jvic.testutil.VicTestHarness;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static emu.jvic.video.VicFrameTrace.DEFAULT_FIRST_LINE;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * When stores to the screen and character memory pointers ($9005) take effect within a raster
 * line. Covers the same behaviour as testmemfetch-1 (screen pointer) and testmemfetch-2 (character
 * pointer) from the VICE VIC test suite, which switch the pointer at store cycle 19 and back at
 * cycle 23, on lines 76-91, for a range of XPOS values.
 *
 * <p>Rule (Matthies): a store to the screen pointer in cycle c is first used for column
 * (c - XPOS - 3) div 2, and a store to the character pointer for column (c - XPOS - 4) div 2.
 *
 * <p>The screen memory and character memory at both pointer values are filled with distinct known
 * values, so each column shows which pointer its fetch used.
 */
public class VicMemoryFetchTimingTest {

    private static final int LAST_STORE_LINE = 91;

    /** $9005 with screen memory at $1A00 instead of $1E00 ($9002 bit 7 stays set). */
    private static final int ALT_SCREEN_POINTER = 0xE0;

    /** $9005 with character memory at $8400 instead of $8000. */
    private static final int ALT_CHAR_POINTER = 0xF1;

    private static final int NORMAL_SCREEN_CODE = 1;
    private static final int ALT_SCREEN_CODE = 2;
    private static final int NORMAL_CHAR_DATA = 0x00;
    private static final int ALT_CHAR_DATA = 0xFF;

    private static VicFrameTrace run(int xpos, int altMemoryReg, int changeCycle, int restoreCycle) {
        VicTestHarness h = VicFrameTrace.screen(xpos, VicFrameTrace.DEFAULT_YPOS,
                VicFrameTrace.DEFAULT_COLUMNS_REG, VicFrameTrace.DEFAULT_ROWS_REG);
        for (int i = 0; i < 0x200; i++) {
            h.poke(0x1E00 + i, NORMAL_SCREEN_CODE);
            h.poke(0x1A00 + i, ALT_SCREEN_CODE);
        }
        for (int i = 0; i < 0x400; i++) {
            h.poke(0x8000 + i, NORMAL_CHAR_DATA);
            h.poke(0x8400 + i, ALT_CHAR_DATA);
        }
        RasterStoreSchedule stores = new RasterStoreSchedule()
                .storeOnLines(DEFAULT_FIRST_LINE, LAST_STORE_LINE, changeCycle, 5, altMemoryReg)
                .storeOnLines(DEFAULT_FIRST_LINE, LAST_STORE_LINE, restoreCycle, 5, VicFrameTrace.DEFAULT_MEMORY_REG);
        return VicFrameTrace.run(h, stores);
    }

    /** First column whose fetch uses a pointer value stored in the given cycle. */
    private static int firstColumnUsing(int storeCycle, int xpos, int delay) {
        return Math.max(0, Math.floorDiv(storeCycle - xpos - delay, 2));
    }

    private static void assertColumns(VicFrameTrace trace, boolean screenPointer, int firstAffected,
            int lastAffected, String context) {
        for (int line = DEFAULT_FIRST_LINE; line <= LAST_STORE_LINE + 1; line++) {
            VicFrameTrace.MatrixLine matrixLine = trace.line(line);
            for (int column = 0; column < 22; column++) {
                boolean affected = (line <= LAST_STORE_LINE) && (column >= firstAffected) && (column <= lastAffected);
                int expected;
                int actual;
                if (screenPointer) {
                    expected = affected ? ALT_SCREEN_CODE : NORMAL_SCREEN_CODE;
                    actual = matrixLine.screenCodes.get(column);
                } else {
                    expected = affected ? ALT_CHAR_DATA : NORMAL_CHAR_DATA;
                    actual = matrixLine.charData.get(column);
                }
                assertEquals(expected, actual, context + " line " + line + " column " + column);
            }
        }
    }

    @ParameterizedTest(name = "XPOS {0}")
    @ValueSource(ints = { 16, 15, 14, 13, 12 })
    void testmemfetch1ScreenPointer(int xpos) {
        VicFrameTrace trace = run(xpos, ALT_SCREEN_POINTER, 19, 23);
        // The program's expected columns: 0-1 for XPOS 16-15, 1-2 for 14-13, 2-3 for 12.
        int first = (xpos >= 15) ? 0 : (xpos >= 13) ? 1 : 2;
        assertColumns(trace, true, first, first + 1, "XPOS " + xpos);
    }

    @ParameterizedTest(name = "XPOS {0}")
    @ValueSource(ints = { 15, 14, 13, 12, 11 })
    void testmemfetch2CharacterPointer(int xpos) {
        VicFrameTrace trace = run(xpos, ALT_CHAR_POINTER, 19, 23);
        // The program's expected columns: 0-1 for XPOS 15-14, 1-2 for 13-12, 2-3 for 11.
        int first = (xpos >= 14) ? 0 : (xpos >= 12) ? 1 : 2;
        assertColumns(trace, false, first, first + 1, "XPOS " + xpos);
    }

    @Test
    void screenPointerFormulaAtEveryStoreCycle() {
        for (int xpos = 11; xpos <= 16; xpos++) {
            for (int changeCycle = 14; changeCycle <= 30; changeCycle++) {
                VicFrameTrace trace = run(xpos, ALT_SCREEN_POINTER, changeCycle, changeCycle + 4);
                assertColumns(trace, true, firstColumnUsing(changeCycle, xpos, 3),
                        firstColumnUsing(changeCycle + 4, xpos, 3) - 1, "XPOS " + xpos + " store at c" + changeCycle);
            }
        }
    }

    @Test
    void characterPointerFormulaAtEveryStoreCycle() {
        for (int xpos = 11; xpos <= 16; xpos++) {
            for (int changeCycle = 14; changeCycle <= 30; changeCycle++) {
                VicFrameTrace trace = run(xpos, ALT_CHAR_POINTER, changeCycle, changeCycle + 4);
                assertColumns(trace, false, firstColumnUsing(changeCycle, xpos, 4),
                        firstColumnUsing(changeCycle + 4, xpos, 4) - 1, "XPOS " + xpos + " store at c" + changeCycle);
            }
        }
    }
}
