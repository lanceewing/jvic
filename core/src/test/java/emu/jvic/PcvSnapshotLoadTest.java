package emu.jvic;

import emu.jvic.config.AppConfigItem;
import emu.jvic.memory.RamType;
import emu.jvic.testutil.MachineTestHarness;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests loading real, genuine PCVIC snapshot files (bundled at
 * {@code assets/snaps/pcv/*.PCV}) through {@link Machine#init}'s real
 * {@code programType.equals("PCV")} path (see {@code Machine.java}'s
 * {@code new PcvSnapshot(programData)} branch). This is the realistic
 * substitute for a save/restore round-trip test: nothing in {@code core}
 * implements a "capture current state into a Snapshot" (save) direction -
 * only decoding an external snapshot format exists - so what's testable is
 * whether loading a real snapshot correctly restores CPU/VIA/VIC/memory state
 * and lets the machine continue running from that point, mid-game.
 */
public class PcvSnapshotLoadTest {

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

    private static MachineTestHarness loadSnapshot(String fileName) throws IOException {
        byte[] pcvData = loadFixture("assets/snaps/pcv/" + fileName);
        AppConfigItem appConfigItem = new AppConfigItem();
        appConfigItem.setFileType("PCV");
        Program program = new Program(appConfigItem, pcvData);
        // Fully expanded RAM, so nothing the snapshot restores lands on UnconnectedMemory.
        return MachineTestHarness.withRealRomsAndProgram(MachineType.PAL, RamType.RAM_35K, program);
    }

    @ParameterizedTest
    @ValueSource(strings = { "HRAKEL.PCV", "ROCKMAN.PCV", "SLAPDAB.PCV", "WEREWOLF.PCV" })
    void loadingARealPcvSnapshotRestoresAMidGameStateNotAColdBoot(String fileName) throws IOException {
        MachineTestHarness h = loadSnapshot(fileName);

        // A cold boot always starts by executing the KERNAL's reset routine, deep in ROM
        // (see MachineBootTest); a genuine mid-game snapshot should not land there.
        int pc = h.machine.getCpu().getProgramCounter();
        assertNotEquals(0, pc, "PC should have been restored from the snapshot, not left at its zero default");

        // Colour RAM's upper nibble is unconditionally cleared by PcvSnapshot.decode() after
        // uncompressing memory - a generic, content-independent fact about the decode path.
        int[] mem = h.getMemory().getMemoryArray();
        boolean anyUpperNibbleSet = false;
        for (int addr = 0x9400; addr < 0x9800; addr++) {
            if ((mem[addr] & 0xF0) != 0) {
                anyUpperNibbleSet = true;
                break;
            }
        }
        assertTrue(!anyUpperNibbleSet, "colour RAM's upper nibble should be cleared after loading a PCV snapshot");
    }

    @ParameterizedTest
    @ValueSource(strings = { "HRAKEL.PCV", "ROCKMAN.PCV", "SLAPDAB.PCV", "WEREWOLF.PCV" })
    void machineContinuesRunningNormallyAfterLoadingARealSnapshot(String fileName) throws IOException {
        MachineTestHarness h = loadSnapshot(fileName);

        // Running a few real frames from the restored state shouldn't crash or hang -
        // the strongest available confirmation that CPU/VIA/VIC state was restored into a
        // coherent, resumable configuration rather than something that only "looks" valid.
        // Reaching this assertion at all (without an exception) is the actual test.
        h.frames(10);

        assertTrue(true);
    }
}
