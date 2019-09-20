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

/**
 * Test for Zero Page Indirect addressing mode, found on some instructions
 * in the 65C02 and 65816
 */
public class CpuZeroPageIndirectTest extends CpuBaseTestCase {

    @Test
    public void test_ora_requiresCmosCpu() throws Exception {
        // Set some initial values in zero page.
        bus.write(0x30, 0x00);
        bus.write(0x31, 0x10);

        bus.write(0x40, 0x01);
        bus.write(0x41, 0x10);

        bus.write(0x50, 0x02);
        bus.write(0x51, 0x10);

        bus.write(0x60, 0x03);
        bus.write(0x61, 0x10);

        bus.write(0x1000, 0x11);
        bus.write(0x1001, 0x22);
        bus.write(0x1002, 0x44);
        bus.write(0x1003, 0x88);

        bus.loadProgram(0x12, 0x30,  // ORA ($30)
                        0x12, 0x40,  // ORA ($40)
                        0x12, 0x50,  // ORA ($50)
                        0x12, 0x60); // ORA ($60)


        assertEquals(0x00, cpu.getAccumulator());

        boolean zState = cpu.getZeroFlag();
        boolean nState = cpu.getNegativeFlag();

        // 0x00 | 0x11 = 0x11, but not implemented in NMOS cpu
        cpu.step();
        assertEquals(0x00, cpu.getAccumulator());
        assertEquals(zState, cpu.getZeroFlag());         // unchanged
        assertEquals(nState, cpu.getNegativeFlag());     // unchanged

        // 0x11 | 0x22 = 0x33, but not implemented in NMOS cpu
        cpu.step();
        assertEquals(0x00, cpu.getAccumulator());
        assertEquals(zState, cpu.getZeroFlag());         // unchanged
        assertEquals(nState, cpu.getNegativeFlag());     // unchanged

        // 0x33 | 0x44 = 0x77, but not implemented in NMOS cpu
        cpu.step();
        assertEquals(0x00, cpu.getAccumulator());
        assertEquals(zState, cpu.getZeroFlag());         // unchanged
        assertEquals(nState, cpu.getNegativeFlag());     // unchanged

        // 0x77 | 0x88 = 0xff, but not implemented in NMOS cpu
        cpu.step();
        assertEquals(0x00, cpu.getAccumulator());
        assertEquals(zState, cpu.getZeroFlag());         // unchanged
        assertEquals(nState, cpu.getNegativeFlag());     // unchanged
    }

    @Test
    public void test_and_requiresCmosCpu() throws Exception {
        // Set some initial values in zero page.
        bus.write(0x30, 0x00);
        bus.write(0x31, 0x10);

        bus.write(0x40, 0x01);
        bus.write(0x41, 0x10);

        bus.write(0x50, 0x02);
        bus.write(0x51, 0x10);

        bus.write(0x1000, 0x33);
        bus.write(0x1001, 0x11);
        bus.write(0x1002, 0x88);

        bus.loadProgram(0x32, 0x30,  // AND ($30)
                        0x32, 0x40,  // AND ($40)
                        0x32, 0x50); // AND ($50)


        cpu.setAccumulator(0xff);

        boolean zState = cpu.getZeroFlag();
        boolean nState = cpu.getNegativeFlag();

        // 0xFF & 0x33 == 0x33, but not implemented in NMOS cpu
        cpu.step();
        assertEquals(0xff, cpu.getAccumulator());
        assertEquals(zState, cpu.getZeroFlag());
        assertEquals(nState, cpu.getNegativeFlag());

        // 0x33 & 0x11 == 0x11, but not implemented in NMOS cpu
        cpu.step();
        assertEquals(0xff, cpu.getAccumulator());
        assertEquals(zState, cpu.getZeroFlag());
        assertEquals(nState, cpu.getNegativeFlag());

        // 0x11 & 0x80 == 0, but not implemented in NMOS cpu
        cpu.step();
        assertEquals(0xff, cpu.getAccumulator());
        assertEquals(zState, cpu.getZeroFlag());
        assertEquals(nState, cpu.getNegativeFlag());
    }

    @Test
    public void test_eor_requiresCmosCpu() throws Exception {
        // Set some initial values in zero page.
        bus.write(0x30, 0x00);
        bus.write(0x31, 0x10);

        bus.write(0x40, 0x01);
        bus.write(0x41, 0x10);

        bus.write(0x50, 0x02);
        bus.write(0x51, 0x10);

        bus.write(0x60, 0x03);
        bus.write(0x61, 0x10);

        bus.write(0x1000, 0x00);
        bus.write(0x1001, 0xff);
        bus.write(0x1002, 0x33);
        bus.write(0x1003, 0x44);

        bus.loadProgram(0x52, 0x30,  // AND ($30)
                        0x52, 0x40,  // AND ($40)
                        0x52, 0x50,  // EOR ($50)
                        0x52, 0x60); // AND ($60)


        cpu.setAccumulator(0x88);

        cpu.step();
        assertEquals(0x88, cpu.getAccumulator());

        cpu.step();
        assertEquals(0x88, cpu.getAccumulator());

        cpu.step();
        assertEquals(0x88, cpu.getAccumulator());

        cpu.step();
        assertEquals(0x88, cpu.getAccumulator());
    }
}
