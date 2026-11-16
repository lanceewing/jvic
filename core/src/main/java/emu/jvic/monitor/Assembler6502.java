package emu.jvic.monitor;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Assembles one 6502 instruction in VICE's syntax, using the same opcode table as
 * the disassembler ({@link Opcodes6502}), so every instruction that d shows can be
 * typed back in, undocumented ones included.
 *
 * <p>Differences from VICE 3.10's assembler: a lone "A" operand is the accumulator
 * (VICE reads "ROL A" as "ROL $0A"), labels are accepted as operands, and a few
 * common alternative names for undocumented opcodes are accepted (ISC, ALR, AXS,
 * KIL, HLT, XAA, LAR, DOP, TOP, SKB, SKW).
 */
final class Assembler6502 {

    /** VICE's message when there is no opcode for the mnemonic and operand. */
    static final String NOT_VALID = "Instruction not valid.";

    /** VICE's message for a branch target out of reach. */
    static final String BRANCH_TOO_FAR = "Branch offset too large.";

    /** The first opcode for each mnemonic and addressing mode, e.g. "LDA/2" to $A9. */
    private static final Map<String, Integer> OPCODES = new HashMap<>();

    private static final Map<String, String> ALIASES = new HashMap<>();

    static {
        for (int opcode = 0; opcode < 256; opcode++) {
            String key = Opcodes6502.mnemonic(opcode) + "/" + Opcodes6502.mode(opcode);
            if (!OPCODES.containsKey(key)) {
                OPCODES.put(key, opcode);
            }
        }
        String[] aliases = { "ISC", "ISB", "ALR", "ASR", "AXS", "SBX", "KIL", "JAM", "HLT", "JAM", "XAA", "ANE",
            "LAR", "LAS", "DOP", "NOOP", "TOP", "NOOP", "SKB", "NOOP", "SKW", "NOOP" };
        for (int i = 0; i < aliases.length; i += 2) {
            ALIASES.put(aliases[i], aliases[i + 1]);
        }
    }

    /** An instruction that parsed but can't be assembled; the message is VICE's. */
    static final class AssemblyException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        AssemblyException(String message) {
            super(message);
        }
    }

    private Assembler6502() {
    }

    /**
     * Assembles one instruction.
     *
     * @param text    The instruction, e.g. "lda ($fb),y".
     * @param address Where it will go (for branches).
     * @param engine  For the radix and labels.
     * @param space   The memspace, for labels.
     * @param column  The instruction's column in the command line, for errors.
     *
     * @return The instruction's bytes.
     *
     * @throws MonitorException   if it can't be parsed (Wrong syntax).
     * @throws AssemblyException  if there is no such instruction, or a branch is too far.
     */
    static int[] assemble(String text, int address, MonitorEngine engine, AddressSpace space, int column) {
        String trimmed = text.trim();
        int letters = 0;
        while ((letters < trimmed.length()) && Character.isLetter(trimmed.charAt(letters))) {
            letters++;
        }
        if (letters == 0) {
            throw new MonitorException(MonitorException.WRONG_SYNTAX, column);
        }
        String mnemonic = trimmed.substring(0, letters).toUpperCase(Locale.ROOT);
        mnemonic = ALIASES.getOrDefault(mnemonic, mnemonic);
        String operand = trimmed.substring(letters).trim();
        String upper = operand.toUpperCase(Locale.ROOT);

        if (operand.isEmpty()) {
            Integer opcode = find(mnemonic, Opcodes6502.IMP);
            if (opcode == null) {
                opcode = find(mnemonic, Opcodes6502.ACC);
            }
            return single(opcode);
        }
        if (upper.equals("A") && (find(mnemonic, Opcodes6502.ACC) != null)) {
            return single(find(mnemonic, Opcodes6502.ACC));
        }
        if (operand.startsWith("#")) {
            int value = evaluate(operand.substring(1), engine, space, column);
            return withByte(find(mnemonic, Opcodes6502.IMM), value);
        }
        if (operand.startsWith("(")) {
            if (upper.endsWith(",X)")) {
                int value = evaluate(operand.substring(1, operand.length() - 3), engine, space, column);
                return withByte(find(mnemonic, Opcodes6502.IZX), value);
            }
            if (upper.endsWith("),Y")) {
                int value = evaluate(operand.substring(1, operand.length() - 3), engine, space, column);
                return withByte(find(mnemonic, Opcodes6502.IZY), value);
            }
            if (upper.endsWith(")") && (find(mnemonic, Opcodes6502.IND) != null)) {
                int value = evaluate(operand.substring(1, operand.length() - 1), engine, space, column);
                return withWord(find(mnemonic, Opcodes6502.IND), value);
            }
        }
        int zeroPageMode = Opcodes6502.ZP;
        int absoluteMode = Opcodes6502.ABS;
        String expression = operand;
        if (upper.endsWith(",X")) {
            zeroPageMode = Opcodes6502.ZPX;
            absoluteMode = Opcodes6502.ABX;
            expression = operand.substring(0, operand.length() - 2);
        } else if (upper.endsWith(",Y")) {
            zeroPageMode = Opcodes6502.ZPY;
            absoluteMode = Opcodes6502.ABY;
            expression = operand.substring(0, operand.length() - 2);
        }
        int value = evaluate(expression, engine, space, column);
        if ((zeroPageMode == Opcodes6502.ZP) && (find(mnemonic, Opcodes6502.REL) != null)) {
            int offset = value - ((address + 2) & 0xFFFF);
            if (offset > 0x8000) {
                offset -= 0x10000;
            } else if (offset < -0x8000) {
                offset += 0x10000;
            }
            if ((offset < -128) || (offset > 127)) {
                throw new AssemblyException(BRANCH_TOO_FAR);
            }
            return withByte(find(mnemonic, Opcodes6502.REL), offset);
        }
        Integer zeroPage = find(mnemonic, zeroPageMode);
        if ((value >= 0) && (value < 0x100) && (zeroPage != null)) {
            return withByte(zeroPage, value);
        }
        return withWord(find(mnemonic, absoluteMode), value);
    }

    private static Integer find(String mnemonic, int mode) {
        return OPCODES.get(mnemonic + "/" + mode);
    }

    private static int[] single(Integer opcode) {
        if (opcode == null) {
            throw new AssemblyException(NOT_VALID);
        }
        return new int[] { opcode };
    }

    private static int[] withByte(Integer opcode, int value) {
        if (opcode == null) {
            throw new AssemblyException(NOT_VALID);
        }
        return new int[] { opcode, value & 0xFF };
    }

    private static int[] withWord(Integer opcode, int value) {
        if (opcode == null) {
            throw new AssemblyException(NOT_VALID);
        }
        return new int[] { opcode, value & 0xFF, (value >> 8) & 0xFF };
    }

    /** Evaluates an operand expression, or a label. */
    private static int evaluate(String text, MonitorEngine engine, AddressSpace space, int column) {
        String trimmed = text.trim();
        if (trimmed.startsWith(".")) {
            Integer address = space.getLabels().get(trimmed);
            if (address == null) {
                throw new MonitorException("Symbol " + trimmed + " not found.", column);
            }
            return address;
        }
        Parser parser = new Parser(trimmed, 0, engine.getRadix());
        try {
            int value = parser.parseExpression();
            if (!parser.atEnd()) {
                throw new MonitorException(MonitorException.WRONG_SYNTAX, column);
            }
            return value;
        } catch (MonitorException e) {
            throw new MonitorException(MonitorException.WRONG_SYNTAX, column);
        }
    }
}
