package emu.jvic;

import emu.jvic.config.AppConfigItem;
import emu.jvic.memory.MemoryMappedChip;
import emu.jvic.memory.RamType;
import emu.jvic.memory.UnconnectedMemory;
import emu.jvic.testutil.MachineTestHarness;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Tests {@link Machine#init}'s {@code RamType.RAM_AUTO} inference: for a PRG
 * program, the RAM configuration is guessed from the program's own start
 * address; for CART/DISK/TAPE, a fixed default is used. Verified indirectly
 * via the resulting {@code Vic20Memory} chip placement (the same technique
 * used by {@code Vic20MemoryMapTest}), since {@code Machine} doesn't expose
 * the resolved {@link RamType} directly.
 */
public class MachineRamAutoDetectionTest {

    private static MemoryMappedChip[] init(String fileType, byte[] programData) {
        AppConfigItem appConfigItem = new AppConfigItem();
        appConfigItem.setFileType(fileType);
        Program program = new Program(appConfigItem, programData);
        MachineTestHarness h = MachineTestHarness.withRealRomsAndProgram(MachineType.PAL, RamType.RAM_AUTO, program);
        return h.getMemory().getMemoryMap();
    }

    private static byte[] prgWithStartAddress(int startAddress) {
        return new byte[] { (byte) (startAddress & 0xFF), (byte) ((startAddress >> 8) & 0xFF), 0x00 };
    }

    private static boolean isRam(MemoryMappedChip[] map, int address) {
        return !(map[address] instanceof UnconnectedMemory);
    }

    @Test
    void prgStartingAt0x1201SelectsRam24k() {
        MemoryMappedChip[] map = init("PRG", prgWithStartAddress(0x1201));

        // RAM_24K = BLK_1|BLK_2|BLK_3: $2000-$7FFF is RAM, but the lower 3K ($0400-$0FFF)
        // and BLK_5 ($A000-$BFFF) are not.
        assertFalse(isRam(map, 0x0400), "RAM_24K should not include the lower 3K expansion");
        assertInstanceOf(emu.jvic.memory.RamChip.class, map[0x2000]);
        assertInstanceOf(emu.jvic.memory.RamChip.class, map[0x6000]);
        assertFalse(isRam(map, 0xA000), "RAM_24K should not include BLK_5");
    }

    @Test
    void prgStartingAt0x0401SelectsRam3k() {
        MemoryMappedChip[] map = init("PRG", prgWithStartAddress(0x0401));

        assertInstanceOf(emu.jvic.memory.RamChip.class, map[0x0400]);
        assertInstanceOf(emu.jvic.memory.RamChip.class, map[0x0800]);
        assertInstanceOf(emu.jvic.memory.RamChip.class, map[0x0C00]);
        assertFalse(isRam(map, 0x2000), "RAM_3K should not include any BLK expansion");
    }

    @Test
    void prgStartingAt0x1001SelectsUnexpandedRam() {
        MemoryMappedChip[] map = init("PRG", prgWithStartAddress(0x1001));

        assertFalse(isRam(map, 0x0400));
        assertFalse(isRam(map, 0x2000));
        assertFalse(isRam(map, 0xA000));
    }

    @Test
    void cartDefaultsToUnexpandedRam() {
        // A 16K cart image, loaded via the CART path.
        byte[] cartData = new byte[16384];
        MemoryMappedChip[] map = init("CART", cartData);

        assertFalse(isRam(map, 0x0400));
        assertFalse(isRam(map, 0x2000));
    }

    @Test
    void diskDefaultsToRam35k() {
        byte[] diskData = new byte[1]; // Content irrelevant to ramType selection.
        MemoryMappedChip[] map = init("DISK", diskData);

        assertInstanceOf(emu.jvic.memory.RamChip.class, map[0x0400]);
        assertInstanceOf(emu.jvic.memory.RamChip.class, map[0x2000]);
        assertInstanceOf(emu.jvic.memory.RamChip.class, map[0xA000]);
    }

    @Test
    void tapeDefaultsToRam32k() throws IOException {
        MemoryMappedChip[] map = init("TAPE", buildMinimalTapFile());

        // RAM_32K = BLK_1|BLK_2|BLK_3|BLK_5, but not the lower 3K.
        assertFalse(isRam(map, 0x0400), "RAM_32K should not include the lower 3K expansion");
        assertInstanceOf(emu.jvic.memory.RamChip.class, map[0x2000]);
        assertInstanceOf(emu.jvic.memory.RamChip.class, map[0xA000]);
    }

    private static byte[] buildMinimalTapFile() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("C64-TAPE-RAW".getBytes("ASCII"));
        out.write(0);
        out.write(1);
        out.write(0);
        out.write(0);
        byte[] payload = { 0x10, 0x10 };
        out.write(payload.length);
        out.write(0);
        out.write(0);
        out.write(0);
        out.write(payload);
        return out.toByteArray();
    }
}
