package emu.jvic.memory;

import emu.jvic.cpu.Cpu6502;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests {@link NibbleRamChip}, used for the VIC-20's colour RAM at $9400-$97FF.
 * Colour RAM is documented, real VIC-20 hardware, as a 4-bit-wide chip - only the
 * low nibble of each byte is real, addressable storage.
 */
public class NibbleRamChipTest {

    private Memory buildMemory() {
        Memory memory = new Memory(new Cpu6502(null), null, false);
        memory.mapChipToMemory(new NibbleRamChip(), 0x9400, 0x97FF);
        return memory;
    }

    @Test
    void writesAreMaskedToTheLowNibble() {
        Memory memory = buildMemory();

        memory.writeMemory(0x9400, 0xFF);

        assertEquals(0x0F, memory.readMemory(0x9400));
    }

    @Test
    void lowNibbleValueRoundTripsExactly() {
        Memory memory = buildMemory();

        memory.writeMemory(0x9401, 0x0A);

        assertEquals(0x0A, memory.readMemory(0x9401));
    }

    @Test
    void currentImplementationReadsTheUnconnectedUpperNibbleAsZero() {
        // This pins down NibbleRamChip's CURRENT behaviour, not a verified hardware
        // fact: on real VIC-20 hardware the upper 4 data lines of colour RAM are
        // physically unconnected, and what an unconnected/floating data line reads
        // back as (0s, 1s, or open-bus noise) needs checking against a public VIC-20
        // hardware reference before treating either answer as "correct". Today the
        // emulator stores the masked (upper-nibble-cleared) value and reads it straight
        // back, so the upper nibble always reads as 0 here - if that turns out not to
        // match real hardware, this test (not just the implementation) will need
        // updating once a reference is confirmed.
        Memory memory = buildMemory();

        memory.writeMemory(0x9400, 0xFF);

        assertEquals(0x0F, memory.readMemory(0x9400) & 0xFF);
    }
}
