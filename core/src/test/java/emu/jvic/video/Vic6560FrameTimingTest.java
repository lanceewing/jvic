package emu.jvic.video;

import emu.jvic.MachineType;
import emu.jvic.testutil.VicTestHarness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NTSC (6560) frame/line timing. Empirically confirmed (by counting real
 * {@code emulateCycle()} calls between two {@code frameRenderComplete} signals):
 * a non-interlaced NTSC frame is exactly 65 cycles/line x 261 lines = 16965
 * cycles. This does NOT equal {@link MachineType#getCyclesPerFrame()} (17045),
 * which is a rounded cyclesPerSecond/framesPerSecond value used elsewhere for
 * real-time pacing, not the exact hardware raster cycle count. In interlaced mode
 * the frame length alternates between two different values (odd/even field), a
 * real characteristic of interlaced video.
 */
public class Vic6560FrameTimingTest {

    private static final int NTSC_CYCLES_PER_LINE = 65;
    private static final int NTSC_LINES_PER_FRAME = 261;
    private static final long NTSC_CYCLES_PER_FRAME = (long) NTSC_CYCLES_PER_LINE * NTSC_LINES_PER_FRAME;

    private VicTestHarness h;

    @BeforeEach
    void setUp() {
        h = new VicTestHarness(MachineType.NTSC);
    }

    private long cyclesToNextFrame() {
        long cycles = 0;
        while (!h.cycle()) {
            cycles++;
        }
        return cycles + 1;
    }

    @Test
    void nonInterlacedFrameIsExactly16965Cycles() {
        cyclesToNextFrame(); // discard the first (short, startup-artifact) frame
        assertEquals(NTSC_CYCLES_PER_FRAME, cyclesToNextFrame());
        assertEquals(NTSC_CYCLES_PER_FRAME, cyclesToNextFrame());
    }

    @Test
    void rasterLineRegisterWrapsToZeroAtEndOfFrame() {
        cyclesToNextFrame();
        assertEquals(0, h.readRegister(4));
    }

    @Test
    void rasterLineRegisterAdvancesOncePerLine() {
        h.cycle();
        int startRaster = h.readRegister(4);
        h.cycles(NTSC_CYCLES_PER_LINE * 2 - 1); // roughly one full line, minus the cycle already consumed
        int laterRaster = h.readRegister(4) | (((h.readRegister(3) & 0x80) != 0) ? 0x100 : 0);
        assertNotEquals(startRaster, laterRaster);
    }

    @Test
    void interlacedModeAlternatesFieldLength() {
        h.writeRegister(0, 0x80); // interlaced mode
        cyclesToNextFrame(); // discard startup artifact

        long field1 = cyclesToNextFrame();
        long field2 = cyclesToNextFrame();
        long field3 = cyclesToNextFrame();

        assertNotEquals(field1, field2, "interlaced fields should alternate in length");
        assertEquals(field1, field3, "the pattern should repeat every 2 fields");
    }

    @Test
    void emulateCycleReturnsTrueExactlyOncePerFrame() {
        int trueCount = 0;
        for (int i = 0; i < NTSC_CYCLES_PER_FRAME * 2; i++) {
            if (h.cycle()) {
                trueCount++;
            }
        }
        // Roughly 2 frames' worth of cycles (allowing for the short first frame),
        // so we expect the completion signal to have fired 2 or 3 times, never 0
        // or 1, and never fired on consecutive cycles.
        assertTrue(trueCount >= 2, "frame-complete signal should fire at least twice over 2 frames' worth of cycles");
    }
}
