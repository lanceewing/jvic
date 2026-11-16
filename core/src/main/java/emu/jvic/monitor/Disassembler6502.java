package emu.jvic.monitor;

import emu.jvic.cpu.Cpu6502;

/**
 * Disassembles 6502 instructions in VICE's format, using {@link Opcodes6502}.
 *
 * <pre>
 * .C:e000  0F DC A5    SLO $A5DC
 * .C:e006  90 03       BCC $E00B
 * </pre>
 *
 * The address is in lower case and the bytes and operand in upper case, as in VICE.
 */
final class Disassembler6502 {

    private Disassembler6502() {
    }

    /**
     * Appends one line of disassembly, ending with a newline.
     *
     * @param space       The memspace to read from.
     * @param address     The address of the instruction.
     * @param sideEffects Whether reads may have side effects (sidefx).
     * @param out         Where to append the line.
     *
     * @return The length of the instruction in bytes.
     */
    static int disassembleLine(AddressSpace space, int address, boolean sideEffects, StringBuilder out) {
        address &= 0xFFFF;
        String label = space.getLabels().nameAt(address);
        if (label != null) {
            // VICE's label line, e.g. ".C:1000   .start:"
            out.append('.').append(space.getName()).append(':').append(Format.hex4(address)).append("   ")
                    .append(label).append(":\n");
        }
        int[] bytes = readInstruction(space, address, sideEffects);
        appendAddressAndBytes(space, address, bytes, out, "  ");
        out.append(instruction(address, bytes, space.getLabels())).append('\n');
        return bytes.length;
    }

    /**
     * One line of disassembly as backtrace shows it, with three spaces after the
     * address and no label line, ending with a newline.
     */
    static String backtraceLine(AddressSpace space, int address) {
        address &= 0xFFFF;
        int[] bytes = readInstruction(space, address, false);
        StringBuilder out = new StringBuilder();
        appendAddressAndBytes(space, address, bytes, out, "   ");
        return out.append(instruction(address, bytes, space.getLabels())).append('\n').toString();
    }

    /**
     * The line VICE prints for an instruction with the CPU's state, after a checkpoint
     * hit, a trace, or a step:
     *
     * <pre>
     * .C:fd27  20 3F FD    JSR $FD3F      - A:00 X:FF Y:00 SP:ff N.-..I..         14
     * </pre>
     *
     * The flags show a letter for each set bit and '.' for each clear one (bit 5 is
     * always '-'), and the last column is the memspace's stopwatch.
     */
    static String stateLine(AddressSpace space, int address) {
        address &= 0xFFFF;
        int[] bytes = readInstruction(space, address, false);
        StringBuilder out = new StringBuilder();
        appendAddressAndBytes(space, address, bytes, out, "  ");
        out.append(Format.padRight(instruction(address, bytes, space.getLabels()), 15));
        Cpu6502 cpu = space.getCpu();
        out.append("- A:").append(Format.hex2Upper(cpu.getAccumulator()));
        out.append(" X:").append(Format.hex2Upper(cpu.getIndexRegisterX()));
        out.append(" Y:").append(Format.hex2Upper(cpu.getIndexRegisterY()));
        out.append(" SP:").append(Format.hex2(cpu.getStackPointer()));
        out.append(' ').append(flags(cpu.getProcessorStatus()));
        out.append(' ').append(Format.decimal(space.getStopwatch(), 10)).append('\n');
        return out.toString();
    }

    /** The status flags as VICE shows them on a state line, e.g. "N.-..I.." */
    static String flags(int status) {
        String letters = "NV-BDIZC";
        StringBuilder text = new StringBuilder(8);
        for (int bit = 7; bit >= 0; bit--) {
            char letter = letters.charAt(7 - bit);
            text.append((letter == '-') ? '-' : (((status >> bit) & 1) != 0) ? letter : '.');
        }
        return text.toString();
    }

    static int[] readInstruction(AddressSpace space, int address, boolean sideEffects) {
        int opcode = space.read(address, sideEffects);
        int[] bytes = new int[Opcodes6502.length(opcode)];
        bytes[0] = opcode;
        for (int i = 1; i < bytes.length; i++) {
            bytes[i] = space.read((address + i) & 0xFFFF, sideEffects);
        }
        return bytes;
    }

    static void appendAddressAndBytes(AddressSpace space, int address, int[] bytes, StringBuilder out,
            String gap) {
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) {
                hex.append(' ');
            }
            hex.append(Format.hex2Upper(bytes[i]));
        }
        out.append('.').append(space.getName()).append(':').append(Format.hex4(address)).append(gap);
        out.append(Format.padRight(hex.toString(), 12));
    }

    /**
     * Returns the instruction text, e.g. "LDA ($12),Y", for the given bytes.
     *
     * @param address The instruction's address (for branch targets).
     * @param bytes   The opcode and its operand bytes.
     */
    static String instruction(int address, int[] bytes) {
        return instruction(address, bytes, null);
    }

    /**
     * As {@link #instruction(int, int[])}, showing a label in place of an address that
     * has one, e.g. "JMP .loop".
     */
    static String instruction(int address, int[] bytes, LabelTable labels) {
        int opcode = bytes[0];
        String mnemonic = Opcodes6502.mnemonic(opcode);
        String operand = operand(address, opcode, bytes, labels);
        return operand.isEmpty() ? mnemonic : mnemonic + " " + operand;
    }

    private static String operand(int address, int opcode, int[] bytes, LabelTable labels) {
        int lo = (bytes.length > 1) ? bytes[1] : 0;
        int word = (bytes.length > 2) ? (lo | (bytes[2] << 8)) : lo;
        switch (Opcodes6502.mode(opcode)) {
            case Opcodes6502.ACC: return "A";
            case Opcodes6502.IMM: return "#$" + Format.hex2Upper(lo);
            case Opcodes6502.ZP: return zeroPage(lo, labels);
            case Opcodes6502.ZPX: return zeroPage(lo, labels) + ",X";
            case Opcodes6502.ZPY: return zeroPage(lo, labels) + ",Y";
            case Opcodes6502.REL: return absolute((address + 2 + (byte) lo) & 0xFFFF, labels);
            case Opcodes6502.ABS: return absolute(word, labels);
            case Opcodes6502.ABX: return absolute(word, labels) + ",X";
            case Opcodes6502.ABY: return absolute(word, labels) + ",Y";
            case Opcodes6502.IND: return "(" + absolute(word, labels) + ")";
            case Opcodes6502.IZX: return "(" + zeroPage(lo, labels) + ",X)";
            case Opcodes6502.IZY: return "(" + zeroPage(lo, labels) + "),Y";
            default: return "";
        }
    }

    private static String zeroPage(int address, LabelTable labels) {
        String label = (labels != null) ? labels.nameAt(address) : null;
        return (label != null) ? label : "$" + Format.hex2Upper(address);
    }

    private static String absolute(int address, LabelTable labels) {
        String label = (labels != null) ? labels.nameAt(address) : null;
        return (label != null) ? label : "$" + Format.hex4Upper(address);
    }
}
