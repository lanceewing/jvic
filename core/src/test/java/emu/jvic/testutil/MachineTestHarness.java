package emu.jvic.testutil;

import emu.jvic.Machine;
import emu.jvic.MachineType;
import emu.jvic.memory.Memory;
import emu.jvic.memory.RamType;
import emu.jvic.sound.SoundGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Test harness that wraps a real, fully-wired {@link Machine}, built via the
 * exact same {@code Machine.init(...)} path the application itself uses (real
 * {@code Cpu6502}, {@code Vic6560}/{@code Vic6561}, {@code Via1}/{@code Via2},
 * {@code Vic20Memory}, {@code C1541Drive}) - not a synthetic/isolated harness
 * like the ones used for the per-chip Phase 1-3 test suites.
 *
 * <p>Two ROM sources are supported:
 * <ul>
 * <li>{@link #withRealRoms} loads the project's genuine bundled ROM images
 * from {@code assets/roms/} for true end-to-end boot tests.</li>
 * <li>{@link #withSyntheticRoms} builds minimal, fully controlled ROM content
 * instead: the KERNAL ROM contains just {@code CLI} followed by a tight
 * {@code JMP} loop at the reset vector, and an {@code RTI} at the IRQ vector -
 * enough for the CPU to sit with interrupts enabled and correctly return from
 * any IRQ a VIA raises, without any of the real KERNAL's complex, less
 * predictable startup behaviour. Used for orchestration/wiring tests that care
 * about cycle-level chip interaction, not about real BASIC/KERNAL semantics.
 * </ul>
 */
public class MachineTestHarness {

    /** Address of the synthetic KERNAL's reset entry point (CLI; loop:JMP loop). */
    public static final int SYNTHETIC_RESET_ADDRESS = 0xE000;

    /** Address of the synthetic KERNAL's IRQ handler (just RTI). */
    public static final int SYNTHETIC_IRQ_HANDLER_ADDRESS = 0xE010;

    /**
     * Address of the synthetic KERNAL's NMI handler (just RTI) - deliberately
     * distinct from the IRQ handler address, so a test can tell which vector the
     * CPU actually took (VIA1 drives the 6502's NMI pin, VIA2 drives its IRQ pin -
     * see {@code Via1.updateIrqPin}/{@code Via2.updateIrqPin}).
     */
    public static final int SYNTHETIC_NMI_HANDLER_ADDRESS = 0xE020;

    private static class NoOpSoundGenerator extends SoundGenerator {
        @Override
        public void initSound(MachineType machineType) {
        }

        @Override
        public void emulateCycle() {
        }

        @Override
        public void pauseSound() {
        }

        @Override
        public void resumeSound() {
        }

        @Override
        public boolean isSoundOn() {
            return false;
        }

        @Override
        public void dispose() {
        }
    }

    public final Machine machine;
    public final RecordingPixelData pixelData;
    public final TestKeyboardMatrix keyboardMatrix;
    public final MachineType machineType;

    private MachineTestHarness(MachineType machineType, RamType ramType, byte[] basicRom, byte[] kernalRom,
            byte[] charRom, byte[] dos1541Rom) {
        this.machineType = machineType;
        keyboardMatrix = new TestKeyboardMatrix();
        pixelData = new RecordingPixelData();
        pixelData.init(machineType.getTotalScreenWidth() * 2, machineType.getTotalScreenHeight() * 2);

        machine = new Machine(new NoOpSoundGenerator(), keyboardMatrix, pixelData);
        machine.init(basicRom, kernalRom, charRom, dos1541Rom, null, machineType, ramType, null);
    }

    public static MachineTestHarness withRealRoms(MachineType machineType, RamType ramType) {
        byte[] basicRom = loadRealRom("basic.rom");
        byte[] kernalRom = loadRealRom(machineType.isPAL() ? "kernal_pal.rom" : "kernal_ntsc.rom");
        byte[] charRom = loadRealRom("char.rom");
        byte[] dos1541Rom = loadRealRom("dos1541.rom");
        return new MachineTestHarness(machineType, ramType, basicRom, kernalRom, charRom, dos1541Rom);
    }

    public static MachineTestHarness withSyntheticRoms(MachineType machineType, RamType ramType) {
        return withSyntheticRoms(machineType, ramType, true);
    }

    /**
     * @param cliAtReset If true, the reset entry point starts with a {@code CLI}
     *                   before the tight loop, so IRQs are unmasked immediately
     *                   (interrupts are enabled by default in the harness, since
     *                   most cross-chip wiring tests want that). If false, the
     *                   6502's own post-reset default (interrupts disabled) is
     *                   left alone, so a test can confirm IRQ - unlike NMI - is
     *                   masked until something explicitly clears the I flag.
     */
    public static MachineTestHarness withSyntheticRoms(MachineType machineType, RamType ramType,
            boolean cliAtReset) {
        byte[] basicRom = fill(8192, 0xEA); // NOPs; never executed in this setup.
        byte[] charRom = fill(4096, 0x00);
        byte[] dos1541Rom = fill(16384, 0xEA);

        byte[] kernalRom = fill(8192, 0xEA);
        int base = SYNTHETIC_RESET_ADDRESS - 0xE000;
        if (cliAtReset) {
            kernalRom[base] = 0x58; // CLI
            kernalRom[base + 1] = 0x4C; // JMP $E001 (tight loop, right after the CLI)
            kernalRom[base + 2] = (byte) ((SYNTHETIC_RESET_ADDRESS + 1) & 0xFF);
            kernalRom[base + 3] = (byte) (((SYNTHETIC_RESET_ADDRESS + 1) >> 8) & 0xFF);
        } else {
            kernalRom[base] = 0x4C; // JMP $E000 (tight loop, interrupts left disabled)
            kernalRom[base + 1] = (byte) (SYNTHETIC_RESET_ADDRESS & 0xFF);
            kernalRom[base + 2] = (byte) ((SYNTHETIC_RESET_ADDRESS >> 8) & 0xFF);
        }

        kernalRom[SYNTHETIC_IRQ_HANDLER_ADDRESS - 0xE000] = 0x40; // RTI
        kernalRom[SYNTHETIC_NMI_HANDLER_ADDRESS - 0xE000] = 0x40; // RTI

        writeVector(kernalRom, 0xFFFC, SYNTHETIC_RESET_ADDRESS); // RESET vector
        writeVector(kernalRom, 0xFFFE, SYNTHETIC_IRQ_HANDLER_ADDRESS); // IRQ/BRK vector
        writeVector(kernalRom, 0xFFFA, SYNTHETIC_NMI_HANDLER_ADDRESS); // NMI vector

        return new MachineTestHarness(machineType, ramType, basicRom, kernalRom, charRom, dos1541Rom);
    }

    private static void writeVector(byte[] kernalRom, int vectorAddress, int targetAddress) {
        int offset = vectorAddress - 0xE000;
        kernalRom[offset] = (byte) (targetAddress & 0xFF);
        kernalRom[offset + 1] = (byte) ((targetAddress >> 8) & 0xFF);
    }

    private static byte[] fill(int length, int value) {
        byte[] data = new byte[length];
        for (int i = 0; i < length; i++) {
            data[i] = (byte) value;
        }
        return data;
    }

    private static byte[] loadRealRom(String fileName) {
        Path path = findRepoRoot().resolve("assets").resolve("roms").resolve(fileName);
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new RuntimeException("Unable to load bundled ROM file: " + path, e);
        }
    }

    private static Path findRepoRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null) {
            if (Files.isDirectory(dir.resolve("assets").resolve("roms"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new RuntimeException(
                "Could not locate an assets/roms directory above " + Paths.get("").toAbsolutePath());
    }

    public Memory getMemory() {
        return machine.getMemory();
    }

    /** Runs a single machine cycle (VIC, then CPU, then VIA1, then VIA2, ...). */
    public void cycle() {
        machine.emulateCycle();
    }

    public void cycles(int count) {
        for (int i = 0; i < count; i++) {
            cycle();
        }
    }

    /** Runs cycles until (and including) the one that completes a frame. */
    public void frame() {
        machine.update();
    }

    public void frames(int count) {
        for (int i = 0; i < count; i++) {
            frame();
        }
    }
}
