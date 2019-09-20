/*
 * Copyright (c) 2016 Seth J. Morabito <web@loomcom.com>
 * 
 * These 6502 CPU unit tests are a slightly modified version of Seth J. Morabito's
 * symon unit tests and have been invaluable for verifying the JOric Cpu6502 
 * implementation. Thank you Seth.
 */

package emu.jvic.cpu;

import static org.junit.Assert.*;

import org.junit.Test;

public class CpuZeroPageYModeTest extends CpuBaseTestCase {

    /*
    * The following opcodes are tested for correctness in this file:
    *
    * STX - $96
    * LDX - $b6
    *
    */

    /* STX - Store X Register - $96 */
    @Test
    public void test_STX() {
        cpu.setYRegister(0x30);
        cpu.setXRegister(0x00);
        bus.loadProgram(0x96, 0x10);  // STX $10,Y
        cpu.step();
        assertEquals(0x00, bus.read(0x40, true));
        // Should have no effect on flags.
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());

        cpu.reset();
        cpu.setYRegister(0x30);
        cpu.setXRegister(0x0f);
        bus.loadProgram(0x96, 0x10);  // STX $10,Y
        cpu.step();
        assertEquals(0x0f, bus.read(0x40, true));
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());

        cpu.reset();
        cpu.setYRegister(0x30);
        cpu.setXRegister(0x80);
        bus.loadProgram(0x96, 0x10);  // STX $10,Y
        cpu.step();
        assertEquals(0x80, bus.read(0x40, true));
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
    }

    /* LDX - Load X Register - $b6 */
    @Test
    public void test_LDX() {
        bus.write(0x40, 0x00);
        bus.write(0x41, 0x0f);
        bus.write(0x42, 0x80);

        bus.loadProgram(0xb6, 0x10,
                        0xb6, 0x11,
                        0xb6, 0x12);

        cpu.setYRegister(0x30);

        cpu.step();
        assertEquals(0x00, cpu.getXRegister());
        assertTrue(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());

        cpu.step();
        assertEquals(0x0f, cpu.getXRegister());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());

        cpu.step();
        assertEquals(0x80, cpu.getXRegister());
        assertFalse(cpu.getZeroFlag());
        assertTrue(cpu.getNegativeFlag());
    }

}
