package emu.jvic.memory;

import java.util.ArrayList;

import emu.jvic.cpu.Cpu6502;
import emu.jvic.snap.Snapshot;

/**
 * This class emulates the memory of a 6502 machine.
 * 
 * @author Lance Ewing
 */
public class Memory {

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
     * Holds reference to the CPU, mainly to get access to data bus buffer.
     */
    protected Cpu6502 cpu;

    // TODO: To support VIC bus tricks.
    protected int lastRead;
    protected int lastWrite;
    protected int lastBusData;

    /**
     * Constructor for Memory.
     * 
     * @param cpu      The CPU that will access this Memory.
     * @param snapshot Optional snapshot of the machine state to start with.
     */
    public Memory(Cpu6502 cpu, Snapshot snapshot) {
        this(cpu, snapshot, false);
    }

    /**
     * Constructor for Memory.
     * 
     * @param cpu      The CPU that will access this Memory.
     * @param snapshot Optional snapshot of the machine state to start with.
     * @param allRam   true if memory should be initialised to all RAM; otherwise false.
     */
    public Memory(Cpu6502 cpu, Snapshot snapshot, boolean allRam) {
        if (snapshot != null) {
            this.mem = snapshot.getMemoryArray();
        } else {
            this.mem = new int[65536];
        }
        this.memoryMap = new MemoryMappedChip[65536];
        this.cpu = cpu;
        cpu.setMemory(this);
        if (allRam) {
            mapChipToMemory(new RamChip(), 0x0000, 0xFFFF);
        }
    }

    /**
     * Maps the given chip instance at the given address range.
     * 
     * @param chip         The chip to map at the given address range.
     * @param startAddress The start of the address range.
     * @param endAddress   The end of the address range.
     */
    protected void mapChipToMemory(MemoryMappedChip chip, int startAddress, int endAddress) {
        mapChipToMemory(chip, startAddress, endAddress, 0x0000);
    }

    /**
     * Maps the given chip instance at the given address range.
     * 
     * @param chip         The chip to map at the given address range.
     * @param startAddress The start of the address range.
     * @param endAddress   The end of the address range.
     * @param mirrorMask   Mask that determines how the chip is mirrored into other parts of memory.
     */
    protected void mapChipToMemory(MemoryMappedChip chip, int startAddress, int endAddress, int mirrorMask) {
        mapChipToMemory(chip, startAddress, endAddress, mirrorMask, null);
    }

    /**
     * Maps the given chip instance at the given address range, optionally loading
     * the given initial state data into that address range. This state data is
     * intended to be used for things such as ROM images (e.g. char, kernel, basic).
     * 
     * @param chip         The chip to map at the given address range.
     * @param startAddress The start of the address range.
     * @param endAddress   The end of the address range.
     * @param mirrorMask   Mask that determines how the chip is mirrored into other parts of memory.
     * @param state        byte array containing initial state (can be null).
     */
    protected void mapChipToMemory(MemoryMappedChip chip, int startAddress, int endAddress, byte[] state) {
        mapChipToMemory(chip, startAddress, endAddress, 0x0000, state);
    }

    /**
     * Maps the given chip instance at the given address range, optionally loading
     * the given initial state data into that address range. This state data is
     * intended to be used for things such as ROM images (e.g. char, kernel, basic).
     * 
     * @param chip         The chip to map at the given address range.
     * @param startAddress The start of the address range.
     * @param endAddress   The end of the address range.
     * @param mirrorMask   Mask that determines how the chip is mirrored into other parts of memory.
     * @param state        byte array containing initial state (can be null).
     */
    protected void mapChipToMemory(MemoryMappedChip chip, int startAddress, int endAddress, 
            int mirrorMask, byte[] state) {
        int statePos = 0;

        // Configure the chip into the memory map between the given start and end addresses.
        ArrayList<Integer> mirrorBases = buildMirrorBases(mirrorMask);
        for (int mirrorBase : mirrorBases) {
            statePos = 0;
            for (int i = startAddress; i <= endAddress; i++) {
                memoryMap[mirrorBase + i] = chip;
                if (state != null) {
                    // Load the initial state into memory if provided. Only works for ROM.
                    mem[mirrorBase + i] = (state[statePos++] & 0xFF);
                }
            }
        }

        chip.setMemory(this);
    }

    /**
     * Builds a List of mirror base addresses for the given mirror mask.
     * 
     * @param mirrorMask
     * 
     * @return
     */
    private ArrayList<Integer> buildMirrorBases(int mirrorMask) {
        ArrayList<Integer> mirrorBases = new ArrayList<Integer>();

        for (int address = 0; address <= 0xFFFF; address++) {
            if ((address & mirrorMask) == address) {
                mirrorBases.add(address);
            }
        }

        return mirrorBases;
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
     * Gets a reference to the Cpu6502.
     * 
     * @return
     */
    public Cpu6502 getCpu() {
        return cpu;
    }

    public int getLastBusData() {
        return lastBusData;
    }

    public int getLastRead() {
        return lastRead;
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
        lastBusData = lastRead = memoryMap[address].readMemory(address);
        return (lastBusData);
    }

    /**
     * Writes a value to the give VIC 20 memory address.
     * 
     * @param address The address to write the value to.
     * @param value   The value to write to the given address.
     */
    public void writeMemory(int address, int value) {
        lastBusData = lastWrite = value;
        memoryMap[address].writeMemory(address, value);
    }

    /**
     * Forces a write to a memory address, even if it is ROM. This is used mainly
     * for setting emulation traps.
     * 
     * @param address The address to write the value to.
     * @param value   The value to write to the given address.
     */
    public void forceWrite(int address, int value) {
        mem[address] = value;
    }

    /**
     * Converts a byte array into an int array.
     * 
     * @param data The byte array to convert.
     * 
     * @return The int array.
     */
    protected int[] convertByteArrayToIntArray(byte[] data) {
        int[] convertedData = new int[data.length];
        for (int i = 0; i < data.length; i++) {
            convertedData[i] = ((int) data[i]) & 0xFF;
        }
        return convertedData;
    }
}
