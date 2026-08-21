package emu.jvic.testutil;

import emu.jvic.cpu.Cpu6502;
import emu.jvic.memory.Memory;

/**
 * Test harness for driving {@link Cpu6502} in isolation, backed by a flat 64KB
 * all-RAM {@link Memory} (via the existing {@code Memory(Cpu6502, Snapshot, boolean)}
 * constructor with {@code allRam=true}).
 *
 * <p>The CPU is cycle-stepped and pipelined: the opcode fetch for instruction N+1
 * happens as part of completing instruction N (see {@code Cpu6502.emulateCycle()},
 * case {@code EXECUTE_LAST}). This was confirmed empirically: calling
 * {@code cpu.step()} once only performs an opcode fetch ("priming"); the next call
 * to {@code cpu.step()} then runs that instruction to completion (including any
 * addressing-mode cycles) and, as its very last cycle, fetches the following
 * opcode. The cycle delta of that second {@code step()} call is exactly the
 * instruction's real hardware cycle count (this matches published 6502 cycle
 * tables, verified against NOP=2, LDA #imm=2, LDA abs=4).
 *
 * <p><b>Caveat for PC assertions:</b> because that final cycle already fetches the
 * next opcode, {@code cpu.getProgramCounter()} immediately after {@link
 * #runInstruction()} is always one byte further along than wherever the
 * instruction itself "landed" (e.g. after a JMP $0400, PC reads $0401, not $0400).
 * This matters for branches/jumps/RTS/RTI; it's irrelevant for instructions that
 * don't alter control flow.
 *
 * <p>Typical usage:
 * <pre>
 *   CpuTestHarness h = new CpuTestHarness();
 *   h.prepareInstruction(0x0300, 0xA9, 0x7F); // LDA #$7F
 *   h.cpu.setCarryFlag(true);                 // set up pre-state AFTER priming
 *   long cycles = h.runInstruction();
 *   assertEquals(0x7F, h.cpu.getAccumulator());
 *   assertEquals(2, cycles);
 * </pre>
 */
public class CpuTestHarness {

    public final Cpu6502 cpu;
    public final Memory memory;
    public final int[] mem;

    public CpuTestHarness() {
        cpu = new Cpu6502(null);
        memory = new Memory(cpu, null, true);
        mem = memory.getMemoryArray();
        setResetVector(0x0300);
        cpu.reset();
    }

    public void setResetVector(int address) {
        writeByte(0xFFFC, address & 0xFF);
        writeByte(0xFFFD, (address >> 8) & 0xFF);
    }

    public void writeByte(int address, int value) {
        mem[address & 0xFFFF] = value & 0xFF;
    }

    public int readByte(int address) {
        return mem[address & 0xFFFF] & 0xFF;
    }

    public void writeWord(int address, int value) {
        writeByte(address, value & 0xFF);
        writeByte(address + 1, (value >> 8) & 0xFF);
    }

    public int readWord(int address) {
        return readByte(address) | (readByte(address + 1) << 8);
    }

    /**
     * Writes the given bytes (opcode + operand(s)) starting at the given address.
     */
    public void loadBytes(int address, int... bytes) {
        for (int i = 0; i < bytes.length; i++) {
            writeByte(address + i, bytes[i]);
        }
    }

    /**
     * Resets the CPU (clearing all registers/flags/pipeline state), writes the
     * given instruction bytes at the given address, sets PC to that address, and
     * "primes" the pipeline by fetching the opcode. Register/flag state for the
     * test should be set up AFTER calling this method (since {@link Cpu6502#reset()}
     * zeroes them), and BEFORE calling {@link #runInstruction()}.
     */
    public void prepareInstruction(int address, int... bytes) {
        loadBytes(address, bytes);
        cpu.reset();
        cpu.setProgramCounter(address);
        cpu.step();
    }

    /**
     * Runs the instruction set up by {@link #prepareInstruction} to completion.
     *
     * @return the exact number of real hardware cycles the instruction took.
     */
    public long runInstruction() {
        long before = cpu.getTotalCycles();
        cpu.step();
        return cpu.getTotalCycles() - before;
    }

    /**
     * Convenience combining {@link #prepareInstruction} immediately followed by
     * {@link #runInstruction()}, for instructions that don't need custom
     * pre-instruction register/flag setup beyond what's passed in.
     */
    public long execute(int address, int... bytes) {
        prepareInstruction(address, bytes);
        return runInstruction();
    }
}
