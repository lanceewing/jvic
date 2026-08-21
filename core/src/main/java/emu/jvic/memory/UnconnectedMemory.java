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

        // TODO: On real 6502-family hardware, reading unmapped/floating memory
        // returns whatever value was last driven onto the data bus (parasitic
        // capacitance briefly "holds" it) rather than a fixed value - the standard
        // "open bus" technique used across 6502-ecosystem emulators. The intended
        // fix is commented out below (getDataBusBuffer() already exists on
        // Cpu6502), but two things are still needed before it can just be
        // uncommented: (1) it's never been validated against real VIC-20 hardware
        // or another reference emulator, so the exact behaviour (e.g. whether the
        // VIC chip's own bus activity should factor in, not just the CPU's) is
        // unconfirmed; and (2) callers that read the shared `mem[]` array directly
        // instead of dispatching through readMemory() - e.g. Vic6560's screen/char
        // data fetches - won't see this fix at all unless they're changed to route
        // through the chip's readMemory() too (writes here are also discarded, so
        // `mem[]` itself never reflects floating-bus values). See Vic6560.java's
        // two matching TODOs for the fetch-side half of this.
        // if (memory != null) {
        // value = memory.cpu.getDataBusBuffer();
        // }

        return value;
    }

    /**
     * Writes a value to the given memory address.
     *
     * @param address the address to write the value to.
     * @param value   the value to write to the given address.
     */
    public void writeMemory(int address, int value) {
        // Do nothing.
    }
}
