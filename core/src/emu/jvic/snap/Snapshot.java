package emu.jvic.snap;

/**
 * Base class for all machine snapshot formats, e.g. PCVIC, S20, VICE, V20, etc. The
 * purpose of any subclass is to decode its format and populate the instance variables
 * shown below.
 * 
 * @author Lance Ewing
 */
public abstract class Snapshot {

  /**
   * Holds the machine's memory.
   */
  protected int mem[] = new int[65536];
  
  
  // 6502 state.
  
  /**
   * The accumulator.
   */
  protected int accumulator;
  
  /**
   * Index register X.
   */
  protected int indexRegisterX;

  /**
   * Index register Y.
   */
  protected int indexRegisterY;
  
  /**
   * Stack pointer.
   */
  protected int stackPointer;
  
  /**
   * Status register.
   */
  protected int processorStatusRegister;
  
  /**
   * Program counter.
   */
  protected int programCounter;
  
  
  // VIC 6560/6561 state.
  
  /**
   * Scanline (0..263(NTSC) or 0..311(PAL))
   */
  protected int scanLine;
  
  /**
   * Scan count (CPU cycle count within scan line, 0 = End of scan line, 129(NTSC) or 131(PAL) = Start of scan line).
   */
  protected int scanCount;

  
  // VIA #1 state
  
  protected int via1InterruptFlagRegister;
  protected int via1InterruptEnableRegister;
  protected int via1OutputRegisterB;
  protected int via1InputRegisterB;
  protected int via1OutputRegisterA;
  protected int via1InputRegisterA;
  protected int via1TimerStatus;
  protected int via1Timer2LatchLow;
  protected int via1Timer2CounterLow;
  
  // VIA #2 state
  
  protected int via2InterruptFlagRegister;
  protected int via2InterruptEnableRegister;
  protected int via2OutputRegisterB;
  protected int via2InputRegisterB;
  protected int via2OutputRegisterA;
  protected int via2InputRegisterA;
  protected int via2TimerStatus;
  protected int via2Timer2LatchLow;
  protected int via2Timer2CounterLow;

  // JVic doesn't use this, but some snapshots, such as PCVIC, provide this value.
  protected int nmiFlank;

  /**
   * The amount of RAM expansion. Supports various configurations.
   */
  protected int ramExpansion;

  public int getAccumulator() {
    return accumulator;
  }

  public void setAccumulator(int accumulator) {
    this.accumulator = accumulator;
  }

  public int getIndexRegisterX() {
    return indexRegisterX;
  }

  public void setIndexRegisterX(int indexRegisterX) {
    this.indexRegisterX = indexRegisterX;
  }

  public int getIndexRegisterY() {
    return indexRegisterY;
  }

  public void setIndexRegisterY(int indexRegisterY) {
    this.indexRegisterY = indexRegisterY;
  }

  public int getStackPointer() {
    return stackPointer;
  }

  public void setStackPointer(int stackPointer) {
    this.stackPointer = stackPointer;
  }

  public int getProcessorStatusRegister() {
    return processorStatusRegister;
  }

  public void setProcessorStatusRegister(int processorStatusRegister) {
    this.processorStatusRegister = processorStatusRegister;
  }

  public int getProgramCounter() {
    return programCounter;
  }

  public void setProgramCounter(int programCounter) {
    this.programCounter = programCounter;
  }

  public int getScanLine() {
    return scanLine;
  }

  public void setScanLine(int scanLine) {
    this.scanLine = scanLine;
  }

  public int getScanCount() {
    return scanCount;
  }

  public void setScanCount(int scanCount) {
    this.scanCount = scanCount;
  }

  public int getVia1InterruptFlagRegister() {
    return via1InterruptFlagRegister;
  }

  public void setVia1InterruptFlagRegister(int via1InterruptFlagRegister) {
    this.via1InterruptFlagRegister = via1InterruptFlagRegister;
  }

  public int getVia1InterruptEnableRegister() {
    return via1InterruptEnableRegister;
  }

  public void setVia1InterruptEnableRegister(int via1InterruptEnableRegister) {
    this.via1InterruptEnableRegister = via1InterruptEnableRegister;
  }

  public int getVia1OutputRegisterB() {
    return via1OutputRegisterB;
  }

  public void setVia1OutputRegisterB(int via1OutputRegisterB) {
    this.via1OutputRegisterB = via1OutputRegisterB;
  }

  public int getVia1InputRegisterB() {
    return via1InputRegisterB;
  }

  public void setVia1InputRegisterB(int via1InputRegisterB) {
    this.via1InputRegisterB = via1InputRegisterB;
  }

  public int getVia1OutputRegisterA() {
    return via1OutputRegisterA;
  }

  public void setVia1OutputRegisterA(int via1OutputRegisterA) {
    this.via1OutputRegisterA = via1OutputRegisterA;
  }

  public int getVia1InputRegisterA() {
    return via1InputRegisterA;
  }

  public void setVia1InputRegisterA(int via1InputRegisterA) {
    this.via1InputRegisterA = via1InputRegisterA;
  }

  public int getVia1TimerStatus() {
    return via1TimerStatus;
  }

  public void setVia1TimerStatus(int via1TimerStatus) {
    this.via1TimerStatus = via1TimerStatus;
  }

  public int getVia1Timer2LatchLow() {
    return via1Timer2LatchLow;
  }

  public void setVia1Timer2LatchLow(int via1Timer2LatchLow) {
    this.via1Timer2LatchLow = via1Timer2LatchLow;
  }

  public int getVia1Timer2CounterLow() {
    return via1Timer2CounterLow;
  }

  public void setVia1Timer2CounterLow(int via1Timer2CounterLow) {
    this.via1Timer2CounterLow = via1Timer2CounterLow;
  }

  public int getVia2InterruptFlagRegister() {
    return via2InterruptFlagRegister;
  }

  public void setVia2InterruptFlagRegister(int via2InterruptFlagRegister) {
    this.via2InterruptFlagRegister = via2InterruptFlagRegister;
  }

  public int getVia2InterruptEnableRegister() {
    return via2InterruptEnableRegister;
  }

  public void setVia2InterruptEnableRegister(int via2InterruptEnableRegister) {
    this.via2InterruptEnableRegister = via2InterruptEnableRegister;
  }

  public int getVia2OutputRegisterB() {
    return via2OutputRegisterB;
  }

  public void setVia2OutputRegisterB(int via2OutputRegisterB) {
    this.via2OutputRegisterB = via2OutputRegisterB;
  }

  public int getVia2InputRegisterB() {
    return via2InputRegisterB;
  }

  public void setVia2InputRegisterB(int via2InputRegisterB) {
    this.via2InputRegisterB = via2InputRegisterB;
  }

  public int getVia2OutputRegisterA() {
    return via2OutputRegisterA;
  }

  public void setVia2OutputRegisterA(int via2OutputRegisterA) {
    this.via2OutputRegisterA = via2OutputRegisterA;
  }

  public int getVia2InputRegisterA() {
    return via2InputRegisterA;
  }

  public void setVia2InputRegisterA(int via2InputRegisterA) {
    this.via2InputRegisterA = via2InputRegisterA;
  }

  public int getVia2TimerStatus() {
    return via2TimerStatus;
  }

  public void setVia2TimerStatus(int via2TimerStatus) {
    this.via2TimerStatus = via2TimerStatus;
  }

  public int getVia2Timer2LatchLow() {
    return via2Timer2LatchLow;
  }

  public void setVia2Timer2LatchLow(int via2Timer2LatchLow) {
    this.via2Timer2LatchLow = via2Timer2LatchLow;
  }

  public int getVia2Timer2CounterLow() {
    return via2Timer2CounterLow;
  }

  public void setVia2Timer2CounterLow(int via2Timer2CounterLow) {
    this.via2Timer2CounterLow = via2Timer2CounterLow;
  }

  public int getNmiFlank() {
    return nmiFlank;
  }

  public void setNmiFlank(int nmiFlank) {
    this.nmiFlank = nmiFlank;
  }

  public int getRamExpansion() {
    return ramExpansion;
  }

  public void setRamExpansion(int ramExpansion) {
    this.ramExpansion = ramExpansion;
  }

  public int[] getMemoryArray() {
    return mem;
  }

  public void setMemoryArray(int[] mem) {
    this.mem = mem;
  }
}
