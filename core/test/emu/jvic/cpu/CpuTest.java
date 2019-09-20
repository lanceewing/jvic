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
 *
 */
public class CpuTest extends CpuBaseTestCase {

    @Test
    public void testReset() {
        assertEquals(0, cpu.getAccumulator());
        assertEquals(0, cpu.getXRegister());
        assertEquals(0, cpu.getYRegister());
        assertEquals(0x0200, cpu.getProgramCounter());
        assertFalse(cpu.getCarryFlag());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getIrqDisableFlag());
        assertFalse(cpu.getDecimalModeFlag());
        assertFalse(cpu.getOverflowFlag());
        assertFalse(cpu.getNegativeFlag());
    }

    @Test
    public void testStack() {

        cpu.stackPush(0x13);
        assertEquals(0x13, cpu.stackPop());

        cpu.stackPush(0x12);
        assertEquals(0x12, cpu.stackPop());

        for (int i = 0x00; i <= 0xff; i++) {
            cpu.stackPush(i);
        }

        for (int i = 0xff; i >= 0x00; i--) {
            assertEquals(i, cpu.stackPop());
        }

    }

    @Test
    public void testStackPush() {
        assertEquals(0xff, cpu.getStackPointer());
        assertEquals(0x00, bus.read(0x1ff, true));

        cpu.stackPush(0x06);
        assertEquals(0xfe, cpu.getStackPointer());
        assertEquals(0x06, bus.read(0x1ff, true));

        cpu.stackPush(0x05);
        assertEquals(0xfd, cpu.getStackPointer());
        assertEquals(0x06, bus.read(0x1ff, true));
        assertEquals(0x05, bus.read(0x1fe, true));

        cpu.stackPush(0x04);
        assertEquals(0xfc, cpu.getStackPointer());
        assertEquals(0x06, bus.read(0x1ff, true));
        assertEquals(0x05, bus.read(0x1fe, true));
        assertEquals(0x04, bus.read(0x1fd, true));

        cpu.stackPush(0x03);
        assertEquals(0xfb, cpu.getStackPointer());
        assertEquals(0x06, bus.read(0x1ff, true));
        assertEquals(0x05, bus.read(0x1fe, true));
        assertEquals(0x04, bus.read(0x1fd, true));
        assertEquals(0x03, bus.read(0x1fc, true));

        cpu.stackPush(0x02);
        assertEquals(0xfa, cpu.getStackPointer());
        assertEquals(0x06, bus.read(0x1ff, true));
        assertEquals(0x05, bus.read(0x1fe, true));
        assertEquals(0x04, bus.read(0x1fd, true));
        assertEquals(0x03, bus.read(0x1fc, true));
        assertEquals(0x02, bus.read(0x1fb, true));

        cpu.stackPush(0x01);
        assertEquals(0xf9, cpu.getStackPointer());
        assertEquals(0x06, bus.read(0x1ff, true));
        assertEquals(0x05, bus.read(0x1fe, true));
        assertEquals(0x04, bus.read(0x1fd, true));
        assertEquals(0x03, bus.read(0x1fc, true));
        assertEquals(0x02, bus.read(0x1fb, true));
        assertEquals(0x01, bus.read(0x1fa, true));
    }

    @Test
    public void testStackPushWrapsAroundToStackTop() {
        cpu.setStackPointer(0x01);

        cpu.stackPush(0x01);
        assertEquals(0x01, bus.read(0x101, true));
        assertEquals(0x00, cpu.getStackPointer());

        cpu.stackPush(0x02);
        assertEquals(0x02, bus.read(0x100, true));
        assertEquals(0xff, cpu.getStackPointer());

        cpu.stackPush(0x03);
        assertEquals(0x03, bus.read(0x1ff, true));
        assertEquals(0xfe, cpu.getStackPointer());
    }

    @Test
    public void testStackPop() {
        bus.write(0x1ff, 0x06);
        bus.write(0x1fe, 0x05);
        bus.write(0x1fd, 0x04);
        bus.write(0x1fc, 0x03);
        bus.write(0x1fb, 0x02);
        bus.write(0x1fa, 0x01);
        cpu.setStackPointer(0xf9);

        assertEquals(0x01, cpu.stackPop());
        assertEquals(0xfa, cpu.getStackPointer());

        assertEquals(0x02, cpu.stackPop());
        assertEquals(0xfb, cpu.getStackPointer());

        assertEquals(0x03, cpu.stackPop());
        assertEquals(0xfc, cpu.getStackPointer());

        assertEquals(0x04, cpu.stackPop());
        assertEquals(0xfd, cpu.getStackPointer());

        assertEquals(0x05, cpu.stackPop());
        assertEquals(0xfe, cpu.getStackPointer());

        assertEquals(0x06, cpu.stackPop());
        assertEquals(0xff, cpu.getStackPointer());
    }

    @Test
    public void testStackPopWrapsAroundToStackBottom() {
        bus.write(0x1ff, 0x0f); // top of stack
        bus.write(0x100, 0xf0); // bottom of stack
        bus.write(0x101, 0xf1);
        bus.write(0x102, 0xf2);

        cpu.setStackPointer(0xfe);

        assertEquals(0x0f, cpu.stackPop());
        assertEquals(0xff, cpu.getStackPointer());

        assertEquals(0xf0, cpu.stackPop());
        assertEquals(0x00, cpu.getStackPointer());

        assertEquals(0xf1, cpu.stackPop());
        assertEquals(0x01, cpu.getStackPointer());

        assertEquals(0xf2, cpu.stackPop());
        assertEquals(0x02, cpu.getStackPointer());
    }

    @Test
    public void testStackPeekDoesNotAlterStackPointer() {
        assertEquals(0x00, cpu.stackPeek());
        assertEquals(0xff, cpu.getStackPointer());

        cpu.stackPush(0x01);
        assertEquals(0x01, cpu.stackPeek());
        assertEquals(0xfe, cpu.getStackPointer());

        cpu.stackPush(0x02);
        assertEquals(0x02, cpu.stackPeek());
        assertEquals(0xfd, cpu.getStackPointer());

        cpu.stackPush(0x03);
        assertEquals(0x03, cpu.stackPeek());
        assertEquals(0xfc, cpu.getStackPointer());

        cpu.stackPush(0x04);
        assertEquals(0x04, cpu.stackPeek());
        assertEquals(0xfb, cpu.getStackPointer());
        assertEquals(0x04, cpu.stackPeek());
        assertEquals(0xfb, cpu.getStackPointer());
        assertEquals(0x04, cpu.stackPeek());
        assertEquals(0xfb, cpu.getStackPointer());
    }

    @Test
    public void testGetProcessorStatus() {
        // By default, no flags are set.  Remember, bit 5
        // is always '1'.
        assertEquals(0x20, cpu.getProcessorStatus());
        cpu.setCarryFlag();
        assertEquals(0x21, cpu.getProcessorStatus());
        cpu.setZeroFlag();
        assertEquals(0x23, cpu.getProcessorStatus());
        cpu.setIrqDisableFlag();
        assertEquals(0x27, cpu.getProcessorStatus());
        cpu.setDecimalModeFlag();
        assertEquals(0x2f, cpu.getProcessorStatus());
        cpu.setOverflowFlag();
        assertEquals(0x6f, cpu.getProcessorStatus());
        cpu.setNegativeFlag();
        assertEquals(0xEf, cpu.getProcessorStatus());

        cpu.clearCarryFlag();
        assertEquals(0xEe, cpu.getProcessorStatus());
        cpu.clearZeroFlag();
        assertEquals(0xEc, cpu.getProcessorStatus());
        cpu.clearIrqDisableFlag();
        assertEquals(0xE8, cpu.getProcessorStatus());
        cpu.clearDecimalModeFlag();
        assertEquals(0xE0, cpu.getProcessorStatus());
        cpu.clearOverflowFlag();
        assertEquals(0xa0, cpu.getProcessorStatus());
        cpu.clearNegativeFlag();
        assertEquals(0x20, cpu.getProcessorStatus());
    }

    @Test
    public void testSetProcessorStatus() {
        // Default
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getIrqDisableFlag());
        assertFalse(cpu.getDecimalModeFlag());
        assertFalse(cpu.getOverflowFlag());
        assertFalse(cpu.getNegativeFlag());

        cpu.setProcessorStatus(0x20 | Cpu.P_CARRY);

        assertTrue(cpu.getCarryFlag());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getIrqDisableFlag());
        assertFalse(cpu.getDecimalModeFlag());
        assertFalse(cpu.getOverflowFlag());
        assertFalse(cpu.getNegativeFlag());

        cpu.setProcessorStatus(0x20 | Cpu.P_CARRY | Cpu.P_NEGATIVE);

        assertTrue(cpu.getCarryFlag());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getIrqDisableFlag());
        assertFalse(cpu.getDecimalModeFlag());
        assertFalse(cpu.getOverflowFlag());
        assertTrue(cpu.getNegativeFlag());

        cpu.setProcessorStatus(0x20 | Cpu.P_CARRY | Cpu.P_NEGATIVE | Cpu.P_ZERO);

        assertTrue(cpu.getCarryFlag());
        assertTrue(cpu.getZeroFlag());
        assertFalse(cpu.getIrqDisableFlag());
        assertFalse(cpu.getDecimalModeFlag());
        assertFalse(cpu.getOverflowFlag());
        assertTrue(cpu.getNegativeFlag());

        cpu.setProcessorStatus(0x20 | Cpu.P_CARRY | Cpu.P_NEGATIVE | Cpu.P_ZERO |
                               Cpu.P_OVERFLOW);

        assertTrue(cpu.getCarryFlag());
        assertTrue(cpu.getZeroFlag());
        assertFalse(cpu.getIrqDisableFlag());
        assertFalse(cpu.getDecimalModeFlag());
        assertTrue(cpu.getOverflowFlag());
        assertTrue(cpu.getNegativeFlag());

        cpu.setProcessorStatus(0x20 | Cpu.P_CARRY | Cpu.P_NEGATIVE | Cpu.P_ZERO |
                               Cpu.P_OVERFLOW | Cpu.P_BREAK);

        assertTrue(cpu.getCarryFlag());
        assertTrue(cpu.getZeroFlag());
        assertFalse(cpu.getIrqDisableFlag());
        assertFalse(cpu.getDecimalModeFlag());
        assertTrue(cpu.getOverflowFlag());
        assertTrue(cpu.getNegativeFlag());


        cpu.setProcessorStatus(0x20 | Cpu.P_CARRY | Cpu.P_NEGATIVE | Cpu.P_ZERO |
                               Cpu.P_OVERFLOW | Cpu.P_BREAK | Cpu.P_DECIMAL);

        assertTrue(cpu.getCarryFlag());
        assertTrue(cpu.getZeroFlag());
        assertFalse(cpu.getIrqDisableFlag());
        assertTrue(cpu.getDecimalModeFlag());
        assertTrue(cpu.getOverflowFlag());
        assertTrue(cpu.getNegativeFlag());

        cpu.setProcessorStatus(0x20 | Cpu.P_CARRY | Cpu.P_NEGATIVE | Cpu.P_ZERO |
                               Cpu.P_OVERFLOW | Cpu.P_BREAK | Cpu.P_DECIMAL |
                               Cpu.P_IRQ_DISABLE);

        assertTrue(cpu.getCarryFlag());
        assertTrue(cpu.getZeroFlag());
        assertTrue(cpu.getIrqDisableFlag());
        assertTrue(cpu.getDecimalModeFlag());
        assertTrue(cpu.getOverflowFlag());
        assertTrue(cpu.getNegativeFlag());

        cpu.setProcessorStatus(0x20);

        assertFalse(cpu.getCarryFlag());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getIrqDisableFlag());
        assertFalse(cpu.getDecimalModeFlag());
        assertFalse(cpu.getOverflowFlag());
        assertFalse(cpu.getNegativeFlag());

        cpu.setProcessorStatus(0x00);

        assertFalse(cpu.getCarryFlag());
        assertFalse(cpu.getZeroFlag());
        assertFalse(cpu.getIrqDisableFlag());
        assertFalse(cpu.getDecimalModeFlag());
        assertFalse(cpu.getOverflowFlag());
        assertFalse(cpu.getNegativeFlag());
    }

    @Test
    public void testIrq() throws Exception {
        // Ensure the IRQ disable flag is cleared
        cpu.clearIrqDisableFlag();

        // Set the IRQ vector
        bus.write(0xffff, 0x12);
        bus.write(0xfffe, 0x34);

        // Create an IRQ handler at 0x1234
        cpu.setProgramCounter(0x1234);
        bus.loadProgram(false,
                        0xa9, 0x33,  // LDA #$33
                        0x69, 0x01); // ADC #$01

        cpu.setProgramCounter(0x0200);
        // Create a little program at 0x0200
        bus.loadProgram(0x18,        // CLC
                        0xa9, 0x01,  // LDA #$00
                        0x69, 0x01); // ADC #$01

        cpu.step();
        assertEquals(0x0201, cpu.getProgramCounter() - 1); // First instruction executed.
        assertEquals(0x00, cpu.getAccumulator());

        cpu.assertIrq();  // Opcode for next instruction already fetched, so IRQ will wait until after the following step.
        cpu.step();
        assertEquals(0x0203, cpu.getProgramCounter());  // NMI steps now loaded, so PC isn't +1 like it normally is.
        assertEquals(0x01, cpu.getAccumulator());

        cpu.step();
        assertTrue(cpu.getIrqDisableFlag()); // Should have been set by the IRQ
        assertEquals(0x1234, cpu.getProgramCounter() - 1);
        cpu.step();
        assertEquals(0x33, cpu.getAccumulator());

        // TODO: I think this check is wrong. IRQ doesn't automatically go high.
        // Be sure that the IRQ line is no longer held low
        //assertFalse(cpu.isIrqAsserted());
    }

    @Test
    public void testIrqDoesNotSetBRK() throws Exception {
        // Ensure the IRQ disable flag is cleared
        cpu.clearIrqDisableFlag();

        // Set the IRQ vector
        bus.write(0xffff, 0x12);
        bus.write(0xfffe, 0x34);

        // Create an IRQ handler at 0x1234
        cpu.setProgramCounter(0x1234);
        bus.loadProgram(false,
                        0xa9, 0x33,  // LDA #$33
                        0x69, 0x01); // ADC #$01

        cpu.setProgramCounter(0x0200);
        // Create a little program at 0x0200
        bus.loadProgram(0x18,        // CLC
                        0xa9, 0x01,  // LDA #$00
                        0x69, 0x01); // ADC #$01

        cpu.step();
        assertEquals(0x0201, cpu.getProgramCounter() - 1); // First instruction executed.
        assertEquals(0x00, cpu.getAccumulator());

        cpu.assertIrq();  // This won't take affect until after the next instruction, because the opcode is already loaded by above step.
        
        cpu.step();
        assertEquals(0x0203, cpu.getProgramCounter());  // IRQ is in effect, so PC is not +1 like it normally is.
        assertEquals(0x01, cpu.getAccumulator());

        cpu.step();
        assertTrue(cpu.getIrqDisableFlag()); // Should have been set by the IRQ
    }

    @Test
    public void testIrqHonorsIrqDisabledFlag() throws Exception {
        // Ensure the IRQ disable flag is set
        cpu.setIrqDisableFlag();

        // Set the IRQ vector
        bus.write(0xffff, 0x12);
        bus.write(0xfffe, 0x34);

        // Create an IRQ handler at 0x1234
        cpu.setProgramCounter(0x1234);
        bus.loadProgram(false,
                        0xa9, 0x33,  // LDA #$33
                        0x69, 0x01); // ADC #$01

        cpu.setProgramCounter(0x0200);
        // Create a little program at 0x0200
        bus.loadProgram(0x18,        // CLC
                        0xa9, 0x01,  // LDA #$00
                        0x69, 0x01); // ADC #$01

        cpu.step();
        assertEquals(0x0201, cpu.getProgramCounter() - 1); // First instruction executed.
        assertEquals(0x00, cpu.getAccumulator());

        cpu.assertIrq(); // Should be ignored, because the disable flag is set.
        
        cpu.step();
        assertEquals(0x0203, cpu.getProgramCounter() - 1);
        assertEquals(0x01, cpu.getAccumulator());

        cpu.step();
        assertTrue(cpu.getIrqDisableFlag()); // Should have been left alone.
        assertEquals(0x0205, cpu.getProgramCounter() - 1);
        assertEquals(0x02, cpu.getAccumulator());
    }

    @Test
    public void testIrqPushesCorrectReturnAddressOntoStack() throws Exception {
        // Ensure the IRQs are enabled
        cpu.clearIrqDisableFlag();

        // Set the IRQ vector
        bus.write(0xffff, 0x10);
        bus.write(0xfffe, 0x00);

        // Create an IRQ handler at 0x1000 that just RTIs.
        cpu.setProgramCounter(0x1000);
        bus.loadProgram(false, 0xea, 0xea, 0x40); // NOP, NOP, RTI

        cpu.setProgramCounter(0x0200);

        // Create a little program at 0x0200 with three instruction sizes.
        bus.loadProgram(0x18,               // CLC
                        0xa9, 0x01,         // LDA #$01
                        0x6d, 0x06, 0x02,   // ADC $0207
                        0x00,               // BRK
                        0x03);              // $03 (data @ $0206)

        cpu.assertIrq();
        
        cpu.step(); // CLC
        assertEquals(0x0201, cpu.getProgramCounter()); // First instruction executed.
        assertEquals(0x00,   cpu.getAccumulator());

        cpu.step(); // IRQ (delayed because CLC opcode was already loaded by the loadProgram call).
        cpu.step(); // NOP
        assertEquals(0x1001, cpu.getProgramCounter() - 1);
        cpu.step(); // NOP
        assertEquals(0x1002, cpu.getProgramCounter() - 1);
        cpu.clearIrq();   // We need to manually clear this, because for IRQ, the CPU doesn't do it.
        cpu.step(); // RTI: PC -> 0x0201
        assertEquals(0x0201, cpu.getProgramCounter() - 1);
        
        cpu.assertIrq();
        
        cpu.step(); // LDA $#01
        assertEquals(0x0203, cpu.getProgramCounter());

        cpu.step(); // IRQ
        cpu.step(); // NOP
        assertEquals(0x1001, cpu.getProgramCounter() - 1);
        cpu.step(); // NOP
        assertEquals(0x1002, cpu.getProgramCounter() - 1);
        cpu.step(); // RTI: PC -> 0x0203
        assertEquals(0x0203, cpu.getProgramCounter());
    }

    @Test
    public void testNmi() throws Exception {
        // Set the NMI vector to 0x1000
        bus.write(0xfffb, 0x10);
        bus.write(0xfffa, 0x00);

        // Create an NMI handler at 0x1000
        cpu.setProgramCounter(0x1000);
        bus.loadProgram(false,
                        0xa9, 0x33,  // LDA #$33
                        0x69, 0x01); // ADC #$01

        // Create a little program at 0x0200
        cpu.setProgramCounter(0x0200);
        bus.loadProgram(0x18,        // CLC
                        0xa9, 0x01,  // LDA #$00
                        0x69, 0x01); // ADC #$01

        cpu.step();
        assertEquals(0x0201, cpu.getProgramCounter() - 1); // First instruction executed.
        assertEquals(0x00, cpu.getAccumulator());

        cpu.assertNmi();
        
        cpu.step();
        assertEquals(0x0203, cpu.getProgramCounter());
        assertEquals(0x01, cpu.getAccumulator());

        cpu.step();
        assertTrue(cpu.getIrqDisableFlag()); // Should have been set by the NMI
        assertEquals(0x1000, cpu.getProgramCounter() - 1);
        cpu.step();
        assertEquals(0x33, cpu.getAccumulator());

        // Be sure that the NMI line is no longer held low
        assertFalse(cpu.isNmiAsserted());
    }

    @Test
    public void testNmiDoesNotSetBRK() throws Exception {
        // Set the NMI vector to 0x1000
        bus.write(0xfffb, 0x10);
        bus.write(0xfffa, 0x00);

        // Create an NMI handler at 0x1000
        cpu.setProgramCounter(0x1000);
        bus.loadProgram(false,
                        0xa9, 0x33,  // LDA #$33
                        0x69, 0x01); // ADC #$01

        // Create a little program at 0x0200
        cpu.setProgramCounter(0x0200);
        bus.loadProgram(0x18,        // CLC
                        0xa9, 0x01,  // LDA #$00
                        0x69, 0x01); // ADC #$01

        cpu.step();
        assertEquals(0x0201, cpu.getProgramCounter() - 1); // First instruction executed (opcode fetch has already happened, so - 1)
        assertEquals(0x00, cpu.getAccumulator());

        cpu.step();
        assertEquals(0x0203, cpu.getProgramCounter() - 1); // opcode fetch has already happened, so - 1
        assertEquals(0x01, cpu.getAccumulator());

        cpu.assertNmi();

        cpu.step();
    }

    @Test
    public void testNmiIgnoresIrqDisableFlag() throws Exception {
        // Set the IRQ disable flag, which should be ignored by the NMI
        cpu.setIrqDisableFlag();

        // Set the NMI vector to 0x1000
        bus.write(0xfffb, 0x10);
        bus.write(0xfffa, 0x00);

        // Create an NMI handler at 0x1000
        cpu.setProgramCounter(0x1000);
        bus.loadProgram(false, 
                        0xa9, 0x33,  // LDA #$33
                        0x69, 0x01); // ADC #$01

        // Create a little program at 0x0200
        cpu.setProgramCounter(0x0200);
        bus.loadProgram(0x18,        // CLC
                        0xa9, 0x01,  // LDA #$00
                        0x69, 0x01); // ADC #$01

        cpu.step();
        assertEquals(0x0201, cpu.getProgramCounter() - 1); // First instruction executed (opcode fetch has already happened, so - 1).
        assertEquals(0x00, cpu.getAccumulator());

        cpu.assertNmi(); // Next opcode is already fetched, so NMI has to wait till next instruction.
        
        cpu.step();
        assertEquals(0x0203, cpu.getProgramCounter()); // NMI steps already loaded, so PC is not +1 like it normally is.
        assertEquals(0x01, cpu.getAccumulator());

        cpu.step();  // Step the NMI
        assertTrue(cpu.getIrqDisableFlag()); // Should have been set by the NMI
        assertEquals(0x1000, cpu.getProgramCounter() - 1);
        cpu.step();
        assertEquals(0x33, cpu.getAccumulator());

        // Be sure that the NMI line is no longer held low
        assertFalse(cpu.isNmiAsserted());
    }

    @Test
    public void testAddress() {
        assertEquals(0xf1ea, address(0xea, 0xf1));
        assertEquals(0x00ea, address(0xea, 0x00));
        assertEquals(0xf100, address(0x00, 0xf1));
        assertEquals(0x1234, address(0x34, 0x12));
        assertEquals(0xffff, address(0xff, 0xff));
    }

    @Test
    public void testZpxAddress() {
        cpu.setXRegister(0x00);
        assertEquals(0x10, cpu.zpxAddress(0x10));
        cpu.setXRegister(0x10);
        assertEquals(0x20, cpu.zpxAddress(0x10));
        cpu.setXRegister(0x25);
        assertEquals(0x35, cpu.zpxAddress(0x10));
        cpu.setXRegister(0xf5);
        assertEquals(0x05, cpu.zpxAddress(0x10));

        cpu.setXRegister(0x00);
        assertEquals(0x80, cpu.zpxAddress(0x80));
        cpu.setXRegister(0x10);
        assertEquals(0x90, cpu.zpxAddress(0x80));
        cpu.setXRegister(0x25);
        assertEquals(0xa5, cpu.zpxAddress(0x80));
        cpu.setXRegister(0x95);
        assertEquals(0x15, cpu.zpxAddress(0x80));
    }

    @Test
    public void testZpyAddress() {
        cpu.setYRegister(0x00);
        assertEquals(0x10, cpu.zpyAddress(0x10));
        cpu.setYRegister(0x10);
        assertEquals(0x20, cpu.zpyAddress(0x10));
        cpu.setYRegister(0x25);
        assertEquals(0x35, cpu.zpyAddress(0x10));
        cpu.setYRegister(0xf5);
        assertEquals(0x05, cpu.zpyAddress(0x10));

        cpu.setYRegister(0x00);
        assertEquals(0x80, cpu.zpyAddress(0x80));
        cpu.setYRegister(0x10);
        assertEquals(0x90, cpu.zpyAddress(0x80));
        cpu.setYRegister(0x25);
        assertEquals(0xa5, cpu.zpyAddress(0x80));
        cpu.setYRegister(0x95);
        assertEquals(0x15, cpu.zpyAddress(0x80));
    }
}