package emu.jvic.io.disk;

import emu.jvic.MachineType;
import emu.jvic.memory.RamType;
import emu.jvic.testutil.MachineTestHarness;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The disk-drive equivalent of {@code emu.jvic.MachineBootTest}: boots a real
 * {@link emu.jvic.Machine} with the genuine bundled ROMs, mounts a real
 * {@code .d64} disk image on a real, fully-wired {@link C1541Drive}, and drives
 * a real KERNAL LOAD (see {@link MachineTestHarness#loadViaKernal}) through the
 * real IEC serial protocol end-to-end - exercising ATN/CLOCK/DATA handshaking,
 * GCR sector reads, sync-mark detection, and head/track movement all at once,
 * the same way real software actually depends on this subsystem.
 *
 * <p>This class lives in {@code emu.jvic.io.disk} (not alongside the other
 * machine-level tests in {@code emu.jvic}) specifically so it can use
 * {@link C1541Drive}'s package-private observability getters
 * ({@code getCurrentTrack()}, {@code isByteReady()}, etc.) to make stronger,
 * white-box-style assertions about what happened during a real load, without
 * needing to fight or bypass the drive's own actively-running DOS ROM CPU.
 */
public class C1541DiskLoadBootTest {

    private static byte[] loadFixture(String relativePath) throws IOException {
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve(relativePath);
            if (Files.exists(candidate)) {
                return Files.readAllBytes(candidate);
            }
            dir = dir.getParent();
        }
        throw new RuntimeException("Could not locate " + relativePath + " above " + Paths.get("").toAbsolutePath());
    }

    private MachineTestHarness bootAndMount(String diskFixturePath) throws IOException {
        MachineTestHarness h = MachineTestHarness.withRealRoms(MachineType.PAL, RamType.RAM_UNEXPANDED);
        h.frames(50); // Reach READY/idle - see MachineBootTest.
        h.machine.getC1541Drive().insertDisk(loadFixture(diskFixturePath), true, null, false);
        return h;
    }

    @Test
    void loadingAWildcardFileFromAnEmptyDiskReturnsFileNotFound() throws IOException {
        MachineTestHarness h = bootAndMount("assets/data/blank.d64");

        MachineTestHarness.KernalLoadResult result = h.loadViaKernal("*", 8, 0x1600, 20_000_000);

        assertTrue(result.completed, "LOAD should have returned within the cycle budget");
        assertTrue(!result.success, "loading from an empty disk should fail");
        // CBM DOS/BASIC error code 4 = FILE NOT FOUND - a well-documented, stable error code
        // across the whole 8-bit CBM line.
        assertEquals(4, result.accumulator, "accumulator should hold the FILE NOT FOUND error code");
    }

    @Test
    void loadingAWildcardFileFromARealDiskSucceedsAndWritesData() throws IOException {
        MachineTestHarness h = bootAndMount("assets/disks/colourtest.d64");
        int loadAddress = 0x1600;
        int[] mem = h.getMemory().getMemoryArray();
        // Sentinel-fill the destination first so we can positively confirm bytes changed,
        // without needing to assert exactly what the loaded content is.
        for (int i = 0; i < 256; i++) {
            mem[loadAddress + i] = 0xEE;
        }

        MachineTestHarness.KernalLoadResult result = h.loadViaKernal("*", 8, loadAddress, 20_000_000);

        assertTrue(result.completed, "LOAD should have returned within the cycle budget");
        assertTrue(result.success, "loading the first file from a real disk should succeed");

        boolean anyByteChanged = false;
        for (int i = 0; i < 256; i++) {
            if ((mem[loadAddress + i] & 0xFF) != 0xEE) {
                anyByteChanged = true;
                break;
            }
        }
        assertTrue(anyByteChanged, "at least the start of the loaded file should have overwritten the sentinel bytes");
    }

    @Test
    void diskHeadMovesOffTrackOneWhileLoadingARealFile() throws IOException {
        // Uses C1541Drive's package-private observability getters (added specifically to
        // support this kind of white-box assertion) to confirm real head movement and
        // byte-ready activity occurred during a real load, rather than only checking the
        // final LOAD outcome.
        MachineTestHarness h = bootAndMount("assets/disks/colourtest.d64");
        C1541Drive drive = h.machine.getC1541Drive();

        assertEquals(1, drive.getCurrentTrack(), "drive should start parked on track 1 after mounting");

        h.loadViaKernal("*", 8, 0x1600, 20_000_000);

        // Loading a real file requires reading the directory (track 18) at minimum, so the
        // head must have moved at some point during the load.
        assertTrue(drive.getCurrentTrack() != 1 || drive.getCurrentHalfTrack() != 2,
                "drive head should have moved during a real file load");
    }
}
