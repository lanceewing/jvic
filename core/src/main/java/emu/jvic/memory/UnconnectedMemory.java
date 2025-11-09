package emu.jvic.memory;

/**
 * This class emulates memory that is not connected to anything.
 *
 * @author Lance Ewing
 */
public class UnconnectedMemory extends MemoryMappedChip {

  /**
   * Reads the value of the given memory address.
   *
   * @param address the address to read the byte from.
   *
   * @return the contents of the memory address.
   */
  public int readMemory(int address) {
    int value = 0;
    
    // TODO: Need to determine how this value is established.
    //if (memory != null) {
    //    value = memory.cpu.getDataBusBuffer();
    //}
    
    return value;
  }

  /**
   * Writes a value to the given memory address.
   *
   * @param address the address to write the value to.
   * @param value the value to write to the given address.
   */
  public void writeMemory(int address, int value) {
    // Do nothing.
  }
}
