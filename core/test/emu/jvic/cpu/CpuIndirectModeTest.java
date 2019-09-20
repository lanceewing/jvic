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

public class CpuIndirectModeTest extends CpuBaseTestCase {

    /*
    * The following opcodes are tested for correctness in this file:
    *
    * JMP - $6c
    *
    */

    /* JMP - Jump - $6c */
    @Test
    public void test_JMP_notOnPageBoundary() {
        bus.write(0x3400, 0x00);
        bus.write(0x3401, 0x54);
        bus.loadProgram(0x6c, 0x00, 0x34);
        cpu.step();
        assertEquals(0x5400, cpu.getProgramCounter() - 1);
        // No change to status flags.
        assertEquals(0x20, cpu.getProcessorStatus());
    }

    @Test
    public void test_JMP_withIndirectBug() {
        bus.write(0x3400, 0x22);
        bus.write(0x34ff, 0x00);
        bus.write(0x3500, 0x54);
        bus.loadProgram(0x6c, 0xff, 0x34);
        cpu.step();
        assertEquals(0x2200, cpu.getProgramCounter() - 1);
        // No change to status flags.
        assertEquals(0x20, cpu.getProcessorStatus());
    }

}