package emu.jvic.memory;

import emu.jvic.cpu.Cpu6502;
import emu.jvic.snap.Snapshot;

/**
 * This class emulators the memory of a 6502 machine.
 * 
 * @author Lance Ewing
 */
public abstract class Memory {
  
  /**
   * Holds the machines memory.
   */
  protected int mem[];

  /**
   * Holds an array of references to instances of MemoryMappedChip where each
   * instance determines the behaviour of reading or writing to the given memory
   * address.
   */
  protected MemoryMappedChip memoryMap[];
  
  /**
   * Constructor for Memory.
   * 
   * @param cpu The CPU that will access this Memory.
   * @param snapshot Optional snapshot of the machine state to start with.
   */
  public Memory(Cpu6502 cpu, Snapshot snapshot) {
    if (snapshot != null) {
      this.mem = snapshot.getMemoryArray();
    } else {
      this.mem = new int[65536];
    }
    this.memoryMap = new MemoryMappedChip[65536];
    cpu.setMemory(this);
  }
  
  /**
   * Maps the given chip instance at the given address range.
   * 
   * @param chip The chip to map at the given address range.
   * @param startAddress The start of the address range.
   * @param endAddress The end of the address range.
   */
  protected void mapChipToMemory(MemoryMappedChip chip, int startAddress, int endAddress) {
    mapChipToMemory(chip, startAddress, endAddress, null);
  }
  
  /**
   * Maps the given chip instance at the given address range, optionally loading the
   * given initial state data into that address range. This state data is intended to be
   * used for things such as ROM images (e.g. char, kernel, basic).
   * 
   * @param chip The chip to map at the given address range.
   * @param startAddress The start of the address range.
   * @param endAddress The end of the address range.
   * @param state byte array containing initial state (can be null).
   */
  protected void mapChipToMemory(MemoryMappedChip chip, int startAddress, int endAddress, byte[] state) {
    int statePos = 0;
    
    // Load the initial state into memory if provided.
    if (state != null) {
      for (int i=startAddress; i<=endAddress; i++) {
        mem[i] = (state[statePos++] & 0xFF);
      }
    }
    
    // Configure the chip into the memory map between the given start and end addresses.
    for (int i = startAddress; i <= endAddress; i++) {
      memoryMap[i] = chip;
    }

    chip.setMemory(this);
  }
  
  /**
   * Gets the int array that represents the VIC 20s memory.
   * 
   * @return an int array represents the VIC 20 memory.
   */
  public int[] getMemoryArray() {
    return mem;
  }

  /**
   * Gets the array of memory mapped devices. 
   * 
   * @return The array of memory mapped devices.
   */
  public MemoryMappedChip[] getMemoryMap() {
    return memoryMap;
  }
  
  /**
   * Reads the value of the given VIC 20 memory address.
   * 
   * @param address The address to read the byte from.
   * 
   * @return The contents of the memory address.
   */
  public int readMemory(int address) {
    return (memoryMap[address].readMemory(address));
  }

  /**
   * Writes a value to the give VIC 20 memory address.
   * 
   * @param address The address to write the value to.
   * @param value The value to write to the given address.
   */
  public void writeMemory(int address, int value) {
    memoryMap[address].writeMemory(address, value);
  }
  /**
   * Forces a write to a memory address, even if it is ROM. This is used mainly
   * for setting emulation traps.
   * 
   * @param address The address to write the value to.
   * @param value The value to write to the given address.
   */
  public void forceWrite(int address, int value) {
    mem[address] = value;
  }
}
