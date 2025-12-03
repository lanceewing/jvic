package emu.jvic.memory;

/**
 * This class emulates memory that is not fully decoded.
 * 
 * @author Lance Ewing
 */
public class NotFullyDecodedMemory extends MemoryMappedChip {

    /**
     * Contains a list of chips that are mapped to this memory range.
     */
    private MemoryMappedChip chips[];

    /**
     * Constructor for NotFullyDecodedMemory.
     * 
     * @param chips The list of chips that are mapped to this memory range.
     */
    public NotFullyDecodedMemory(MemoryMappedChip chips[]) {
        this.chips = chips;
    }

    /**
     * Reads the value of the given memory address.
     * 
     * @param address The address to read the byte from.
     * 
     * @return the contents of the memory address.
     */
    public int readMemory(int address) {
        int value = 0;
        int len = chips.length;

        for (int i = 0; i < len; i++) {
            value &= chips[i].readMemory(address);
        }

        return value;
    }

    /**
     * Writes a value to the given memory address.
     * 
     * @param address The address to write the value to.
     * @param value   The value to write to the given address.
     */
    public void writeMemory(int address, int value) {
        int len = chips.length;
        for (int i = 0; i < len; i++) {
            chips[i].writeMemory(address, value);
        }
    }
}
