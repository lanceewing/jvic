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

public class CpuIndirectIndexedModeTest extends CpuBaseTestCase {

    @Test
    public void test_LDA() throws Exception {
        assertEquals(cpu.toString(), 0x00, cpu.getAccumulator());
        bus.write(0x0014, 0x00);
        bus.write(0x0015, 0xd8);
        bus.write(0xd828, 0x03);

        cpu.setYRegister(0x28);

        bus.loadProgram(0xb1, 0x14); // LDA ($14),Y
        cpu.step(1);

        assertEquals(0x03, cpu.getAccumulator());
    }

    @Test
    public void test_ORA() throws Exception {
        bus.write(0x0014, 0x00);
        bus.write(0x0015, 0xd8);
        bus.write(0xd828, 0xe3);

        cpu.setYRegister(0x28);
        cpu.setAccumulator(0x32);

        bus.loadProgram(0x11, 0x14); // ORA ($14),Y
        cpu.step(1);

        assertEquals(0xf3, cpu.getAccumulator());
        assertEquals(0xe3, bus.read(0xd828, true));
    }

    @Test
    public void test_AND() throws Exception {
        bus.write(0x0014, 0x00);
        bus.write(0x0015, 0xd8);
        bus.write(0xd828, 0xe3);

        cpu.setYRegister(0x28);
        cpu.setAccumulator(0x32);

        bus.loadProgram(0x31, 0x14); // AND ($14),Y
        cpu.step(1);

        assertEquals(0x22, cpu.getAccumulator());
        assertEquals(0xe3, bus.read(0xd828, true));
    }

}