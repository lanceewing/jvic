package emu.jvic.memory;

import emu.jvic.MachineType;
import emu.jvic.cpu.Cpu6502;
import emu.jvic.io.Joystick;
import emu.jvic.io.Keyboard;
import emu.jvic.io.SerialBus;
import emu.jvic.io.Via1;
import emu.jvic.io.Via2;
import emu.jvic.io.Via6522;
import emu.jvic.io.tape.C1530Datasette;
import emu.jvic.testutil.RecordingPixelData;
import emu.jvic.testutil.TestKeyboardMatrix;
import emu.jvic.video.Vic;
import emu.jvic.video.Vic6561;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link Vic20Memory} wires up the real, documented VIC-20 memory map:
 * which chip answers which address range, for every supported {@link RamType}
 * expansion configuration, including the well-known "$9010-$93FF is not fully
 * address decoded" quirk.
 *
 * <p>Reference facts encoded here come directly from {@link Vic20Memory}'s own
 * {@code initVicMemory()} - this test exists to catch a future accidental edit
 * breaking the address decode table, not to re-derive it from a datasheet.
 */
public class Vic20MemoryMapTest {

    private static class Built {
        final Vic20Memory memory;
        final Vic vic;
        final Via6522 via1;
        final Via6522 via2;

        Built(Vic20Memory memory, Vic vic, Via6522 via1, Via6522 via2) {
            this.memory = memory;
            this.vic = vic;
            this.via1 = via1;
            this.via2 = via2;
        }
    }

    private static final byte[] BASIC_ROM = fill(8192, 0x11);
    private static final byte[] KERNAL_ROM = fill(8192, 0x22);
    private static final byte[] CHAR_ROM = fill(4096, 0x33);

    private static byte[] fill(int length, int value) {
        byte[] data = new byte[length];
        for (int i = 0; i < length; i++) {
            data[i] = (byte) value;
        }
        return data;
    }

    private Built build(RamType ramType) {
        Cpu6502 cpu = new Cpu6502(null);
        TestKeyboardMatrix keyboardMatrix = new TestKeyboardMatrix();
        Keyboard keyboard = new Keyboard(keyboardMatrix);
        Joystick joystick = new Joystick(keyboardMatrix);
        SerialBus serialBus = new SerialBus();
        C1530Datasette datasette = new C1530Datasette();
        RecordingPixelData pixelData = new RecordingPixelData();
        pixelData.init(MachineType.PAL.getTotalScreenWidth() * 2, MachineType.PAL.getTotalScreenHeight() * 2);

        Vic vic = new Vic6561(pixelData, MachineType.PAL, null);
        Via6522 via1 = new Via1(cpu, keyboard, joystick, serialBus, datasette, null);
        Via6522 via2 = new Via2(cpu, keyboard, joystick, serialBus, datasette, null);

        Vic20Memory memory = new Vic20Memory(cpu, vic, via1, via2, ramType.getRamPlacement(), MachineType.PAL,
                BASIC_ROM, KERNAL_ROM, CHAR_ROM, null);

        return new Built(memory, vic, via1, via2);
    }

    // ------------------------------------------------------------ always-RAM ----

    @Test
    void firstOneKIsAlwaysRamRegardlessOfExpansion() {
        Built b = build(RamType.RAM_UNEXPANDED);
        for (int addr = 0x0000; addr <= 0x03FF; addr += 0x100) {
            assertInstanceOf(VicBusRamChip.class, b.memory.getMemoryMap()[addr]);
        }
    }

    @Test
    void fourKAt0x1000IsAlwaysRamRegardlessOfExpansion() {
        Built b = build(RamType.RAM_UNEXPANDED);
        for (int addr = 0x1000; addr <= 0x1FFF; addr += 0x100) {
            assertInstanceOf(VicBusRamChip.class, b.memory.getMemoryMap()[addr]);
        }
    }

    // ------------------------------------------------------------ expansion RAM ----

    @Test
    void unexpandedHasNoOptionalRamAnywhere() {
        Built b = build(RamType.RAM_UNEXPANDED);
        MemoryMappedChip[] map = b.memory.getMemoryMap();
        assertInstanceOf(UnconnectedMemory.class, map[0x0400]);
        assertInstanceOf(UnconnectedMemory.class, map[0x0800]);
        assertInstanceOf(UnconnectedMemory.class, map[0x0C00]);
        assertInstanceOf(UnconnectedMemory.class, map[0x2000]);
        assertInstanceOf(UnconnectedMemory.class, map[0x4000]);
        assertInstanceOf(UnconnectedMemory.class, map[0x6000]);
        assertInstanceOf(UnconnectedMemory.class, map[0xA000]);
    }

    @Test
    void ram3kFillsTheThreeBlocksBelowTheBuiltIn4k() {
        Built b = build(RamType.RAM_3K);
        MemoryMappedChip[] map = b.memory.getMemoryMap();
        assertInstanceOf(RamChip.class, map[0x0400]);
        assertInstanceOf(RamChip.class, map[0x07FF]);
        assertInstanceOf(RamChip.class, map[0x0800]);
        assertInstanceOf(RamChip.class, map[0x0BFF]);
        assertInstanceOf(RamChip.class, map[0x0C00]);
        assertInstanceOf(RamChip.class, map[0x0FFF]);
        // But nothing above that.
        assertInstanceOf(UnconnectedMemory.class, map[0x2000]);
    }

    @Test
    void ram8kFillsOnlyTheFirst8kExpansionBlock() {
        Built b = build(RamType.RAM_8K);
        MemoryMappedChip[] map = b.memory.getMemoryMap();
        assertInstanceOf(RamChip.class, map[0x2000]);
        assertInstanceOf(RamChip.class, map[0x3FFF]);
        assertInstanceOf(UnconnectedMemory.class, map[0x4000]);
        assertInstanceOf(UnconnectedMemory.class, map[0x6000]);
        assertInstanceOf(UnconnectedMemory.class, map[0xA000]);
        // 8K expansion alone doesn't touch the 0x0400-0x0FFF blocks.
        assertInstanceOf(UnconnectedMemory.class, map[0x0400]);
    }

    @Test
    void ram32kFillsAllThreeBlocksPlusBlk5ButNotTheLower3k() {
        Built b = build(RamType.RAM_32K);
        MemoryMappedChip[] map = b.memory.getMemoryMap();
        assertInstanceOf(RamChip.class, map[0x2000]);
        assertInstanceOf(RamChip.class, map[0x4000]);
        assertInstanceOf(RamChip.class, map[0x6000]);
        assertInstanceOf(RamChip.class, map[0xA000]);
        assertInstanceOf(UnconnectedMemory.class, map[0x0400]);
    }

    @Test
    void ram35kIsFullyExpandedEverywhereOptionalRamCanGo() {
        Built b = build(RamType.RAM_35K);
        MemoryMappedChip[] map = b.memory.getMemoryMap();
        assertInstanceOf(RamChip.class, map[0x0400]);
        assertInstanceOf(RamChip.class, map[0x0800]);
        assertInstanceOf(RamChip.class, map[0x0C00]);
        assertInstanceOf(RamChip.class, map[0x2000]);
        assertInstanceOf(RamChip.class, map[0x4000]);
        assertInstanceOf(RamChip.class, map[0x6000]);
        assertInstanceOf(RamChip.class, map[0xA000]);
    }

    // ------------------------------------------------------------ chip registers ----

    @Test
    void standardVicViaRegisterLocationsMapToTheGivenChipInstances() {
        Built b = build(RamType.RAM_UNEXPANDED);
        MemoryMappedChip[] map = b.memory.getMemoryMap();

        assertSame(b.vic, map[0x9000]);
        assertSame(b.vic, map[0x900F]);
        assertSame(b.via1, map[0x9110]);
        assertSame(b.via1, map[0x911F]);
        assertSame(b.via2, map[0x9120]);
        assertSame(b.via2, map[0x912F]);
    }

    @Test
    void unconnectedVicAddressBusSpaceStillSelectsTheVicChip() {
        // $9100-$910F, $9140-$914F, etc. and $9800-$9FFF are documented as "unconnected
        // VIC address bus space" in Vic20Memory - i.e. the VIC chip is still the one
        // selected there (its address bus lines just aren't fully connected), it isn't
        // simply unmapped.
        Built b = build(RamType.RAM_UNEXPANDED);
        MemoryMappedChip[] map = b.memory.getMemoryMap();
        assertSame(b.vic, map[0x9100]);
        assertSame(b.vic, map[0x9800]);
        assertSame(b.vic, map[0x9FFF]);
    }

    @Test
    void colourRamIsA4BitNibbleChipAt9400() {
        Built b = build(RamType.RAM_UNEXPANDED);
        MemoryMappedChip[] map = b.memory.getMemoryMap();
        assertInstanceOf(NibbleRamChip.class, map[0x9400]);
        assertInstanceOf(NibbleRamChip.class, map[0x97FF]);
    }

    @Test
    void basicAndKernalRomsAreLoadedAtTheirDocumentedAddresses() {
        Built b = build(RamType.RAM_UNEXPANDED);
        MemoryMappedChip[] map = b.memory.getMemoryMap();
        assertInstanceOf(RomChip.class, map[0xC000]);
        assertInstanceOf(RomChip.class, map[0xDFFF]);
        assertInstanceOf(RomChip.class, map[0xE000]);
        assertInstanceOf(RomChip.class, map[0xFFFF]);

        int[] mem = b.memory.getMemoryArray();
        assertEquals(0x11, mem[0xC000]);
        assertEquals(0x11, mem[0xDFFF]);
        assertEquals(0x22, mem[0xE000]);
        assertEquals(0x22, mem[0xFFFF]);
    }

    @Test
    void charRomIsLoadedAt0x8000() {
        Built b = build(RamType.RAM_UNEXPANDED);
        int[] mem = b.memory.getMemoryArray();
        assertEquals(0x33, mem[0x8000]);
        assertEquals(0x33, mem[0x8FFF]);
    }

    // ------------------------------------------------------------ not fully decoded ----

    @Test
    void notFullyDecodedRegionsAreDistinctFromExclusivelyMappedRegions() {
        Built b = build(RamType.RAM_UNEXPANDED);
        MemoryMappedChip[] map = b.memory.getMemoryMap();

        // $9010-$901F is documented as vic+via1 (not fully decoded), unlike the
        // exclusive $9000-$900F (vic only) and $9110-$911F (via1 only) ranges.
        assertInstanceOf(NotFullyDecodedMemory.class, map[0x9010]);
        assertFalse(map[0x9010] == b.vic);
        assertFalse(map[0x9010] == b.via1);
    }

    @Test
    void repeatingNotFullyDecodedGroupsShareTheSameChipGroupingAcrossTheirRepeats() {
        // Per Vic20Memory's own table, the vic+via1 grouping repeats at $9010, $9050,
        // $9090, $90D0, etc. - each repeat should be wired to the *same* shared
        // NotFullyDecodedMemory instance (same set of chips fighting over the bus),
        // not merely "some" NotFullyDecodedMemory.
        Built b = build(RamType.RAM_UNEXPANDED);
        MemoryMappedChip[] map = b.memory.getMemoryMap();

        assertSame(map[0x9010], map[0x9050]);
        assertSame(map[0x9010], map[0x9090]);
        assertSame(map[0x9010], map[0x90D0]);
    }

    @Test
    void differentNotFullyDecodedGroupsAreWiredToDifferentSharedInstances() {
        // $9010 (vic+via1) and $9020 (vic+via2) are documented as different chip
        // groupings, so must not resolve to the same NotFullyDecodedMemory instance.
        Built b = build(RamType.RAM_UNEXPANDED);
        MemoryMappedChip[] map = b.memory.getMemoryMap();

        assertTrue(map[0x9010] != map[0x9020]);
        assertTrue(map[0x9010] != map[0x9030]);
        assertTrue(map[0x9020] != map[0x9030]);
    }

    @Test
    void via1Via2GroupAt0x9130RepeatsAcrossItsDocumentedRanges() {
        Built b = build(RamType.RAM_UNEXPANDED);
        MemoryMappedChip[] map = b.memory.getMemoryMap();

        assertInstanceOf(NotFullyDecodedMemory.class, map[0x9130]);
        assertSame(map[0x9130], map[0x9170]);
        assertSame(map[0x9130], map[0x91B0]);
    }
}
