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

    /**
     * Reads the value of the given memory address without any side effects, for
     * the debug monitor. The default is the same as {@link #readMemory(int)}, which
     * is correct for chips whose reads have no side effects (RAM, ROM). Chips whose
     * reads do have side effects (such as clearing interrupt flags) override this.
     *
     * @param address the address to read the byte from.
     *
     * @return the contents of the memory address.
     */
    public int peekMemory(int address) {
        return readMemory(address);
    }
}
