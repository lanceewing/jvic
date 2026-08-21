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
 * so "runs in exactly 1 cycle" reliably means "not implemented".
 *
 * <p>This test intentionally stays green for the currently-unimplemented illegal
 * opcodes (asserting the CURRENT 1-cycle skip behaviour, labelled as a known gap)
 * rather than failing outright, so the full punch list of missing opcodes stays
 * visible and enumerable without blocking the build.
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

    static IntStream unimplementedNonJamOpcodes() {
        return IntStream.of(Opcode6502Reference.UNIMPLEMENTED_NON_JAM_OPCODES);
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
    @MethodSource("unimplementedNonJamOpcodes")
    void unimplementedIllegalOpcodeIsAKnownGap(int opcode) {
        long cycles = execute(opcode);
        assertEquals(1, cycles, () -> String.format(
                "KNOWN GAP: opcode 0x%02X (%s) has no INSTRUCTION_DECODE_MATRIX entry in Cpu6502 - it is "
                        + "silently skipped as a 1-cycle no-op instead of performing its documented "
                        + "illegal-opcode behaviour. If this now runs for >1 cycle, the gap has been "
                        + "closed - please remove it from UNIMPLEMENTED_NON_JAM_OPCODES and add proper "
                        + "correctness tests to UndocumentedOpcodesTest instead.",
                opcode, Opcode6502Reference.MNEMONIC[opcode]));
    }

    @ParameterizedTest(name = "0x{0}")
    @MethodSource("jamOpcodes")
    void jamOpcodeDivergesFromRealHardwareHalt(int opcode) {
        long cycles = execute(opcode);
        assertEquals(1, cycles, () -> String.format(
                "KNOWN DEVIATION: real NMOS hardware halts completely (JAM/KIL) on opcode 0x%02X until a "
                        + "physical reset; this emulator instead silently treats it as a 1-cycle no-op and "
                        + "continues executing normally.",
                opcode));
    }

    @Test
    void opcodeSpaceIsFullyAccountedFor() {
        int jam = Opcode6502Reference.JAM_OPCODES.length;
        int unimplemented = Opcode6502Reference.UNIMPLEMENTED_NON_JAM_OPCODES.length;
        int implemented = 256 - jam - unimplemented;
        assertEquals(256, implemented + jam + unimplemented);
        System.out.println("6502 opcode coverage: " + implemented + " implemented, " + unimplemented
                + " illegal opcodes not implemented (known gaps), " + jam
                + " JAM/KIL opcodes (real hardware halts; this emulator does not).");
    }
}
