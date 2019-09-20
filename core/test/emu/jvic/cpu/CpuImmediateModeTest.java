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

public class CpuImmediateModeTest extends CpuBaseTestCase {

    /*
    * The following opcodes are tested for correctness in this file:
    *
    * ORA - $09
    * AND - $29
    * EOR - $49
    * ADC - $69
    * LDY - $a0
    *
    * LDX - $a2
    * LDA - $a9
    * CPY - $c0
    * CMP - $c9
    * CPX - $e0
    *
    * SBC - $e9
    */

    /* ORA Immediate Mode Tests - 0x09 */
    @Test
    public void test_ORA() {
        bus.loadProgram(0x09, 0x00,  // ORA #$00
                        0x09, 0x11,  // ORA #$11
                        0x09, 0x22,  // ORA #$22
                        0x09, 0x44,  // ORA #$44
                        0x09, 0x88); // ORA #$88
        cpu.step();
        assertEquals(0x00, cpu.getAccumulator());

        cpu.step();
        assertEquals(0x11, cpu.getAccumulator());

        cpu.step();
        assertEquals(0x33, cpu.getAccumulator());

        cpu.step();
        assertEquals(0x77, cpu.getAccumulator());

        cpu.step();
        assertEquals(0xff, cpu.getAccumulator());
    }

    @Test
    public void test_ORA_SetsZeroFlagIfResultIsZero() {
        bus.loadProgram(0x09, 0x00);  // ORA #$00
        cpu.step();
        assertTrue(cpu.getZeroFlag());
    }

    @Test
    public void test_ORA_DoesNotSetZeroFlagIfResultNotZero() {
        bus.loadProgram(0x09, 0x01);  // ORA #$01
        cpu.step();
        assertFalse(cpu.getZeroFlag());
    }

    @Test
    public void test_ORA_SetsNegativeFlagIfResultIsNegative() {
        bus.loadProgram(0x09, 0x80);  // ORA #$80
        cpu.step();
        assertTrue(cpu.getNegativeFlag());
    }

    @Test
    public void test_ORA_DoesNotSetNegativeFlagIfResultNotNegative() {
        bus.loadProgram(0x09, 0x7f);  // ORA #$7F
        cpu.step();
        assertFalse(cpu.getNegativeFlag());
    }

    /* AND Immediate Mode Tests - 0x29 */
    @Test
    public void test_AND() {
        bus.loadProgram(0x29, 0x00,  // AND #$00
                        0x29, 0x11,  // AND #$11
                        0xa9, 0xaa,  // LDA #$AA
                        0x29, 0xff,  // AND #$FF
                        0x29, 0x99,  // AND #$99
                        0x29, 0x11); // AND #$11
        cpu.step();
        assertEquals(0x00, cpu.getAccumulator());

        cpu.step();
        assertEquals(0x00, cpu.getAccumulator());

        cpu.step(2);
        assertEquals(0xaa, cpu.getAccumulator());

        cpu.step();
        assertEquals(0x88, cpu.getAccumulator());

        cpu.step();
        assertEquals(0x00, cpu.getAccumulator());
    }
    
    @Test
    public void test_AND_SetsZeroFlagIfResultIsZero() {
        bus.loadProgram(0xa9, 0x88,  // LDA #$88
                        0x29, 0x11); // AND #$11
        cpu.step(2);
        assertTrue(cpu.getZeroFlag());
    }

    @Test
    public void test_AND_DoesNotSetZeroFlagIfResultNotZero() {
        bus.loadProgram(0xa9, 0x88,  // LDA #$88
                        0x29, 0xf1); // AND #$F1
        cpu.step(2);
        assertFalse(cpu.getZeroFlag());
    }

    @Test
    public void test_AND_SetsNegativeFlagIfResultIsNegative() {
        bus.loadProgram(0xa9, 0x88,  // LDA #$88
                        0x29, 0xf0); // AND #$F0
        cpu.step(2);
        assertTrue(cpu.getNegativeFlag());
    }

    @Test
    public void test_AND_DoesNotSetNegativeFlagIfResultNotNegative() {
        bus.loadProgram(0xa9, 0x88,  // LDA #$88
                        0x29, 0x0f); // AND #$0F
        cpu.step(2);
        assertFalse(cpu.getNegativeFlag());
    }

    /* EOR Immediate Mode Tests - 0x49 */
    @Test
    public void test_EOR() {
        bus.loadProgram(0xa9, 0x88,  // LDA #$88
                        0x49, 0x00,  // EOR #$00
                        0x49, 0xff,  // EOR #$ff
                        0x49, 0x33); // EOR #$33
        cpu.step(2);
        assertEquals(0x88, cpu.getAccumulator());

        cpu.step();
        assertEquals(0x77, cpu.getAccumulator());

        cpu.step();
        assertEquals(0x44, cpu.getAccumulator());
    }

    @Test
    public void test_EOR_SetsArithmeticFlags() {
        bus.loadProgram(0xa9, 0x77,  // LDA #$77
                        0x49, 0x77,  // EOR #$77
                        0x49, 0xff); // EOR #$ff
        cpu.step(2);
        assertEquals(0x00, cpu.getAccumulator());
        assertTrue(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());

        cpu.step();
        assertEquals(0xff, cpu.getAccumulator());
        assertFalse(cpu.getZeroFlag());
        assertTrue(cpu.getNegativeFlag());
    }

    /* ADC Immediate Mode Tests - 0x69 */
    @Test
    public void test_ADC() {
        bus.loadProgram(0xa9, 0x00,  // LDA #$00
                        0x69, 0x01); // ADC #$01
        cpu.step(2);
        assertEquals(0x01, cpu.getAccumulator());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getOverflowFlag());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.reset();
        bus.loadProgram(0xa9, 0x7f,  // LDA #$7f
                        0x69, 0x01); // ADC #$01
        cpu.step(2);
        assertEquals(0x80, cpu.getAccumulator());

        assertTrue(cpu.getNegativeFlag());
        assertTrue(cpu.getOverflowFlag());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.reset();
        bus.loadProgram(0xa9, 0x80,  // LDA #$80
                        0x69, 0x01); // ADC #$01
        cpu.step(2);
        assertEquals(0x81, cpu.getAccumulator());
        assertTrue(cpu.getNegativeFlag());
        assertFalse(cpu.getOverflowFlag());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.reset();
        bus.loadProgram(0xa9, 0xff,  // LDA #$ff
                        0x69, 0x01); // ADC #$01
        cpu.step(2);
        assertEquals(0x00, cpu.getAccumulator());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getOverflowFlag());
        assertTrue(cpu.getZeroFlag());
        assertTrue(cpu.getCarryFlag());

        cpu.reset();
        bus.loadProgram(0xa9, 0x00,  // LDA #$00
                        0x69, 0xff); // ADC #$ff
        cpu.step(2);
        assertEquals(0xff, cpu.getAccumulator());
        assertTrue(cpu.getNegativeFlag());
        assertFalse(cpu.getOverflowFlag());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.reset();
        bus.loadProgram(0xa9, 0x7f,  // LDA #$7f
                        0x69, 0xff); // ADC #$ff
        cpu.step(2);
        assertEquals(0x7e, cpu.getAccumulator());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getOverflowFlag());
        assertFalse(cpu.getZeroFlag());
        assertTrue(cpu.getCarryFlag());

        cpu.reset();
        bus.loadProgram(0xa9, 0x80,  // LDA #$80
                        0x69, 0xff); // ADC #$ff
        cpu.step(2);
        assertEquals(0x7f, cpu.getAccumulator());
        assertFalse(cpu.getNegativeFlag());
        assertTrue(cpu.getOverflowFlag());
        assertFalse(cpu.getZeroFlag());
        assertTrue(cpu.getCarryFlag());

        cpu.reset();
        bus.loadProgram(0xa9, 0xff,  // LDA #$ff
                        0x69, 0xff); // ADC #$ff
        cpu.step(2);
        assertEquals(0xfe, cpu.getAccumulator());
        assertTrue(cpu.getNegativeFlag());
        assertFalse(cpu.getOverflowFlag());
        assertFalse(cpu.getZeroFlag());
        assertTrue(cpu.getCarryFlag());
    }

    @Test
    public void test_ADC_IncludesCarry() {
        bus.loadProgram(0xa9, 0x00,  // LDA #$01
                        0x38,        // SEC
                        0x69, 0x01); // ADC #$01
        cpu.step(3);
        assertEquals(0x02, cpu.getAccumulator());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getOverflowFlag());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getCarryFlag());
    }

    @Test
    public void test_ADC_DecimalMode() {
        bus.loadProgram(0xf8,        // SED
                        0xa9, 0x01,  // LDA #$01
                        0x69, 0x01); // ADC #$01
        cpu.step(3);
        assertEquals(0x02, cpu.getAccumulator());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getOverflowFlag());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.reset();
        bus.loadProgram(0xf8,        // SED
                        0xa9, 0x49,  // LDA #$49
                        0x69, 0x01); // ADC #$01
        cpu.step(3);
        assertEquals(0x50, cpu.getAccumulator());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getOverflowFlag());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.reset();
        bus.loadProgram(0xf8,        // SED
                        0xa9, 0x50,  // LDA #$50
                        0x69, 0x01); // ADC #$01
        cpu.step(3);
        assertEquals(0x51, cpu.getAccumulator());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getOverflowFlag());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.reset();
        bus.loadProgram(0xf8,        // SED
                        0xa9, 0x99,  // LDA #$99
                        0x69, 0x01); // ADC #$01
        cpu.step(3);
        assertEquals(0x00, cpu.getAccumulator());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getOverflowFlag());
        assertTrue(cpu.getZeroFlag());
        assertTrue(cpu.getCarryFlag());

        cpu.reset();
        bus.loadProgram(0xf8,        // SED
                        0xa9, 0x00,  // LDA #$00
                        0x69, 0x99); // ADC #$01
        cpu.step(3);
        assertEquals(0x99, cpu.getAccumulator());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getOverflowFlag());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.reset();
        bus.loadProgram(0xf8,        // SED
                        0xa9, 0x49,  // LDA #$49
                        0x69, 0x99); // ADC #$99
        cpu.step(3);
        assertEquals(0x48, cpu.getAccumulator());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getOverflowFlag());
        assertFalse(cpu.getZeroFlag());
        assertTrue(cpu.getCarryFlag());

        cpu.reset();
        bus.loadProgram(0xf8,        // SED
                        0xa9, 0x50,  // LDA #$59
                        0x69, 0x99); // ADC #$99
        cpu.step(3);
        assertEquals(0x49, cpu.getAccumulator());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getOverflowFlag());
        assertFalse(cpu.getZeroFlag());
        assertTrue(cpu.getCarryFlag());
    }

    /* LDY Immediate Mode Tests - 0xa0 */
    @Test
    public void test_LDY_SetsYRegister() {
        bus.loadProgram(0xa0, 0x12);  // LDY #$12
        cpu.step();
        assertEquals(0x12, cpu.getYRegister());
    }

    @Test
    public void test_LDY_SetsZeroFlagIfArgIsZero() {
        bus.loadProgram(0xa0, 0x00);  // LDY #$00
        cpu.step();
        assertTrue(cpu.getZeroFlag());
    }

    @Test
    public void test_LDY_DoesNotSetZeroFlagIfResultNotZero() {
        bus.loadProgram(0xa0, 0x12);  // LDY #$12
        cpu.step();
        assertFalse(cpu.getZeroFlag());
    }

    @Test
    public void test_LDY_SetsNegativeFlagIfResultIsNegative() {
        bus.loadProgram(0xa0, 0x80);  // LDY #$80
        cpu.step();
        assertTrue(cpu.getNegativeFlag());
    }

    @Test
    public void test_LDY_DoesNotSetNegativeFlagIfResultNotNegative() {
        bus.loadProgram(0xa0, 0x7f);  // LDY #$7F
        cpu.step();
        assertFalse(cpu.getNegativeFlag());
    }

    /* LDX Immediate Mode Tests - 0xa2 */
    @Test
    public void test_LDX_SetsXRegister() {
        bus.loadProgram(0xa2, 0x12);  // LDX #$12
        cpu.step();
        assertEquals(0x12, cpu.getXRegister());
    }

    @Test
    public void test_LDX_SetsZeroFlagIfResultIsZero() {
        bus.loadProgram(0xa2, 0x00);  // LDX #$00
        cpu.step();
        assertTrue(cpu.getZeroFlag());
    }

    @Test
    public void test_LDX_DoesNotSetZeroFlagIfResultNotZero() {
        bus.loadProgram(0xa2, 0x12);  // LDX #$12
        cpu.step();
        assertFalse(cpu.getZeroFlag());
    }

    @Test
    public void test_LDX_SetsNegativeFlagIfResultIsNegative() {
        bus.loadProgram(0xa2, 0x80);  // LDX #$80
        cpu.step();
        assertTrue(cpu.getNegativeFlag());
    }

    @Test
    public void test_LDX_DoesNotSetNegativeFlagIfResultNotNegative() {
        bus.loadProgram(0xa2, 0x7f);  // LDX #$7F
        cpu.step();
        assertFalse(cpu.getNegativeFlag());
    }

    /* LDA Immediate Mode Tests - 0xa9 */
    @Test
    public void test_LDA_SetsAccumulator() {
        bus.loadProgram(0xa9, 0x12);  // LDA #$12
        cpu.step();
        assertEquals(0x12, cpu.getAccumulator());
    }

    @Test
    public void test_LDA_SetsZeroFlagIfResultIsZero() {
        bus.loadProgram(0xa9, 0x00);  // LDA #$00
        cpu.step();
        assertTrue(cpu.getZeroFlag());
    }

    @Test
    public void test_LDA_DoesNotSetZeroFlagIfResultNotZero() {
        bus.loadProgram(0xa9, 0x12);  // LDA #$12
        cpu.step();
        assertFalse(cpu.getZeroFlag());
    }

    @Test
    public void test_LDA_SetsNegativeFlagIfResultIsNegative() {
        bus.loadProgram(0xa9, 0x80);  // LDA #$80
        cpu.step();
        assertTrue(cpu.getNegativeFlag());
    }

    @Test
    public void test_LDA_DoesNotSetNegativeFlagIfResultNotNegative() {
        bus.loadProgram(0xa9, 0x7f);  // LDA #$7F
        cpu.step();
        assertFalse(cpu.getNegativeFlag());
    }

    /* CPY Immediate Mode Tests - 0xc0 */
    @Test
    public void test_CPY_SetsZeroAndCarryFlagsIfNumbersSame() {
        bus.loadProgram(0xa0, 0x00,  // LDY #$00
                        0xc0, 0x00); // CPY #$00
        cpu.step(2);
        assertTrue(cpu.getCarryFlag());
        assertTrue(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());

        cpu.reset();
        bus.loadProgram(0xa0, 0x01,  // LDY #$01
                        0xc0, 0x01); // CPY #$01
        cpu.step(2);
        assertTrue(cpu.getCarryFlag());
        assertTrue(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());

        cpu.reset();
        bus.loadProgram(0xa0, 0x7f,   // LDY #$7F
                        0xc0, 0x7f);  // CPY #$7F
        cpu.step(2);
        assertTrue(cpu.getCarryFlag());
        assertTrue(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());

        cpu.reset();
        bus.loadProgram(0xa0, 0xFF,   // LDY #$FF
                        0xc0, 0xFF);  // CPY #$FF
        cpu.step(2);
        assertTrue(cpu.getCarryFlag());
        assertTrue(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
    }

    @Test
    public void test_CPY_SetsCarryFlagIfYGreaterThanMemory() {
        bus.loadProgram(0xa0, 0x0a,  // LDY #$0A
                        0xc0, 0x08); // CPY #$08
        cpu.step(2);
        assertTrue(cpu.getCarryFlag());
        assertFalse(cpu.getZeroFlag());
        // $0a - $08 = positive
        assertFalse(cpu.getNegativeFlag());

        cpu.reset();
        bus.loadProgram(0xa0, 0xfa,  // LDY #$FA
                        0xc0, 0x80); // CPY #$80
        cpu.step(2);
        assertTrue(cpu.getCarryFlag());
        assertFalse(cpu.getZeroFlag());
        // $fa - 80 = positive
        assertFalse(cpu.getNegativeFlag());
    }

    @Test
    public void test_CPY_DoesNotSetCarryFlagIfYLessThanThanMemory() {
        bus.loadProgram(0xa0, 0x08,  // LDY #$08
                        0xc0, 0x0a); // CPY #$0A
        cpu.step(2);
        assertFalse(cpu.getCarryFlag());
        assertFalse(cpu.getZeroFlag());
        // 08 - 0a = negative
        assertTrue(cpu.getNegativeFlag());

        cpu.reset();
        bus.loadProgram(0xa0, 0x70,  // LDY #$70
                        0xc0, 0x80); // CPY #$80
        cpu.step(2);
        assertFalse(cpu.getCarryFlag());
        assertFalse(cpu.getZeroFlag());
        // $70 - $80 = negative
        assertTrue(cpu.getNegativeFlag());
    }

    /* CMP Immediate Mode Tests - 0xc9 */
    @Test
    public void test_CMP_SetsZeroAndCarryFlagsIfNumbersSame() {
        bus.loadProgram(0xa9, 0x00,  // LDA #$00
                        0xc9, 0x00); // CMP #$00
        cpu.step(2);
        assertTrue(cpu.getCarryFlag());
        assertTrue(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());

        cpu.reset();
        bus.loadProgram(0xa9, 0x01,  // LDA #$01
                        0xc9, 0x01); // CMP #$01
        cpu.step(2);
        assertTrue(cpu.getCarryFlag());
        assertTrue(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());

        cpu.reset();
        bus.loadProgram(0xa9, 0x7f,   // LDA #$7F
                        0xc9, 0x7f);  // CMP #$7F
        cpu.step(2);
        assertTrue(cpu.getCarryFlag());
        assertTrue(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());

        cpu.reset();
        bus.loadProgram(0xa9, 0xFF,   // LDA #$FF
                        0xc9, 0xFF);  // CMP #$FF
        cpu.step(2);
        assertTrue(cpu.getCarryFlag());
        assertTrue(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
    }

    @Test
    public void test_CMP_SetsCarryFlagIfYGreaterThanMemory() {
        bus.loadProgram(0xa9, 0x0a,  // LDA #$0A
                        0xc9, 0x08); // CMP #$08
        cpu.step(2);
        assertTrue(cpu.getCarryFlag());
        assertFalse(cpu.getZeroFlag());
        // $0a - $08 = positive
        assertFalse(cpu.getNegativeFlag());

        cpu.reset();
        bus.loadProgram(0xa9, 0xfa,  // LDA #$FA
                        0xc9, 0x80); // CMP #$80
        cpu.step(2);
        assertTrue(cpu.getCarryFlag());
        assertFalse(cpu.getZeroFlag());
        // $fa - $80 = positive
        assertFalse(cpu.getNegativeFlag());
    }

    @Test
    public void test_CMP_DoesNotSetCarryFlagIfYGreaterThanMemory() {
        bus.loadProgram(0xa9, 0x08,  // LDA #$08
                        0xc9, 0x0a); // CMP #$0A
        cpu.step(2);
        assertFalse(cpu.getCarryFlag());
        assertFalse(cpu.getZeroFlag());
        // 08 - 0a = negative
        assertTrue(cpu.getNegativeFlag());

        cpu.reset();
        bus.loadProgram(0xa9, 0x70,  // LDA #$70
                        0xc9, 0x80); // CMP #$80
        cpu.step(2);
        assertFalse(cpu.getCarryFlag());
        assertFalse(cpu.getZeroFlag());
        // 70 - 80 = negative
        assertTrue(cpu.getNegativeFlag());
    }

    /* CPX Immediate Mode Tests - 0xe0 */
    @Test
    public void test_CPX_SetsZeroAndCarryFlagsIfNumbersSame() {
        bus.loadProgram(0xa2, 0x00,  // LDX #$00
                        0xe0, 0x00); // CPX #$00
        cpu.step(2);
        assertTrue(cpu.getCarryFlag());
        assertTrue(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());

        cpu.reset();
        bus.loadProgram(0xa2, 0x01,  // LDX #$01
                        0xe0, 0x01); // CPX #$01
        cpu.step(2);
        assertTrue(cpu.getCarryFlag());
        assertTrue(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());

        cpu.reset();
        bus.loadProgram(0xa2, 0x7f,   // LDX #$7F
                        0xe0, 0x7f);  // CPX #$7F
        cpu.step(2);
        assertTrue(cpu.getCarryFlag());
        assertTrue(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());

        cpu.reset();
        bus.loadProgram(0xa2, 0xFF,   // LDX #$FF
                        0xe0, 0xFF);  // CPX #$FF
        cpu.step(2);
        assertTrue(cpu.getCarryFlag());
        assertTrue(cpu.getZeroFlag());
        assertFalse(cpu.getNegativeFlag());
    }

    @Test
    public void test_CPX_SetsCarryFlagIfYGreaterThanMemory() {
        bus.loadProgram(0xa2, 0x0a,  // LDX #$0A
                        0xe0, 0x08); // CPX #$08
        cpu.step(2);
        assertTrue(cpu.getCarryFlag());
        assertFalse(cpu.getZeroFlag());
        // $0a - $08 = positive
        assertFalse(cpu.getNegativeFlag());

        cpu.reset();
        bus.loadProgram(0xa2, 0xfa,  // LDX #$FA
                        0xe0, 0x80); // CPX #$80
        cpu.step(2);
        assertTrue(cpu.getCarryFlag());
        assertFalse(cpu.getZeroFlag());
        // $fa - $80 = positive
        assertFalse(cpu.getNegativeFlag());
    }

    @Test
    public void test_CPX_DoesNotSetCarryFlagIfYGreaterThanMemory() {
        bus.loadProgram(0xa2, 0x08,  // LDX #$08
                        0xe0, 0x0a); // CPX #$0A
        cpu.step(2);
        assertFalse(cpu.getCarryFlag());
        assertFalse(cpu.getZeroFlag());
        // $08 - $0a = negative
        assertTrue(cpu.getNegativeFlag());

        cpu.reset();
        bus.loadProgram(0xa2, 0x70,  // LDX #$70
                        0xe0, 0x80); // CMX #$80
        cpu.step(2);
        assertFalse(cpu.getCarryFlag());
        assertFalse(cpu.getZeroFlag());
        // $70 - $80 = negative
        assertTrue(cpu.getNegativeFlag());
    }

    /* SBC Immediate Mode Tests - 0xe9 */
    @Test
    public void test_SBC() {
        bus.loadProgram(0xa9, 0x00,  // LDA #$00
                        0xe9, 0x01); // SBC #$01
        cpu.step(2);
        assertEquals(0xfe, cpu.getAccumulator());
        assertTrue(cpu.getNegativeFlag());
        assertFalse(cpu.getOverflowFlag());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getCarryFlag());

        cpu.reset();
        bus.loadProgram(0xa9, 0x7f,  // LDA #$7f
                        0xe9, 0x01); // SBC #$01
        cpu.step(2);
        assertEquals(0x7d, cpu.getAccumulator());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getOverflowFlag());
        assertFalse(cpu.getZeroFlag());
        assertTrue(cpu.getCarryFlag());

        cpu.reset();
        bus.loadProgram(0xa9, 0x80,  // LDA #$80
                        0xe9, 0x01); // SBC #$01
        cpu.step(2);
        assertEquals(0x7e, cpu.getAccumulator());
        assertFalse(cpu.getNegativeFlag());
        assertTrue(cpu.getOverflowFlag());
        assertFalse(cpu.getZeroFlag());
        assertTrue(cpu.getCarryFlag());

        cpu.reset();
        bus.loadProgram(0xa9, 0xff,  // LDA #$ff
                        0xe9, 0x01); // SBC #$01
        cpu.step(2);
        assertEquals(0xfd, cpu.getAccumulator());
        assertTrue(cpu.getNegativeFlag());
        assertFalse(cpu.getOverflowFlag());
        assertFalse(cpu.getZeroFlag());
        assertTrue(cpu.getCarryFlag());

        cpu.reset();
        bus.loadProgram(0xa9, 0x02,  // LDA #$02
                        0xe9, 0x01); // SBC #$01
        cpu.step(2);
        assertEquals(0x00, cpu.getAccumulator());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getOverflowFlag());
        assertTrue(cpu.getZeroFlag());
        assertTrue(cpu.getCarryFlag());
    }

    @Test
    public void test_SBC_IncludesNotOfCarry() {
        // Subtrace with Carry Flag cleared
        bus.loadProgram(0x18,        // CLC
                        0xa9, 0x05,  // LDA #$00
                        0xe9, 0x01); // SBC #$01

        cpu.step(3);
        assertEquals(0x03, cpu.getAccumulator());

        cpu.reset();

        // Subtrace with Carry Flag cleared
        bus.loadProgram(0x18,        // CLC
                        0xa9, 0x00,  // LDA #$00
                        0xe9, 0x01); // SBC #$01

        cpu.step(3);
        assertEquals(0xfe, cpu.getAccumulator());

        cpu.reset();

        // Subtract with Carry Flag set
        bus.loadProgram(0x38,        // SEC
                        0xa9, 0x05,  // LDA #$00
                        0xe9, 0x01); // SBC #$01
        cpu.step(3);
        assertEquals(0x04, cpu.getAccumulator());
        assertTrue(cpu.getCarryFlag());

        cpu.reset();

        // Subtract with Carry Flag set
        bus.loadProgram(0x38,        // SEC
                        0xa9, 0x00,  // LDA #$00
                        0xe9, 0x01); // SBC #$01
        cpu.step(3);
        assertEquals(0xff, cpu.getAccumulator());
        assertFalse(cpu.getCarryFlag());

    }

    @Test
    public void test_SBC_DecimalMode() {
        bus.loadProgram(0xf8,
                        0xa9, 0x00,
                        0xe9, 0x01);
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
                        0xe9, 0x01);
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
                        0xe9, 0x01);
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
                        0xe9, 0x01);  // SBC #$01
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
                        0xe9, 0x11);  // SBC #$11
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
                        0xe9, 0x01);  // SBC #$01
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
                        0xe9, 0x01);  // SBC #$01
        cpu.step(4);
        assertEquals(0x99, cpu.getAccumulator());
        assertFalse(cpu.getCarryFlag());
        assertFalse(cpu.getNegativeFlag());
        assertFalse(cpu.getOverflowFlag());
        assertFalse(cpu.getZeroFlag());
        assertTrue(cpu.getDecimalModeFlag());
    }
}
