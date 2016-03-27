package emu.jvic.memory;

import static emu.jvic.memory.Memory.*;

/**
 * Enum representing the supported RAM configuration types.
 * 
 * @author Lance Ewing
 */
public enum RamType {

  RAM_UNEXPANDED(0), 
  RAM_3K(RAM_1 | RAM_2 | RAM_3),
  RAM_8K(BLK_1),
  RAM_16K(BLK_1 | BLK_2),
  RAM_24K(BLK_1 | BLK_2 | BLK_3),
  RAM_32K(BLK_1 | BLK_2 | BLK_3 | BLK_5),
  RAM_35K(RAM_1 | RAM_2 | RAM_3 | BLK_1 | BLK_2 | BLK_3 | BLK_5),
  RAM_AUTO(0xFF);

  /**
   * The configuration of where RAM exists within the memory map.
   */
  private int ramPlacement;
  
  /**
   * Constructor for RamExpansionType.
   * 
   * @param ramPlacement The configuration of where RAM exists within the memory map.
   */
  RamType(int ramPlacement) {
    this.ramPlacement = ramPlacement;
  }
  
  public int getRamPlacement() {
    return ramPlacement;
  }
}
