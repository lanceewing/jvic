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

public class CpuZeroPageModeTest extends CpuBaseTestCase {

    /*
    * The following opcodes are tested for correctness in this file:
    *
    * ORA - $05
    * ASL - $06
    * BIT - $24
    * AND - $25
    * ROL - $26
    *
    * EOR - $45
    * LSR - $46
    * ADC - $65
    * ROR - $66
    * STY - $84
    *
    * STA - $85
    * STX - $86
    * LDY - $a4
    * LDA - $a5
    * LDX - $a6
    *
    * CPY - $c4
    * CMP - $c5
    * DEC - $c6
    * CPX - $e4
    * SBC - $e5
    *
    * INC - $e6
    */

    /* ORA - Logical Inclusive OR - $05 */
    @Test
    public void test_ORA() {
        // Set some initial values in zero page.
        bus.write(0x0000, 0x00);
        bus.write(0x0002, 0x11);
        bus.write(0x0004, 0x22);
        bus.write(0x0008, 0x44);
        bus.write(0x0010, 0x88);

        bus.loadProgram(0x05, 0x00,  // ORA $00
                        0x05, 0x02,  // ORA $02
                        0x05, 0x04,  // ORA $04
                        0x05, 0x08,  // ORA $08
                        0x05, 0x10); // ORA $10
        cpu.step();
        assertEquals(0x00, cpu.getAccumulator());
        assertTrue(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());

        cpu.step();
        assertEquals(0x11, cpu.getAccumulator());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());

        cpu.step();
        assertEquals(0x33, cpu.getAccumulator());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());

        cpu.step();
        assertEquals(0x77, cpu.getAccumulator());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());

        cpu.step();
        assertEquals(0xff, cpu.getAccumulator());
        assertFalse(cpu.getZeroFlag());
        assertTrue(cpu.getNegativeFlag());
    }

    /* ASL - Arithmetic Shift Left - $06 */
    @Test
    public void test_ASL() {
        bus.write(0x0000, 0x00);
        bus.write(0x0001, 0x01);
        bus.write(0x0002, 0x02);
        bus.write(0x0003, 0x44);
        bus.write(0x0004, 0x80);

        bus.loadProgram(0x06, 0x00,
                        0x06, 0x01,
                        0x06, 0x02,
                        0x06, 0x03,
                        0x06, 0x04);

        cpu.step();
        assertEquals(0x00, bus.read(0x0000, true));
        assertTrue(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.step();
        assertEquals(0x02, bus.read(0x0001, true));
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.step();
        assertEquals(0x04, bus.read(0x0002, true));
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.step();
        assertEquals(0x88, bus.read(0x0003, true));
        assertFalse(cpu.getZeroFlag());
        assertTrue(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.step();
        assertEquals(0x00, bus.read(0x0004, true));
        assertTrue(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertTrue(cpu.getCarryFlag());
    }

    /* BIT - Bit Test - $24 */
    @Test
    public void test_BIT() {
        bus.write(0x0000, 0xc0);
        bus.write(0x0010, 0x40);
        bus.write(0x0020, 0x80);

        bus.loadProgram(0xa9, 0x01,  // LDA #$01
                        0x24, 0x00,  // BIT $00

                        0xa9, 0x0f,  // LDA #$0f
                        0x24, 0x00,  // BIT $00

                        0xa9, 0x40,  // LDA #$40
                        0x24, 0x20,  // BIT $20

                        0xa9, 0x80,  // LDA #$80
                        0x24, 0x10,  // BIT $10

                        0xa9, 0xc0,  // LDA #$c0
                        0x24, 0x00,  // BIT $00

                        0xa9, 0xff,  // LDA #$ff
                        0x24, 0x00); // BIT $00

        cpu.step(2);
        assertTrue(cpu.getZeroFlag());
        assertTrue(cpu.getNegativeFlag());
        assertTrue(cpu.getOverflowFlag());

        cpu.step(2);
        assertTrue(cpu.getZeroFlag());
        assertTrue(cpu.getNegativeFlag());
        assertTrue(cpu.getOverflowFlag());

        cpu.step(2);
        assertTrue(cpu.getZeroFlag());
        assertTrue(cpu.getNegativeFlag());
        assertFalse(cpu.getOverflowFlag());

        cpu.step(2);
        assertTrue(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertTrue(cpu.getOverflowFlag());

        cpu.step(2);
        assertFalse(cpu.getZeroFlag());
        assertTrue(cpu.getNegativeFlag());
        assertTrue(cpu.getOverflowFlag());

        cpu.step(2);
        assertFalse(cpu.getZeroFlag());
        assertTrue(cpu.getNegativeFlag());
        assertTrue(cpu.getOverflowFlag());
    }

    /* AND - Logical AND - $25 */
    @Test
    public void test_AND() {
        bus.write(0x0000, 0x00);
        bus.write(0x0001, 0x11);
        bus.write(0x0002, 0xff);
        bus.write(0x0003, 0x99);
        bus.write(0x0004, 0x11);
        bus.write(0x0005, 0x0f);

        bus.loadProgram(0x25, 0x00,  // AND $00
                        0x25, 0x01,  // AND $01
                        0xa9, 0xaa,  // LDA #$aa
                        0x25, 0x02,  // AND $02
                        0x25, 0x03,  // AND $03
                        0x25, 0x04,  // AND $04
                        0xa9, 0xff,  // LDA #$ff
                        0x25, 0x05); // AND $05
        cpu.step();
        assertEquals(0x00, cpu.getAccumulator());
        assertTrue(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());

        cpu.step();
        assertEquals(0x00, cpu.getAccumulator());
        assertTrue(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());

        cpu.step(2);
        assertEquals(0xaa, cpu.getAccumulator());
        assertFalse(cpu.getZeroFlag());
        assertTrue(cpu.getNegativeFlag());

        cpu.step();
        assertEquals(0x88, cpu.getAccumulator());
        assertFalse(cpu.getZeroFlag());
        assertTrue(cpu.getNegativeFlag());

        cpu.step();
        assertEquals(0x00, cpu.getAccumulator());
        assertTrue(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());

        cpu.step(2);
        assertEquals(0x0f, cpu.getAccumulator());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
    }

    /* ROL - Rotate Shift Left - $26 */
    @Test
    public void test_ROL() {

        bus.write(0x0000, 0x00);
        bus.write(0x0001, 0x01);

        bus.loadProgram(0x26, 0x00,  // ROL $00 (m=%00000000, c=0)
                        0x26, 0x01,  // ROL $01 (m=%00000010, c=0)
                        0x38,        // SEC     (m=%00000010, c=1)
                        0x26, 0x01,  // ROL $01 (m=%00000101, c=0)
                        0x26, 0x01,  // ROL $01 (m=%00001010, c=0)
                        0x26, 0x01,  // ROL $01 (m=%00010100, c=0)
                        0x26, 0x01,  // ROL $01 (m=%00101000, c=0)
                        0x26, 0x01,  // ROL $01 (m=%01010000, c=0)
                        0x26, 0x01,  // ROL $01 (m=%10100000, c=0)
                        0x26, 0x01,  // ROL $01 (m=%01000000, c=1)
                        0x26, 0x01); // ROL $01 (m=%10000001, c=0)

        cpu.step();
        assertEquals(0x00, bus.read(0x0000, true));
        assertTrue(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.step();
        assertEquals(0x02, bus.read(0x0001, true));
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.step(2);
        assertEquals(0x05, bus.read(0x0001, true));
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.step();
        assertEquals(0x0a, bus.read(0x0001, true));
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.step();
        assertEquals(0x14, bus.read(0x0001, true));
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.step();
        assertEquals(0x28, bus.read(0x0001, true));
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.step();
        assertEquals(0x50, bus.read(0x0001, true));
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.step();
        assertEquals(0xa0, bus.read(0x0001, true));
        assertFalse(cpu.getZeroFlag());
        assertTrue(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.step();
        assertEquals(0x40, bus.read(0x0001, true));
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertTrue(cpu.getCarryFlag());

        cpu.step();
        assertEquals(0x81, bus.read(0x0001, true));
        assertFalse(cpu.getZeroFlag());
        assertTrue(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());
    }

    /* EOR - Exclusive OR - $45 */
    @Test
    public void test_EOR() {
        bus.write(0x10, 0x00);
        bus.write(0x11, 0xff);
        bus.write(0x12, 0x33);
        bus.write(0x13, 0x44);

        bus.loadProgram(0xa9, 0x88,  // LDA #$88
                        0x45, 0x10,  // EOR $10
                        0x45, 0x11,  // EOR $11
                        0x45, 0x12,  // EOR $12
                        0x45, 0x13); // EOR $13
        cpu.step(2);
        assertEquals(0x88, cpu.getAccumulator());
        assertTrue(cpu.getNegativeFlag());
        assertFalse(cpu.getZeroFlag());

        cpu.step();
        assertEquals(0x77, cpu.getAccumulator());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getZeroFlag());

        cpu.step();
        assertEquals(0x44, cpu.getAccumulator());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getZeroFlag());

        cpu.step();
        assertEquals(0x00, cpu.getAccumulator());
        assertFalse(cpu.getNegativeFlag());
        assertTrue(cpu.getZeroFlag());
    }

    /* LSR - Logical Shift Right - $46 */
    @Test
    public void test_LSR() {
        bus.write(0x0000, 0x00);
        bus.write(0x0001, 0x01);
        bus.write(0x0002, 0x02);
        bus.write(0x0003, 0x44);
        bus.write(0x0004, 0x80);
        bus.write(0x0005, 0x02);

        bus.loadProgram(0x46, 0x00,  // LSR $00
                        0x46, 0x01,  // LSR $01
                        0x46, 0x02,  // LSR $02
                        0x46, 0x03,  // LSR $03
                        0x46, 0x04,  // LSR $04
                        0x38,        // SEC
                        0x46, 0x05); // LSR $05

        cpu.step();
        assertEquals(0x00, bus.read(0x0000, true));
        assertTrue(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.step();
        assertEquals(0x00, bus.read(0x0001, true));
        assertTrue(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertTrue(cpu.getCarryFlag());

        cpu.step();
        assertEquals(0x01, bus.read(0x0002, true));
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.step();
        assertEquals(0x22, bus.read(0x0003, true));
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.step();
        assertEquals(0x40, bus.read(0x0004, true));
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());

        // Setting Carry should not affect the result.
        cpu.step(2);
        assertEquals(0x01, bus.read(0x0005, true));
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());
    }

    /* ADC - Add with Carry - $65 */
    @Test
    public void test_ADC() {
        bus.write(0x10, 0x01);
        bus.write(0x11, 0xff);

        bus.loadProgram(0xa9, 0x00,  // LDA #$00
                        0x65, 0x10); // ADC $10
        cpu.step(2);
        assertEquals(0x01, cpu.getAccumulator());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getOverflowFlag());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.reset();
        bus.loadProgram(0xa9, 0x7f,  // LDA #$7f
                        0x65, 0x10); // ADC $10
        cpu.step(2);
        assertEquals(0x80, cpu.getAccumulator());
        assertTrue(cpu.getNegativeFlag());
        assertTrue(cpu.getOverflowFlag());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.reset();
        bus.loadProgram(0xa9, 0x80,  // LDA #$80
                        0x65, 0x10); // ADC $10
        cpu.step(2);
        assertEquals(0x81, cpu.getAccumulator());
        assertTrue(cpu.getNegativeFlag());
        assertFalse(cpu.getOverflowFlag());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.reset();
        bus.loadProgram(0xa9, 0xff,  // LDA #$ff
                        0x65, 0x10); // ADC $10
        cpu.step(2);
        assertEquals(0x00, cpu.getAccumulator());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getOverflowFlag());
        assertTrue(cpu.getZeroFlag());
        assertTrue(cpu.getCarryFlag());

        cpu.reset();
        bus.loadProgram(0xa9, 0x00,  // LDA #$00
                        0x65, 0x11); // ADC $11
        cpu.step(2);
        assertEquals(0xff, cpu.getAccumulator());
        assertTrue(cpu.getNegativeFlag());
        assertFalse(cpu.getOverflowFlag());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.reset();
        bus.loadProgram(0xa9, 0x7f,  // LDA #$7f
                        0x65, 0x11); // ADC $11
        cpu.step(2);
        assertEquals(0x7e, cpu.getAccumulator());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getOverflowFlag());
        assertFalse(cpu.getZeroFlag());
        assertTrue(cpu.getCarryFlag());

        cpu.reset();
        bus.loadProgram(0xa9, 0x80,  // LDA #$80
                        0x65, 0x11); // ADC $11
        cpu.step(2);
        assertEquals(0x7f, cpu.getAccumulator());
        assertFalse(cpu.getNegativeFlag());
        assertTrue(cpu.getOverflowFlag());
        assertFalse(cpu.getZeroFlag());
        assertTrue(cpu.getCarryFlag());

        cpu.reset();
        bus.loadProgram(0xa9, 0xff,  // LDA #$ff
                        0x65, 0x11); // ADC $11
        cpu.step(2);
        assertEquals(0xfe, cpu.getAccumulator());
        assertTrue(cpu.getNegativeFlag());
        assertFalse(cpu.getOverflowFlag());
        assertFalse(cpu.getZeroFlag());
        assertTrue(cpu.getCarryFlag());
    }

    @Test
    public void test_ADC_IncludesCarry() {
        bus.write(0x10, 0x01);

        bus.loadProgram(0xa9, 0x00,  // LDA #$00
                        0x38,        // SEC
                        0x65, 0x10); // ADC $10
        cpu.step(3);
        assertEquals(0x02, cpu.getAccumulator());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getOverflowFlag());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getCarryFlag());
    }

    @Test
    public void test_ADC_DecimalMode() {
        bus.write(0x10, 0x01);
        bus.write(0x11, 0x99);

        bus.loadProgram(0xf8,        // SED
                        0xa9, 0x01,  // LDA #$01
                        0x65, 0x10); // ADC $10
        cpu.step(3);
        assertEquals(0x02, cpu.getAccumulator());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getOverflowFlag());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.reset();
        bus.loadProgram(0xf8,        // SED
                        0xa9, 0x49,  // LDA #$49
                        0x65, 0x10); // ADC $10
        cpu.step(3);
        assertEquals(0x50, cpu.getAccumulator());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getOverflowFlag());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.reset();
        bus.loadProgram(0xf8,        // SED
                        0xa9, 0x50,  // LDA #$50
                        0x65, 0x10); // ADC $10
        cpu.step(3);
        assertEquals(0x51, cpu.getAccumulator());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getOverflowFlag());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.reset();
        bus.loadProgram(0xf8,        // SED
                        0xa9, 0x99,  // LDA #$99
                        0x65, 0x10); // ADC $10
        cpu.step(3);
        assertEquals(0x00, cpu.getAccumulator());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getOverflowFlag());
        assertTrue(cpu.getZeroFlag());
        assertTrue(cpu.getCarryFlag());

        cpu.reset();
        bus.loadProgram(0xf8,        // SED
                        0xa9, 0x00,  // LDA #$00
                        0x65, 0x11); // ADC $10
        cpu.step(3);
        assertEquals(0x99, cpu.getAccumulator());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getOverflowFlag());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.reset();
        bus.loadProgram(0xf8,        // SED
                        0xa9, 0x49,  // LDA #$49
                        0x65, 0x11); // ADC $11
        cpu.step(3);
        assertEquals(0x48, cpu.getAccumulator());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getOverflowFlag());
        assertFalse(cpu.getZeroFlag());
        assertTrue(cpu.getCarryFlag());

        cpu.reset();
        bus.loadProgram(0xf8,        // SED
                        0xa9, 0x50,  // LDA #$59
                        0x65, 0x11); // ADC $11
        cpu.step(3);
        assertEquals(0x49, cpu.getAccumulator());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getOverflowFlag());
        assertFalse(cpu.getZeroFlag());
        assertTrue(cpu.getCarryFlag());
    }

    /* ROR - Rotate Right - $66 */
    @Test
    public void test_ROR() {
        bus.write(0x10, 0x00);
        bus.write(0x11, 0x10);

        bus.loadProgram(0x66, 0x10,  // ROR $00 (m=%00000000, c=0)
                        0x66, 0x11,  // ROR $01 (m=%00001000, c=0)
                        0x66, 0x11,  // ROR $01 (m=%00000100, c=0)
                        0x66, 0x11,  // ROR $01 (m=%00000010, c=0)
                        0x66, 0x11,  // ROR $01 (m=%00000001, c=0)
                        0x66, 0x11,  // ROR $01 (m=%00000000, c=1)
                        0x66, 0x11,  // ROR $01 (m=%10000000, c=0)
                        0x66, 0x11,  // ROR $01 (m=%01000000, c=0)
                        0x66, 0x11,  // ROR $01 (m=%00100000, c=0)
                        0x66, 0x11); // ROR $01 (m=%00010000, c=0)

        cpu.step();
        assertEquals(0x00, bus.read(0x10, true));
        assertTrue(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.step();
        assertEquals(0x08, bus.read(0x11, true));
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.step();
        assertEquals(0x04, bus.read(0x11, true));
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.step();
        assertEquals(0x02, bus.read(0x11, true));
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.step();
        assertEquals(0x01, bus.read(0x11, true));
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.step();
        assertEquals(0x00, bus.read(0x11, true));
        assertTrue(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertTrue(cpu.getCarryFlag());

        cpu.step();
        assertEquals(0x80, bus.read(0x11, true));
        assertFalse(cpu.getZeroFlag());
        assertTrue(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.step();
        assertEquals(0x40, bus.read(0x11, true));
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.step();
        assertEquals(0x20, bus.read(0x11, true));
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.step();
        assertEquals(0x10, bus.read(0x11, true));
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getCarryFlag());
    }

    /* STY - Store Y Register - $84 */
    @Test
    public void test_STY() {
        cpu.setYRegister(0x00);
        bus.loadProgram(0x84, 0x10);
        cpu.step();
        assertEquals(0x00, bus.read(0x10, true));
        // Should have no effect on flags.
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());

        cpu.reset();

        cpu.setYRegister(0x0f);
        bus.loadProgram(0x84, 0x10);
        cpu.step();
        assertEquals(0x0f, bus.read(0x10, true));
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());

        cpu.reset();

        cpu.setYRegister(0x80);
        bus.loadProgram(0x84, 0x10);
        cpu.step();
        assertEquals(0x80, bus.read(0x10, true));
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
    }

    /* STA - Store Accumulator - $85 */
    @Test
    public void test_STA() {
        cpu.setAccumulator(0x00);
        bus.loadProgram(0x85, 0x10);
        cpu.step();
        assertEquals(0x00, bus.read(0x10, true));
        // Should have no effect on flags.
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());

        cpu.reset();

        cpu.setAccumulator(0x0f);
        bus.loadProgram(0x85, 0x10);
        cpu.step();
        assertEquals(0x0f, bus.read(0x10, true));
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());

        cpu.reset();

        cpu.setAccumulator(0x80);
        bus.loadProgram(0x85, 0x10);
        cpu.step();
        assertEquals(0x80, bus.read(0x10, true));
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
    }

    /* STX - Store X Register - $86 */
    @Test
    public void test_STX() {
        cpu.setXRegister(0x00);
        bus.loadProgram(0x86, 0x10);
        cpu.step();
        assertEquals(0x00, bus.read(0x10, true));
        // Should have no effect on flags.
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());

        cpu.reset();

        cpu.setXRegister(0x0f);
        bus.loadProgram(0x86, 0x10);
        cpu.step();
        assertEquals(0x0f, bus.read(0x10, true));
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());

        cpu.reset();

        cpu.setXRegister(0x80);
        bus.loadProgram(0x86, 0x10);
        cpu.step();
        assertEquals(0x80, bus.read(0x10, true));
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
    }

    /* LDY - Load Y Register - $a4 */
    @Test
    public void test_LDY() {
        bus.write(0x10, 0x00);
        bus.write(0x11, 0x0f);
        bus.write(0x12, 0x80);

        bus.loadProgram(0xa4, 0x10,
                        0xa4, 0x11,
                        0xa4, 0x12);

        cpu.step();
        assertEquals(0x00, cpu.getYRegister());
        assertTrue(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());

        cpu.step();
        assertEquals(0x0f, cpu.getYRegister());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());

        cpu.step();
        assertEquals(0x80, cpu.getYRegister());
        assertFalse(cpu.getZeroFlag());
        assertTrue(cpu.getNegativeFlag());
    }

    /* LDA - Load Accumulator - $a5 */
    @Test
    public void test_LDA() {
        bus.write(0x10, 0x00);
        bus.write(0x11, 0x0f);
        bus.write(0x12, 0x80);

        bus.loadProgram(0xa5, 0x10,
                        0xa5, 0x11,
                        0xa5, 0x12);

        cpu.step();
        assertEquals(0x00, cpu.getAccumulator());
        assertTrue(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());

        cpu.step();
        assertEquals(0x0f, cpu.getAccumulator());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());

        cpu.step();
        assertEquals(0x80, cpu.getAccumulator());
        assertFalse(cpu.getZeroFlag());
        assertTrue(cpu.getNegativeFlag());
    }

    /* LDX - Load X Register - $a6 */
    @Test
    public void test_LDX() {
        bus.write(0x10, 0x00);
        bus.write(0x11, 0x0f);
        bus.write(0x12, 0x80);

        bus.loadProgram(0xa6, 0x10,
                        0xa6, 0x11,
                        0xa6, 0x12);

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

    /* CPY - Compare Y Register - $c4 */
    @Test
    public void test_CPY() {
        bus.write(0x10, 0x00);
        bus.write(0x11, 0x80);
        bus.write(0x12, 0xff);

        cpu.setYRegister(0x80);

        bus.loadProgram(0xc4, 0x10,
                        0xc4, 0x11,
                        0xc4, 0x12);

        cpu.step();
        assertTrue(cpu.getCarryFlag());    // m > y
        assertFalse(cpu.getZeroFlag());
        assertTrue(cpu.getNegativeFlag()); // m - y < 0

        cpu.step();
        assertTrue(cpu.getCarryFlag());    // m = y
        assertTrue(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag()); // m - y == 0

        cpu.step();
        assertFalse(cpu.getCarryFlag());    // m < y
        assertFalse(cpu.getZeroFlag());
        assertTrue(cpu.getNegativeFlag()); // m - y < 0
    }

    /* CMP - Compare Accumulator - $c5 */
    @Test
    public void test_CMP() {
        bus.write(0x10, 0x00);
        bus.write(0x11, 0x80);
        bus.write(0x12, 0xff);

        cpu.setAccumulator(0x80);

        bus.loadProgram(0xc5, 0x10,
                        0xc5, 0x11,
                        0xc5, 0x12);

        cpu.step();
        assertTrue(cpu.getCarryFlag());    // m > y
        assertFalse(cpu.getZeroFlag());
        assertTrue(cpu.getNegativeFlag()); // m - y < 0

        cpu.step();
        assertTrue(cpu.getCarryFlag());    // m = y
        assertTrue(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag()); // m - y == 0

        cpu.step();
        assertFalse(cpu.getCarryFlag());    // m < y
        assertFalse(cpu.getZeroFlag());
        assertTrue(cpu.getNegativeFlag()); // m - y < 0
    }

    /* DEC - Decrement Memory Location - $c6 */
    @Test
    public void test_DEC() {
        bus.write(0x10, 0x00);
        bus.write(0x11, 0x01);
        bus.write(0x12, 0x80);
        bus.write(0x13, 0xff);

        bus.loadProgram(0xc6, 0x10,  // DEC $10
                        0xc6, 0x11,  // DEC $11
                        0xc6, 0x12,  // DEC $12
                        0xc6, 0x13); // DEC $13

        cpu.step();
        assertEquals(0xff, bus.read(0x10, true));
        assertFalse(cpu.getZeroFlag());
        assertTrue(cpu.getNegativeFlag());

        cpu.step();
        assertEquals(0x00, bus.read(0x11, true));
        assertTrue(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());

        cpu.step();
        assertEquals(0x7f, bus.read(0x12, true));
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());

        cpu.step();
        assertEquals(0xfe, bus.read(0x13, true));
        assertFalse(cpu.getZeroFlag());
        assertTrue(cpu.getNegativeFlag());
    }

    /* CPX - Compare X Register - $e4 */
    @Test
    public void test_CPX() {
        bus.write(0x10, 0x00);
        bus.write(0x11, 0x80);
        bus.write(0x12, 0xff);

        cpu.setXRegister(0x80);

        bus.loadProgram(0xe4, 0x10,
                        0xe4, 0x11,
                        0xe4, 0x12);

        cpu.step();
        assertTrue(cpu.getCarryFlag());    // m > y
        assertFalse(cpu.getZeroFlag());
        assertTrue(cpu.getNegativeFlag()); // m - y < 0

        cpu.step();
        assertTrue(cpu.getCarryFlag());    // m = y
        assertTrue(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag()); // m - y == 0

        cpu.step();
        assertFalse(cpu.getCarryFlag());    // m < y
        assertFalse(cpu.getZeroFlag());
        assertTrue(cpu.getNegativeFlag()); // m - y < 0
    }

    /* SBC - Subtract with Carry - $e5 */
    @Test
    public void test_SBC() {
        bus.write(0x10, 0x01);

        bus.loadProgram(0xa9, 0x00,  // LDA #$00
                        0xe5, 0x10); // SBC $10
        cpu.step(2);
        assertEquals(0xfe, cpu.getAccumulator());
        assertTrue(cpu.getNegativeFlag());
        assertFalse(cpu.getOverflowFlag());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.reset();
        bus.loadProgram(0xa9, 0x7f,  // LDA #$7f
                        0xe5, 0x10); // SBC $10
        cpu.step(2);
        assertEquals(0x7d, cpu.getAccumulator());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getOverflowFlag());
        assertFalse(cpu.getZeroFlag());
        assertTrue(cpu.getCarryFlag());

        cpu.reset();
        bus.loadProgram(0xa9, 0x80,  // LDA #$80
                        0xe5, 0x10); // SBC $10
        cpu.step(2);
        assertEquals(0x7e, cpu.getAccumulator());
        assertFalse(cpu.getNegativeFlag());
        assertTrue(cpu.getOverflowFlag());
        assertFalse(cpu.getZeroFlag());
        assertTrue(cpu.getCarryFlag());

        cpu.reset();
        bus.loadProgram(0xa9, 0xff,  // LDA #$ff
                        0xe5, 0x10); // SBC $10
        cpu.step(2);
        assertEquals(0xfd, cpu.getAccumulator());
        assertTrue(cpu.getNegativeFlag());
        assertFalse(cpu.getOverflowFlag());
        assertFalse(cpu.getZeroFlag());
        assertTrue(cpu.getCarryFlag());

        cpu.reset();
        bus.loadProgram(0xa9, 0x02,  // LDA #$02
                        0xe5, 0x10); // SBC $10
        cpu.step(2);
        assertEquals(0x00, cpu.getAccumulator());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getOverflowFlag());
        assertTrue(cpu.getZeroFlag());
        assertTrue(cpu.getCarryFlag());
    }

    @Test
    public void test_SBC_IncludesNotOfCarry() {
        bus.write(0x10, 0x01);

        // Subtrace with Carry Flag cleared
        bus.loadProgram(0x18,        // CLC
                        0xa9, 0x05,  // LDA #$00
                        0xe5, 0x10); // SBC $10

        cpu.step(3);
        assertEquals(0x03, cpu.getAccumulator());

        cpu.reset();

        // Subtrace with Carry Flag cleared
        bus.loadProgram(0x18,        // CLC
                        0xa9, 0x00,  // LDA #$00
                        0xe5, 0x10); // SBC $10

        cpu.step(3);
        assertEquals(0xfe, cpu.getAccumulator());

        cpu.reset();

        // Subtract with Carry Flag set
        bus.loadProgram(0x38,        // SEC
                        0xa9, 0x05,  // LDA #$00
                        0xe5, 0x10); // SBC $10
        cpu.step(3);
        assertEquals(0x04, cpu.getAccumulator());
        assertTrue(cpu.getCarryFlag());

        cpu.reset();

        // Subtract with Carry Flag set
        bus.loadProgram(0x38,        // SEC
                        0xa9, 0x00,  // LDA #$00
                        0xe5, 0x10); // SBC $10
        cpu.step(3);
        assertEquals(0xff, cpu.getAccumulator());
        assertFalse(cpu.getCarryFlag());

    }

    @Test
    public void test_SBC_DecimalMode() {
        bus.write(0x10, 0x01);
        bus.write(0x20, 0x11);

        bus.loadProgram(0xf8,
                        0xa9, 0x00,
                        0xe5, 0x10);
        cpu.step(3);
        assertEquals(0x98, cpu.getAccumulator());
        assertFalse(cpu.getCarryFlag()); // borrow = set flag
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getOverflowFlag());
        assertFalse(cpu.getZeroFlag());
        assertTrue(cpu.getDecimalModeFlag());

        cpu.reset();

        bus.loadProgram(0xf8,
                        0xa9, 0x99,
                        0xe5, 0x10);
        cpu.step(3);
        assertEquals(0x97, cpu.getAccumulator());
        assertTrue(cpu.getCarryFlag()); // No borrow = clear flag
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getOverflowFlag());
        assertFalse(cpu.getZeroFlag());
        assertTrue(cpu.getDecimalModeFlag());

        cpu.reset();

        bus.loadProgram(0xf8,
                        0xa9, 0x50,
                        0xe5, 0x10);
        cpu.step(3);
        assertEquals(0x48, cpu.getAccumulator());
        assertTrue(cpu.getCarryFlag());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getOverflowFlag());
        assertFalse(cpu.getZeroFlag());
        assertTrue(cpu.getDecimalModeFlag());


        cpu.reset();

        bus.loadProgram(0xf8,         // SED
                        0xa9, 0x02,   // LDA #$02
                        0xe5, 0x10);  // SBC $10
        cpu.step(3);
        assertEquals(0x00, cpu.getAccumulator());
        assertTrue(cpu.getCarryFlag());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getOverflowFlag());
        assertTrue(cpu.getZeroFlag());
        assertTrue(cpu.getDecimalModeFlag());

        cpu.reset();

        bus.loadProgram(0xf8,         // SED
                        0xa9, 0x10,   // LDA #$10
                        0xe5, 0x20);  // SBC $20
        cpu.step(3);
        assertEquals(0x98, cpu.getAccumulator());
        assertFalse(cpu.getCarryFlag());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getOverflowFlag());
        assertFalse(cpu.getZeroFlag());
        assertTrue(cpu.getDecimalModeFlag());

        cpu.reset();

        bus.loadProgram(0x38,         // SEC
                        0xf8,         // SED
                        0xa9, 0x05,   // LDA #$05
                        0xe5, 0x10);  // SBC $10
        cpu.step(4);
        assertEquals(0x04, cpu.getAccumulator());
        assertTrue(cpu.getCarryFlag());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getOverflowFlag());
        assertFalse(cpu.getZeroFlag());
        assertTrue(cpu.getDecimalModeFlag());

        cpu.reset();

        bus.loadProgram(0x38,         // SEC
                        0xf8,         // SED
                        0xa9, 0x00,   // LDA #$00
                        0xe5, 0x10);  // SBC $10
        cpu.step(4);
        assertEquals(0x99, cpu.getAccumulator());
        assertFalse(cpu.getCarryFlag());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getOverflowFlag());
        assertFalse(cpu.getZeroFlag());
        assertTrue(cpu.getDecimalModeFlag());
    }

    /* INC - Increment Memory Location - $e6 */
    @Test
    public void test_INC() {
        bus.write(0x10, 0x00);
        bus.write(0x11, 0x7f);
        bus.write(0x12, 0xff);

        bus.loadProgram(0xe6, 0x10,  // INC $10
                        0xe6, 0x11,  // INC $11
                        0xe6, 0x12); // INC $12

        cpu.step();
        assertEquals(0x01, bus.read(0x10, true));
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());

        cpu.step();
        assertEquals(0x80, bus.read(0x11, true));
        assertFalse(cpu.getZeroFlag());
        assertTrue(cpu.getNegativeFlag());

        cpu.step();
        assertEquals(0x00, bus.read(0x12, true));
        assertTrue(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
    }

}
