package emu.jvic.memory;

/**
 * This class emulates a RAM chip on the VIC bus.
 * 
 * @author Lance Ewing
 */
public class VicBusRamChip extends RamChip {

    /**
     * Reads the value of the given memory address.
     *
     * @param address the address to read the byte from.
     *
     * @return the contents of the memory address.
     */
    public int readMemory(int address) {
        int value = mem[address];
        memory.setLastBusData(value);
        return value;
    }

    /**
     * Writes a value to the given memory address.
     *
     * @param address the address to write the value to.
     * @param value   the value to write to the given address.
     */
    public void writeMemory(int address, int value) {
        mem[address] = value;
        memory.setLastBusData(value);
    }
}
