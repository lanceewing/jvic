package emu.jvic.monitor;

/**
 * The 6502 opcode table shared by the monitor's disassembler (and later its
 * assembler): a mnemonic and addressing mode for all 256 opcodes, including the
 * undocumented ones, which jvic's CPU emulates.
 *
 * <p>The mnemonics and modes are VICE 3.10's, generated from xvic's disassembly of
 * every opcode, so that jvic's disassembly matches VICE's: for example NOOP for the
 * undocumented NOPs, USBC for $EB, SHS for $9B, and BRK as a one-byte instruction.
 */
final class Opcodes6502 {

    /** Implied: no operand. */
    static final int IMP = 0;
    /** Accumulator: "ASL A". */
    static final int ACC = 1;
    /** Immediate: "#$12". */
    static final int IMM = 2;
    /** Zero page: "$12". */
    static final int ZP = 3;
    /** Zero page,X: "$12,X". */
    static final int ZPX = 4;
    /** Zero page,Y: "$12,Y". */
    static final int ZPY = 5;
    /** Relative (branches): shown as the target address. */
    static final int REL = 6;
    /** Absolute: "$1234". */
    static final int ABS = 7;
    /** Absolute,X: "$1234,X". */
    static final int ABX = 8;
    /** Absolute,Y: "$1234,Y". */
    static final int ABY = 9;
    /** Indirect (JMP only): "($1234)". */
    static final int IND = 10;
    /** Indexed indirect: "($12,X)". */
    static final int IZX = 11;
    /** Indirect indexed: "($12),Y". */
    static final int IZY = 12;

    /** The instruction length in bytes for each addressing mode. */
    private static final int[] MODE_LENGTHS = { 1, 1, 2, 2, 2, 2, 2, 3, 3, 3, 3, 2, 2 };

    static final String[] MNEMONICS = {
        "BRK", "ORA", "JAM", "SLO", "NOOP", "ORA", "ASL", "SLO", // 00
        "PHP", "ORA", "ASL", "ANC", "NOOP", "ORA", "ASL", "SLO", // 08
        "BPL", "ORA", "JAM", "SLO", "NOOP", "ORA", "ASL", "SLO", // 10
        "CLC", "ORA", "NOOP", "SLO", "NOOP", "ORA", "ASL", "SLO", // 18
        "JSR", "AND", "JAM", "RLA", "BIT", "AND", "ROL", "RLA", // 20
        "PLP", "AND", "ROL", "ANC", "BIT", "AND", "ROL", "RLA", // 28
        "BMI", "AND", "JAM", "RLA", "NOOP", "AND", "ROL", "RLA", // 30
        "SEC", "AND", "NOOP", "RLA", "NOOP", "AND", "ROL", "RLA", // 38
        "RTI", "EOR", "JAM", "SRE", "NOOP", "EOR", "LSR", "SRE", // 40
        "PHA", "EOR", "LSR", "ASR", "JMP", "EOR", "LSR", "SRE", // 48
        "BVC", "EOR", "JAM", "SRE", "NOOP", "EOR", "LSR", "SRE", // 50
        "CLI", "EOR", "NOOP", "SRE", "NOOP", "EOR", "LSR", "SRE", // 58
        "RTS", "ADC", "JAM", "RRA", "NOOP", "ADC", "ROR", "RRA", // 60
        "PLA", "ADC", "ROR", "ARR", "JMP", "ADC", "ROR", "RRA", // 68
        "BVS", "ADC", "JAM", "RRA", "NOOP", "ADC", "ROR", "RRA", // 70
        "SEI", "ADC", "NOOP", "RRA", "NOOP", "ADC", "ROR", "RRA", // 78
        "NOOP", "STA", "NOOP", "SAX", "STY", "STA", "STX", "SAX", // 80
        "DEY", "NOOP", "TXA", "ANE", "STY", "STA", "STX", "SAX", // 88
        "BCC", "STA", "JAM", "SHA", "STY", "STA", "STX", "SAX", // 90
        "TYA", "STA", "TXS", "SHS", "SHY", "STA", "SHX", "SHA", // 98
        "LDY", "LDA", "LDX", "LAX", "LDY", "LDA", "LDX", "LAX", // A0
        "TAY", "LDA", "TAX", "LXA", "LDY", "LDA", "LDX", "LAX", // A8
        "BCS", "LDA", "JAM", "LAX", "LDY", "LDA", "LDX", "LAX", // B0
        "CLV", "LDA", "TSX", "LAS", "LDY", "LDA", "LDX", "LAX", // B8
        "CPY", "CMP", "NOOP", "DCP", "CPY", "CMP", "DEC", "DCP", // C0
        "INY", "CMP", "DEX", "SBX", "CPY", "CMP", "DEC", "DCP", // C8
        "BNE", "CMP", "JAM", "DCP", "NOOP", "CMP", "DEC", "DCP", // D0
        "CLD", "CMP", "NOOP", "DCP", "NOOP", "CMP", "DEC", "DCP", // D8
        "CPX", "SBC", "NOOP", "ISB", "CPX", "SBC", "INC", "ISB", // E0
        "INX", "SBC", "NOP", "USBC", "CPX", "SBC", "INC", "ISB", // E8
        "BEQ", "SBC", "JAM", "ISB", "NOOP", "SBC", "INC", "ISB", // F0
        "SED", "SBC", "NOOP", "ISB", "NOOP", "SBC", "INC", "ISB", // F8
    };

    static final int[] MODES = {
        IMP, IZX, IMP, IZX, ZP, ZP, ZP, ZP, // 00
        IMP, IMM, ACC, IMM, ABS, ABS, ABS, ABS, // 08
        REL, IZY, IMP, IZY, ZPX, ZPX, ZPX, ZPX, // 10
        IMP, ABY, IMP, ABY, ABX, ABX, ABX, ABX, // 18
        ABS, IZX, IMP, IZX, ZP, ZP, ZP, ZP, // 20
        IMP, IMM, ACC, IMM, ABS, ABS, ABS, ABS, // 28
        REL, IZY, IMP, IZY, ZPX, ZPX, ZPX, ZPX, // 30
        IMP, ABY, IMP, ABY, ABX, ABX, ABX, ABX, // 38
        IMP, IZX, IMP, IZX, ZP, ZP, ZP, ZP, // 40
        IMP, IMM, ACC, IMM, ABS, ABS, ABS, ABS, // 48
        REL, IZY, IMP, IZY, ZPX, ZPX, ZPX, ZPX, // 50
        IMP, ABY, IMP, ABY, ABX, ABX, ABX, ABX, // 58
        IMP, IZX, IMP, IZX, ZP, ZP, ZP, ZP, // 60
        IMP, IMM, ACC, IMM, IND, ABS, ABS, ABS, // 68
        REL, IZY, IMP, IZY, ZPX, ZPX, ZPX, ZPX, // 70
        IMP, ABY, IMP, ABY, ABX, ABX, ABX, ABX, // 78
        IMM, IZX, IMM, IZX, ZP, ZP, ZP, ZP, // 80
        IMP, IMM, IMP, IMM, ABS, ABS, ABS, ABS, // 88
        REL, IZY, IMP, IZY, ZPX, ZPX, ZPY, ZPY, // 90
        IMP, ABY, IMP, ABY, ABX, ABX, ABY, ABY, // 98
        IMM, IZX, IMM, IZX, ZP, ZP, ZP, ZP, // A0
        IMP, IMM, IMP, IMM, ABS, ABS, ABS, ABS, // A8
        REL, IZY, IMP, IZY, ZPX, ZPX, ZPY, ZPY, // B0
        IMP, ABY, IMP, ABY, ABX, ABX, ABY, ABY, // B8
        IMM, IZX, IMM, IZX, ZP, ZP, ZP, ZP, // C0
        IMP, IMM, IMP, IMM, ABS, ABS, ABS, ABS, // C8
        REL, IZY, IMP, IZY, ZPX, ZPX, ZPX, ZPX, // D0
        IMP, ABY, IMP, ABY, ABX, ABX, ABX, ABX, // D8
        IMM, IZX, IMM, IZX, ZP, ZP, ZP, ZP, // E0
        IMP, IMM, IMP, IMM, ABS, ABS, ABS, ABS, // E8
        REL, IZY, IMP, IZY, ZPX, ZPX, ZPX, ZPX, // F0
        IMP, ABY, IMP, ABY, ABX, ABX, ABX, ABX, // F8
    };

    private Opcodes6502() {
    }

    static String mnemonic(int opcode) {
        return MNEMONICS[opcode & 0xFF];
    }

    static int mode(int opcode) {
        return MODES[opcode & 0xFF];
    }

    /** The length in bytes of the instruction with the given opcode. */
    static int length(int opcode) {
        return MODE_LENGTHS[MODES[opcode & 0xFF]];
    }
}
