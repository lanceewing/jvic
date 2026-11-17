package emu.jvic.monitor;

import emu.jvic.cpu.Cpu6502;

/**
 * A ring buffer of the instructions a CPU has executed, for chis: each entry has the
 * address, the instruction's bytes, the registers as the instruction started, and
 * the CPU cycle count.
 */
final class CpuHistory {

    /** How many instructions are kept: as many as xvic keeps by default. */
    static final int SIZE = 8192;

    private final int[] addresses = new int[SIZE];
    /** Opcode, operand bytes: opcode | b1 << 8 | b2 << 16. */
    private final int[] bytes = new int[SIZE];
    /** A | X << 8 | Y << 16 | SP << 24. */
    private final int[] registers = new int[SIZE];
    private final byte[] status = new byte[SIZE];
    private final long[] cycles = new long[SIZE];

    private int next;
    private int count;

    /**
     * Records an instruction about to be executed.
     */
    void record(AddressSpace space, int address) {
        Cpu6502 cpu = space.getCpu();
        int i = next;
        addresses[i] = address;
        bytes[i] = space.read(address, false) | (space.read(address + 1, false) << 8)
                | (space.read(address + 2, false) << 16);
        registers[i] = cpu.getAccumulator() | (cpu.getIndexRegisterX() << 8) | (cpu.getIndexRegisterY() << 16)
                | (cpu.getStackPointer() << 24);
        status[i] = (byte) cpu.getProcessorStatus();
        cycles[i] = cpu.getTotalCycles();
        next = (next + 1) & (SIZE - 1);
        if (count < SIZE) {
            count++;
        }
    }

    int size() {
        return count;
    }

    void clear() {
        count = 0;
        next = 0;
    }

    /** The index of the n-th entry from the oldest, for the other getters. */
    private int index(int n) {
        return (next - count + n) & (SIZE - 1);
    }

    long getCycle(int n) {
        return cycles[index(n)];
    }

    int getAddress(int n) {
        return addresses[index(n)];
    }

    /** The instruction's bytes: opcode | b1 << 8 | b2 << 16. */
    int getBytes(int n) {
        return bytes[index(n)];
    }

    /** A | X << 8 | Y << 16 | SP << 24. */
    int getRegisters(int n) {
        return registers[index(n)];
    }

    int getStatus(int n) {
        return status[index(n)] & 0xFF;
    }

    /**
     * The chis line for the n-th entry from the oldest, e.g.
     * ".C:e5e8  A5 C6       LDA $C6        A:00 X:00 Y:0a SP:f3 ..-...Z.       575937".
     */
    String line(AddressSpace space, int n) {
        int i = index(n);
        int opcode = bytes[i] & 0xFF;
        int[] instruction = new int[Opcodes6502.length(opcode)];
        for (int b = 0; b < instruction.length; b++) {
            instruction[b] = (bytes[i] >> (8 * b)) & 0xFF;
        }
        StringBuilder out = new StringBuilder();
        Disassembler6502.appendAddressAndBytes(space, addresses[i], instruction, out, "  ");
        out.append(Format.padRight(Disassembler6502.instruction(addresses[i], instruction, space.getLabels()), 15));
        int regs = registers[i];
        out.append("A:").append(Format.hex2(regs)).append(" X:").append(Format.hex2(regs >> 8));
        out.append(" Y:").append(Format.hex2(regs >> 16)).append(" SP:").append(Format.hex2(regs >>> 24));
        out.append(' ').append(Disassembler6502.flags(status[i] & 0xFF));
        out.append(' ').append(Format.decimal(cycles[i], 12)).append('\n');
        return out.toString();
    }
}
