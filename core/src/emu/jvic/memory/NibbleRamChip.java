package emu.jvic.memory;

/**
 * This class emulates a 4-bit RAM chip (nibble).
 *
 * @author Lance Ewing
 */
public class NibbleRamChip extends MemoryMappedChip {

  /**
   * Reads the value of the given memory address.
   *
   * @param address the address to read the byte from.
   *
   * @return the contents of the memory address.
   */
  public int readMemory(int address) {
    return mem[address];
  }

  /**
   * Writes a value to the given memory address.
   *
   * @param address the address to write the value to.
   * @param value the value to write to the given address.
   */
  public void writeMemory(int address, int value) {
    mem[address] = (value & 0x0F);
  }
}
