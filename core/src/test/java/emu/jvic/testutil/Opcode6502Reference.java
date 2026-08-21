package emu.jvic.testutil;

/**
 * The standard public NMOS 6502 opcode matrix (documented + all illegal/undocumented
 * opcodes), used purely for readable labelling in {@code OpcodeCoverageTest}. Mnemonic
 * per byte value 0x00-0xFF, using the commonly published illegal-opcode names (SLO,
 * RLA, SRE, RRA, SAX, LAX, DCP, ISC, ANC, ALR, ARR, SBX, ANE, LXA, LAS, TAS, SHY, SHX,
 * SHA, JAM).
 */
public final class Opcode6502Reference {

    private Opcode6502Reference() {
    }

    public static final String[] MNEMONIC = {
        // 0x00-0x0F
        "BRK","ORA","JAM","SLO","NOP","ORA","ASL","SLO","PHP","ORA","ASL","ANC","NOP","ORA","ASL","SLO",
        // 0x10-0x1F
        "BPL","ORA","JAM","SLO","NOP","ORA","ASL","SLO","CLC","ORA","NOP","SLO","NOP","ORA","ASL","SLO",
        // 0x20-0x2F
        "JSR","AND","JAM","RLA","BIT","AND","ROL","RLA","PLP","AND","ROL","ANC","BIT","AND","ROL","RLA",
        // 0x30-0x3F
        "BMI","AND","JAM","RLA","NOP","AND","ROL","RLA","SEC","AND","NOP","RLA","NOP","AND","ROL","RLA",
        // 0x40-0x4F
        "RTI","EOR","JAM","SRE","NOP","EOR","LSR","SRE","PHA","EOR","LSR","ALR","JMP","EOR","LSR","SRE",
        // 0x50-0x5F
        "BVC","EOR","JAM","SRE","NOP","EOR","LSR","SRE","CLI","EOR","NOP","SRE","NOP","EOR","LSR","SRE",
        // 0x60-0x6F
        "RTS","ADC","JAM","RRA","NOP","ADC","ROR","RRA","PLA","ADC","ROR","ARR","JMP","ADC","ROR","RRA",
        // 0x70-0x7F
        "BVS","ADC","JAM","RRA","NOP","ADC","ROR","RRA","SEI","ADC","NOP","RRA","NOP","ADC","ROR","RRA",
        // 0x80-0x8F
        "NOP","STA","NOP","SAX","STY","STA","STX","SAX","DEY","NOP","TXA","ANE","STY","STA","STX","SAX",
        // 0x90-0x9F
        "BCC","STA","JAM","SHA","STY","STA","STX","SAX","TYA","STA","TXS","TAS","SHY","STA","SHX","SHA",
        // 0xA0-0xAF
        "LDY","LDA","LDX","LAX","LDY","LDA","LDX","LAX","TAY","LDA","TAX","LXA","LDY","LDA","LDX","LAX",
        // 0xB0-0xBF
        "BCS","LDA","JAM","LAX","LDY","LDA","LDX","LAX","CLV","LDA","TSX","LAS","LDY","LDA","LDX","LAX",
        // 0xC0-0xCF
        "CPY","CMP","NOP","DCP","CPY","CMP","DEC","DCP","INY","CMP","DEX","SBX","CPY","CMP","DEC","DCP",
        // 0xD0-0xDF
        "BNE","CMP","JAM","DCP","NOP","CMP","DEC","DCP","CLD","CMP","NOP","DCP","NOP","CMP","DEC","DCP",
        // 0xE0-0xEF
        "CPX","SBC","NOP","ISC","CPX","SBC","INC","ISC","INX","SBC","NOP","SBC","CPX","SBC","INC","ISC",
        // 0xF0-0xFF
        "BEQ","SBC","JAM","ISC","NOP","SBC","INC","ISC","SED","SBC","NOP","ISC","NOP","SBC","INC","ISC",
    };

    /**
     * The 12 official NMOS "JAM"/"KIL" opcodes, which halt real hardware completely
     * (requiring a physical reset). This emulator does not reproduce the halt - see
     * {@code OpcodeCoverageTest} for its actual (divergent) behaviour.
     */
    public static final int[] JAM_OPCODES = {
        0x02, 0x12, 0x22, 0x32, 0x42, 0x52, 0x62, 0x72, 0x92, 0xB2, 0xD2, 0xF2
    };

    /**
     * Opcodes whose {@code INSTRUCTION_DECODE_MATRIX} entry in Cpu6502 is an empty
     * {@code {}} - i.e. intentionally not implemented - determined by reading the
     * source directly (cross-checked against the empirical "runs in 1 cycle"
     * signature that OpcodeCoverageTest also verifies independently). Excludes the
     * 12 JAM opcodes above (listed separately - their real-hardware semantics,
     * halting the CPU, are fundamentally different from "not implemented").
     *
     * <p>What remains here (unlike the larger list this used to be) is a
     * deliberate scope decision, not a to-do list: these are the "unstable"
     * illegal opcodes whose real-hardware behaviour is chip-batch/temperature
     * dependent and not consistently documented (ANE/XAA, LXA, LAS, SHA, SHX,
     * SHY, TAS) - not worth chasing for a VIC-20 emulator. Every other illegal
     * opcode in the standard NMOS matrix is implemented.
     */
    public static final int[] UNIMPLEMENTED_NON_JAM_OPCODES = {
        0x8B, // ANE/XAA
        0x93, // SHA/AHX - (Indirect),Y
        0x9B, // TAS/SHS
        0x9C, // SHY
        0x9E, // SHX
        0x9F, // SHA/AHX - Absolute,Y
        0xAB, // LXA/ATX/OAL
        0xBB, // LAS/LAE
    };
}
