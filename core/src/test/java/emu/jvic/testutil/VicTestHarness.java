package emu.jvic.testutil;

import emu.jvic.MachineType;
import emu.jvic.cpu.Cpu6502;
import emu.jvic.memory.Memory;
import emu.jvic.video.Vic;
import emu.jvic.video.Vic6560;
import emu.jvic.video.Vic6561;

/**
 * Test harness for driving a {@link Vic6560} (NTSC) or {@link Vic6561} (PAL) in
 * isolation, backed by a flat 64KB all-RAM {@link Memory}. VIC chip registers are
 * memory-mapped at $9000-$900F (address bit masking mirrors them across
 * $9000-$93FF, handled internally by {@code Vic.readMemory/writeMemory}); screen,
 * character and colour RAM contents are just poked directly into the shared
 * backing array at whatever CPU-visible address the test scenario needs (see
 * {@code Vic.VIC_MEM_TABLE} for how the VIC's 14-bit internal address space maps
 * onto real VIC-20 CPU addresses).
 */
public class VicTestHarness {

    public static final int REG_BASE = 0x9000;

    public final Cpu6502 cpu;
    public final Memory memory;
    public final int[] mem;
    public final Vic vic;
    public final RecordingPixelData pixelData;
    public final MachineType machineType;

    public VicTestHarness(MachineType machineType) {
        this.machineType = machineType;
        cpu = new Cpu6502(null);
        memory = new Memory(cpu, null, true);
        mem = memory.getMemoryArray();
        pixelData = new RecordingPixelData();
        // Generously oversized so no putPixel call is ever out of bounds, even
        // during blanking-region bookkeeping.
        pixelData.init(machineType.getTotalScreenWidth() * 2, machineType.getTotalScreenHeight() * 2);

        if (machineType == MachineType.NTSC) {
            vic = new Vic6560(pixelData, machineType, null);
        } else {
            vic = new Vic6561(pixelData, machineType, null);
        }
        vic.setMemory(memory);
    }

    public void writeRegister(int reg, int value) {
        vic.writeMemory(REG_BASE + reg, value);
    }

    public int readRegister(int reg) {
        return vic.readMemory(REG_BASE + reg);
    }

    /** Directly pokes a byte into the shared memory array at a real CPU address. */
    public void poke(int address, int value) {
        mem[address & 0xFFFF] = value & 0xFF;
    }

    public int peek(int address) {
        return mem[address & 0xFFFF] & 0xFF;
    }

    /** Runs one VIC cycle; returns true if a frame was just completed. */
    public boolean cycle() {
        return vic.emulateCycle();
    }

    /** Runs the given number of VIC cycles. */
    public void cycles(int count) {
        for (int i = 0; i < count; i++) {
            cycle();
        }
    }

    /** Runs cycles until (and including) the one that completes a frame. */
    public void runToEndOfFrame() {
        while (!cycle()) {
            // keep going
        }
    }
}
