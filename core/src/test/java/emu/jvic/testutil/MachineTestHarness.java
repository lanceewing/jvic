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
        this(machineType, ramType, basicRom, kernalRom, charRom, dos1541Rom, null);
    }

    private MachineTestHarness(MachineType machineType, RamType ramType, byte[] basicRom, byte[] kernalRom,
            byte[] charRom, byte[] dos1541Rom, emu.jvic.Program program) {
        this.machineType = machineType;
        keyboardMatrix = new TestKeyboardMatrix();
        pixelData = new RecordingPixelData();
        pixelData.init(machineType.getTotalScreenWidth() * 2, machineType.getTotalScreenHeight() * 2);

        machine = new Machine(new NoOpSoundGenerator(), keyboardMatrix, pixelData);
        machine.init(basicRom, kernalRom, charRom, dos1541Rom, program, machineType, ramType, null);
    }

    public static MachineTestHarness withRealRoms(MachineType machineType, RamType ramType) {
        byte[] basicRom = loadRealRom("basic.rom");
        byte[] kernalRom = loadRealRom(machineType.isPAL() ? "kernal_pal.rom" : "kernal_ntsc.rom");
        byte[] charRom = loadRealRom("char.rom");
        byte[] dos1541Rom = loadRealRom("dos1541.rom");
        return new MachineTestHarness(machineType, ramType, basicRom, kernalRom, charRom, dos1541Rom);
    }

    public static MachineTestHarness withRealRomsAndProgram(MachineType machineType, RamType ramType,
            emu.jvic.Program program) {
        byte[] basicRom = loadRealRom("basic.rom");
        byte[] kernalRom = loadRealRom(machineType.isPAL() ? "kernal_pal.rom" : "kernal_ntsc.rom");
        byte[] charRom = loadRealRom("char.rom");
        byte[] dos1541Rom = loadRealRom("dos1541.rom");
        return new MachineTestHarness(machineType, ramType, basicRom, kernalRom, charRom, dos1541Rom, program);
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

    /** Base address of the small scratch KERNAL-calling routine built by {@link #loadViaKernal}. */
    public static final int KERNAL_CALL_ROUTINE_ADDRESS = 0x1500;

    /** Address of the filename buffer used by {@link #loadViaKernal} (up to 0x40 bytes). */
    public static final int KERNAL_CALL_FILENAME_ADDRESS = 0x1580;

    /**
     * The outcome of a {@link #loadViaKernal} call.
     */
    public static class KernalLoadResult {
        /** True if the CPU reached the parked instruction within the cycle budget. */
        public final boolean completed;
        /** True if the KERNAL LOAD call reported success (carry clear). Only meaningful if completed. */
        public final boolean success;
        /** The accumulator on return - a CBM DOS/BASIC error code if !success. */
        public final int accumulator;
        /** The X register on return. */
        public final int x;
        /** The Y register on return. */
        public final int y;
        /** The number of emulated cycles the call took. */
        public final int cyclesUsed;

        KernalLoadResult(boolean completed, boolean success, int accumulator, int x, int y, int cyclesUsed) {
            this.completed = completed;
            this.success = success;
            this.accumulator = accumulator;
            this.x = x;
            this.y = y;
            this.cyclesUsed = cyclesUsed;
        }
    }

    /**
     * Drives a real KERNAL LOAD (via the real, documented, stable jump-table entries
     * SETNAM $FFBD, SETLFS $FFBA, LOAD $FFD5 - unchanged across the whole CBM 8-bit
     * KERNAL family) by injecting a tiny hand-assembled 6502 routine into free RAM
     * and redirecting the CPU there with {@code Cpu6502.setProgramCounter}, the same
     * mechanism {@code CpuTestHarness} uses. This exercises the real LOAD routine -
     * and, for device 8, the real IEC serial protocol and {@code C1541Drive} - without
     * needing to simulate interactive keyboard/BASIC command-line input (which turns
     * out to be unsuitable for non-interactive test use - see the class-level notes).
     *
     * <p>The routine parks in a tight self-JMP loop once LOAD returns; this method runs
     * cycles until that loop is observed to repeat (not just glimpsed once, since the
     * CPU's pipelined fetch briefly shows the park address's own instruction bytes
     * as a transient artifact during the JSR itself) or the cycle budget is exhausted.
     *
     * @param filename    The filename to load (e.g. "*" for the first file on disk).
     * @param device      The device number (8 for disk, 1 for tape).
     * @param loadAddress The RAM address to load the file's contents into (secondary
     *                    address 0: "load to this address", ignoring the file's own
     *                    embedded load-address header bytes). Must not overlap
     *                    {@link #KERNAL_CALL_ROUTINE_ADDRESS}..{@link #KERNAL_CALL_FILENAME_ADDRESS} + 0x40.
     * @param maxCycles   The maximum number of emulated cycles to run before giving up.
     */
    public KernalLoadResult loadViaKernal(String filename, int device, int loadAddress, int maxCycles) {
        int[] mem = getMemory().getMemoryArray();
        int routine = KERNAL_CALL_ROUTINE_ADDRESS;
        int filenameAddress = KERNAL_CALL_FILENAME_ADDRESS;

        int pos = routine;
        mem[pos++] = 0xA9; mem[pos++] = filename.length(); // LDA #len
        mem[pos++] = 0xA2; mem[pos++] = filenameAddress & 0xFF; // LDX #<filename
        mem[pos++] = 0xA0; mem[pos++] = (filenameAddress >> 8) & 0xFF; // LDY #>filename
        mem[pos++] = 0x20; mem[pos++] = 0xBD; mem[pos++] = 0xFF; // JSR SETNAM
        mem[pos++] = 0xA9; mem[pos++] = 0x01; // LDA #1 (logical file number)
        mem[pos++] = 0xA2; mem[pos++] = device & 0xFF; // LDX #device
        // LDY #0 (secondary address 0: load to the given address, ignoring the file's own
        // embedded header address - confirmed empirically; secondary address 1 does the
        // opposite (loads to the file's own header address, ignoring X/Y passed to LOAD)).
        mem[pos++] = 0xA0; mem[pos++] = 0x00;
        mem[pos++] = 0x20; mem[pos++] = 0xBA; mem[pos++] = 0xFF; // JSR SETLFS
        mem[pos++] = 0xA9; mem[pos++] = 0x00; // LDA #0 (LOAD, not verify)
        mem[pos++] = 0xA2; mem[pos++] = loadAddress & 0xFF; // LDX #<loadAddress
        mem[pos++] = 0xA0; mem[pos++] = (loadAddress >> 8) & 0xFF; // LDY #>loadAddress
        mem[pos++] = 0x20; mem[pos++] = 0xD5; mem[pos++] = 0xFF; // JSR LOAD
        int parkAddress = pos;
        mem[pos++] = 0x4C; mem[pos++] = parkAddress & 0xFF; mem[pos++] = (parkAddress >> 8) & 0xFF; // JMP parkAddress

        for (int i = 0; i < filename.length(); i++) {
            mem[filenameAddress + i] = filename.charAt(i) & 0xFF;
        }

        machine.getCpu().setProgramCounter(routine);

        int consecutiveParkHits = 0;
        int lastParkCycle = -1000;
        int cyclesRun = 0;
        for (; cyclesRun < maxCycles; cyclesRun++) {
            cycle();
            if (machine.getCpu().getProgramCounter() == parkAddress + 1) {
                consecutiveParkHits = (cyclesRun - lastParkCycle <= 4) ? consecutiveParkHits + 1 : 1;
                lastParkCycle = cyclesRun;
                if (consecutiveParkHits >= 5) {
                    break;
                }
            }
        }

        boolean completed = consecutiveParkHits >= 5;
        int status = machine.getCpu().getProcessorStatus();
        boolean success = completed && ((status & 0x01) == 0); // Carry clear = success.
        return new KernalLoadResult(completed, success, machine.getCpu().getAccumulator(),
                machine.getCpu().getIndexRegisterX(), machine.getCpu().getIndexRegisterY(), cyclesRun);
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
