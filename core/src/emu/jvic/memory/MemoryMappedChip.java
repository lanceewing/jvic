package emu.jvic.memory;

import emu.jvic.BaseChip;

/**
 * This interface is used by chips that are memory mapped, such as RAM
 * chips, ROM chips, video chips, IO chips, etc.
 *
 * @author Lance Ewing
 */
public abstract class MemoryMappedChip extends BaseChip {

    /**
     * Reads the value of the given memory address.
     *
     * @param address the address to read the byte from.
     *
     * @return the contents of the memory address.
     */
    public abstract int readMemory(int address);

    /**
     * Writes a value to the given memory address.
     *
     * @param address the address to write the value to.
     * @param value the value to write to the given address.
     */
    public abstract void writeMemory(int address, int value);
}
