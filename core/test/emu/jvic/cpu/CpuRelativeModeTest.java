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

public class CpuRelativeModeTest extends CpuBaseTestCase {

  /*
   * The following opcodes are tested for correctness in this file:
   *
   * BPL - Branch if Positive          - 0x10
   * BMI - Branch if Minus             - 0x30
   * BVC - Branch if Overflow Clear    - 0x50
   * BVS - Branch if Overflow Set      - 0x70
   * BCC - Branch if Carry Clear       - 0x90
   * BCS - Branch if Carry Set         - 0xb0
   * BNE - Branch if Not Equal to Zero - 0xd0
   * BEQ - Branch if Equal to Zero     - 0xf0
   *
   */

  /* BPL - Branch if Positive          - 0x10 */
    @Test
    public void test_BPL() {
        // Positive Offset
        bus.loadProgram(0x10, 0x05);  // BPL $05 ; *=$0202+$05 ($0207)
        cpu.setNegativeFlag();
        cpu.step();
        assertEquals(0x202, cpu.getProgramCounter() - 1);  // Due to pipelining-like behaviour, we've already move on one due to opcode fetch.

        cpu.reset();
        bus.loadProgram(0x10, 0x05);  // BPL $05 ; *=$0202+$05 ($0207)
        cpu.clearNegativeFlag();
        cpu.step();
        assertEquals(0x207, cpu.getProgramCounter() - 1);  // Due to pipelining-like behaviour, we've already move on one due to opcode fetch.

        // Negative Offset
        cpu.reset();
        bus.loadProgram(0x10, 0xfb);  // BPL $fb ; *=$0202-$05 ($01fd)
        cpu.setNegativeFlag();
        cpu.step();
        assertEquals(0x202, cpu.getProgramCounter() - 1);  // Due to pipelining-like behaviour, we've already move on one due to opcode fetch.

        cpu.reset();
        bus.loadProgram(0x10, 0xfb);  // BPL $fb ; *=$0202-$05 ($01fd)
        cpu.clearNegativeFlag();
        cpu.step();
        assertEquals(0x1fd, cpu.getProgramCounter() - 1);  // Due to pipelining-like behaviour, we've already move on one due to opcode fetch.
    }

  /* BMI - Branch if Minus             - 0x30 */
    @Test
    public void test_BMI() {
        // Positive Offset
        bus.loadProgram(0x30, 0x05);  // BMI $05 ; *=$0202+$05 ($0207)
        cpu.setNegativeFlag();
        cpu.step();
        assertEquals(0x207, cpu.getProgramCounter() - 1);  // Due to pipelining-like behaviour, we've already move on one due to opcode fetch.

        cpu.reset();
        bus.loadProgram(0x30, 0x05);  // BMI $05 ; *=$0202+$05 ($0207)
        cpu.clearNegativeFlag();
        cpu.step();
        assertEquals(0x202, cpu.getProgramCounter() - 1);  // Due to pipelining-like behaviour, we've already move on one due to opcode fetch.

        // Negative Offset
        cpu.reset();
        bus.loadProgram(0x30, 0xfb);  // BMI $fb ; *=$0202-$05 ($01fd)
        cpu.setNegativeFlag();
        cpu.step();
        assertEquals(0x1fd, cpu.getProgramCounter() - 1);  // Due to pipelining-like behaviour, we've already move on one due to opcode fetch.

        cpu.reset();
        bus.loadProgram(0x30, 0xfb);  // BMI $fb ; *=$0202-$05 ($01fd)
        cpu.clearNegativeFlag();
        cpu.step();
        assertEquals(0x202, cpu.getProgramCounter() - 1);  // Due to pipelining-like behaviour, we've already move on one due to opcode fetch.
    }

  /* BVC - Branch if Overflow Clear    - 0x50 */
    @Test
    public void test_BVC() {
        // Positive Offset
        bus.loadProgram(0x50, 0x05);  // BVC $05 ; *=$0202+$05 ($0207)
        cpu.setOverflowFlag();
        cpu.step();
        assertEquals(0x202, cpu.getProgramCounter() - 1);  // Due to pipelining-like behaviour, we've already move on one due to opcode fetch.

        cpu.reset();
        bus.loadProgram(0x50, 0x05);  // BVC $05 ; *=$0202+$05 ($0207)
        cpu.clearOverflowFlag();
        cpu.step();
        assertEquals(0x207, cpu.getProgramCounter() - 1);  // Due to pipelining-like behaviour, we've already move on one due to opcode fetch.

        // Negative Offset
        cpu.reset();
        bus.loadProgram(0x50, 0xfb);  // BVC $fb ; *=$0202-$05 ($01fd)
        cpu.setOverflowFlag();
        cpu.step();
        assertEquals(0x202, cpu.getProgramCounter() - 1);  // Due to pipelining-like behaviour, we've already move on one due to opcode fetch.

        cpu.reset();
        bus.loadProgram(0x50, 0xfb);  // BVC $fb ; *=$0202-$05 ($01fd)
        cpu.clearOverflowFlag();
        cpu.step();
        assertEquals(0x1fd, cpu.getProgramCounter() - 1);  // Due to pipelining-like behaviour, we've already move on one due to opcode fetch.
    }

  /* BVS - Branch if Overflow Set      - 0x70 */
    @Test
    public void test_BVS() {
        // Positive Offset
        bus.loadProgram(0x70, 0x05);  // BVS $05 ; *=$0202+$05 ($0207)
        cpu.setOverflowFlag();
        cpu.step();
        assertEquals(0x207, cpu.getProgramCounter() - 1);  // Due to pipelining-like behaviour, we've already move on one due to opcode fetch.

        cpu.reset();
        bus.loadProgram(0x70, 0x05);  // BVS $05 ; *=$0202+$05 ($0207)
        cpu.clearOverflowFlag();
        cpu.step();
        assertEquals(0x202, cpu.getProgramCounter() - 1);  // Due to pipelining-like behaviour, we've already move on one due to opcode fetch.

        // Negative Offset
        cpu.reset();
        bus.loadProgram(0x70, 0xfb);  // BVS $fb ; *=$0202-$05 ($01fd)
        cpu.setOverflowFlag();
        cpu.step();
        assertEquals(0x1fd, cpu.getProgramCounter() - 1);  // Due to pipelining-like behaviour, we've already move on one due to opcode fetch.

        cpu.reset();
        bus.loadProgram(0x70, 0xfb);  // BVS $fb ; *=$0202-$05 ($01fd)
        cpu.clearOverflowFlag();
        cpu.step();
        assertEquals(0x202, cpu.getProgramCounter() - 1);  // Due to pipelining-like behaviour, we've already move on one due to opcode fetch.
    }

  /* BCC - Branch if Carry Clear       - 0x90 */
    @Test
    public void test_BCC() {
        // Positive Offset
        bus.loadProgram(0x90, 0x05);  // BCC $05 ; *=$0202+$05 ($0207)
        cpu.setCarryFlag();
        cpu.step();
        assertEquals(0x202, cpu.getProgramCounter() - 1);  // Due to pipelining-like behaviour, we've already move on one due to opcode fetch.

        cpu.reset();
        bus.loadProgram(0x90, 0x05);  // BCC $05 ; *=$0202+$05 ($0207)
        cpu.clearCarryFlag();
        cpu.step();
        assertEquals(0x207, cpu.getProgramCounter() - 1);  // Due to pipelining-like behaviour, we've already move on one due to opcode fetch.

        // Negative Offset
        cpu.reset();
        bus.loadProgram(0x90, 0xfb);  // BCC $fb ; *=$0202-$05 ($01fd)
        cpu.setCarryFlag();
        cpu.step();
        assertEquals(0x202, cpu.getProgramCounter() - 1);  // Due to pipelining-like behaviour, we've already move on one due to opcode fetch.

        cpu.reset();
        bus.loadProgram(0x90, 0xfb);  // BCC $fb ; *=$0202-$05 ($01fd)
        cpu.clearCarryFlag();
        cpu.step();
        assertEquals(0x1fd, cpu.getProgramCounter() - 1);  // Due to pipelining-like behaviour, we've already move on one due to opcode fetch.
    }

  /* BCS - Branch if Carry Set         - 0xb0 */
    @Test
    public void test_BCS() {
        // Positive Offset
        bus.loadProgram(0xb0, 0x05);  // BCS $05 ; *=$0202+$05 ($0207)
        cpu.setCarryFlag();
        cpu.step();
        assertEquals(0x207, cpu.getProgramCounter() - 1);  // Due to pipelining-like behaviour, we've already move on one due to opcode fetch.

        cpu.reset();
        bus.loadProgram(0xb0, 0x05);  // BCS $05 ; *=$0202+$05 ($0207)
        cpu.clearCarryFlag();
        cpu.step();
        assertEquals(0x202, cpu.getProgramCounter() - 1);  // Due to pipelining-like behaviour, we've already move on one due to opcode fetch.

        // Negative Offset
        cpu.reset();
        bus.loadProgram(0xb0, 0xfb);  // BCS $fb ; *=$0202-$05 ($01fd)
        cpu.setCarryFlag();
        cpu.step();
        assertEquals(0x1fd, cpu.getProgramCounter() - 1);  // Due to pipelining-like behaviour, we've already move on one due to opcode fetch.

        cpu.reset();
        bus.loadProgram(0xb0, 0xfb);  // BCS $fb ; *=$0202-$05 ($01fd)
        cpu.clearCarryFlag();
        cpu.step();
        assertEquals(0x202, cpu.getProgramCounter() - 1);  // Due to pipelining-like behaviour, we've already move on one due to opcode fetch.
    }

  /* BNE - Branch if Not Equal to Zero - 0xd0 */
    @Test
    public void test_BNE() {
        // Positive Offset
        bus.loadProgram(0xd0, 0x05);  // BNE $05 ; *=$0202+$05 ($0207)
        cpu.setZeroFlag();
        cpu.step();
        assertEquals(0x202, cpu.getProgramCounter() - 1);  // Due to pipelining-like behaviour, we've already move on one due to opcode fetch.

        cpu.reset();
        bus.loadProgram(0xd0, 0x05);  // BNE $05 ; *=$0202+$05 ($0207)
        cpu.clearZeroFlag();
        cpu.step();
        assertEquals(0x207, cpu.getProgramCounter() - 1);  // Due to pipelining-like behaviour, we've already move on one due to opcode fetch.

        // Negative Offset
        cpu.reset();
        bus.loadProgram(0xd0, 0xfb);  // BNE $fb ; *=$0202-$05 ($01fd)
        cpu.setZeroFlag();
        cpu.step();
        assertEquals(0x202, cpu.getProgramCounter() - 1);  // Due to pipelining-like behaviour, we've already move on one due to opcode fetch.

        cpu.reset();
        bus.loadProgram(0xd0, 0xfb);  // BNE $fb ; *=$0202-$05 ($01fd)
        cpu.clearZeroFlag();
        cpu.step();
        assertEquals(0x1fd, cpu.getProgramCounter() - 1);  // Due to pipelining-like behaviour, we've already move on one due to opcode fetch.
    }

  /* BEQ - Branch if Equal to Zero     - 0xf0 */
    @Test
    public void test_BEQ() {
        // Positive Offset
        bus.loadProgram(0xf0, 0x05);  // BEQ $05 ; *=$0202+$05 ($0207)
        cpu.setZeroFlag();
        cpu.step();
        assertEquals(0x207, cpu.getProgramCounter() - 1);  // Due to pipelining-like behaviour, we've already move on one due to opcode fetch.

        cpu.reset();
        bus.loadProgram(0xf0, 0x05);  // BEQ $05 ; *=$0202+$05 ($0207)
        cpu.clearZeroFlag();
        cpu.step();
        assertEquals(0x202, cpu.getProgramCounter() - 1);  // Due to pipelining-like behaviour, we've already move on one due to opcode fetch.

        // Negative Offset
        cpu.reset();
        bus.loadProgram(0xf0, 0xfb);  // BEQ $fb ; *=$0202-$05 ($01fd)
        cpu.setZeroFlag();
        cpu.step();
        assertEquals(0x1fd, cpu.getProgramCounter() - 1);  // Due to pipelining-like behaviour, we've already move on one due to opcode fetch.

        cpu.reset();
        bus.loadProgram(0xf0, 0xfb);  // BEQ $fb ; *=$0202-$05 ($01fd)
        cpu.clearZeroFlag();
        cpu.step();
        assertEquals(0x202, cpu.getProgramCounter() - 1);  // Due to pipelining-like behaviour, we've already move on one due to opcode fetch.
    }

}