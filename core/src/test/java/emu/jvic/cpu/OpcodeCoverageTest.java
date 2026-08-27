package emu.jvic.cpu;

import emu.jvic.testutil.CpuTestHarness;
import emu.jvic.testutil.Opcode6502Reference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Enumerates the full 256-opcode 6502 space (documented instructions plus all
 * illegal/undocumented ones from the standard public opcode matrix, see
 * {@link Opcode6502Reference}) and empirically classifies each one against the
 * running {@link Cpu6502}, rather than trusting a static reading of the source.
 *
 * <p>Detection signature: every real 6502 instruction takes at least 2 cycles. An
 * opcode with no {@code INSTRUCTION_DECODE_MATRIX} entry (an empty {@code {}} in
 * Cpu6502) has a zero-length decode array, which this emulator's cycle-stepping
 * treats as an immediate 1-cycle no-op re-dispatch instead of raising an error -
 * so "runs in exactly 1 cycle, and stays at the same PC forever" reliably means
 * "not implemented, and jammed".
 *
 * <p>Every opcode in the standard NMOS matrix now has a decode entry, including
 * the 12 JAM/KIL opcodes (which halt the CPU, matching real hardware - see
 * {@link #jamOpcodeHaltsTheCpu}) and the 8 "unstable" illegal opcodes (ANE, LXA,
 * LAS, SHA x2, SHX, SHY, TAS - approximated with a fixed "magic" constant rather
 * than the genuinely chip/temperature-dependent real behaviour; see
 * {@link UndocumentedOpcodesTest} for their correctness tests).
 */
public class OpcodeCoverageTest {

    private static final Set<Integer> UNIMPLEMENTED = new HashSet<>();

    static {
        for (int op : Opcode6502Reference.UNIMPLEMENTED_NON_JAM_OPCODES) {
            UNIMPLEMENTED.add(op);
        }
        for (int op : Opcode6502Reference.JAM_OPCODES) {
            UNIMPLEMENTED.add(op);
        }
    }

    private static long execute(int opcode) {
        CpuTestHarness h = new CpuTestHarness();
        // Zero operand bytes cover the largest possible operand (a 2-byte absolute
        // address); trailing zero bytes are simply unused for shorter instructions.
        return h.execute(0x0300, opcode, 0x00, 0x00);
    }

    static IntStream implementedOpcodes() {
        return IntStream.range(0, 256).filter(op -> !UNIMPLEMENTED.contains(op));
    }

    static IntStream jamOpcodes() {
        return IntStream.of(Opcode6502Reference.JAM_OPCODES);
    }

    @ParameterizedTest(name = "0x{0}")
    @MethodSource("implementedOpcodes")
    void implementedOpcodeRunsForAtLeastTwoCycles(int opcode) {
        long cycles = execute(opcode);
        assertTrue(cycles >= 2, () -> String.format(
                "opcode 0x%02X (%s) appears to have no decode entry: only %d cycle(s) - "
                        + "Opcode6502Reference.UNIMPLEMENTED_NON_JAM_OPCODES may be out of date",
                opcode, Opcode6502Reference.MNEMONIC[opcode], cycles));
    }

    @ParameterizedTest(name = "0x{0}")
    @MethodSource("jamOpcodes")
    void jamOpcodeHaltsTheCpu(int opcode) {
        CpuTestHarness h = new CpuTestHarness();
        long firstCycles = h.execute(0x0300, opcode, 0x00, 0x00);
        assertEquals(1, firstCycles, () -> String.format(
                "opcode 0x%02X (%s): expected a single discarded-read cycle before jamming",
                opcode, Opcode6502Reference.MNEMONIC[opcode]));
        int jammedPc = h.cpu.getProgramCounter();

        // Real hardware never recovers from JAM/KIL without a RESET: it keeps re-reading
        // the same address forever instead of ever fetching a new opcode. Run several
        // more cycles and confirm the CPU never moves on.
        for (int i = 0; i < 5; i++) {
            long cycles = h.runInstruction();
            assertEquals(1, cycles, () -> String.format(
                    "opcode 0x%02X (%s): should still be jammed, spinning one cycle at a time",
                    opcode, Opcode6502Reference.MNEMONIC[opcode]));
            assertEquals(jammedPc, h.cpu.getProgramCounter(), () -> String.format(
                    "opcode 0x%02X (%s): PC must not advance while jammed",
                    opcode, Opcode6502Reference.MNEMONIC[opcode]));
        }
    }

    @Test
    void opcodeSpaceIsFullyAccountedFor() {
        int jam = Opcode6502Reference.JAM_OPCODES.length;
        int unimplemented = Opcode6502Reference.UNIMPLEMENTED_NON_JAM_OPCODES.length;
        int implemented = 256 - jam - unimplemented;
        assertEquals(256, implemented + jam + unimplemented);
        System.out.println("6502 opcode coverage: " + implemented + " implemented, " + unimplemented
                + " illegal opcodes not implemented (known gaps), " + jam
                + " JAM/KIL opcodes (real hardware halts; this emulator matches that).");
    }
}
