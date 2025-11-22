package emu.jvic;

import emu.jvic.memory.*;

/**
 * This is the base class of all chips.
 *
 * @author Lance Ewing
 */
public abstract class BaseChip {

    /**
     * Holds a reference to the machine's memory map.
     */
    protected Memory memory;

    /**
     * Holds a direct reference to the int array holding the machine's memory. This
     * is often used for faster reading and writing when it is safe to do so.
     */
    protected int mem[];

    /**
     * Holds an array of references to instances of MemoryMappedChip where each
     * instance determines the behaviour of reading or writing to the given memory
     * address.
     */
    protected MemoryMappedChip memoryMap[];

    /**
     * Sets a reference to the VIC 20 memory map.
     * 
     * @param memory The VIC 20 memory map.
     */
    public void setMemory(Memory memory) {
        this.memory = memory;
        this.mem = memory.getMemoryArray();
        this.memoryMap = memory.getMemoryMap();
    }
}
