package emu.jvic.video;

import emu.jvic.MachineType;
import emu.jvic.testutil.VicTestHarness;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Border colour rendering with 0 columns/rows configured (registers 2 and 3
 * default to 0 on a freshly reset chip), so the video matrix never actually opens
 * and the entire visible frame is border colour.
 *
 * <p>Empirically confirmed pixel counts per frame (i.e. actual {@code putPixel()}
 * calls, excluding horizontal/vertical blanking, which uses {@code
 * putBlankPixels()} instead and never touches the pixel buffer): NTSC = 50400
 * (200 visible pixels/line x 252 visible lines), PAL = 70599.
 *
 * <p>PAL renders TWO slightly different RGBA shades for the same border colour
 * index, alternating by line - this is real PAL hardware colour-phase
 * alternation, reproduced here via separate {@code palOddRGBA8888Colours}/
 * {@code palEvenRGBA8888Colours} tables in Vic6561, not a rendering bug.
 */
public class VicBorderRenderingTest {

    private static final int NTSC_RED = 0x982D18FF;
    private static final int PAL_RED_EVEN = 0x982D18FF;
    private static final int PAL_RED_ODD = 0x942741FF;

    @Test
    void ntscRendersOnlyBorderColourWhenMatrixNeverOpens() {
        VicTestHarness h = new VicTestHarness(MachineType.NTSC);
        h.writeRegister(15, 0x02); // border=red, background=black

        h.runToEndOfFrame(); // discard startup artifact
        long before = h.pixelData.putPixelCallCount;
        h.runToEndOfFrame();
        long pixelsThisFrame = h.pixelData.putPixelCallCount - before;

        assertEquals(50400, pixelsThisFrame);
        for (int p : h.pixelData.pixels) {
            if (p != 0) {
                assertEquals(NTSC_RED, p, "every rendered pixel should be the border colour");
            }
        }
    }

    @Test
    void palRendersOnlyBorderColourWhenMatrixNeverOpens() {
        VicTestHarness h = new VicTestHarness(MachineType.PAL);
        h.writeRegister(15, 0x02);

        h.runToEndOfFrame();
        long before = h.pixelData.putPixelCallCount;
        h.runToEndOfFrame();
        long pixelsThisFrame = h.pixelData.putPixelCallCount - before;

        assertEquals(70599, pixelsThisFrame);
        for (int p : h.pixelData.pixels) {
            if (p != 0) {
                assertTrue(p == PAL_RED_EVEN || p == PAL_RED_ODD,
                        "every rendered pixel should be one of the two (odd/even line) border colour shades");
            }
        }
    }

    @Test
    void changingBorderColourRegisterChangesRenderedOutput() {
        VicTestHarness h = new VicTestHarness(MachineType.NTSC);
        h.writeRegister(15, 0x05); // border=green

        h.runToEndOfFrame();
        h.runToEndOfFrame();

        boolean sawGreen = false;
        for (int p : h.pixelData.pixels) {
            if (p == 0x54D87DFF) {
                sawGreen = true;
                break;
            }
        }
        assertTrue(sawGreen);
        for (int p : h.pixelData.pixels) {
            assertTrue(p == 0 || p == 0x54D87DFF, "red must no longer appear once the border colour register changes");
        }
    }

    @Test
    void blankingPixelsAreNotWrittenToTheFrameBuffer() {
        // The number of putPixel() calls must be well below the full raster area
        // (totalScreenWidth x totalScreenHeight), confirming horizontal/vertical
        // blanking regions use putBlankPixels() and never touch the pixel buffer.
        VicTestHarness h = new VicTestHarness(MachineType.NTSC);
        h.runToEndOfFrame();
        long before = h.pixelData.putPixelCallCount;
        h.runToEndOfFrame();
        long pixelsThisFrame = h.pixelData.putPixelCallCount - before;

        long fullRasterArea = (long) MachineType.NTSC.getTotalScreenWidth() * MachineType.NTSC.getTotalScreenHeight();
        assertTrue(pixelsThisFrame < fullRasterArea);
    }
}
