package emu.jvic.video;

import emu.jvic.MachineType;
import emu.jvic.testutil.VicTestHarness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * VIC chip register read/write semantics, per {@code Vic.readMemory}/{@code
 * writeMemory}: registers 4 (raster line), 6/7 (light pen X/Y) and 8/9 (paddle
 * X/Y) are read-only from the CPU's perspective (writes are silently discarded -
 * they're only ever updated internally by the VIC/joystick hardware); all others
 * are plain read/write. Address decoding masks out bits 4-7 of the low address
 * byte (`address & 0xFF0F`), mirroring each of the 16 registers across every
 * 16-byte block sharing the same high byte, but NOT across different high bytes.
 */
public class VicRegisterAccessTest {

    private VicTestHarness h;

    @BeforeEach
    void setUp() {
        h = new VicTestHarness(MachineType.NTSC);
    }

    @Test
    void writableRegistersRoundTrip() {
        int[] writableRegisters = {0, 1, 2, 3, 5, 10, 11, 12, 13, 14, 15};
        for (int reg : writableRegisters) {
            h.writeRegister(reg, 0xAA);
            assertEquals(0xAA, h.readRegister(reg), "register " + reg + " should round-trip");
        }
    }

    @Test
    void readOnlyRegistersIgnoreWrites() {
        int[] readOnlyRegisters = {4, 6, 7, 8, 9};
        for (int reg : readOnlyRegisters) {
            int before = h.readRegister(reg);
            h.writeRegister(reg, 0x55);
            assertEquals(before, h.readRegister(reg), "register " + reg + " must ignore CPU writes");
        }
    }

    @Test
    void registerAddressMirrorsWithinSameHighByteBlock() {
        // $9000 and $9010 both decode to register 0 (bits 4-7 of the low byte are masked out).
        h.vic.writeMemory(0x9000, 0x77);
        assertEquals(0x77, h.vic.readMemory(0x9010));

        h.vic.writeMemory(0x90F5, 0x22); // low nibble 5 -> register 5, regardless of the upper nibble
        assertEquals(0x22, h.vic.readMemory(0x9005));
    }

    @Test
    void registerAddressDoesNotMirrorAcrossDifferentHighBytes() {
        // The high byte (bits 8-15) is preserved by the address mask, so $9000
        // and $9100 are treated as independent memory cells even though both
        // decode to "register 0" in isolation.
        h.vic.writeMemory(0x9000, 0x11);
        h.vic.writeMemory(0x9100, 0x99);

        assertEquals(0x11, h.vic.readMemory(0x9000));
        assertEquals(0x99, h.vic.readMemory(0x9100));
    }

    @Test
    void writingUpdatesLastBusData() {
        h.writeRegister(2, 0x33);
        assertEquals(0x33, h.memory.getLastBusData());

        h.readRegister(2);
        assertEquals(0x33, h.memory.getLastBusData());
    }
}
