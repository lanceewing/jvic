package emu.jvic.cpu;

import emu.jvic.testutil.CpuTestHarness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The illegal/undocumented NOP opcodes: implied (single-byte), immediate, zero
 * page, zero page,X, and absolute,X variants. All reuse the same {@code NOP}
 * execute case as the documented {@code 0xEA} - they read (and discard) an
 * operand per their addressing mode but otherwise have no effect, and their only
 * real difference from a true NOP is cycle count and (for the memory-reading
 * ones) triggering a real bus read at the computed address.
 */
public class IllegalNopInstructionsTest {

    private CpuTestHarness h;

    @BeforeEach
    void setUp() {
        h = new CpuTestHarness();
    }

    private void assertNoSideEffects(long cycles, long expectedCycles) {
        assertEquals(0x12, h.cpu.getAccumulator());
        assertEquals(0x34, h.cpu.getIndexRegisterX());
        assertEquals(0x56, h.cpu.getIndexRegisterY());
        assertEquals(expectedCycles, cycles);
    }

    private void primeRegisters() {
        h.cpu.setAccumulator(0x12);
        h.cpu.setIndexRegisterX(0x34);
        h.cpu.setIndexRegisterY(0x56);
    }

    // ------------------------------------------------------ implied (2 cycles) ----

    private void impliedNop(int opcode) {
        h.prepareInstruction(0x0300, opcode);
        primeRegisters();
        long cycles = h.runInstruction();
        assertNoSideEffects(cycles, 2);
    }

    @Test
    void impliedNop0x1A() {
        impliedNop(0x1A);
    }

    @Test
    void impliedNop0x3A() {
        impliedNop(0x3A);
    }

    @Test
    void impliedNop0x5A() {
        impliedNop(0x5A);
    }

    @Test
    void impliedNop0x7A() {
        impliedNop(0x7A);
    }

    @Test
    void impliedNop0xDA() {
        impliedNop(0xDA);
    }

    @Test
    void impliedNop0xFA() {
        impliedNop(0xFA);
    }

    // ------------------------------------------------------ immediate (2 cycles) ----

    private void immediateNop(int opcode) {
        h.prepareInstruction(0x0300, opcode, 0x99);
        primeRegisters();
        long cycles = h.runInstruction();
        assertNoSideEffects(cycles, 2);
    }

    @Test
    void immediateNop0x80() {
        immediateNop(0x80);
    }

    @Test
    void immediateNop0x82() {
        immediateNop(0x82);
    }

    @Test
    void immediateNop0x89() {
        immediateNop(0x89);
    }

    @Test
    void immediateNop0xC2() {
        immediateNop(0xC2);
    }

    @Test
    void immediateNop0xE2() {
        immediateNop(0xE2);
    }

    // ------------------------------------------------------ zero page (3 cycles) ----

    @Test
    void zeroPageNop0x44() {
        h.writeByte(0x0050, 0x77);
        h.prepareInstruction(0x0300, 0x44, 0x50);
        primeRegisters();
        long cycles = h.runInstruction();
        assertNoSideEffects(cycles, 3);
    }

    // ------------------------------------------------------ zero page,X (4 cycles) ----

    private void zeroPageXNop(int opcode) {
        h.prepareInstruction(0x0300, opcode, 0x50);
        primeRegisters();
        h.cpu.setIndexRegisterX(0x05); // overrides the "no side effects" X set by primeRegisters()
        long cycles = h.runInstruction();
        assertEquals(0x12, h.cpu.getAccumulator());
        assertEquals(0x05, h.cpu.getIndexRegisterX());
        assertEquals(0x56, h.cpu.getIndexRegisterY());
        assertEquals(4, cycles);
    }

    @Test
    void zeroPageXNop0x14() {
        zeroPageXNop(0x14);
    }

    @Test
    void zeroPageXNop0x34() {
        zeroPageXNop(0x34);
    }

    @Test
    void zeroPageXNop0x54() {
        zeroPageXNop(0x54);
    }

    @Test
    void zeroPageXNop0xD4() {
        zeroPageXNop(0xD4);
    }

    @Test
    void zeroPageXNop0xF4() {
        zeroPageXNop(0xF4);
    }

    // ------------------------------------------------------ absolute,X (4/5 cycles) ----

    private void absoluteXNopNoPageCross(int opcode) {
        h.prepareInstruction(0x0300, opcode, 0x00, 0x04); // $0400,X
        h.cpu.setIndexRegisterX(0x05);
        long cycles = h.runInstruction();
        assertEquals(4, cycles);
    }

    private void absoluteXNopPageCross(int opcode) {
        h.prepareInstruction(0x0300, opcode, 0xFF, 0x04); // $04FF,X
        h.cpu.setIndexRegisterX(0x02); // crosses into $0501
        long cycles = h.runInstruction();
        assertEquals(5, cycles);
    }

    @Test
    void absoluteXNop0x1C() {
        absoluteXNopNoPageCross(0x1C);
    }

    @Test
    void absoluteXNop0x1CPageCross() {
        absoluteXNopPageCross(0x1C);
    }

    @Test
    void absoluteXNop0x3C() {
        absoluteXNopNoPageCross(0x3C);
    }

    @Test
    void absoluteXNop0x5C() {
        absoluteXNopNoPageCross(0x5C);
    }

    @Test
    void absoluteXNop0x7C() {
        absoluteXNopNoPageCross(0x7C);
    }

    @Test
    void absoluteXNop0xDC() {
        absoluteXNopNoPageCross(0xDC);
    }

    @Test
    void absoluteXNop0xFC() {
        absoluteXNopNoPageCross(0xFC);
    }

    // ------------------------------------------------------ pre-existing modes (regression) ----

    @Test
    void zeroPageNop0x04ReadsAndDiscardsOperand() {
        h.writeByte(0x0050, 0x77);
        h.prepareInstruction(0x0300, 0x04, 0x50);
        primeRegisters();
        long cycles = h.runInstruction();
        assertNoSideEffects(cycles, 3);
    }

    @Test
    void absoluteNop0x0CCycleCount() {
        long cycles = h.execute(0x0300, 0x0C, 0x00, 0x04);
        assertEquals(4, cycles);
    }

    @Test
    void zeroPageXNop0x74CycleCount() {
        h.prepareInstruction(0x0300, 0x74, 0x50);
        h.cpu.setIndexRegisterX(0x05);
        long cycles = h.runInstruction();
        assertEquals(4, cycles);
    }
}
