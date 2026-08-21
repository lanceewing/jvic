package emu.jvic.video;

import emu.jvic.MachineType;
import emu.jvic.testutil.VicTestHarness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pixel-level rendering of a single known character cell, in hi-res (normal and
 * reverse) and multicolour modes. Verified at the black-box level (actual output
 * pixel colours), rather than trying to unit-test the internal cycle-by-cycle
 * fetch state machine (see the class comment on {@link Vic6560FrameTimingTest} and
 * the plan this suite followed - the internals are a from-scratch reverse
 * engineering with no independent public spec at that granularity).
 *
 * <p>Common setup: register 5 = $F0 places screen memory at VIC address $3C00
 * (CPU $1C00) and character memory at VIC address $0000 (CPU $8000); 1
 * column/row; screen origin X = 4 (empirically the smallest value that lands the
 * character's pixels past the NTSC horizontal-blanking boundary rather than being
 * silently dropped as still-blanked output - see VicTestHarness/this class's
 * git history for how that was determined) and Y = 0. Character 0 in screen
 * memory selects the 8 bytes at $8000-$8007 as its bitmap (single-height cells).
 */
public class VicTextModeRenderingTest {

    private static final int CYAN = 0x73D5FFFF;
    private static final int GREEN = 0x54D87DFF;
    private static final int BLACK = 0x000000FF;
    private static final int WHITE = 0xFFFFFFFF;

    private VicTestHarness h;

    @BeforeEach
    void setUp() {
        h = new VicTestHarness(MachineType.NTSC);
        h.writeRegister(5, 0xF0); // screen mem CPU $1C00, char mem CPU $8000
        h.writeRegister(2, 1);    // 1 column
        h.writeRegister(3, 0x02); // 1 row, single height
        h.writeRegister(0, 4);    // screen origin x
        h.writeRegister(1, 0);    // screen origin y
        h.poke(0x1C00, 0x00);     // cell index 0 -> bitmap at char mem + 0
    }

    private int[] renderFirstRow() {
        h.runToEndOfFrame(); // discard the short startup-artifact frame
        h.runToEndOfFrame(); // one clean full frame
        int width = h.machineType.getTotalScreenWidth();
        int[] row = new int[width];
        System.arraycopy(h.pixelData.pixels, 0, row, 0, width);
        return row;
    }

    @Test
    void hiresNonReverseRendersSetBitsAsForegroundColour() {
        h.writeRegister(15, 0x09); // background=black, non-reverse, border=white
        h.writeRegister(14, 0x00);
        h.poke(0x9400, 0x03); // colour RAM: foreground=cyan, hires (bit3=0)
        h.poke(0x8000, 0xF0); // bitmap row: 1111 0000

        int[] row = renderFirstRow();

        assertEquals(WHITE, row[10], "border before the character");
        assertEquals(CYAN, row[11]);
        assertEquals(CYAN, row[12]);
        assertEquals(CYAN, row[13]);
        assertEquals(CYAN, row[14]);
        assertEquals(BLACK, row[15]);
        assertEquals(BLACK, row[16]);
        assertEquals(BLACK, row[17]);
        assertEquals(BLACK, row[18]);
        assertEquals(WHITE, row[19], "border after the character");
    }

    @Test
    void hiresReverseModeSwapsSetAndClearBitColours() {
        h.writeRegister(15, 0x01); // bit3=0 -> reverse mode, background=black, border=white
        h.writeRegister(14, 0x00);
        h.poke(0x9400, 0x03);
        h.poke(0x8000, 0xF0); // same bitmap as the non-reverse test

        int[] row = renderFirstRow();

        assertEquals(BLACK, row[11], "reverse mode: set bits now render background colour");
        assertEquals(BLACK, row[14]);
        assertEquals(CYAN, row[15], "reverse mode: clear bits now render foreground colour");
        assertEquals(CYAN, row[18]);
    }

    @Test
    void multicolourModeRendersFourColoursFromTwoBitPairs() {
        h.writeRegister(15, 0x09); // background=black, non-reverse, border=white
        h.writeRegister(14, 0x50); // auxiliary colour = green
        h.poke(0x9400, 0x0B); // bit3=1 -> multicolour, foreground=cyan
        h.poke(0x8000, 0b00011011); // pairs (MSB first): 00, 01, 10, 11

        int[] row = renderFirstRow();

        // Multicolour doubles pixel width, so each 2-bit pair spans 2 output pixels.
        assertEquals(BLACK, row[11], "pair 00 -> background");
        assertEquals(BLACK, row[12]);
        assertEquals(WHITE, row[13], "pair 01 -> border colour");
        assertEquals(WHITE, row[14]);
        assertEquals(CYAN, row[15], "pair 10 -> foreground colour");
        assertEquals(CYAN, row[16]);
        assertEquals(GREEN, row[17], "pair 11 -> auxiliary colour");
        assertEquals(GREEN, row[18]);
    }

    @Test
    void changingForegroundColourChangesOnlySetBitPixels() {
        h.writeRegister(15, 0x09);
        h.writeRegister(14, 0x00);
        h.poke(0x8000, 0xF0);

        h.poke(0x9400, 0x05); // foreground=green instead of cyan
        int[] row = renderFirstRow();

        assertEquals(GREEN, row[11]);
        assertEquals(BLACK, row[15], "background colour is unaffected by the foreground colour RAM nibble");
    }
}
