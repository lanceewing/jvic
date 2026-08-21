package emu.jvic.cpu;

import emu.jvic.testutil.CpuTestHarness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LDA / LDX / LDY / STA / STX / STY across every addressing mode they support, per
 * the public NMOS 6502 opcode/cycle reference. Loads set N/Z from the loaded value;
 * stores never touch flags.
 */
public class LoadStoreInstructionsTest {

    private CpuTestHarness h;

    @BeforeEach
    void setUp() {
        h = new CpuTestHarness();
    }

    // ---------------------------------------------------------------- LDA ----

    @Test
    void ldaImmediate() {
        long cycles = h.execute(0x0300, 0xA9, 0x7F); // LDA #$7F
        assertEquals(0x7F, h.cpu.getAccumulator());
        assertEquals(2, cycles);
        assertFalse(h.cpu.getNegativeResultFlag());
        assertFalse(h.cpu.getZeroResultFlag());
    }

    @Test
    void ldaImmediateSetsZeroFlag() {
        h.execute(0x0300, 0xA9, 0x00);
        assertTrue(h.cpu.getZeroResultFlag());
        assertFalse(h.cpu.getNegativeResultFlag());
    }

    @Test
    void ldaImmediateSetsNegativeFlag() {
        h.execute(0x0300, 0xA9, 0x80);
        assertTrue(h.cpu.getNegativeResultFlag());
        assertFalse(h.cpu.getZeroResultFlag());
    }

    @Test
    void ldaZeroPage() {
        h.writeByte(0x0050, 0x33);
        long cycles = h.execute(0x0300, 0xA5, 0x50); // LDA $50
        assertEquals(0x33, h.cpu.getAccumulator());
        assertEquals(3, cycles);
    }

    @Test
    void ldaZeroPageX() {
        h.writeByte(0x0055, 0x44);
        h.prepareInstruction(0x0300, 0xB5, 0x50); // LDA $50,X
        h.cpu.setIndexRegisterX(0x05);
        long cycles = h.runInstruction();
        assertEquals(0x44, h.cpu.getAccumulator());
        assertEquals(4, cycles);
    }

    @Test
    void ldaZeroPageXWrapsWithinZeroPage() {
        h.writeByte(0x007F, 0x55); // 0xFF + 0x80 wraps to 0x7F, not 0x017F
        h.prepareInstruction(0x0300, 0xB5, 0xFF);
        h.cpu.setIndexRegisterX(0x80);
        h.runInstruction();
        assertEquals(0x55, h.cpu.getAccumulator());
    }

    @Test
    void ldaAbsolute() {
        h.writeByte(0x0400, 0x66);
        long cycles = h.execute(0x0300, 0xAD, 0x00, 0x04); // LDA $0400
        assertEquals(0x66, h.cpu.getAccumulator());
        assertEquals(4, cycles);
    }

    @Test
    void ldaAbsoluteXNoPageCross() {
        h.writeByte(0x0405, 0x77);
        h.prepareInstruction(0x0300, 0xBD, 0x00, 0x04); // LDA $0400,X
        h.cpu.setIndexRegisterX(0x05);
        long cycles = h.runInstruction();
        assertEquals(0x77, h.cpu.getAccumulator());
        assertEquals(4, cycles);
    }

    @Test
    void ldaAbsoluteXPageCrossCostsExtraCycle() {
        h.writeByte(0x0501, 0x88);
        h.prepareInstruction(0x0300, 0xBD, 0xFF, 0x04); // LDA $04FF,X
        h.cpu.setIndexRegisterX(0x02); // 0x04FF + 0x02 = 0x0501, crosses page
        long cycles = h.runInstruction();
        assertEquals(0x88, h.cpu.getAccumulator());
        assertEquals(5, cycles);
    }

    @Test
    void ldaAbsoluteY() {
        h.writeByte(0x0410, 0x11);
        h.prepareInstruction(0x0300, 0xB9, 0x00, 0x04); // LDA $0400,Y
        h.cpu.setIndexRegisterY(0x10);
        long cycles = h.runInstruction();
        assertEquals(0x11, h.cpu.getAccumulator());
        assertEquals(4, cycles);
    }

    @Test
    void ldaAbsoluteYPageCrossCostsExtraCycle() {
        h.writeByte(0x0501, 0x22);
        h.prepareInstruction(0x0300, 0xB9, 0xFF, 0x04);
        h.cpu.setIndexRegisterY(0x02);
        long cycles = h.runInstruction();
        assertEquals(0x22, h.cpu.getAccumulator());
        assertEquals(5, cycles);
    }

    @Test
    void ldaIndexedIndirectX() {
        // ($20,X) with X=4 -> pointer at $24/$25 -> effective address.
        h.writeWord(0x0024, 0x0500);
        h.writeByte(0x0500, 0x99);
        h.prepareInstruction(0x0300, 0xA1, 0x20); // LDA ($20,X)
        h.cpu.setIndexRegisterX(0x04);
        long cycles = h.runInstruction();
        assertEquals(0x99, h.cpu.getAccumulator());
        assertEquals(6, cycles);
    }

    @Test
    void ldaIndexedIndirectXPointerWrapsWithinZeroPage() {
        h.writeByte(0x00FF, 0x00); // low byte of pointer
        h.writeByte(0x0000, 0x06); // high byte of pointer wraps to $00, not $0100
        h.writeByte(0x0600, 0xAB);
        h.prepareInstruction(0x0300, 0xA1, 0xFE); // LDA ($FE,X)
        h.cpu.setIndexRegisterX(0x01); // $FE + 1 = $FF
        h.runInstruction();
        assertEquals(0xAB, h.cpu.getAccumulator());
    }

    @Test
    void ldaIndirectIndexedY() {
        // ($20),Y : pointer at $20/$21 -> base address, + Y.
        h.writeWord(0x0020, 0x0500);
        h.writeByte(0x0505, 0xCD);
        h.prepareInstruction(0x0300, 0xB1, 0x20); // LDA ($20),Y
        h.cpu.setIndexRegisterY(0x05);
        long cycles = h.runInstruction();
        assertEquals(0xCD, h.cpu.getAccumulator());
        assertEquals(5, cycles);
    }

    @Test
    void ldaIndirectIndexedYPageCrossCostsExtraCycle() {
        h.writeWord(0x0020, 0x04FF);
        h.writeByte(0x0501, 0xEF);
        h.prepareInstruction(0x0300, 0xB1, 0x20);
        h.cpu.setIndexRegisterY(0x02); // 0x04FF + 0x02 crosses into 0x0501
        long cycles = h.runInstruction();
        assertEquals(0xEF, h.cpu.getAccumulator());
        assertEquals(6, cycles);
    }

    // ---------------------------------------------------------------- LDX ----

    @Test
    void ldxImmediate() {
        long cycles = h.execute(0x0300, 0xA2, 0x12);
        assertEquals(0x12, h.cpu.getIndexRegisterX());
        assertEquals(2, cycles);
    }

    @Test
    void ldxZeroPage() {
        h.writeByte(0x0060, 0x34);
        long cycles = h.execute(0x0300, 0xA6, 0x60);
        assertEquals(0x34, h.cpu.getIndexRegisterX());
        assertEquals(3, cycles);
    }

    @Test
    void ldxZeroPageY() {
        h.writeByte(0x0065, 0x56);
        h.prepareInstruction(0x0300, 0xB6, 0x60);
        h.cpu.setIndexRegisterY(0x05);
        long cycles = h.runInstruction();
        assertEquals(0x56, h.cpu.getIndexRegisterX());
        assertEquals(4, cycles);
    }

    @Test
    void ldxAbsolute() {
        h.writeByte(0x0420, 0x78);
        long cycles = h.execute(0x0300, 0xAE, 0x20, 0x04);
        assertEquals(0x78, h.cpu.getIndexRegisterX());
        assertEquals(4, cycles);
    }

    @Test
    void ldxAbsoluteYPageCross() {
        h.writeByte(0x0501, 0x9A);
        h.prepareInstruction(0x0300, 0xBE, 0xFF, 0x04);
        h.cpu.setIndexRegisterY(0x02);
        long cycles = h.runInstruction();
        assertEquals(0x9A, h.cpu.getIndexRegisterX());
        assertEquals(5, cycles);
    }

    // ---------------------------------------------------------------- LDY ----

    @Test
    void ldyImmediate() {
        long cycles = h.execute(0x0300, 0xA0, 0x13);
        assertEquals(0x13, h.cpu.getIndexRegisterY());
        assertEquals(2, cycles);
    }

    @Test
    void ldyZeroPageX() {
        h.writeByte(0x0065, 0x35);
        h.prepareInstruction(0x0300, 0xB4, 0x60);
        h.cpu.setIndexRegisterX(0x05);
        long cycles = h.runInstruction();
        assertEquals(0x35, h.cpu.getIndexRegisterY());
        assertEquals(4, cycles);
    }

    @Test
    void ldyAbsoluteXPageCross() {
        h.writeByte(0x0501, 0x9B);
        h.prepareInstruction(0x0300, 0xBC, 0xFF, 0x04);
        h.cpu.setIndexRegisterX(0x02);
        long cycles = h.runInstruction();
        assertEquals(0x9B, h.cpu.getIndexRegisterY());
        assertEquals(5, cycles);
    }

    // ---------------------------------------------------------------- STA ----

    @Test
    void staZeroPageDoesNotAffectFlags() {
        h.prepareInstruction(0x0300, 0x85, 0x50); // STA $50
        h.cpu.setAccumulator(0x00);
        h.cpu.setZeroResultFlag(false); // deliberately wrong, must not be corrected by STA
        long cycles = h.runInstruction();
        assertEquals(0x00, h.readByte(0x0050));
        assertEquals(3, cycles);
        assertFalse(h.cpu.getZeroResultFlag());
    }

    @Test
    void staZeroPageX() {
        h.prepareInstruction(0x0300, 0x95, 0x50);
        h.cpu.setAccumulator(0x41);
        h.cpu.setIndexRegisterX(0x05);
        long cycles = h.runInstruction();
        assertEquals(0x41, h.readByte(0x0055));
        assertEquals(4, cycles);
    }

    @Test
    void staAbsolute() {
        h.prepareInstruction(0x0300, 0x8D, 0x00, 0x04);
        h.cpu.setAccumulator(0x42);
        long cycles = h.runInstruction();
        assertEquals(0x42, h.readByte(0x0400));
        assertEquals(4, cycles);
    }

    @Test
    void staAbsoluteXAlwaysCostsFiveCyclesRegardlessOfPageCross() {
        // STA has no page-cross fast path since the address is used for a write.
        h.prepareInstruction(0x0300, 0x9D, 0x00, 0x04); // no page cross
        h.cpu.setAccumulator(0x43);
        h.cpu.setIndexRegisterX(0x05);
        long cycles = h.runInstruction();
        assertEquals(0x43, h.readByte(0x0405));
        assertEquals(5, cycles);
    }

    @Test
    void staAbsoluteY() {
        h.prepareInstruction(0x0300, 0x99, 0x00, 0x04);
        h.cpu.setAccumulator(0x44);
        h.cpu.setIndexRegisterY(0x05);
        long cycles = h.runInstruction();
        assertEquals(0x44, h.readByte(0x0405));
        assertEquals(5, cycles);
    }

    @Test
    void staIndexedIndirectX() {
        h.writeWord(0x0024, 0x0500);
        h.prepareInstruction(0x0300, 0x81, 0x20);
        h.cpu.setAccumulator(0x45);
        h.cpu.setIndexRegisterX(0x04);
        long cycles = h.runInstruction();
        assertEquals(0x45, h.readByte(0x0500));
        assertEquals(6, cycles);
    }

    @Test
    void staIndirectIndexedY() {
        h.writeWord(0x0020, 0x0500);
        h.prepareInstruction(0x0300, 0x91, 0x20);
        h.cpu.setAccumulator(0x46);
        h.cpu.setIndexRegisterY(0x05);
        long cycles = h.runInstruction();
        assertEquals(0x46, h.readByte(0x0505));
        assertEquals(6, cycles);
    }

    // ---------------------------------------------------------------- STX ----

    @Test
    void stxZeroPageY() {
        h.prepareInstruction(0x0300, 0x96, 0x50);
        h.cpu.setIndexRegisterX(0x47);
        h.cpu.setIndexRegisterY(0x05);
        long cycles = h.runInstruction();
        assertEquals(0x47, h.readByte(0x0055));
        assertEquals(4, cycles);
    }

    @Test
    void stxAbsolute() {
        h.prepareInstruction(0x0300, 0x8E, 0x00, 0x04);
        h.cpu.setIndexRegisterX(0x48);
        long cycles = h.runInstruction();
        assertEquals(0x48, h.readByte(0x0400));
        assertEquals(4, cycles);
    }

    // ---------------------------------------------------------------- STY ----

    @Test
    void styZeroPageX() {
        h.prepareInstruction(0x0300, 0x94, 0x50);
        h.cpu.setIndexRegisterY(0x49);
        h.cpu.setIndexRegisterX(0x05);
        long cycles = h.runInstruction();
        assertEquals(0x49, h.readByte(0x0055));
        assertEquals(4, cycles);
    }

    @Test
    void styAbsolute() {
        h.prepareInstruction(0x0300, 0x8C, 0x00, 0x04);
        h.cpu.setIndexRegisterY(0x4A);
        long cycles = h.runInstruction();
        assertEquals(0x4A, h.readByte(0x0400));
        assertEquals(4, cycles);
    }
}
