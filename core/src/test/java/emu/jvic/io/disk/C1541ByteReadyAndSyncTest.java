package emu.jvic.io.disk;

import emu.jvic.MachineType;
import emu.jvic.memory.RamType;
import emu.jvic.testutil.MachineTestHarness;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link C1541Drive}'s byte-ready/sync-mark state machine
 * ({@code atSyncMark()}, {@code byteReady}, the CPU "set overflow" fast-read
 * patch in {@code emulateCycle()}) during a real disk load, using the
 * package-private observability getters added this session.
 *
 * <p>Investigated (per this session's established "investigate, don't
 * blindly fix" discipline for author TODOs) the {@code lastSync} TODO in
 * {@code moveForwardOneByte()}: {@code "TODO: Note sure why we need to check
 * the last byte. Check this carefully."} The logic only sets {@code byteReady}
 * while reading if {@code !atSyncMark() || !lastSync} - i.e. it suppresses
 * repeated byte-ready pulses for every byte *within* a run of sync bytes, but
 * still fires once on the byte where a sync run first begins. Real 1541
 * hardware suppresses byte-ready for the *entire* sync run (no byte framing at
 * all while continuously seeing 1-bits), so firing once on the first sync
 * byte looks like it could be a subtle deviation from real hardware. However,
 * {@link C1541DiskLoadBootTest} already demonstrates real, unmodified DOS ROM
 * code successfully completing both a failed (FILE NOT FOUND) and a
 * successful LOAD against real disk images end-to-end through this exact
 * code path - strong evidence the current behaviour is at least functionally
 * compatible with what the real DOS ROM expects, the same way the VIC chip's
 * documented-but-unfixed TODOs were left alone without a concrete observed
 * failure to fix against. Left as-is; documented here for a future pass.
 */
public class C1541ByteReadyAndSyncTest {

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

    @Test
    void byteReadyAndSyncMarkStateBothToggleDuringARealLoad() throws IOException {
        MachineTestHarness h = MachineTestHarness.withRealRoms(MachineType.PAL, RamType.RAM_UNEXPANDED);
        h.frames(50);
        h.machine.getC1541Drive().insertDisk(loadFixture("assets/disks/colourtest.d64"), true, null, false);
        C1541Drive drive = h.machine.getC1541Drive();

        // Inline the same KERNAL LOAD call sequence as MachineTestHarness.loadViaKernal,
        // but sampling the drive's state on every cycle while the load is in progress
        // (the harness helper only returns the final outcome).
        int[] mem = h.getMemory().getMemoryArray();
        int routine = MachineTestHarness.KERNAL_CALL_ROUTINE_ADDRESS;
        int filenameAddress = MachineTestHarness.KERNAL_CALL_FILENAME_ADDRESS;
        int loadAddress = 0x1600;
        int pos = routine;
        mem[pos++] = 0xA9; mem[pos++] = 1;
        mem[pos++] = 0xA2; mem[pos++] = filenameAddress & 0xFF;
        mem[pos++] = 0xA0; mem[pos++] = (filenameAddress >> 8) & 0xFF;
        mem[pos++] = 0x20; mem[pos++] = 0xBD; mem[pos++] = 0xFF; // SETNAM
        mem[pos++] = 0xA9; mem[pos++] = 1;
        mem[pos++] = 0xA2; mem[pos++] = 8;
        mem[pos++] = 0xA0; mem[pos++] = 0;
        mem[pos++] = 0x20; mem[pos++] = 0xBA; mem[pos++] = 0xFF; // SETLFS
        mem[pos++] = 0xA9; mem[pos++] = 0;
        mem[pos++] = 0xA2; mem[pos++] = loadAddress & 0xFF;
        mem[pos++] = 0xA0; mem[pos++] = (loadAddress >> 8) & 0xFF;
        mem[pos++] = 0x20; mem[pos++] = 0xD5; mem[pos++] = 0xFF; // LOAD
        int parkAddress = pos;
        mem[pos++] = 0x4C; mem[pos++] = parkAddress & 0xFF; mem[pos++] = (parkAddress >> 8) & 0xFF;
        mem[filenameAddress] = '*';

        h.machine.getCpu().setProgramCounter(routine);

        boolean sawByteReadyTrue = false;
        boolean sawByteReadyFalse = false;
        boolean sawSyncMark = false;
        boolean sawNonSyncWhileMotorOn = false;
        int consecutiveParkHits = 0;
        int lastParkCycle = -1000;

        for (int i = 0; i < 20_000_000; i++) {
            h.cycle();
            if (drive.isByteReady()) {
                sawByteReadyTrue = true;
            } else {
                sawByteReadyFalse = true;
            }
            if (drive.isMotorOn()) {
                if (drive.isAtSyncMark()) {
                    sawSyncMark = true;
                } else {
                    sawNonSyncWhileMotorOn = true;
                }
            }
            if (h.machine.getCpu().getProgramCounter() == parkAddress + 1) {
                consecutiveParkHits = (i - lastParkCycle <= 4) ? consecutiveParkHits + 1 : 1;
                lastParkCycle = i;
                if (consecutiveParkHits >= 5) {
                    break;
                }
            }
        }

        assertTrue(consecutiveParkHits >= 5, "the LOAD call should have completed within the cycle budget");
        assertTrue(sawByteReadyTrue, "byteReady should become true at some point during a real load");
        assertTrue(sawByteReadyFalse, "byteReady should also be false at some point (it's not permanently stuck)");
        assertTrue(sawSyncMark, "the head should pass over real sync marks while reading a real disk");
        assertTrue(sawNonSyncWhileMotorOn, "the head should also spend time over non-sync data while reading");
    }
}
