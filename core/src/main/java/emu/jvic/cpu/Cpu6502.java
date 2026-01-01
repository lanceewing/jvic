package emu.jvic.cpu;


import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import emu.jvic.BaseChip;
import emu.jvic.snap.Snapshot;
import emu.jvic.util.StringUtils;

/**
 * This class emulates a 6502 CPU. It emulates at the machine cycle level
 * so that it can be run interleaved with I/O, video, etc. operations.
 *
 * @author Lance Ewing
 */
public class Cpu6502 extends BaseChip {

    /**
     * Constant to use when signalling an IRQ.
     */
    public static final int S_IRQ = 0x01;

    /**
     * Constant to use when signally a NMI.
     */
    public static final int S_NMI = 0x02;

    /**
     * An opcode outside normal range that is used to execute a trap routine, which
     * can perform miscellaneous logic, such as the quick tape load.
     */
    private static final int EMU_TRAP_CODE = 0x100;

    /**
     * Data type used as the values within the traps Map.
     */
    private class Trap {
        int originalByte;
        Callable<Integer> trapRoutine;

        Trap(int originalByte, Callable<Integer> trapRoutine) {
            this.originalByte = originalByte;
            this.trapRoutine = trapRoutine;
        }
    };

    /**
     * Trap routines currently registered with this Cpu6502. The key is the memory
     * address and the Trap contains the code to run when the PC is at that address
     * and is used for storing the original byte at the trap address for when the
     * trap routine is deregistered.
     */
    private Map<Integer, Trap> traps;

    // Instruction constants.
    private static final int ADC = 0;
    private static final int AND = 1;
    private static final int ASL = 2;
    private static final int BCC = 3;
    private static final int BCS = 4;
    private static final int BEQ = 5;
    private static final int BNE = 6;
    private static final int BMI = 7;
    private static final int BPL = 8;
    private static final int BVS = 9;
    private static final int BVC = 10;
    private static final int BIT = 11;
    private static final int BRK = 12;
    private static final int CLC = 13;
    private static final int CLD = 14;
    private static final int CLI = 15;
    private static final int CLV = 16;
    private static final int CMP = 17;
    private static final int CPX = 18;
    private static final int CPY = 19;
    private static final int DEC = 20;
    private static final int DEX = 21;
    private static final int DEY = 22;
    private static final int EOR = 23;
    private static final int INC = 24;
    private static final int INX = 25;
    private static final int INY = 26;
    private static final int JMP = 27;
    private static final int JSR = 28;
    private static final int LDA = 29;
    private static final int LDX = 30;
    private static final int LDY = 31;
    private static final int LSR = 32;
    private static final int NOP = 33;
    private static final int ORA = 34;
    private static final int PHA = 35;
    private static final int PHP = 36;
    private static final int PLA = 37;
    private static final int PLP = 38;
    private static final int ROL = 39;
    private static final int ROR = 40;
    private static final int RTI = 41;
    private static final int RTS = 42;
    private static final int SBC = 43;
    private static final int SEC = 44;
    private static final int SED = 45;
    private static final int SEI = 46;
    private static final int STA = 47;
    private static final int STX = 48;
    private static final int STY = 49;
    private static final int TAX = 50;
    private static final int TAY = 51;
    private static final int TSX = 52;
    private static final int TXA = 53;
    private static final int TXS = 54;
    private static final int TYA = 55;
    private static final int ASL_A = 56;
    private static final int LSR_A = 57;
    private static final int ROL_A = 58;
    private static final int ROR_A = 59;
    private static final int NMI = 60;
    private static final int IRQ = 61;

    private static final int SLO = 62;
    private static final int SAX = 63;
    private static final int ANC = 64;
    private static final int LAX = 65;
    private static final int ISC = 66;
    private static final int RRA = 67;
    private static final int ARR = 68;

    // Instruction constant for emulation trap routine.
    private static final int TRAP = 0x100;

    // Instruction decode constants.
    private static final int BRANCH_DIS_NEXT = 1;
    private static final int BRANCH_DIS_OFFSET = 2;
    private static final int EXECUTE_BRANCH = 3;
    private static final int EXECUTE_DIS = 4; // Execute (with fetch next op code then discard)
    private static final int EXECUTE_LAST = 5; // Execute (with fetch next op code)
    private static final int EXECUTE_MID_ADL = 6; // Executes (does a write unmodified data at same time)
    private static final int EXECUTE_MID_BA = 7;
    private static final int EXECUTE_MID_BAL = 8;
    private static final int EXECUTE_MID_EA = 9;
    private static final int EXECUTE_STORE_ADL = 10;
    private static final int EXECUTE_STORE_EA = 11;
    private static final int EXECUTE_STORE_BA = 12;
    private static final int EXECUTE_STORE_BAL = 13;

    private static final int FETCH_ADH_BAL = 14; // Increments BAL by 1 aswell (& 0xFF)
    private static final int FETCH_ADH_FFFB = 15;
    private static final int FETCH_ADH_FFFF = 16;
    private static final int FETCH_ADH_PC = 17;
    private static final int FETCH_ADH_IA = 18;
    private static final int FETCH_ADL_BAL = 19; // Increments BAL by 1 aswell (& 0xFF)
    private static final int FETCH_ADL_FFFA = 20;
    private static final int FETCH_ADL_FFFE = 21;
    private static final int FETCH_ADL_PC = 22;
    private static final int FETCH_ADL_IA = 23;
    private static final int FETCH_BAH_IAL = 24;
    private static final int FETCH_BAH_PC = 25;
    private static final int FETCH_BAL_IAL = 26; // Increments IAL by 1 aswell (& 0xFF??)
    private static final int FETCH_BAL_PC = 27;
    private static final int FETCH_DATA_ADL = 28;
    private static final int FETCH_DATA_BA = 29;
    private static final int FETCH_DATA_BA_X = 30; // Fetch using BAH+c, BAL+x. Adds 1 to BHL if page crossed.
    private static final int FETCH_DATA_BA_Y = 31;
    private static final int FETCH_DATA_BAL = 32;
    private static final int FETCH_DATA_EA = 33;
    private static final int FETCH_DATA_PC = 34;
    private static final int FETCH_DATA_SP = 35;
    private static final int FETCH_DIS_BA_X = 36;
    private static final int FETCH_DIS_BA_Y = 37;
    private static final int FETCH_DIS_BAL_X = 38; // Fetch using BA then discard. Add X to BA.
    private static final int FETCH_DIS_BAL_Y = 39;
    private static final int FETCH_DIS_PC = 40; // Fetches using PC but doesn't increment PC.
    private static final int FETCH_DIS_SP = 41;
    private static final int FETCH_IAH_PC = 42;
    private static final int FETCH_IAL_PC = 43;
    private static final int FETCH_INC_PC = 44; // Fetch using PC and then does increment PC.
    private static final int FETCH_P_SP = 45;
    private static final int FETCH_PCH_SP = 46;
    private static final int FETCH_PCL_SP = 47;

    private static final int STORE_DATA_ADL = 48;
    private static final int STORE_DATA_BA = 49;
    private static final int STORE_DATA_BAL = 50;
    private static final int STORE_DATA_EA = 51;
    private static final int STORE_DATA_SP = 52;
    private static final int STORE_P_SP = 53;
    private static final int STORE_PCH_SP = 54;
    private static final int STORE_PCL_SP = 55;

    /**
     * This static lookup table holds the decoding details of all of the 6502's
     * instructions. It is this array that allows the class to emulate at the cycle
     * level of execution. The length of each instruction array determines the
     * number of cycles since each item of an instruction array represents the
     * action to take for a particular cycle. T0 is always the fetch.
     */
    private static int INSTRUCTION_DECODE_MATRIX[][] = {
        // 00 (0)
        {BRK, FETCH_INC_PC, STORE_PCH_SP, STORE_PCL_SP, STORE_P_SP, FETCH_ADL_FFFE, FETCH_ADH_FFFF, EXECUTE_LAST}, // BRK
        {ORA, FETCH_BAL_PC, FETCH_DIS_BAL_X, FETCH_ADL_BAL, FETCH_ADH_BAL, FETCH_DATA_EA, EXECUTE_LAST}, // ORA - (Indirect, X)
        {}, // - KIL
        {SLO, FETCH_BAL_PC, FETCH_DIS_BAL_X, FETCH_ADL_BAL, FETCH_ADH_BAL, FETCH_DATA_EA, EXECUTE_MID_ADL, STORE_DATA_ADL}, // SLO* - (Indirect, X)
        {NOP, FETCH_ADL_PC, FETCH_DATA_ADL, EXECUTE_LAST}, // NOP* - Zero Page
        {ORA, FETCH_ADL_PC, FETCH_DATA_ADL, EXECUTE_LAST}, // ORA - Zero Page
        {ASL, FETCH_ADL_PC, FETCH_DATA_ADL, EXECUTE_MID_ADL, STORE_DATA_ADL}, // ASL - Zero Page
        {SLO, FETCH_ADL_PC, FETCH_DATA_ADL,  EXECUTE_MID_ADL, STORE_DATA_ADL}, // SLO* - Zero Page
    
        // 08 (8)
        {PHP, EXECUTE_DIS, STORE_DATA_SP}, // PHP
        {ORA, FETCH_DATA_PC, EXECUTE_LAST},                             // ORA - Immediate
        {ASL_A, EXECUTE_DIS},                                           // ASL - Accumulator
        {ANC, FETCH_DATA_PC, EXECUTE_LAST},                             // ANC* - Immediate
        {NOP, FETCH_ADL_PC, FETCH_ADH_PC, FETCH_DATA_EA, EXECUTE_LAST}, // NOP - Absolute
        {ORA, FETCH_ADL_PC, FETCH_ADH_PC, FETCH_DATA_EA, EXECUTE_LAST}, // ORA - Absolute
        {ASL, FETCH_ADL_PC, FETCH_ADH_PC, FETCH_DATA_EA, EXECUTE_MID_EA, STORE_DATA_EA}, // ASL - Absolute
        {SLO, FETCH_ADL_PC, FETCH_ADH_PC, FETCH_DATA_EA, EXECUTE_MID_EA, STORE_DATA_EA}, // SLO* - Absolute
    
        // 10 (16)
        {BPL, EXECUTE_BRANCH, BRANCH_DIS_NEXT, BRANCH_DIS_OFFSET}, // BPL
        {ORA, FETCH_IAL_PC, FETCH_BAL_IAL, FETCH_BAH_IAL, FETCH_DATA_BA_Y, FETCH_DATA_BA, EXECUTE_LAST}, // ORA - (Indirect), Y
        {}, // -
        {}, // -
        {}, // -
        {ORA, FETCH_BAL_PC, FETCH_DIS_BAL_X, FETCH_DATA_BAL, EXECUTE_LAST}, // ORA - Zero Page, X
        {ASL, FETCH_BAL_PC, FETCH_DIS_BAL_X, FETCH_DATA_BAL, EXECUTE_MID_BAL, STORE_DATA_BAL}, // ASL - Zero Page, X
        {}, // -
    
        // 18 (24)
        {CLC, EXECUTE_DIS}, // CLC
        {ORA, FETCH_BAL_PC, FETCH_BAH_PC, FETCH_DATA_BA_Y, FETCH_DATA_BA, EXECUTE_LAST}, // ORA - Absolute, Y
        {}, // -
        {}, // -
        {}, // -
        {ORA, FETCH_BAL_PC, FETCH_BAH_PC, FETCH_DATA_BA_X, FETCH_DATA_BA, EXECUTE_LAST}, // ORA - Absolute, X
        {ASL, FETCH_BAL_PC, FETCH_BAH_PC, FETCH_DIS_BA_X, FETCH_DATA_BA, EXECUTE_MID_BA, STORE_DATA_BA}, // ASL - Absolute, X
        {}, // -
    
        // 20 (32)
        {JSR, FETCH_ADL_PC, FETCH_DIS_SP, STORE_PCH_SP, STORE_PCL_SP, FETCH_ADH_PC, EXECUTE_LAST}, // JSR
        {AND, FETCH_BAL_PC, FETCH_DIS_BAL_X, FETCH_ADL_BAL, FETCH_ADH_BAL, FETCH_DATA_EA, EXECUTE_LAST}, // AND - (Indirect, X)
        {}, // -
        {}, // -
        {BIT, FETCH_ADL_PC, FETCH_DATA_ADL, EXECUTE_LAST}, // BIT - Zero Page
        {AND, FETCH_ADL_PC, FETCH_DATA_ADL, EXECUTE_LAST}, // AND - Zero Page
        {ROL, FETCH_ADL_PC, FETCH_DATA_ADL, EXECUTE_MID_ADL, STORE_DATA_ADL}, // ROL - Zero Page
        {}, // -
    
        // 28 (40)
        {PLP, FETCH_DIS_PC, FETCH_DIS_SP, FETCH_DATA_SP, EXECUTE_LAST}, // PLP
        {AND, FETCH_DATA_PC, EXECUTE_LAST}, // AND - Immediate
        {ROL_A, EXECUTE_DIS}, // ROL - Accumulator
        {}, // -
        {BIT, FETCH_ADL_PC, FETCH_ADH_PC, FETCH_DATA_EA, EXECUTE_LAST}, // BIT - Absolute
        {AND, FETCH_ADL_PC, FETCH_ADH_PC, FETCH_DATA_EA, EXECUTE_LAST}, // AND - Absolute
        {ROL, FETCH_ADL_PC, FETCH_ADH_PC, FETCH_DATA_EA, EXECUTE_MID_EA, STORE_DATA_EA}, // ROL - Absolute
        {}, // -
    
        // 30 (48)
        {BMI, EXECUTE_BRANCH, BRANCH_DIS_NEXT, BRANCH_DIS_OFFSET}, // BMI
        {AND, FETCH_IAL_PC, FETCH_BAL_IAL, FETCH_BAH_IAL, FETCH_DATA_BA_Y, FETCH_DATA_BA, EXECUTE_LAST}, // AND - (Indirect), Y
        {}, // -
        {}, // -
        {}, // -
        {AND, FETCH_BAL_PC, FETCH_DIS_BAL_X, FETCH_DATA_BAL, EXECUTE_LAST}, // AND - Zero Page, X
        {ROL, FETCH_BAL_PC, FETCH_DIS_BAL_X, FETCH_DATA_BAL, EXECUTE_MID_BAL, STORE_DATA_BAL}, // ROL - Zero Page, X
        {}, // -
    
        // 38 (56)
        {SEC, EXECUTE_DIS}, // SEC
        {AND, FETCH_BAL_PC, FETCH_BAH_PC, FETCH_DATA_BA_Y, FETCH_DATA_BA, EXECUTE_LAST}, // AND - Absolute, Y
        {}, // -
        {}, // -
        {}, // -
        {AND, FETCH_BAL_PC, FETCH_BAH_PC, FETCH_DATA_BA_X, FETCH_DATA_BA, EXECUTE_LAST}, // AND - Absolute, X
        {ROL, FETCH_BAL_PC, FETCH_BAH_PC, FETCH_DIS_BA_X, FETCH_DATA_BA, EXECUTE_MID_BA, STORE_DATA_BA}, // ROL - Absolute, X
        {}, // -
    
        // 40 (64)
        {RTI, FETCH_DIS_PC, FETCH_DIS_SP, FETCH_P_SP, FETCH_PCL_SP, FETCH_PCH_SP, EXECUTE_LAST}, // RTI
        {EOR, FETCH_BAL_PC, FETCH_DIS_BAL_X, FETCH_ADL_BAL, FETCH_ADH_BAL, FETCH_DATA_EA, EXECUTE_LAST}, // EOR - (Indirect, X)
        {}, // -
        {}, // -
        {}, // -
        {EOR, FETCH_ADL_PC, FETCH_DATA_ADL, EXECUTE_LAST}, // EOR - Zero Page
        {LSR, FETCH_ADL_PC, FETCH_DATA_ADL, EXECUTE_MID_ADL, STORE_DATA_ADL}, // LSR - Zero Page
        {}, // -
    
        // 48 (72)
        {PHA, EXECUTE_DIS, STORE_DATA_SP}, // PHA
        {EOR, FETCH_DATA_PC, EXECUTE_LAST}, // EOR - Immediate
        {LSR_A, EXECUTE_DIS}, // LSR - Accumulator
        {}, // -
        {JMP, FETCH_ADL_PC, FETCH_ADH_PC, EXECUTE_LAST}, // JMP - Absolute
        {EOR, FETCH_ADL_PC, FETCH_ADH_PC, FETCH_DATA_EA, EXECUTE_LAST}, // EOR - Absolute
        {LSR, FETCH_ADL_PC, FETCH_ADH_PC, FETCH_DATA_EA, EXECUTE_MID_EA, STORE_DATA_EA}, // LSR - Absolute
        {}, // -
    
        // 50 (80)
        {BVC, EXECUTE_BRANCH, BRANCH_DIS_NEXT, BRANCH_DIS_OFFSET}, // BVC
        {EOR, FETCH_IAL_PC, FETCH_BAL_IAL, FETCH_BAH_IAL, FETCH_DATA_BA_Y, FETCH_DATA_BA, EXECUTE_LAST}, // EOR - (Indirect), Y
        {}, // -
        {}, // -
        {}, // -
        {EOR, FETCH_BAL_PC, FETCH_DIS_BAL_X, FETCH_DATA_BAL, EXECUTE_LAST}, // EOR - Zero Page, X
        {LSR, FETCH_BAL_PC, FETCH_DIS_BAL_X, FETCH_DATA_BAL, EXECUTE_MID_BAL, STORE_DATA_BAL}, // LSR - Zero Page, X
        {}, // -
    
        // 58 (88)
        {CLI, EXECUTE_DIS}, // CLI
        {EOR, FETCH_BAL_PC, FETCH_BAH_PC, FETCH_DATA_BA_Y, FETCH_DATA_BA, EXECUTE_LAST}, // EOR - Absolute, Y
        {}, // -
        {}, // -
        {}, // -
        {EOR, FETCH_BAL_PC, FETCH_BAH_PC, FETCH_DATA_BA_X, FETCH_DATA_BA, EXECUTE_LAST}, // EOR - Absolute, X
        {LSR, FETCH_BAL_PC, FETCH_BAH_PC, FETCH_DIS_BA_X, FETCH_DATA_BA, EXECUTE_MID_BA, STORE_DATA_BA}, // LSR - Absolute, X
        {}, // -
    
        // 60 (96)
        {RTS, FETCH_DIS_PC, FETCH_DIS_SP, FETCH_PCL_SP, FETCH_PCH_SP, FETCH_DATA_PC}, // RTS
        {ADC, FETCH_BAL_PC, FETCH_DIS_BAL_X, FETCH_ADL_BAL, FETCH_ADH_BAL, FETCH_DATA_EA, EXECUTE_LAST}, // ADC - (Indirect, X)
        {}, // -
        {}, // -
        {NOP, FETCH_ADL_PC, FETCH_DATA_ADL, EXECUTE_LAST}, // NOP - Zero Page
        {ADC, FETCH_ADL_PC, FETCH_DATA_ADL, EXECUTE_LAST}, // ADC - Zero Page
        {ROR, FETCH_ADL_PC, FETCH_DATA_ADL, EXECUTE_MID_ADL, STORE_DATA_ADL}, // ROR - Zero Page
        {}, // -
    
        // 68 (104)
        {PLA, FETCH_DIS_PC, FETCH_DIS_SP, FETCH_DATA_SP, EXECUTE_LAST}, // PLA
        {ADC, FETCH_DATA_PC, EXECUTE_LAST}, // ADC - Immediate
        {ROR_A, EXECUTE_DIS}, // ROR - Accumulator
        {ARR, FETCH_DATA_PC, EXECUTE_LAST}, // - ARR #
        {JMP, FETCH_IAL_PC, FETCH_IAH_PC, FETCH_ADL_IA, FETCH_ADH_IA, EXECUTE_LAST}, // JMP - Indirect
        {ADC, FETCH_ADL_PC, FETCH_ADH_PC, FETCH_DATA_EA, EXECUTE_LAST}, // ADC - Absolute
        {ROR, FETCH_ADL_PC, FETCH_ADH_PC, FETCH_DATA_EA, EXECUTE_MID_EA, STORE_DATA_EA}, // ROR - Absolute
        {RRA, FETCH_ADL_PC, FETCH_ADH_PC, FETCH_DATA_EA, EXECUTE_MID_EA, STORE_DATA_EA}, // RRA - Absolute (ROR + ADC)
    
        // 70 (112)
        {BVS, EXECUTE_BRANCH, BRANCH_DIS_NEXT, BRANCH_DIS_OFFSET}, // BVS
        {ADC, FETCH_IAL_PC, FETCH_BAL_IAL, FETCH_BAH_IAL, FETCH_DATA_BA_Y, FETCH_DATA_BA, EXECUTE_LAST}, // ADC - (Indirect), Y
        {}, // -
        {RRA, FETCH_IAL_PC, FETCH_BAL_IAL, FETCH_BAH_IAL, FETCH_DATA_BA_Y, FETCH_DATA_BA, EXECUTE_MID_EA, STORE_DATA_EA}, // RRA (Indirect), Y
        {NOP, FETCH_BAL_PC, FETCH_DIS_BAL_X, FETCH_DATA_BAL, EXECUTE_LAST}, // NOP - Zero Page, X
        {ADC, FETCH_BAL_PC, FETCH_DIS_BAL_X, FETCH_DATA_BAL, EXECUTE_LAST}, // ADC - Zero Page, X
        {ROR, FETCH_BAL_PC, FETCH_DIS_BAL_X, FETCH_DATA_BAL, EXECUTE_MID_BAL, STORE_DATA_BAL}, // ROR - Zero Page, X
        {}, // -
    
        // 78 (120)
        {SEI, EXECUTE_DIS}, // SEI
        {ADC, FETCH_BAL_PC, FETCH_BAH_PC, FETCH_DATA_BA_Y, FETCH_DATA_BA, EXECUTE_LAST}, // ADC - Absolute, Y
        {}, // -
        {}, // -
        {}, // -
        {ADC, FETCH_BAL_PC, FETCH_BAH_PC, FETCH_DATA_BA_X, FETCH_DATA_BA, EXECUTE_LAST}, // ADC - Absolute, X
        {ROR, FETCH_BAL_PC, FETCH_BAH_PC, FETCH_DIS_BA_X, FETCH_DATA_BA, EXECUTE_MID_BA, STORE_DATA_BA}, // ROR - Absolute, X
        {}, // -
    
        // 80 (128)
        {}, // -
        {STA, FETCH_BAL_PC, FETCH_DIS_BAL_X, FETCH_ADL_BAL, FETCH_ADH_BAL, EXECUTE_STORE_EA}, // STA - (Indirect, X)
        {}, // -
        {}, // -
        {STY, FETCH_ADL_PC, EXECUTE_STORE_ADL}, // STY - Zero Page
        {STA, FETCH_ADL_PC, EXECUTE_STORE_ADL}, // STA - Zero Page
        {STX, FETCH_ADL_PC, EXECUTE_STORE_ADL}, // STX - Zero Page
        {}, // -
    
        // 88 (136)
        {DEY, EXECUTE_DIS}, // DEY
        {}, // -
        {TXA, EXECUTE_DIS}, // TXA Implied
        {}, // -
        {STY, FETCH_ADL_PC, FETCH_ADH_PC, EXECUTE_STORE_EA}, // STY - Absolute
        {STA, FETCH_ADL_PC, FETCH_ADH_PC, EXECUTE_STORE_EA}, // STA - Absolute
        {STX, FETCH_ADL_PC, FETCH_ADH_PC, EXECUTE_STORE_EA}, // STX - Absolute
        {SAX, FETCH_ADL_PC, FETCH_ADH_PC, EXECUTE_STORE_EA}, // SAX* - Absolute
    
        // 90 (144)
        {BCC, EXECUTE_BRANCH, BRANCH_DIS_NEXT, BRANCH_DIS_OFFSET}, // BCC
        {STA, FETCH_IAL_PC, FETCH_BAL_IAL, FETCH_BAH_IAL, FETCH_DIS_BA_Y, EXECUTE_STORE_BA}, // STA - (Indirect), Y
        {}, // -
        {}, // -
        {STY, FETCH_BAL_PC, FETCH_DIS_BAL_X, EXECUTE_STORE_BAL}, // STY - Zero Page, X
        {STA, FETCH_BAL_PC, FETCH_DIS_BAL_X, EXECUTE_STORE_BAL}, // STA - Zero Page, X
        {STX, FETCH_BAL_PC, FETCH_DIS_BAL_Y, EXECUTE_STORE_BAL}, // STX - Zero Page, Y
        {}, // -
    
        // 98 (152)
        {TYA, EXECUTE_DIS}, // TYA
        {STA, FETCH_BAL_PC, FETCH_BAH_PC, FETCH_DIS_BA_Y, EXECUTE_STORE_BA}, // STA - Absolute, Y
        {TXS, EXECUTE_DIS}, // TXS
        {}, // -
        {}, // -
        {STA, FETCH_BAL_PC, FETCH_BAH_PC, FETCH_DIS_BA_X, EXECUTE_STORE_BA}, // STA - Absolute, X
        {}, // -
        {}, // -
    
        // A0 (160)
        {LDY, FETCH_DATA_PC, EXECUTE_LAST}, // LDY - Immediate
        {LDA, FETCH_BAL_PC, FETCH_DIS_BAL_X, FETCH_ADL_BAL, FETCH_ADH_BAL, FETCH_DATA_EA, EXECUTE_LAST}, // LDA - (Indirect, X)
        {LDX, FETCH_DATA_PC, EXECUTE_LAST}, // LDX - Immediate
        {}, // -
        {LDY, FETCH_ADL_PC, FETCH_DATA_ADL, EXECUTE_LAST}, // LDY - Zero Page
        {LDA, FETCH_ADL_PC, FETCH_DATA_ADL, EXECUTE_LAST}, // LDA - Zero Page
        {LDX, FETCH_ADL_PC, FETCH_DATA_ADL, EXECUTE_LAST}, // LDX - Zero Page
        {}, // -
    
        // A8 (168)
        {TAY, EXECUTE_DIS}, // TAY
        {LDA, FETCH_DATA_PC, EXECUTE_LAST}, // LDA - Immediate
        {TAX, EXECUTE_DIS}, // TAX
        {}, // -
        {LDY, FETCH_ADL_PC, FETCH_ADH_PC, FETCH_DATA_EA, EXECUTE_LAST}, // LDY - Absolute
        {LDA, FETCH_ADL_PC, FETCH_ADH_PC, FETCH_DATA_EA, EXECUTE_LAST}, // LDA - Absolute
        {LDX, FETCH_ADL_PC, FETCH_ADH_PC, FETCH_DATA_EA, EXECUTE_LAST}, // LDX - Absolute
        {}, // -
    
        // B0 (176)
        {BCS, EXECUTE_BRANCH, BRANCH_DIS_NEXT, BRANCH_DIS_OFFSET}, // BCS
        {LDA, FETCH_IAL_PC, FETCH_BAL_IAL, FETCH_BAH_IAL, FETCH_DATA_BA_Y, FETCH_DATA_BA, EXECUTE_LAST}, // LDA - (Indirect), Y
        {}, // -
        {}, // -
        {LDY, FETCH_BAL_PC, FETCH_DIS_BAL_X, FETCH_DATA_BAL, EXECUTE_LAST}, // LDY - Zero Page, X
        {LDA, FETCH_BAL_PC, FETCH_DIS_BAL_X, FETCH_DATA_BAL, EXECUTE_LAST}, // LDA - Zero Page, X
        {LDX, FETCH_BAL_PC, FETCH_DIS_BAL_Y, FETCH_DATA_BAL, EXECUTE_LAST}, // LDX - Zero Page, Y
        {LAX, FETCH_BAL_PC, FETCH_DIS_BAL_Y, FETCH_DATA_BAL, EXECUTE_LAST}, // - 183 (B7) - LAX - Zero Page, Y
    
        // B8 (184)
        {CLV, EXECUTE_DIS}, // CLV
        {LDA, FETCH_BAL_PC, FETCH_BAH_PC, FETCH_DATA_BA_Y, FETCH_DATA_BA, EXECUTE_LAST}, // LDA - Absolute, Y
        {TSX, EXECUTE_DIS}, // TSX
        {}, // -
        {LDY, FETCH_BAL_PC, FETCH_BAH_PC, FETCH_DATA_BA_X, FETCH_DATA_BA, EXECUTE_LAST}, // LDY - Absolute, X
        {LDA, FETCH_BAL_PC, FETCH_BAH_PC, FETCH_DATA_BA_X, FETCH_DATA_BA, EXECUTE_LAST}, // LDA - Absolute, X
        {LDX, FETCH_BAL_PC, FETCH_BAH_PC, FETCH_DATA_BA_Y, FETCH_DATA_BA, EXECUTE_LAST}, // LDX - Absolute, Y
        {}, // -
    
    
        {CPY, FETCH_DATA_PC, EXECUTE_LAST}, // CPY - Immediate
        {CMP, FETCH_BAL_PC, FETCH_DIS_BAL_X, FETCH_ADL_BAL, FETCH_ADH_BAL, FETCH_DATA_EA, EXECUTE_LAST}, // CMP - (Indirect, X)
        {}, // -
        {}, // -
        {CPY, FETCH_ADL_PC, FETCH_DATA_ADL, EXECUTE_LAST}, // CPY - Zero Page
        {CMP, FETCH_ADL_PC, FETCH_DATA_ADL, EXECUTE_LAST}, // CMP - Zero Page
        {DEC, FETCH_ADL_PC, FETCH_DATA_ADL, EXECUTE_MID_ADL, STORE_DATA_ADL}, // DEC - Zero Page
        {}, // -
    
        {INY, EXECUTE_DIS}, // INY
        {CMP, FETCH_DATA_PC, EXECUTE_LAST}, // CMP - Immediate
        {DEX, EXECUTE_DIS}, // DEX
        {}, // -
        {CPY, FETCH_ADL_PC, FETCH_ADH_PC, FETCH_DATA_EA, EXECUTE_LAST}, // CPY - Absolute
        {CMP, FETCH_ADL_PC, FETCH_ADH_PC, FETCH_DATA_EA, EXECUTE_LAST}, // CMP - Absolute
        {DEC, FETCH_ADL_PC, FETCH_ADH_PC, FETCH_DATA_EA, EXECUTE_MID_EA, STORE_DATA_EA}, // DEC - Absolute
        {}, // -
    
        {BNE, EXECUTE_BRANCH, BRANCH_DIS_NEXT, BRANCH_DIS_OFFSET}, // BNE
        {CMP, FETCH_IAL_PC, FETCH_BAL_IAL, FETCH_BAH_IAL, FETCH_DATA_BA_Y, FETCH_DATA_BA, EXECUTE_LAST}, // CMP - (Indirect), Y
        {}, // -
        {}, // -
        {}, // -
        {CMP, FETCH_BAL_PC, FETCH_DIS_BAL_X, FETCH_DATA_BAL, EXECUTE_LAST}, // CMP - Zero Page, X
        {DEC, FETCH_BAL_PC, FETCH_DIS_BAL_X, FETCH_DATA_BAL, EXECUTE_MID_BAL, STORE_DATA_BAL}, // DEC - Zero Page, X
        {}, // -
    
        {CLD, EXECUTE_DIS}, // CLD
        {CMP, FETCH_BAL_PC, FETCH_BAH_PC, FETCH_DATA_BA_Y, FETCH_DATA_BA, EXECUTE_LAST}, // CMP - Absolute, Y
        {}, // -
        {}, // -
        {}, // -
        {CMP, FETCH_BAL_PC, FETCH_BAH_PC, FETCH_DATA_BA_X, FETCH_DATA_BA, EXECUTE_LAST}, // CMP - Absolute, X
        {DEC, FETCH_BAL_PC, FETCH_BAH_PC, FETCH_DIS_BA_X, FETCH_DATA_BA, EXECUTE_MID_BA, STORE_DATA_BA}, // DEC - Absolute, X
        {}, // -
    
    
        {CPX, FETCH_DATA_PC, EXECUTE_LAST}, // CPX - Immediate
        {SBC, FETCH_BAL_PC, FETCH_DIS_BAL_X, FETCH_ADL_BAL, FETCH_ADH_BAL, FETCH_DATA_EA, EXECUTE_LAST}, // SBC - (Indirect, X)
        {}, // -
        {}, // -
        {CPX, FETCH_ADL_PC, FETCH_DATA_ADL, EXECUTE_LAST}, // CPX - Zero Page
        {SBC, FETCH_ADL_PC, FETCH_DATA_ADL, EXECUTE_LAST}, // SBC - Zero Page
        {INC, FETCH_ADL_PC, FETCH_DATA_ADL, EXECUTE_MID_ADL, STORE_DATA_ADL}, // INC - Zero Page
        {}, // -
    
        {INX, EXECUTE_DIS}, // INX
        {SBC, FETCH_DATA_PC, EXECUTE_LAST}, // SBC - Immediate
        {NOP, EXECUTE_DIS}, // NOP
        {}, // -
        {CPX, FETCH_ADL_PC, FETCH_ADH_PC, FETCH_DATA_EA, EXECUTE_LAST}, // CPX - Absolute
        {SBC, FETCH_ADL_PC, FETCH_ADH_PC, FETCH_DATA_EA, EXECUTE_LAST}, // SBC - Absolute
        {INC, FETCH_ADL_PC, FETCH_ADH_PC, FETCH_DATA_EA, EXECUTE_MID_EA, STORE_DATA_EA}, // INC - Absolute
        {}, // -
    
        // F0
        {BEQ, EXECUTE_BRANCH, BRANCH_DIS_NEXT, BRANCH_DIS_OFFSET}, // BEQ
        {SBC, FETCH_IAL_PC, FETCH_BAL_IAL, FETCH_BAH_IAL, FETCH_DATA_BA_Y, FETCH_DATA_BA, EXECUTE_LAST}, // SBC - (Indirect), Y
        {}, // -
        {}, // -
        {}, // -
        {SBC, FETCH_BAL_PC, FETCH_DIS_BAL_X, FETCH_DATA_BAL, EXECUTE_LAST}, // SBC - Zero Page, X
        {INC, FETCH_BAL_PC, FETCH_DIS_BAL_X, FETCH_DATA_BAL, EXECUTE_MID_BAL, STORE_DATA_BAL}, // INC - Zero Page, X
        {ISC, FETCH_BAL_PC, FETCH_DIS_BAL_X, FETCH_DATA_BAL, EXECUTE_MID_BAL, STORE_DATA_BAL}, // ISC - Zero Page, X
    
        // F8
        {SED, EXECUTE_DIS}, // SED
        {SBC, FETCH_BAL_PC, FETCH_BAH_PC, FETCH_DATA_BA_Y, FETCH_DATA_BA, EXECUTE_LAST}, // SBC - Absolute, Y
        {}, // -
        {}, // -
        {}, // -
        {SBC, FETCH_BAL_PC, FETCH_BAH_PC, FETCH_DATA_BA_X, FETCH_DATA_BA, EXECUTE_LAST}, // SBC - Absolute, X
        {INC, FETCH_BAL_PC, FETCH_BAH_PC, FETCH_DIS_BA_X, FETCH_DATA_BA, EXECUTE_MID_BA, STORE_DATA_BA}, // INC - Absolute, X
        {},  // -
        
        {TRAP, EXECUTE_DIS}   // Opcode 256 (0x100) is mapped to an emulation trap routine.
    };

    /**
     * The execution steps for an IRQ.
     */
    private static final int IRQ_STEPS[] = {
        IRQ, FETCH_DIS_PC, FETCH_DIS_PC, STORE_PCH_SP, STORE_PCL_SP, STORE_P_SP, FETCH_ADL_FFFE, FETCH_ADH_FFFF, EXECUTE_LAST
    };

    /**
     * The execution steps for a NMI.
     */
    private static final int NMI_STEPS[] = {
        NMI, FETCH_DIS_PC, FETCH_DIS_PC, STORE_PCH_SP, STORE_PCL_SP, STORE_P_SP, FETCH_ADL_FFFA, FETCH_ADH_FFFB, EXECUTE_LAST
    };

    /**
     * The vector for RESET signals.
     */
    private static final int RESET_VECTOR = 0xFFFC;

    /**
     * The current state of the interrupt pins.
     */
    private int interruptStatus;

    /**
     * Tells the 6502 to delay the check for a pending interrupt by one cycle. This
     * emulates an undocumented feature where taken branches that do not cross a
     * page boundary will delay a pending interrupt by one cycle.
     */
    private boolean delayInterruptOneCycle;

    /**
     * Index register X.
     */
    private int indexRegisterX;

    /**
     * Index register Y.
     */
    private int indexRegisterY;

    /**
     * Accummulator.
     */
    private int accumulator;

    /**
     * Stack pointer.
     */
    private int stackPointer;

    /**
     * Program counter.
     */
    private int programCounter;

    /**
     * Status register.
     */
    private int processorStatusRegister;

    // The individual flag values. There are only 6 flags that physically exist. B flag 
    // does not (only on the stack).
    private boolean negativeResultFlag;
    private boolean overflowFlag;
    private boolean decimalModeFlag;
    private boolean interruptDisableFlag;
    private boolean zeroResultFlag;
    private boolean carryFlag;

    /**
     * Instruction register. Holds the opcode of the current instruction.
     */
    private int instructionRegister;

    /**
     * Holds a reference to the array containing the steps for the current instruction.
     */
    private int instructionSteps[];

    /**
     * Input data latch.
     */
    private int inputDataLatch;

    /**
     * Data bus buffer. Holds outbound data.
     */
    private int dataBusBuffer;

    /**
     * Holds the current step of execution of the current instruction.
     */
    private int currentInstructionStep;

    /**
     * Holds the current number of execution steps of the current instruction.
     */
    private int numOfInstructionSteps;

    /**
     * Holds the low byte of the effective address.
     */
    private int effectiveAddressLow;

    /**
     * Holds the high byte of the effective address.
     */
    private int effectiveAddressHigh;

    /**
     * Holds the low byte of the base address.
     */
    private int baseAddressLow;

    /**
     * Holds the high byte of the base address.
     */
    private int baseAddressHigh;

    /**
     * Holds the low byte of the indirect address.
     */
    private int indirectAddressLow;

    /**
     * Holds the high byte of the indirect address.
     */
    private int indirectAddressHigh;

    /**
     * Indicates whether the current branch should be actioned or not.
     */
    private boolean branchFlag;

    /**
     * The address to branch to for a branch instruction.
     */
    private int branchAddress;
  
    /**
     * Constructor for CPU6502.
     * 
     * @param snapshot An optional machine Snapshot to restore the CPU state from.
     */
    public Cpu6502(Snapshot snapshot) {
        traps = new HashMap<Integer, Trap>();

        if (snapshot != null) {
            // Set the program counter to the reset vector.
            programCounter = snapshot.getProgramCounter();

            // Initialise the registers.
            accumulator = snapshot.getAccumulator();
            indexRegisterX = snapshot.getIndexRegisterX();
            indexRegisterY = snapshot.getIndexRegisterY();
            stackPointer = snapshot.getStackPointer();

            // Intialise the flags.
            processorStatusRegister = snapshot.getProcessorStatusRegister();
            unpackPSR();
        }
    }

    /**
     * Resets the CPU.
     */
    public void reset() {
        // Set the program counter to the reset vector.
        programCounter = getWordFromMemory(RESET_VECTOR);

        // Initialise the registers.
        accumulator = 0;
        indexRegisterX = 0;
        indexRegisterY = 0;
        inputDataLatch = 0;
        dataBusBuffer = 0;
        stackPointer = 0xFF;

        // Intialise the flags.
        negativeResultFlag = false;
        overflowFlag = false;
        decimalModeFlag = false;
        interruptDisableFlag = false;
        zeroResultFlag = false;
        carryFlag = false;
        processorStatusRegister = 0x20;

        // Initial instruction decoding variables.
        numOfInstructionSteps = 0;
        instructionRegister = 0;
        effectiveAddressHigh = 0;
        effectiveAddressLow = 0;
        baseAddressHigh = 0;
        baseAddressLow = 0;
        indirectAddressHigh = 0;
        indirectAddressLow = 0;
        currentInstructionStep = 0;
    }

    /**
     * Gets a word (2 bytes) from a location in memory.
     *
     * @param address the address of the word to get.
     */
    private int getWordFromMemory(int address) {
        return (memoryMap[address].readMemory(address) | ((memoryMap[address + 1].readMemory(address + 1) << 8) & 0xFF00));
    }

    /**
     * Sets the negative and zero flags.
     *
     * @param value the value to test.
     */
    private void setNZ(int value) {
        negativeResultFlag = ((value & 0x80) != 0);
        zeroResultFlag = (value == 0);
    }

    /**
     * Sets the carry flag depending on whether a carry has occurred or not.
     *
     * @param value the value to test.
     */
    private void setFlagCarry(int value) {
        carryFlag = ((value & 0x100) != 0);
    }

    /**
     * Sets the carry flag depending on whether a borrow has occurred or not.
     *
     * @param value the value to test.
     */
    private void setFlagBorrow(int value) {
        carryFlag = ((value & 0x100) == 0);
    }

    /**
     * Packs the status register flags into a single byte value.
     */
    private void packPSR() {
        processorStatusRegister =
            (negativeResultFlag ? 0x80 : 0) |
            (overflowFlag ? 0x40 : 0) | 0x20 |
            (decimalModeFlag ? 8 : 0) |
            (interruptDisableFlag ? 4 : 0) |
            (zeroResultFlag ? 2 : 0) |
            (carryFlag ? 1 : 0);
    }

    /**
     * Unpacks the status register flags from a single byte value.
     */
    private void unpackPSR() {
        negativeResultFlag = ((processorStatusRegister & 0x80) != 0);
        overflowFlag = ((processorStatusRegister & 0x40) != 0);
        decimalModeFlag = ((processorStatusRegister & 0x08) != 0);
        interruptDisableFlag = ((processorStatusRegister & 0x04) != 0);
        zeroResultFlag = ((processorStatusRegister & 0x02) != 0);
        carryFlag = ((processorStatusRegister & 0x01) != 0);
    }

    /**
     * Signals an interrupt occurring.
     *
     * @param irqSignalCode either S_IRQ or a S_NMI.
     */
    public void setInterrupt(int irqSignalCode) {
        this.interruptStatus |= irqSignalCode;
    }

    /**
     * Clears an interrupt signal.
     *
     * @param irqSignalCode either S_IRQ or S_NMI.
     */
    public void clearInterrupt(int irqSignalCode) {
        this.interruptStatus &= ~irqSignalCode;
    }

    /**
     * Registers a trap routine with this Cpu6502. When the PC is at the given
     * address, it will execute the given Runnable routine.
     * 
     * @param address     The address to set the trap up at.
     * @param trapRoutine The code to run when that trap is hit.
     */
    public void registerTrapRoutine(int address, Callable<Integer> trapRoutine) {
        traps.put(address, new Trap(memory.readMemory(address), trapRoutine));
        memory.forceWrite(address, EMU_TRAP_CODE);
    }

    /**
     * Deregisters a trap routine. The original byte at the address is restored to
     * the trap address.
     * 
     * @param address The address that the trap was set up at.
     */
    public void deregisterTrapRoutine(int address) {
        Trap trap = traps.remove(address);
        memory.forceWrite(address, trap.originalByte);
    }
  
  /**
   * Executes the current instruction, using the data just loaded if applicable.
   * Input data is contained in the inputDataLatch instance variable, and data
   * to be output will be stored in the dataBusBuffer after completion of this
   * method class.
   */
  public void executeInstruction() {
    int tmp = 0, op1 = 0, op2 = 0;
    
    switch(instructionSteps[0]) {
      case ADC:
        op1 = accumulator;
        op2 = inputDataLatch;
        if (decimalModeFlag) {
          int lo, hi;
          lo = (op1 & 0x0f) + (op2 & 0x0f) + (carryFlag ? 1 : 0);
          if ((lo & 0xff) > 9) lo += 6;
          hi = (op1 >> 4) + (op2 >> 4) + (lo > 15 ? 1 : 0);
          if ((hi & 0xff) > 9) hi += 6;
          tmp = (hi << 4) | (lo & 0x0f);
          accumulator = tmp & 0xff;
          carryFlag = (hi > 15);
          zeroResultFlag = (accumulator == 0);
          overflowFlag = false;       // BCD never sets overflow flag
          negativeResultFlag = false; // BCD is never negative on NMOS 6502
        }
        else {       // binary mode
          tmp = op1 + op2 + (carryFlag ? 1 : 0);
          accumulator = tmp & 0xFF;
          overflowFlag = ((op1 ^ accumulator) & ~(op1 ^ op2) & 0x80) != 0;
          setFlagCarry(tmp);
          setNZ(accumulator);
        }
        break;

      case AND:
        accumulator = accumulator & inputDataLatch;
        setNZ(accumulator);
        break;

      case ASL:
        carryFlag = ((inputDataLatch & 0x80) != 0);
        dataBusBuffer = ((inputDataLatch << 1) & 0xFF);
        setNZ(dataBusBuffer);
        break;

      case BCC:
        branchFlag = !carryFlag;
        break;

      case BCS:
        branchFlag = carryFlag;
        break;

      case BEQ:
        branchFlag = zeroResultFlag;
        break;

      case BNE:
        branchFlag = !zeroResultFlag;
        break;

      case BMI:
        branchFlag = negativeResultFlag;
        break;

      case BPL:
        branchFlag = !negativeResultFlag;
        break;

      case BVS:
        branchFlag = overflowFlag;
        break;

      case BVC:
        branchFlag = !overflowFlag;
        break;

      case BIT:
        overflowFlag = (inputDataLatch & 0x40) != 0;
        negativeResultFlag = (inputDataLatch & 0x80) != 0;
        zeroResultFlag = (inputDataLatch & accumulator) == 0;
        break;

      case BRK:
        interruptDisableFlag = true;
        programCounter = (effectiveAddressHigh | effectiveAddressLow);
        break;

      case CLC:
        carryFlag = false;
        break;

      case CLD:
        decimalModeFlag = false;
        break;

      case CLI:
        interruptDisableFlag = false;
        break;

      case CLV:
        overflowFlag = false;
        break;

      case CMP:
        tmp = accumulator - inputDataLatch;
        setFlagBorrow(tmp);
        setNZ(tmp);
        break;

      case CPX:
        tmp = indexRegisterX - inputDataLatch;
        setFlagBorrow(tmp);
        setNZ(tmp);
        break;

      case CPY:
        tmp = indexRegisterY - inputDataLatch;
        setFlagBorrow(tmp);
        setNZ(tmp);
        break;

      case DEC:
        dataBusBuffer = ((inputDataLatch - 1) & 0xFF);
        setNZ(dataBusBuffer);
        break;

      case DEX:
        indexRegisterX = ((indexRegisterX - 1) & 0xFF);
        setNZ(indexRegisterX);
        break;

      case DEY:
        indexRegisterY = ((indexRegisterY - 1) & 0xFF);
        setNZ(indexRegisterY);
        break;

      case EOR:
        accumulator = accumulator ^ inputDataLatch;
        setNZ(accumulator);
        break;

      case INC:
        dataBusBuffer = ((inputDataLatch + 1) & 0xFF);
        setNZ(dataBusBuffer);
        break;

      case INX:
        indexRegisterX = ((indexRegisterX + 1) & 0xFF);
        setNZ(indexRegisterX);
        break;

      case INY:
        indexRegisterY = ((indexRegisterY + 1) & 0xFF);
        setNZ(indexRegisterY);
        break;

      case JMP:
        programCounter = (effectiveAddressHigh | effectiveAddressLow);
        break;

      case JSR:
        programCounter = (effectiveAddressHigh | effectiveAddressLow);
        break;

      case LDA:
        accumulator = inputDataLatch;
        setNZ(accumulator);
        break;

      case LDX:
        indexRegisterX = inputDataLatch;
        setNZ(indexRegisterX);
        break;

      case LDY:
        indexRegisterY = inputDataLatch;
        setNZ(indexRegisterY);
        break;

      case LSR:
        carryFlag = (inputDataLatch & 1) != 0;
        dataBusBuffer = inputDataLatch >> 1;
        setNZ(dataBusBuffer);
        break;

      case NOP:
        break;

      case ORA:
        accumulator = accumulator | inputDataLatch;
        setNZ(accumulator);
        break;

      case PHA:
        dataBusBuffer = accumulator;
        break;

      case PHP:
        // PHP pushes with B flag bit set, just like BRK does. Note B flag does not physically exist in PSR.
        packPSR();
        dataBusBuffer = processorStatusRegister | 0x10;
        break;

      case PLA:
        accumulator = inputDataLatch;
        setNZ(accumulator);
        break;

      case PLP:
        processorStatusRegister = inputDataLatch;
        unpackPSR();
        break;

      case ROL:
        tmp = (inputDataLatch & 0x80);
        dataBusBuffer = (((inputDataLatch << 1) | (carryFlag ? 1 : 0)) & 0xFF);
        carryFlag = (tmp != 0);
        setNZ(dataBusBuffer);
        break;

      case ROR:
        tmp = (inputDataLatch & 1);
        dataBusBuffer = ((inputDataLatch >> 1) | (carryFlag ? 0x80 : 0));
        carryFlag = (tmp != 0);
        setNZ(dataBusBuffer);
        break;

      case RTI:
        // Nothing left to do. PC was popped in last cycle.
        break;

      case RTS:
        // Nothing left to do.
        break;

      case SBC:
        op1 = accumulator;
        op2 = inputDataLatch;
        if (decimalModeFlag) {
          int lo, hi;
          lo = (op1 & 0x0F) - (op2 & 0x0F) - (carryFlag ? 0 : 1);
          if ((lo & 0x10) != 0) lo -= 6;
          hi = (op1 >> 4) - (op2 >> 4) - ((lo & 0x10) != 0 ? 1 : 0);
          if ((hi & 0x10) != 0) hi -= 6;
          tmp = (hi << 4) | (lo & 0x0F);
          accumulator = tmp & 0xFF;
          carryFlag = ((hi & 0xFF) < 15);
          zeroResultFlag = (accumulator == 0);
          overflowFlag = false;       // BCD never sets overflow flag
          negativeResultFlag = false; // BCD is never negative on NMOS 6502
          
        } else {  // binary mode
          tmp = op1 - op2 - (carryFlag ? 0 : 1);
          accumulator = tmp & 0xFF;
          overflowFlag = ((op1 ^ op2) & (op1 ^ accumulator) & 0x80) != 0;
          setFlagBorrow(tmp);
          setNZ(accumulator);
        }
        break;

      case SEC:
        carryFlag = true;
        break;

      case SED:
        decimalModeFlag = true;
        break;

      case SEI:
        interruptDisableFlag = true;
        break;

      case STA:
        dataBusBuffer = accumulator;
        break;

      case STX:
        dataBusBuffer = indexRegisterX;
        break;

      case STY:
        dataBusBuffer = indexRegisterY;
        break;

      case TAX:
        indexRegisterX = accumulator;
        setNZ(indexRegisterX);
        break;

      case TAY:
        indexRegisterY = accumulator;
        setNZ(indexRegisterY);
        break;

      case TSX:
        indexRegisterX = stackPointer;
        setNZ(indexRegisterX);
        break;

      case TXA:
        accumulator = indexRegisterX;
        setNZ(accumulator);
        break;

      case TXS:
        stackPointer = indexRegisterX;
        // Does not affect the PSR.
        break;

      case TYA:
        accumulator = indexRegisterY;
        setNZ(accumulator);
        break;

      case ASL_A:
        carryFlag = ((accumulator & 0x80) != 0);
        accumulator = ((accumulator << 1) & 0xFF);
        setNZ(accumulator);
        break;

      case LSR_A:
        carryFlag = (accumulator & 1) != 0;
        accumulator = accumulator >> 1;
        setNZ(accumulator);
        break;

      case ROL_A:
        tmp = (accumulator << 1) | (carryFlag ? 1 : 0);
        accumulator = (tmp & 0xFF);
        //setFlagCarry(tmp);
        carryFlag = ((tmp & 0x100) != 0);
        setNZ(accumulator);
        break;

      case ROR_A:
        tmp = accumulator | (carryFlag ? 0x100 : 0);
        carryFlag = (accumulator & 1) != 0;
        accumulator = (tmp >> 1);
        setNZ(accumulator);
        break;

      /* These are not instructions but this engine treats them as such */
      case NMI:
        interruptDisableFlag = true;
        programCounter = (effectiveAddressHigh | effectiveAddressLow);
        // NMI signals occur on the negative transition only, so we need to reset.
        interruptStatus &= ~S_NMI;
        break;

      case IRQ:
        // This flag should be set 3 cycles ago, but it shouldn't matter too much, because its
        // only checked on instruction completion, so setting it partway through IRQ steps is fine.
        interruptDisableFlag = true;
        programCounter = (effectiveAddressHigh | effectiveAddressLow);
        // IRQ signals occur on a low level, so it is the responsibility of
        // the external hardware device to reset it.
        break;

      case SLO:
        // Shift left one bit in memory, then OR accumulator with memory.
        carryFlag = ((inputDataLatch & 0x80) != 0);
        dataBusBuffer = ((inputDataLatch << 1) & 0xFF);
        accumulator = accumulator | dataBusBuffer;
        setNZ(accumulator);
        break;
        
      case SAX:
        // ANDs the contents of the A and X registers (without changing the 
        // contents of either register) and stores the result in memory.
        // AXS does not affect any flags in the processor status register.
        dataBusBuffer = (accumulator & indexRegisterX);
        break;
        
      case ANC:
        // AND byte with accumulator. If result is negative then carry is set.
        accumulator = accumulator & inputDataLatch;
        setNZ(accumulator);
        carryFlag = negativeResultFlag;
        break;
        
      case LAX:
        // M -> A -> X
        accumulator = inputDataLatch;
        indexRegisterX = inputDataLatch;
        setNZ(indexRegisterX);
        break;
        
      case ISC:
        // M + 1 -> M, A - M - C -> A
        // INC oper + SBC oper
        // Increase memory by one, then subtract memory from accumulator (with borrow).
        // Status flags: N,V,Z,C
        // TODO: Test this. There are demos that use it.
        dataBusBuffer = ((inputDataLatch + 1) & 0xFF);
        op1 = accumulator;
        //op2 = inputDataLatch;
        op2 = dataBusBuffer;
        if (decimalModeFlag) {
            int lo, hi;
            lo = (op1 & 0x0F) - (op2 & 0x0F) - (carryFlag ? 0 : 1);
            if ((lo & 0x10) != 0) lo -= 6;
            hi = (op1 >> 4) - (op2 >> 4) - ((lo & 0x10) != 0 ? 1 : 0);
            if ((hi & 0x10) != 0) hi -= 6;
            tmp = (hi << 4) | (lo & 0x0F);
            accumulator = tmp & 0xFF;
            carryFlag = ((hi & 0xFF) < 15);
            zeroResultFlag = (accumulator == 0);
            overflowFlag = false;       // BCD never sets overflow flag
            negativeResultFlag = false; // BCD is never negative on NMOS 6502
            
        } else {  // binary mode
            tmp = op1 - op2 - (carryFlag ? 0 : 1);
            accumulator = tmp & 0xFF;
            overflowFlag = ((op1 ^ op2) & (op1 ^ accumulator) & 0x80) != 0;
            setFlagBorrow(tmp);
            setNZ(accumulator);
        }
        break;
        
      case RRA:
        // M = C -> [76543210] -> C, A + M + C -> A, C
        // Rotate one bit right in memory, then add memory to accumulator (with
        // carry).
        tmp = (inputDataLatch & 1);
        dataBusBuffer = ((inputDataLatch >> 1) | (carryFlag ? 0x80 : 0));
        //carryFlag = (tmp != 0);
        //setNZ(dataBusBuffer);
        op1 = accumulator;
        //op2 = inputDataLatch;
        op2 = dataBusBuffer;
        if (decimalModeFlag) {
            int lo, hi;
            lo = (op1 & 0x0f) + (op2 & 0x0f) + (carryFlag ? 1 : 0);
            if ((lo & 0xff) > 9) lo += 6;
            hi = (op1 >> 4) + (op2 >> 4) + (lo > 15 ? 1 : 0);
            if ((hi & 0xff) > 9) hi += 6;
            tmp = (hi << 4) | (lo & 0x0f);
            accumulator = tmp & 0xff;
            carryFlag = (hi > 15);
            zeroResultFlag = (accumulator == 0);
            overflowFlag = false;       // BCD never sets overflow flag
            negativeResultFlag = false; // BCD is never negative on NMOS 6502
        }
        else {       // binary mode
            tmp = op1 + op2 + (carryFlag ? 1 : 0);
            accumulator = tmp & 0xFF;
            overflowFlag = ((op1 ^ accumulator) & ~(op1 ^ op2) & 0x80) != 0;
            setFlagCarry(tmp);
            setNZ(accumulator);
        }
        break;
        
      case ARR:
          // AND oper + ROR
          // A AND oper, C -> [76543210] -> C
          // V-flag is set according to (A AND oper) + oper
          // The carry is not set, but bit 7 (sign) is exchanged with the carry
          // AND byte with accumulator, then rotate one bit right in accumulator and
          // check bit 5 and 6:
          // If both bits are 1: set C, clear V.
          // If both bits are 0: clear C and V.
          // If only bit 5 is 1: set V, clear C.
          // If only bit 6 is 1: set C and V.
          // Status flags: N,V,Z,C
          
          accumulator = accumulator & inputDataLatch;
          //setNZ(accumulator);
          
          tmp = accumulator | (carryFlag ? 0x100 : 0);
          carryFlag = (accumulator & 1) != 0;
          accumulator = (tmp >> 1);
          setNZ(accumulator);
          
          break;
        
      case TRAP:
        // A Trap pretends to be 6502 subroutine, allowing the emulator to hook non-standard features into the emulation.
        Trap trap = traps.get(programCounter - 1);
        if (trap != null) {
          try {
            Integer newPC = trap.trapRoutine.call();
            if (newPC != null) {
              programCounter = newPC;
            }
          } catch (Exception e) { /* Will never happen. It's part of the Callable interface so has to be caught. */ }
        } else {
          System.err.print("Failed to run trap routine. No routine matches PC value of: " + programCounter);
        }
        break;
         
      default: // Unknown instruction.
        break;
    }
  }

    // Total number of cycles emulated since the machine began.
    private long totalCycles;
    
    /**
     * Gets total number of cycles emulated since the machine began.
     * 
     * @return total number of cycles emulated since the machine began.
     */
    public long getTotalCycles() {
        return totalCycles;
    }

    /**
     * Steps through a single instruction. Used mainly for unit tests and debugging CPU.
     */
    public void step() {
        // Keep emulating cycles until the instruction changes.
        do {
            emulateCycle();
        } while (currentInstructionStep > 1);
    }

    /**
     * Steps through the given number of instructions. Used mainly for unit tests and
     * debugging the CPU.
     * 
     * @param numberOfInstructions The number of instructions to step.
     */
    public void step(int numberOfInstructions) {
        for (int i = 0; i < numberOfInstructions; i++) {
            step();
        }
    }
  
  /**
   * Emulates a machine cycle. There should be exactly one read or one write
   * per cycle, even in scenarios where the fetched data is discarded.
   */
  public void emulateCycle() {
    int action = 0;
    
    totalCycles++;
    
    if (currentInstructionStep < numOfInstructionSteps) {
      // Get the action for the current cycle of the instruction.
      action = instructionSteps[currentInstructionStep];

      // Execute instruction step.
      switch (action) {
        case BRANCH_DIS_NEXT:
          // Third step of a branch. Fetch next op code, and add offset.
          inputDataLatch = ((inputDataLatch & 0x80) == 0? inputDataLatch : inputDataLatch - 0x100);
          branchAddress = ((programCounter + inputDataLatch) & 0xFFFF);
          if ((programCounter & 0xFF00) == (branchAddress & 0xFF00)) {
            // Destination address within same page, so skip next instruction step.
            programCounter = branchAddress;
            currentInstructionStep++;
          } else {
            // Crossing page boundary, which means that delayed interrupt doesn't apply.
            delayInterruptOneCycle = false;
          }
          break;

        case BRANCH_DIS_OFFSET:
          // Fourth step of a branch. Fetch bad op code, fix program counter.
          programCounter = branchAddress;
          break;

        case EXECUTE_BRANCH:
          // Fetch offset
          inputDataLatch = memoryMap[programCounter].readMemory(programCounter);
          programCounter++;
          // Execute the branch test.
          executeInstruction();
          // Skip next two instruction steps if branch is not taken.
          if (!branchFlag) {
            currentInstructionStep += 2;
          } else {
            // Delay pending interrupt by one cycle if branch is taken. Happens only when not 
            // crossing page boundary (see BRANCH_DIS_NEXT for its reversal of this on such 
            // boundary crossing). Note that this is an undocumented feature, but has been 
            // discovered independently by different people in BBC, Atari, Commodrore, etc. 
            // communities at different times over the years.
            delayInterruptOneCycle = true;
          }
          break;

        case EXECUTE_DIS:               // Execute (with fetch next op code then discard)
          // Single byte instructions & PHP, PHA
          executeInstruction();
          break;

        case EXECUTE_LAST:              // Execute (with fetch next op code)
          // Execute previous instruction.
          executeInstruction();
          // Fetch next op code.
          currentInstructionStep = 0;
          if ((interruptStatus == 0) || (((interruptStatus & S_NMI) == 0) && interruptDisableFlag) || delayInterruptOneCycle) {
            if (debug) {
              displayCurrentInstruction();
            }
            // No interrupts, so proceed to next instruction.
            instructionRegister = memoryMap[programCounter].readMemory(programCounter);
            programCounter++;
            instructionSteps = INSTRUCTION_DECODE_MATRIX[instructionRegister];
            delayInterruptOneCycle = false;
          }
          else {
            // An interrupt occurred.
            instructionSteps = ((interruptStatus & S_NMI) == 0? IRQ_STEPS : NMI_STEPS);
          }
          numOfInstructionSteps = instructionSteps.length;
          break;

        case EXECUTE_MID_ADL:           // Executes (does a write unmodified data at same time)
          // Dummy write. No I/O in page zero (ASL, LSR, ROL, ROR, DEC, INC - Zero Page)
          mem[effectiveAddressLow] = inputDataLatch;
          executeInstruction();
          break;

        case EXECUTE_MID_BA:
          // Dummy write (ASL, LSR, ROL, ROR, DEC, INC - Absolute, X)
          memory.writeMemory(baseAddressHigh | baseAddressLow, inputDataLatch);
          executeInstruction();
          break;

        case EXECUTE_MID_BAL:
          // Dummy write. No I/O in page zero (ASL, LSR, ROL, ROR, DEC, INC - Zero Page, X)
          mem[baseAddressLow] = inputDataLatch;
          executeInstruction();
          break;

        case EXECUTE_MID_EA:
          // Dummy write (ASL, LSR, ROL, ROR, DEC, INC - Absolute)
          memory.writeMemory(effectiveAddressHigh | effectiveAddressLow, inputDataLatch);
          executeInstruction();
          break;

        case EXECUTE_STORE_ADL:
          // No I/O in pzge zero (STA, STX, STY - Zero Page)
          executeInstruction();
          mem[effectiveAddressLow] = dataBusBuffer;
          break;

        case EXECUTE_STORE_EA:
          // (STA, STX, STY - (Indirect, X) & Absolute)
          executeInstruction();
          memory.writeMemory(effectiveAddressHigh | effectiveAddressLow, dataBusBuffer);
          break;

        case EXECUTE_STORE_BA:
          // (STA - Absolute, X & (Indirect),Y)
          executeInstruction();
          memory.writeMemory(baseAddressHigh | baseAddressLow, dataBusBuffer);
          break;

        case EXECUTE_STORE_BAL:
          // No I/O in page zero (STY, STA, STX - Zero Page, X & Y)
          executeInstruction();
          mem[baseAddressLow] = dataBusBuffer;
          break;


        case FETCH_ADH_BAL:
          // No I/O in page zero, so we can access memory directly.
          effectiveAddressHigh = (mem[baseAddressLow] << 8);
          break;

        case FETCH_ADH_FFFB:
          effectiveAddressHigh = (memoryMap[0xFFFB].readMemory(0xFFFB) << 8);
          break;

        case FETCH_ADH_FFFF:
          effectiveAddressHigh = (memoryMap[0xFFFF].readMemory(0xFFFF) << 8);
          break;

        case FETCH_ADH_PC:
          // Program counter is highly unlikely to be pointing at I/O
          effectiveAddressHigh = (memoryMap[programCounter].readMemory(programCounter) << 8);
          programCounter++;
          break;

        case FETCH_ADH_IA:
          // Only used by JMP, so not likely to have IO address involved.
          int indirectAddress = indirectAddressHigh | indirectAddressLow;
          effectiveAddressHigh = (memoryMap[indirectAddress].readMemory(indirectAddress) << 8);
          break;

        case FETCH_ADL_BAL:
          // No I/O in page zero, so we can access memory directly.
          effectiveAddressLow = mem[baseAddressLow];
          baseAddressLow = ((baseAddressLow + 1) & 0xFF);
          break;

        case FETCH_ADL_FFFA:
          effectiveAddressLow = memoryMap[0xFFFA].readMemory(0xFFFA);
          break;

        case FETCH_ADL_FFFE:
          effectiveAddressLow = memoryMap[0xFFFE].readMemory(0xFFFE);
          break;

        case FETCH_ADL_PC:
          // Program counter is highly unlikely to be pointing at I/O
          effectiveAddressLow = memoryMap[programCounter].readMemory(programCounter);
          programCounter++;
          break;

        case FETCH_ADL_IA:
          // Only used by JMP, so not likely to have IO address involved.
          indirectAddress = indirectAddressHigh | indirectAddressLow;
          effectiveAddressLow = memoryMap[indirectAddress].readMemory(indirectAddress);
          indirectAddressLow = ((indirectAddressLow + 1) & 0xFF); // Well known NMOS 6502 bug
          break;

        case FETCH_BAH_IAL:
          // No I/O in page zero.
          baseAddressHigh = (mem[indirectAddressLow] << 8);
          break;

        case FETCH_BAH_PC:
          // Program counter is highly unlikely to be pointing at I/O
          baseAddressHigh = (memoryMap[programCounter].readMemory(programCounter) << 8);
          programCounter++;
          break;

        case FETCH_BAL_IAL:             // Increments IAL by 1 aswell (& 0xFF??)
          // No I/O in page zero.
          baseAddressLow = mem[indirectAddressLow];
          indirectAddressLow = ((indirectAddressLow + 1) & 0xFF);
          break;

        case FETCH_BAL_PC:
          // Program counter is highly unlikely to be pointing at I/O
          baseAddressLow = memoryMap[programCounter].readMemory(programCounter);
          programCounter++;
          break;

        case FETCH_DATA_ADL:
          // No I/O in page zero.
          inputDataLatch = mem[effectiveAddressLow];
          break;

        case FETCH_DATA_BA:
          inputDataLatch = memory.readMemory(baseAddressHigh | baseAddressLow);
          break;

        case FETCH_DATA_BA_X:
          // (Absolute, X)
          baseAddressLow = baseAddressLow + indexRegisterX;
          if ((baseAddressLow & 0x100) != 0) {
            baseAddressLow = (baseAddressLow & 0xFF);
            memory.readMemory(baseAddressHigh | baseAddressLow);
            baseAddressHigh = ((baseAddressHigh + 0x100) & 0xFF00);
          }
          else {
            // Page boundary not crossed, so the data is okay.
            inputDataLatch = memory.readMemory(baseAddressHigh | baseAddressLow);
            // Skip next step in instruction because it isn't needed.
            currentInstructionStep++;
          }
          break;

        case FETCH_DATA_BA_Y:
          // ((Indirect), Y & Absolute, Y)
          baseAddressLow = baseAddressLow + indexRegisterY;
          if ((baseAddressLow & 0x100) != 0) {
            baseAddressLow = (baseAddressLow & 0xFF);
            memory.readMemory(baseAddressHigh | baseAddressLow);
            baseAddressHigh = ((baseAddressHigh + 0x100) & 0xFF00);
          }
          else {
            // Page boundary not crossed, so the data is okay.
            inputDataLatch = memory.readMemory(baseAddressHigh | baseAddressLow);
            // Skip next step in instruction because it isn't needed.
            currentInstructionStep++;
          }
          break;

        case FETCH_DATA_BAL:
          // No I/O in page zero
          inputDataLatch = mem[baseAddressLow];
          break;

        case FETCH_DATA_EA:
          inputDataLatch = memory.readMemory(effectiveAddressHigh | effectiveAddressLow);
          break;

        case FETCH_DATA_PC:
          // Program counter is highly unlikely to be pointing at I/O
          inputDataLatch = memoryMap[programCounter].readMemory(programCounter);
          programCounter++;
          break;

        case FETCH_DATA_SP:
          // No I/O in the stack page (PLP, PLA)
          stackPointer = ((stackPointer + 1) & 0xFF);
          inputDataLatch = mem[stackPointer + 0x100];
          break;

        case FETCH_DIS_BA_X:
          // (ASL, ROL, LSR, ROR, STA, DEC, INC - Absolute, X)
          baseAddressLow = baseAddressLow + indexRegisterX;
          if ((baseAddressLow & 0x100) != 0) {
            baseAddressLow = (baseAddressLow & 0xFF);
            memory.readMemory(baseAddressHigh | baseAddressLow);
            baseAddressHigh = ((baseAddressHigh + 0x100) & 0xFF00);
          }
          else {
            memory.readMemory(baseAddressHigh | baseAddressLow);
          }
          break;

        case FETCH_DIS_BA_Y:
          // (STA - Absolute, Y & (Indirect), Y)
          baseAddressLow = baseAddressLow + indexRegisterY;
          if ((baseAddressLow & 0x100) != 0) {
            baseAddressLow = (baseAddressLow & 0xFF);
            memory.readMemory(baseAddressHigh | baseAddressLow);
            baseAddressHigh = ((baseAddressHigh + 0x100) & 0xFF00);
          }
          else {
            memory.readMemory(baseAddressHigh | baseAddressLow);
          }
          break;

        case FETCH_DIS_BAL_X:           // Fetch using BAL then discard. Add X to BAL.
          // No I/O in page zero ((Indirect, X) & Zero Page, X)
          baseAddressLow = ((baseAddressLow + indexRegisterX) & 0xFF);
          break;

        case FETCH_DIS_BAL_Y:
          // No I/O in page zero (STX, LDX - Zero Page, Y)
          baseAddressLow = ((baseAddressLow + indexRegisterY) & 0xFF);
          break;

        case FETCH_DIS_PC:              // Fetches using PC but doesn't increment PC.
          // Program counter is highly unlikely to be pointing at I/O (PLA, PLP. RTS, RTI, IRQ, NMI)
          // TODO: Apparently it does fetch data using PC, but having this here affects the sound. ?!
          //memoryMap[programCounter].readMemory(programCounter);
          break;

        case FETCH_DIS_SP:
          // No I/O in stack page (PLA, PLP, JSR, RTS, RTI)
          break;

        case FETCH_IAH_PC:
          // Program counter is highly unlikely to be pointing at I/O
          indirectAddressHigh = (memoryMap[programCounter].readMemory(programCounter) << 8);
          programCounter++;
          break;

        case FETCH_IAL_PC:
          // Program counter is highly unlikely to be pointing at I/O
          indirectAddressLow = memoryMap[programCounter].readMemory(programCounter);
          programCounter++;
          break;
          
        case FETCH_INC_PC:
          // Fetch using PC, discard data, then increment PC.
          memoryMap[programCounter].readMemory(programCounter);
          programCounter++;
          break;
          
        case FETCH_P_SP:
          // No I/O in the stack page (RTI)
          stackPointer = ((stackPointer + 1) & 0xFF);
          processorStatusRegister = mem[stackPointer + 0x100];
          unpackPSR();
          break;
          
        case FETCH_PCH_SP:
          // No I/O in the stack page (RTS, RTI)
          stackPointer = ((stackPointer + 1) & 0xFF);
          programCounter = (programCounter | (mem[stackPointer + 0x100] << 8));
          break;

        case FETCH_PCL_SP:
          // No I/O in the stack page (RTS, RTI)
          stackPointer = ((stackPointer + 1) & 0xFF);
          programCounter = mem[stackPointer + 0x100];
          break;


        case STORE_DATA_ADL:
          // No I/O in zero page (ASL, ROL, LSR, ROR, DEC, INC - Zero Page)
          mem[effectiveAddressLow] = dataBusBuffer;
          break;

        case STORE_DATA_BA:
          // (ASL, ROL, LSR, ROR, DEC, INC - Absolute, X)
          memory.writeMemory(baseAddressHigh | baseAddressLow, dataBusBuffer);
          break;

        case STORE_DATA_BAL:
          // No I/O in zero page (ASL, ROL, LSR, ROR, DEC, INC - Zero Page, X)
          mem[baseAddressLow] = dataBusBuffer;
          break;

        case STORE_DATA_EA:
          // (ASL, ROL, LSR, ROR, DEC, INC - Absolute)
          memory.writeMemory(effectiveAddressHigh | effectiveAddressLow, dataBusBuffer);
          break;

        case STORE_DATA_SP:
          // No I/O in the stack page (PHP, PHA)
          mem[stackPointer + 0x100] = dataBusBuffer;
          stackPointer = ((stackPointer - 1) & 0xFF);
          break;

        case STORE_P_SP:
          // No I/O in the stack page (BRK)
          packPSR();
          mem[stackPointer + 0x100] = (processorStatusRegister | (instructionRegister == 0? 0x10 : 0)); // BRK flag only exists on stack.
          stackPointer = ((stackPointer - 1) & 0xFF);
          break;

        case STORE_PCH_SP:
          // No I/O in the stack page (BRK, JSR)
          mem[stackPointer + 0x100] = (programCounter >> 8);
          stackPointer = ((stackPointer - 1) & 0xFF);
          break;

        case STORE_PCL_SP:
          // No I/O in the stack page (BRK, JSR)
          mem[stackPointer + 0x100] = (programCounter & 0xFF);
          stackPointer = ((stackPointer - 1) & 0xFF);
          break;

        default: // Action code not recognised.
          break;
      }

      // Increment timing control.
      currentInstructionStep++;
    }
    else {
      // Set up next instruction.
      currentInstructionStep = 1;

      if ((interruptStatus == 0) || (((interruptStatus & S_NMI) == 0) && interruptDisableFlag) || delayInterruptOneCycle) {
        if (debug) {
          displayCurrentInstruction();
        }
        // No interrupts, so proceed to next instruction.
        instructionRegister = memoryMap[programCounter].readMemory(programCounter);
        programCounter++;
        instructionSteps = INSTRUCTION_DECODE_MATRIX[instructionRegister];
        delayInterruptOneCycle = false;
      }
      else {
        // An interrupt occurred.
        instructionSteps = ((interruptStatus & S_NMI) == 0? IRQ_STEPS : NMI_STEPS);
      }

      numOfInstructionSteps = instructionSteps.length;
    }
    
    if (instructionSteps.length == 0) {
        if (instructionRegister != 114)
      System.out.println(StringUtils.format("Unknown instruction: {0}", instructionRegister));
    }
  }
  
    ///////////////////////////////// DEBUG /////////////////////////////////////////
    
    /**
     * Whether the chip is running in debug mode or not.
     */
    private boolean debug = false;
  
    // Address mode constants for use with debugging monitor.
    private static final int Ac = 0;
    private static final int Il = 1;
    private static final int Im = 2;
    private static final int Ab = 3;
    private static final int Zp = 4;
    private static final int Zx = 5;
    private static final int Zy = 6;
    private static final int Ax = 7;
    private static final int Ay = 8;
    private static final int Rl = 9;
    private static final int Ix = 10;
    private static final int Iy = 11;
    private static final int In = 12;
    private static final int No = 13;

    /**
     * Instruction names for use with debugging monitor.
     */
    private static String instructionNames[]= {
        "ADC ","AND ","ASL ","BCC ","BCS ","BEQ ","BIT ","BMI ",
        "BNE ","BPL ","BRK","BVC ","BVS ","CLC","CLD","CLI",
        "CLV","CMP ","CPX ","CPY ","DEC ","DEX","DEY","INX",
        "INY","EOR ","INC ","JMP ","JSR ","LDA ","NOP ","LDX ",
        "LDY ","LSR ","ORA ","PHA","PHP","PLA","PLP","ROL ",
        "ROR ","RTI","RTS","SBC ","STA ","STX ","STY ","SEC ",
        "SED","SEI","TAX","TAY","TXA","TYA","TSX","TXS"
    };

    /**
     * Instruction set name and addressing mode lookup table for use with
     * debugging monitor.
     */
    private static int instructionInfo[] = {
        10,Il, 34,Ix, No,No, No,No, No,No, 34,Zp,  2,Zp, No,No,
        36,Il, 34,Im,  2,Ac, No,No, No,No, 34,Ab,  2,Ab, No,No,
         9,Rl, 34,Iy, No,No, No,No, No,No, 34,Zx,  2,Zx, No,No,
        13,Il, 34,Ay, No,No, No,No, No,No, 34,Ax,  2,Ax, No,No,
        28,Ab,  1,Ix, No,No, No,No,  6,Zp,  1,Zp, 39,Zp, No,No,
        38,Il,  1,Im, 39,Ac, No,No,  6,Ab,  1,Ab, 39,Ab, No,No,
         7,Rl,  1,Iy, No,No, No,No, No,No,  1,Zx, 39,Zx, No,No,
        47,Il,  1,Ay, No,No, No,No, No,No,  1,Ax, 39,Ax, No,No,
        41,Il, 25,Ix, No,No, No,No, No,No, 25,Zp, 33,Zp, No,No,
        35,Il, 25,Im, 33,Ac, No,No, 27,Ab, 25,Ab, 33,Ab, No,No,
        11,Rl, 25,Iy, No,No, No,No, No,No, 25,Zx, 33,Zx, No,No,
        15,Il, 25,Ay, No,No, No,No, No,No, 25,Ax, 33,Ax, No,No,
        42,Il,  0,Ix, No,No, No,No, No,No,  0,Zp, 40,Zp, No,No,
        37,Il,  0,Im, 40,Ac, No,No, 27,In,  0,Ab, 40,Ab, No,No,
        12,Rl,  0,Iy, No,No, No,No, No,No,  0,Zx, 40,Zx, No,No,
        49,Il,  0,Ay, No,No, No,No, No,No,  0,Ax, 40,Ax, No,No,
        No,No, 44,Ix, No,No, No,No, 46,Zp, 44,Zp, 45,Zp, No,No,
        22,Il, No,No, 52,Il, No,No, 46,Ab, 44,Ab, 45,Ab, No,No,
         3,Rl, 44,Iy, No,No, No,No, 46,Zx, 44,Zx, 45,Zy, No,No,
        53,Il, 44,Ay, 55,Il, No,No, No,No, 44,Ax, No,No, No,No,
        32,Im, 29,Ix, 31,Im, No,No, 32,Zp, 29,Zp, 31,Zp, No,No,
        51,Il, 29,Im, 50,Il, No,No, 32,Ab, 29,Ab, 31,Ab, No,No,
         4,Rl, 29,Iy, No,No, No,No, 32,Zx, 29,Zx, 31,Zy, No,No,
        16,Il, 29,Ay, 54,Il, No,No, 32,Ax, 29,Ax, 31,Ay, No,No,
        19,Im, 17,Ix, No,No, No,No, 19,Zp, 17,Zp, 20,Zp, No,No,
        24,Il, 17,Im, 21,Il, No,No, 19,Ab, 17,Ab, 20,Ab, No,No,
         8,Rl, 17,Iy, No,No, No,No, No,No, 17,Zx, 20,Zx, No,No,
        14,Il, 17,Ay, No,No, No,No, No,No, 17,Ax, 20,Ax, No,No,
        18,Im, 43,Ix, No,No, No,No, 18,Zp, 43,Zp, 26,Zp, No,No,
        23,Il, 43,Im, 30,Il, No,No, 18,Ab, 43,Ab, 26,Ab, No,No,
         5,Rl, 43,Iy, No,No, No,No, No,No, 43,Zx, 26,Zx, No,No,
        48,Il, 43,Ay, No,No, No,No, No,No, 43,Ax, 26,Ax, No,No
    };
 
    /**
     * Displays the instruction currently pointed to by the program counter.
     */
    public void displayCurrentInstruction() {
        int start = 0;
        int insNum = 0;
        int offset = 0, to = 0;

        start = programCounter;
        insNum = (memory.readMemory(start) << 1);

        StringBuffer insBuf = new StringBuffer();
        insBuf.append(getRegisterStatus());
        insBuf.append("\n");
        insBuf.append(StringUtils.format("{0}", start));
        insBuf.append(":    ");
        insBuf.append(instructionNames[instructionInfo[insNum]]);

        switch (instructionInfo[insNum + 1]) {
            case Ac:
                insBuf.append("A");
                break;
            case Il:
                break;
    
            case Rl:
                offset = memory.readMemory(start + 1);
                to = programCounter + 2 + ((offset < 0x80) ? offset : (offset - 0x100));
                insBuf.append("$");
                insBuf.append(Integer.toHexString(to));
                break;
    
            case Im:
                insBuf.append("#$");
                insBuf.append(addLeadingZeroes(Integer.toHexString(memory.readMemory(start + 1)), 2));
                break;
            case Zp:
                insBuf.append("$");
                insBuf.append(addLeadingZeroes(Integer.toHexString(memory.readMemory(start + 1)), 2));
                break;
            case Zx:
                insBuf.append("$");
                insBuf.append(addLeadingZeroes(Integer.toHexString(memory.readMemory(start + 1)), 2));
                insBuf.append(",x");
                break;
            case Zy:
                insBuf.append("$");
                insBuf.append(addLeadingZeroes(Integer.toHexString(memory.readMemory(start + 1)), 2));
                insBuf.append(",y");
                break;
            case Ix:
                insBuf.append("($");
                insBuf.append(addLeadingZeroes(Integer.toHexString(memory.readMemory(start + 1)), 2));
                insBuf.append(",x)");
                break;
            case Iy:
                insBuf.append("($");
                insBuf.append(addLeadingZeroes(Integer.toHexString(memory.readMemory(start + 1)), 2));
                insBuf.append("),y");
                break;
    
            case Ab:
                insBuf.append("$");
                insBuf.append(addLeadingZeroes(
                        Integer.toHexString((memory.readMemory(start + 2) << 8) + memory.readMemory(start + 1)), 4));
                break;
            case Ax:
                insBuf.append("$");
                insBuf.append(addLeadingZeroes(
                        Integer.toHexString((memory.readMemory(start + 2) << 8) + memory.readMemory(start + 1)), 4));
                insBuf.append(",x");
                break;
            case Ay:
                insBuf.append("$");
                insBuf.append(addLeadingZeroes(
                        Integer.toHexString((memory.readMemory(start + 2) << 8) + memory.readMemory(start + 1)), 4));
                insBuf.append(",y");
                break;
            case In:
                insBuf.append("($");
                insBuf.append(addLeadingZeroes(
                        Integer.toHexString((memory.readMemory(start + 2) << 8) + memory.readMemory(start + 1)), 4));
                insBuf.append(")");
                break;
    
            default:
                insBuf.append(".db $");
                insBuf.append(Integer.toHexString(insNum >> 1));
                insBuf.append("; <Invalid OPcode>");
        }

        System.out.println(insBuf.toString());
    }

    /**
     * Returns a string containing the current status of the internal registers.
     *
     * @return a string containing the current status of the internal registers.
     */
    public String getRegisterStatus() {
        StringBuffer regBuf = new StringBuffer();

        regBuf.append(StringUtils.format("{0}", totalCycles));
        regBuf.append(":  ");
        regBuf.append("PC=");
        regBuf.append(addLeadingZeroes(Integer.toHexString(programCounter), 4));
        regBuf.append(",A=");
        regBuf.append(addLeadingZeroes(Integer.toHexString(accumulator), 2));
        regBuf.append(",X=");
        regBuf.append(addLeadingZeroes(Integer.toHexString(indexRegisterX), 2));
        regBuf.append(",Y=");
        regBuf.append(addLeadingZeroes(Integer.toHexString(indexRegisterY), 2));
        regBuf.append(",S=");
        regBuf.append(addLeadingZeroes(Integer.toHexString(stackPointer), 2));
        regBuf.append(",P=");
        regBuf.append(getFlagStatus());
        regBuf.append(",stack=[");
        regBuf.append(getStackContents());
        regBuf.append("] ");

        return (regBuf.toString());
    }

    /**
     * Returns a string containing the current status of the internal processor
     * status register flags.
     *
     * @return the current state of the CPU flags.
     */
    public String getFlagStatus() {
        StringBuffer flagBuf = new StringBuffer();

        flagBuf.append(negativeResultFlag ? "N" : "-");
        flagBuf.append(overflowFlag ? "O" : "-");
        flagBuf.append("E");
        // flagBuf.append(breakCommandFlag? "B" : "-");
        flagBuf.append(decimalModeFlag ? "D" : "-");
        flagBuf.append(interruptDisableFlag ? "I" : "-");
        flagBuf.append(zeroResultFlag ? "Z" : "-");
        flagBuf.append(carryFlag ? "C" : "-");

        return (flagBuf.toString());
    }

    /**
     * Returns a string containing the current contents of the stack starting from
     * 0xFF down to the current value of stackPointer.
     *
     * @return the current contents of the stack.
     */
    public String getStackContents() {
        StringBuffer stackBuf = new StringBuffer();

        for (int i = 0xFF; i > stackPointer; i--) {
            if (i < 0xFF) {
                stackBuf.append(",");
            }
            stackBuf.append(addLeadingZeroes(Integer.toHexString(mem[i + 0x0100]), 2));
        }

        return (stackBuf.toString());
    }

    /**
     * Pads the given string with leading '0' characters until it reaches the
     * desired width.
     *
     * @param str          the string to add the leading zeroes to.
     * @param desiredWidth the desired width of the final string.
     *
     * @return the string with the leading '0' characters added.
     */
    private static String addLeadingZeroes(String str, int desiredWidth) {
        StringBuffer buf = new StringBuffer();
        int numToAdd = desiredWidth - str.length();

        for (int i = 0; i < numToAdd; i++) {
            buf.append("0");
        }
        buf.append(str);

        return (buf.toString());
    }

    public int getAccumulator() {
        return accumulator;
    }

    public void setAccumulator(int value) {
        accumulator = value;
    }

    public int getIndexRegisterX() {
        return indexRegisterX;
    }

    public void setIndexRegisterX(int value) {
        indexRegisterX = value;
    }

    public int getIndexRegisterY() {
        return indexRegisterY;
    }

    public void setIndexRegisterY(int value) {
        indexRegisterY = value;
    }

    public int getStackPointer() {
        return stackPointer;
    }

    public void setStackPointer(int value) {
        stackPointer = value;
    }

    public int getProgramCounter() {
        return programCounter;
    }

    public void setProgramCounter(int value) {
        programCounter = value;
    }

    public int getProcessorStatus() {
        packPSR();
        return processorStatusRegister;
    }

    public void setProcessorStatus(int value) {
        processorStatusRegister = value;
        unpackPSR();
    }

    public int getInstructionRegister() {
        return instructionRegister;
    }

    public boolean getCarryFlag() {
        return carryFlag;
    }

    public void setCarryFlag(boolean value) {
        carryFlag = value;
    }

    public boolean getZeroResultFlag() {
        return zeroResultFlag;
    }

    public void setZeroResultFlag(boolean value) {
        zeroResultFlag = value;
    }

    public boolean getNegativeResultFlag() {
        return negativeResultFlag;
    }

    public void setNegativeResultFlag(boolean value) {
        negativeResultFlag = value;
    }

    public boolean getOverflowFlag() {
        return overflowFlag;
    }

    public void setOverflowFlag(boolean value) {
        overflowFlag = value;
    }

    public boolean getDecimalModeFlag() {
        return decimalModeFlag;
    }

    public void setDecimalModeFlag(boolean value) {
        decimalModeFlag = value;
    }

    public boolean getInterruptDisableFlag() {
        return interruptDisableFlag;
    }

    public void setInterruptDisableFlag(boolean value) {
        interruptDisableFlag = value;
    }

    public void stackPush(int value) {
        mem[stackPointer + 0x100] = value;
        stackPointer = ((stackPointer - 1) & 0xFF);
    }

    public int stackPeek() {
        return mem[0x100 + stackPointer + 1];
    }

    public int stackPop() {
        stackPointer = ((stackPointer + 1) & 0xFF);
        return mem[stackPointer + 0x100];
    }

    public boolean isNmiAsserted() {
        return (interruptStatus & S_NMI) != 0;
    }

    public boolean isIrqAsserted() {
        return (interruptStatus & S_IRQ) != 0;
    }

    /**
     * Sets the debug mode on or off depending on the parameter.
     *
     * @param debug debug on if true, otherwise off.
     */
    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public int getDataBusBuffer() {
        return dataBusBuffer;
    }

    public int getInputDataLatch() {
        return inputDataLatch;
    }
}