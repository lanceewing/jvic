package emu.jvic.cpu;

import emu.jvic.testutil.CpuTestHarness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * INC / DEC (memory, read-modify-write) and INX / INY / DEX / DEY (register,
 * implied). All wrap around the 8-bit range (0xFF + 1 = 0x00, 0x00 - 1 = 0xFF).
 */
public class IncDecInstructionsTest {

    private CpuTestHarness h;

    @BeforeEach
    void setUp() {
        h = new CpuTestHarness();
    }

    @Test
    void incZeroPage() {
        h.writeByte(0x0050, 0x01);
        long cycles = h.execute(0x0300, 0xE6, 0x50); // INC $50
        assertEquals(0x02, h.readByte(0x0050));
        assertEquals(5, cycles);
    }

    @Test
    void incWrapsFromFFToZeroAndSetsZeroFlag() {
        h.writeByte(0x0050, 0xFF);
        h.execute(0x0300, 0xE6, 0x50);
        assertEquals(0x00, h.readByte(0x0050));
        assertTrue(h.cpu.getZeroResultFlag());
    }

    @Test
    void incAbsolute() {
        h.writeByte(0x0400, 0x7F);
        long cycles = h.execute(0x0300, 0xEE, 0x00, 0x04); // INC $0400
        assertEquals(0x80, h.readByte(0x0400));
        assertTrue(h.cpu.getNegativeResultFlag());
        assertEquals(6, cycles);
    }

    @Test
    void decZeroPage() {
        h.writeByte(0x0050, 0x01);
        long cycles = h.execute(0x0300, 0xC6, 0x50); // DEC $50
        assertEquals(0x00, h.readByte(0x0050));
        assertTrue(h.cpu.getZeroResultFlag());
        assertEquals(5, cycles);
    }

    @Test
    void decWrapsFromZeroToFF() {
        h.writeByte(0x0050, 0x00);
        h.execute(0x0300, 0xC6, 0x50);
        assertEquals(0xFF, h.readByte(0x0050));
        assertTrue(h.cpu.getNegativeResultFlag());
    }

    @Test
    void inxWraps() {
        h.prepareInstruction(0x0300, 0xE8); // INX
        h.cpu.setIndexRegisterX(0xFF);
        long cycles = h.runInstruction();
        assertEquals(0x00, h.cpu.getIndexRegisterX());
        assertTrue(h.cpu.getZeroResultFlag());
        assertEquals(2, cycles);
    }

    @Test
    void inyIncrements() {
        h.prepareInstruction(0x0300, 0xC8); // INY
        h.cpu.setIndexRegisterY(0x7F);
        long cycles = h.runInstruction();
        assertEquals(0x80, h.cpu.getIndexRegisterY());
        assertTrue(h.cpu.getNegativeResultFlag());
        assertEquals(2, cycles);
    }

    @Test
    void dexWraps() {
        h.prepareInstruction(0x0300, 0xCA); // DEX
        h.cpu.setIndexRegisterX(0x00);
        long cycles = h.runInstruction();
        assertEquals(0xFF, h.cpu.getIndexRegisterX());
        assertTrue(h.cpu.getNegativeResultFlag());
        assertEquals(2, cycles);
    }

    @Test
    void deyDecrements() {
        h.prepareInstruction(0x0300, 0x88); // DEY
        h.cpu.setIndexRegisterY(0x01);
        long cycles = h.runInstruction();
        assertEquals(0x00, h.cpu.getIndexRegisterY());
        assertTrue(h.cpu.getZeroResultFlag());
        assertEquals(2, cycles);
    }
}
