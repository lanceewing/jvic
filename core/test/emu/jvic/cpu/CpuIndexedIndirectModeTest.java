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

public class CpuIndexedIndirectModeTest extends CpuBaseTestCase {

    @Test
    public void test_LDA() throws Exception {
        bus.write(0x008c, 0x3f);
        bus.write(0x008d, 0xc4);
        bus.write(0xc43f, 0x45);

        cpu.setXRegister(0x0c);

        bus.loadProgram(0xa1, 0x80); // LDA ($80,X)
        cpu.step(1);

        assertEquals(0x45, cpu.getAccumulator());
    }

    @Test
    public void testZeroPageWrap() throws Exception {
        bus.write(0x0020, 0x01);
        bus.write(0x0021, 0x07); // ($0701)
        bus.write(0x0701, 0xaa);

        bus.write(0x000f, 0x02);
        bus.write(0x0010, 0x08); // ($0802)
        bus.write(0x0802, 0xbb);

        bus.write(0x010f, 0x03);
        bus.write(0x0110, 0x09); // ($0903)
        bus.write(0x0903, 0xcc);

        cpu.setXRegister(0x10);

        // No wrap needed
        bus.loadProgram(0xa1, 0x10,  // LDA ($10,X) = ($10 + $10) & $ff =  $20
                        0xa1, 0xff); // LDA ($ff,X) = ($ff + $10) & $ff =  $0f, NOT $10f!
        cpu.step(1);
        assertEquals(0xaa, cpu.getAccumulator());

        cpu.step(1);
        assertEquals(0xbb, cpu.getAccumulator());
    }

    @Test
    public void test_ORA() throws Exception {
        bus.write(0x0012, 0x1f);
        bus.write(0x0013, 0xc5);
        bus.write(0xc51f, 0x31);

        cpu.setXRegister(0x02);
        cpu.setAccumulator(0x15);

        bus.loadProgram(0x01, 0x10); // ORA ($10,X)
        cpu.step(1);

        assertEquals(0x35, cpu.getAccumulator());
        assertEquals(0x31, bus.read(0xc51f, true));
    }

    @Test
    public void test_AND() throws Exception {
        bus.write(0x0012, 0x1f);
        bus.write(0x0013, 0xc5);
        bus.write(0xc51f, 0x31);

        cpu.setXRegister(0x02);
        cpu.setAccumulator(0x15);

        bus.loadProgram(0x21, 0x10); // AND ($10,X)
        cpu.step(1);

        assertEquals(0x11, cpu.getAccumulator());
        assertEquals(0x31, bus.read(0xc51f, true));
    }
}