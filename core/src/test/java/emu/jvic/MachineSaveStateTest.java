package emu.jvic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import emu.jvic.debug.StateReader;
import emu.jvic.debug.StateWriter;
import emu.jvic.io.disk.persistence.NoOpDiskImagePersistenceSession;
import emu.jvic.memory.RamType;
import emu.jvic.testutil.MachineTestHarness;

/**
 * Tests for save states: a state restored into a fresh machine of the same kind must
 * carry on exactly as the original did. If any piece of state were missed, the two
 * machines would drift apart, and their later states would differ.
 */
public class MachineSaveStateTest {

    private static byte[] save(Machine machine) {
        StateWriter out = new StateWriter();
        machine.saveState(out);
        return out.toByteArray();
    }

    private static void runCycles(MachineTestHarness h, int cycles) {
        for (int i = 0; i < cycles; i++) {
            h.machine.emulateCycle();
        }
    }

    /**
     * Compares two states, reporting the first difference by section and offset (a
     * plain array comparison would build a huge message).
     */
    private static void assertSameState(byte[] expected, byte[] actual, String message) {
        int length = Math.min(expected.length, actual.length);
        int p = 12;
        String section = "header";
        int sectionStart = 0;
        for (int i = 0; i < length; i++) {
            if ((i == p) && (i + 8 <= length)) {
                section = new String(expected, i, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
                int sectionLength = ((expected[i + 4] & 0xFF) << 24) | ((expected[i + 5] & 0xFF) << 16)
                        | ((expected[i + 6] & 0xFF) << 8) | (expected[i + 7] & 0xFF);
                sectionStart = i + 8;
                p = sectionStart + sectionLength;
            }
            if (expected[i] != actual[i]) {
                throw new AssertionError(message + ": first difference in section " + section + " at offset "
                        + (i - sectionStart) + " (" + (expected[i] & 0xFF) + " vs " + (actual[i] & 0xFF) + ")");
            }
        }
        assertEquals(expected.length, actual.length, message + ": lengths");
    }

    private static void assertSameFuture(MachineTestHarness original, MachineTestHarness copy, int cycles) {
        byte[] state = save(original.machine);
        copy.machine.loadState(new StateReader(state));
        assertSameState(state, save(copy.machine), "the restored state saves the same");
        runCycles(original, cycles);
        runCycles(copy, cycles);
        assertSameState(save(original.machine), save(copy.machine), "the two machines drifted apart");
    }

    @Test
    void aRestoredPalMachineCarriesOnExactlyAsTheOriginal() {
        MachineTestHarness original = MachineTestHarness.withRealRoms(MachineType.PAL, RamType.RAM_8K);
        runCycles(original, 1_234_567);
        MachineTestHarness copy = MachineTestHarness.withRealRoms(MachineType.PAL, RamType.RAM_8K);
        assertSameFuture(original, copy, 777_777);
    }

    @Test
    void aRestoredNtscMachineCarriesOnExactlyAsTheOriginal() {
        MachineTestHarness original = MachineTestHarness.withRealRoms(MachineType.NTSC, RamType.RAM_UNEXPANDED);
        runCycles(original, 1_000_003);
        MachineTestHarness copy = MachineTestHarness.withRealRoms(MachineType.NTSC, RamType.RAM_UNEXPANDED);
        assertSameFuture(original, copy, 500_001);
    }

    @Test
    void theDriveAndDiskAreRestoredMidLoad() throws IOException {
        byte[] disk = Files.readAllBytes(findRepoFile("assets/disks/BalloonCaves_20.d64"));
        MachineTestHarness original = MachineTestHarness.withRealRoms(MachineType.PAL, RamType.RAM_24K);
        original.machine.getC1541Drive().insertDisk(disk, true, new NoOpDiskImagePersistenceSession(disk), false);
        runCycles(original, 1_500_000);
        // Type LOAD"*",8 and save while the drive is busy.
        int[] mem = original.getMemory().getMemoryArray();
        String command = "LOAD\"*\",8\r";
        for (int i = 0; i < command.length(); i++) {
            mem[0x277 + i] = command.charAt(i);
        }
        mem[0xC6] = command.length();
        runCycles(original, 3_000_000);
        MachineTestHarness copy = MachineTestHarness.withRealRoms(MachineType.PAL, RamType.RAM_24K);
        assertSameFuture(original, copy, 1_500_000);
        assertTrue(copy.machine.getC1541Drive().getDiskImage() != null);
    }

    @Test
    void aStateOnlyLoadsIntoAMachineSetUpTheSameWay() {
        MachineTestHarness original = MachineTestHarness.withRealRoms(MachineType.PAL, RamType.RAM_8K);
        runCycles(original, 10_000);
        byte[] state = save(original.machine);
        MachineTestHarness other = MachineTestHarness.withRealRoms(MachineType.PAL, RamType.RAM_UNEXPANDED);
        StateReader.StateException e = assertThrows(StateReader.StateException.class,
                () -> other.machine.loadState(new StateReader(state)));
        assertTrue(e.getMessage().contains("different RAM expansions"), e.getMessage());
        MachineTestHarness ntsc = MachineTestHarness.withRealRoms(MachineType.NTSC, RamType.RAM_8K);
        e = assertThrows(StateReader.StateException.class, () -> ntsc.machine.loadState(new StateReader(state)));
        assertEquals("The save state is for a PAL machine, not NTSC.", e.getMessage());
        assertThrows(StateReader.StateException.class, () -> new StateReader(new byte[] { 1, 2, 3 }));
    }

    private static Path findRepoFile(String name) {
        Path dir = Paths.get("").toAbsolutePath();
        while (!Files.exists(dir.resolve(name))) {
            dir = dir.getParent();
        }
        return dir.resolve(name);
    }
}
