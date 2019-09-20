/*
 * Copyright (c) 2016 Seth J. Morabito <web@loomcom.com>
 * 
 * These 6502 CPU unit tests are a slightly modified version of Seth J. Morabito's
 * symon unit tests and have been invaluable for verifying the JOric Cpu6502 
 * implementation. Thank you Seth.
 */

package emu.jvic.cpu;

import org.junit.Before;

import emu.jvic.memory.Memory;
import static org.junit.Assert.*;

// TODO: Rename this to be more Cpu6502 specific once migration is complete.
public abstract class CpuBaseTestCase {

  protected Memory memory;

  /**
   * The Cpu6502 that the unit tests are testing.
   */
  protected Cpu6502 cpu6502;

  protected Cpu cpu;

  protected Bus bus;

  @Before
  public void setup() {
    bus = new Bus();
    cpu = new Cpu();
    cpu6502 = new Cpu6502(null);
    memory = new Memory(cpu6502, null, true);

    // RESET vector will be at 0x0200
    memory.writeMemory(0xFFFC, Bus.DEFAULT_LOAD_ADDRESS & 0xFF);
    memory.writeMemory(0xFFFD, (Bus.DEFAULT_LOAD_ADDRESS >> 8) & 0xFF);

    cpu6502.reset();
    cpu6502.setDebug(true);
    
    // Assert initial state
    assertEquals(0, cpu.getAccumulator());
    assertEquals(0, cpu.getXRegister());
    assertEquals(0, cpu.getYRegister());
    assertEquals(0x200, cpu.getProgramCounter());
    assertEquals(0xff, cpu.getStackPointer());
    assertEquals(0x20, cpu.getProcessorStatus());
  }

  /**
   * An adapter class that allows Symon unit tests to be run.
   */
  public class Cpu {

    /* Process status register mnemonics */
    public static final int P_CARRY       = 0x01;
    public static final int P_ZERO        = 0x02;
    public static final int P_IRQ_DISABLE = 0x04;
    public static final int P_DECIMAL     = 0x08;
    public static final int P_BREAK       = 0x10;
    // Bit 5 is always '1'
    public static final int P_OVERFLOW    = 0x40;
    public static final int P_NEGATIVE    = 0x80;
    
    public int getAccumulator() {
      return cpu6502.getAccumulator();
    }

    public void setAccumulator(int val) {
      cpu6502.setAccumulator(val);
    }

    public int getXRegister() {
      return cpu6502.getIndexRegisterX();
    }

    public void setXRegister(int val) {
      cpu6502.setIndexRegisterX(val);
    }

    public int getYRegister() {
      return cpu6502.getIndexRegisterY();
    }

    public void setYRegister(int val) {
      cpu6502.setIndexRegisterY(val);
    }

    public int getProgramCounter() {
      return cpu6502.getProgramCounter();
    }

    public void setProgramCounter(int addr) {
      cpu6502.setProgramCounter(addr);
    }

    public int getStackPointer() {
      return cpu6502.getStackPointer();
    }

    public void setStackPointer(int offset) {
      cpu6502.setStackPointer(offset);
    }

    public int getProcessorStatus() {
      return cpu6502.getProcessorStatus();
    }

    public void assertIrq() {
      cpu6502.setInterrupt(Cpu6502.S_IRQ);
    }

    public void clearIrq() {
      cpu6502.clearInterrupt(Cpu6502.S_IRQ);
    }

    public void assertNmi() {
      cpu6502.setInterrupt(Cpu6502.S_NMI);
    }

    public void clearNmi() {
      cpu6502.clearInterrupt(Cpu6502.S_NMI);
    }

    public void step() {
      cpu6502.step();
    }
    
    public void step(int numOfInstructions) {
      for (int i=0; i<numOfInstructions; i++) {
        cpu6502.step();
      }
    }

    public boolean getZeroFlag() {
      return cpu6502.getZeroResultFlag();
    }

    public boolean getNegativeFlag() {
      return cpu6502.getNegativeResultFlag();
    }

    public boolean getCarryFlag() {
      return cpu6502.getCarryFlag();
    }

    public boolean getOverflowFlag() {
      return cpu6502.getOverflowFlag();
    }

    public void reset() {
      cpu6502.reset();
    }

    public boolean getDecimalModeFlag() {
      return cpu6502.getDecimalModeFlag();
    }

    public void setCarryFlag() {
      cpu6502.setCarryFlag(true);
    }

    public void setOverflowFlag() {
      cpu6502.setOverflowFlag(true);
    }

    public void setNegativeFlag() {
      cpu6502.setNegativeResultFlag(true);
    }

    public void clearNegativeFlag() {
      cpu6502.setNegativeResultFlag(false);
    }

    public void clearOverflowFlag() {
      cpu6502.setOverflowFlag(false);
    }

    public void clearCarryFlag() {
      cpu6502.setCarryFlag(false);
    }

    public void setZeroFlag() {
      cpu6502.setZeroResultFlag(true);
    }

    public void clearZeroFlag() {
      cpu6502.setZeroResultFlag(false);
    }

    public boolean getIrqDisableFlag() {
      return cpu6502.getInterruptDisableFlag();
    }

    public void setIrqDisableFlag() {
      cpu6502.setInterruptDisableFlag(true);
    }

    public void setDecimalModeFlag() {
      cpu6502.setDecimalModeFlag(true);
    }

    public void setProcessorStatus(int value) {
      cpu6502.setProcessorStatus(value);
    }

    public void stackPush(int value) {
      cpu6502.stackPush(value);
    }

    public int stackPeek() {
      return cpu6502.stackPeek();
    }

    public int stackPop() {
      return cpu6502.stackPop();
    }

    public void clearDecimalModeFlag() {
      cpu6502.setDecimalModeFlag(false);
    }

    public void clearIrqDisableFlag() {
      cpu6502.setInterruptDisableFlag(false);
    }
    
    /**
     * Given a single byte, compute the Zero Page,X offset address.
     */
    int zpxAddress(int zp) {
      return (zp + cpu6502.getIndexRegisterX()) & 0xff;
    }

    /**
     * Given a single byte, compute the Zero Page,Y offset address.
     */
    int zpyAddress(int zp) {
      return (zp + cpu6502.getIndexRegisterY()) & 0xff;
    }

    public boolean isNmiAsserted() {
      return cpu6502.isNmiAsserted();
    }

    public boolean isIrqAsserted() {
      return cpu6502.isIrqAsserted();
    }
  }

  /**
   * An adapter class that allows Symon unit tests to be run.
   */
  public class Bus {

    // The default address at which to load programs
    public static final int DEFAULT_LOAD_ADDRESS = 0x0200;

    // By default, our bus starts at 0, and goes up to 64K
    private int startAddress = 0x0000;
    private int endAddress = 0xffff;

    public Bus() {
    }

    public Bus(int size) {
      this(0, size - 1);
    }

    public Bus(int startAddress, int endAddress) {
      this.startAddress = startAddress;
      this.endAddress = endAddress;
    }

    public int startAddress() {
      return startAddress;
    }

    public int endAddress() {
      return endAddress;
    }

    public int read(int address, boolean cpuAccess) {
      return memory.readMemory(address);
    }

    public void write(int address, int value) {
      memory.writeMemory(address, value);
    }

    public void assertIrq() {
      if (cpu != null) {
        cpu.assertIrq();
      }
    }

    public void clearIrq() {
      if (cpu != null) {
        cpu.clearIrq();
      }
    }

    public void assertNmi() {
      if (cpu != null) {
        cpu.assertNmi();
      }
    }

    public void clearNmi() {
      if (cpu != null) {
        cpu.clearNmi();
      }
    }

    public Cpu getCpu() {
      return cpu;
    }

    /**
     * Loads the given program bytes into memory starting at 0x0400. Designed to
     * be used once per test method, i.e. a test should use only one program,
     * that is unless the cpu is reset, which is perfectly fine.
     * 
     * @param data The program data to load into memory.
     */
    public void loadProgram(int... data) {
      loadProgram(true, data);
    }
    
    public void loadProgram(boolean prime, int... data) {
      int memPos = getCpu().getProgramCounter();
      for (int i = 0; i < data.length; i++) {
        memory.writeMemory(memPos++, data[i]);
      }
      
      // Emulate one cycle to force the first op code read. Normally, once started, the
      // transition between two instructions is a bit blurry. The next opcode is fetched
      // while the last instruction is still being executed. But for the very first
      // instruction, that didn't happen, so we're setting it up like it did.
      if (prime) {
        cpu6502.emulateCycle();
      }
    }
  }

  /**
   * Given two bytes, return an address.
   */
  public static int address(int lowByte, int hiByte) {
    return ((hiByte << 8) | lowByte) & 0xffff;
  }
}
