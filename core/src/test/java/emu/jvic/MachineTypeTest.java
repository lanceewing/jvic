package emu.jvic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sanity-checks {@link MachineType}'s derived arithmetic. These values feed
 * directly into VIC frame-timing behaviour (see {@code Vic6560FrameTimingTest}/
 * {@code Vic6561FrameTimingTest}), so a fat-fingered constructor constant here
 * should be caught directly rather than surfacing as a confusing VIC test
 * failure elsewhere.
 */
public class MachineTypeTest {

    @ParameterizedTest
    @EnumSource(MachineType.class)
    void cyclesPerLineIsTotalScreenWidthDividedByFour(MachineType machineType) {
        assertEquals(machineType.getTotalScreenWidth() / 4, machineType.getCyclesPerLine());
    }

    @ParameterizedTest
    @EnumSource(MachineType.class)
    void cyclesPerFrameIsCyclesPerSecondDividedByFramesPerSecond(MachineType machineType) {
        assertEquals(machineType.getCyclesPerSecond() / machineType.getFramesPerSecond(),
                machineType.getCyclesPerFrame());
    }

    @ParameterizedTest
    @EnumSource(MachineType.class)
    void frameDurationIsOneThousandDividedByFramesPerSecond(MachineType machineType) {
        assertEquals(1000 / machineType.getFramesPerSecond(), machineType.getFrameDuration());
    }

    @ParameterizedTest
    @EnumSource(MachineType.class)
    void lastLineIsTotalScreenHeightMinusOne(MachineType machineType) {
        assertEquals(machineType.getTotalScreenHeight() - 1, machineType.getLastLine());
    }

    @Test
    void palAndNtscAreMutuallyExclusiveAndCorrectlyIdentified() {
        assertTrue(MachineType.PAL.isPAL());
        assertFalse(MachineType.PAL.isNTSC());
        assertTrue(MachineType.NTSC.isNTSC());
        assertFalse(MachineType.NTSC.isPAL());
    }

    @Test
    void vic44AndVic44kAreDistinctFromEachOtherAndFromPalNtsc() {
        assertTrue(MachineType.VIC44.isVIC44());
        assertFalse(MachineType.VIC44.isVIC44K());
        assertTrue(MachineType.VIC44K.isVIC44K());
        assertFalse(MachineType.VIC44K.isVIC44());
        assertFalse(MachineType.PAL.isVIC44());
        assertFalse(MachineType.NTSC.isVIC44K());
    }
}
