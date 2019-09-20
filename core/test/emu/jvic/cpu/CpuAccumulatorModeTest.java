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

public class CpuAccumulatorModeTest extends CpuBaseTestCase {

   /*
    * The following opcodes are tested for correctness in this file:
    *
    * ASL - $0a
    * ROL - $2a
    * LSR - $4a
    * ROR - $6a
	*/

	  /* ASL - Arithmetic Shift Left - $0a */
    @Test
    public void test_ASL() {
        bus.loadProgram(0xa9, 0x00,  // LDA #$00
                        0x0a,        // ASL A

                        0xa9, 0x01,  // LDA #$01
                        0x0a,        // ASL A

                        0xa9, 0x02,  // LDA #$02
                        0x0a,        // ASL A

                        0xa9, 0x44,  // LDA #$44
                        0x0a,        // ASL A

                        0xa9, 0x80,  // LDA #$80
                        0x0a);       // ASL A

        cpu.step(2);
        assertEquals(0x00, cpu.getAccumulator());
        assertTrue(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.step(2);
        assertEquals(0x02, cpu.getAccumulator());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.step(2);
        assertEquals(0x04, cpu.getAccumulator());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.step(2);
        assertEquals(0x88, cpu.getAccumulator());
        assertFalse(cpu.getZeroFlag());
        assertTrue(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.step(2);
        assertEquals(0x00, cpu.getAccumulator());
        assertTrue(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertTrue(cpu.getCarryFlag());
    }

	/* ROL - Rotate Left - $2a */
    @Test
    public void test_ROL() {
        bus.loadProgram(0xa9, 0x00,  // LDA #$00
                        0x2a,        // ROL A   (m=%00000000, c=0)
                        0xa9, 0x01,  // LDA #$01
                        0x2a,        // ROL A   (m=%00000010, c=0)
                        0x38,        // SEC     (m=%00000010, c=1)
                        0x2a,        // ROL A   (m=%00000101, c=0)
                        0x2a,        // ROL A   (m=%00001010, c=0)
                        0x2a,        // ROL A   (m=%00010100, c=0)
                        0x2a,        // ROL A   (m=%00101000, c=0)
                        0x2a,        // ROL A   (m=%01010000, c=0)
                        0x2a,        // ROL A   (m=%10100000, c=0)
                        0x2a,        // ROL A   (m=%01000000, c=1)
                        0x2a);       // ROL A   (m=%10000001, c=0)

        cpu.step(2);
        assertEquals(0x00, cpu.getAccumulator());
        assertTrue(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.step(2);
        assertEquals(0x02, cpu.getAccumulator());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.step(2);
        assertEquals(0x05, cpu.getAccumulator());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.step();
        assertEquals(0x0a, cpu.getAccumulator());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.step();
        assertEquals(0x14, cpu.getAccumulator());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.step();
        assertEquals(0x28, cpu.getAccumulator());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.step();
        assertEquals(0x50, cpu.getAccumulator());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.step();
        assertEquals(0xa0, cpu.getAccumulator());
        assertFalse(cpu.getZeroFlag());
        assertTrue(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.step();
        assertEquals(0x40, cpu.getAccumulator());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertTrue(cpu.getCarryFlag());

        cpu.step();
        assertEquals(0x81, cpu.getAccumulator());
        assertFalse(cpu.getZeroFlag());
        assertTrue(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());
    }

	/* LSR - Logical Shift Right - $4a */
    @Test
    public void test_LSR() {
        bus.loadProgram(0xa9, 0x00,  // LDA #$00
                        0x4a,        // LSR A

                        0xa9, 0x01,  // LDA #$01
                        0x4a,        // LSR A

                        0xa9, 0x02,  // LDA #$02
                        0x4a,        // LSR A

                        0xa9, 0x44,  // LDA #$44
                        0x4a,        // LSR A

                        0xa9, 0x80,  // LDA #$80
                        0x4a,        // LSR A

                        0x38,        // SEC
                        0xa9, 0x02,  // LDA #$02
                        0x4a);       // LSR $05

        cpu.step(2);
        assertEquals(0x00, cpu.getAccumulator());
        assertTrue(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.step(2);
        assertEquals(0x00, cpu.getAccumulator());
        assertTrue(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertTrue(cpu.getCarryFlag());

        cpu.step(2);
        assertEquals(0x01, cpu.getAccumulator());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.step(2);
        assertEquals(0x22, cpu.getAccumulator());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.step(2);
        assertEquals(0x40, cpu.getAccumulator());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());

        // Setting Carry should not affect the result.
        cpu.step(3);
        assertEquals(0x01, cpu.getAccumulator());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());
    }

	/* ROR - Rotate Right - $6a */
    @Test
    public void test_ROR() {
        bus.loadProgram(0xa9, 0x00,  // LDA #$00
                        0x6a,        // ROR A   (m=%00000000, c=0)
                        0xa9, 0x10,  // LDA #$10
                        0x6a,        // ROR A   (m=%00001000, c=0)
                        0x6a,        // ROR A   (m=%00000100, c=0)
                        0x6a,        // ROR A   (m=%00000010, c=0)
                        0x6a,        // ROR A   (m=%00000001, c=0)
                        0x6a,        // ROR A   (m=%00000000, c=1)
                        0x6a,        // ROR A   (m=%10000000, c=0)
                        0x6a,        // ROR A   (m=%01000000, c=0)
                        0x6a,        // ROR A   (m=%00100000, c=0)
                        0x6a);       // ROR A   (m=%00010000, c=0)

        cpu.step(2);
        assertEquals(0x00, cpu.getAccumulator());
        assertTrue(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.step(2);
        assertEquals(0x08, cpu.getAccumulator());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.step();
        assertEquals(0x04, cpu.getAccumulator());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.step();
        assertEquals(0x02, cpu.getAccumulator());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.step();
        assertEquals(0x01, cpu.getAccumulator());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.step();
        assertEquals(0x00, cpu.getAccumulator());
        assertTrue(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertTrue(cpu.getCarryFlag());

        cpu.step();
        assertEquals(0x80, cpu.getAccumulator());
        assertFalse(cpu.getZeroFlag());
        assertTrue(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.step();
        assertEquals(0x40, cpu.getAccumulator());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.step();
        assertEquals(0x20, cpu.getAccumulator());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.step();
        assertEquals(0x10, cpu.getAccumulator());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());
    }

}