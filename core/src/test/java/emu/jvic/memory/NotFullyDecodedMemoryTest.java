package emu.jvic.memory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests {@link NotFullyDecodedMemory} in isolation, using small fake chips that
 * return known sentinel values, rather than the real VIC/VIA1/VIA2 chips it is
 * normally used with in {@link Vic20Memory}.
 *
 * <p>On real VIC-20 hardware, the $9010-$93FF I/O page is not fully address
 * decoded: several chip-select lines are left disconnected, so more than one
 * chip can be selected by the same address at once. All of the selected chips'
 * outputs then fight over the same physical data bus lines, which behaves like
 * a wired-AND: an idle/undriven bus line reads as 1, and each selected chip can
 * only pull individual bits low, never force a bit high that another chip is
 * driving low. The correct emulation is therefore to start the accumulator at
 * 0xFF and AND in each selected chip's byte.
 */
public class NotFullyDecodedMemoryTest {

    private static class FakeChip extends MemoryMappedChip {
        private final int value;

        FakeChip(int value) {
            this.value = value;
        }

        @Override
        public int readMemory(int address) {
            return value;
        }

        @Override
        public void writeMemory(int address, int value) {
            // Not used by these tests.
        }
    }

    private static class RecordingChip extends MemoryMappedChip {
        int lastWrittenAddress = -1;
        int lastWrittenValue = -1;
        int writeCount = 0;

        @Override
        public int readMemory(int address) {
            return 0;
        }

        @Override
        public void writeMemory(int address, int value) {
            lastWrittenAddress = address;
            lastWrittenValue = value;
            writeCount++;
        }
    }

    @Test
    void twoChipsBothDrivingAllOnesReadsAllOnes() {
        NotFullyDecodedMemory mem = new NotFullyDecodedMemory(
                new MemoryMappedChip[] { new FakeChip(0xFF), new FakeChip(0xFF) });

        assertEquals(0xFF, mem.readMemory(0x9010));
    }

    @Test
    void oneChipPullingBitsLowIsReflectedInTheResult() {
        // One chip drives 0xF0, the other drives 0xFF (i.e. isn't pulling anything low).
        // The wired-AND result should be 0xF0 - the low nibble stays pulled low.
        NotFullyDecodedMemory mem = new NotFullyDecodedMemory(
                new MemoryMappedChip[] { new FakeChip(0xF0), new FakeChip(0xFF) });

        assertEquals(0xF0, mem.readMemory(0x9010));
    }

    @Test
    void multipleChipsPullingDifferentBitsLowCombineOnTheBus() {
        // vic pulls the high nibble low, via1 pulls the low nibble low. Combined
        // on a real wired-AND bus, every bit ends up low.
        NotFullyDecodedMemory mem = new NotFullyDecodedMemory(
                new MemoryMappedChip[] { new FakeChip(0x0F), new FakeChip(0xF0), new FakeChip(0xFF) });

        assertEquals(0x00, mem.readMemory(0x9030));
    }

    @Test
    void threeChipsWhereNoneDriveAnyBitLowReadsAllOnes() {
        NotFullyDecodedMemory mem = new NotFullyDecodedMemory(
                new MemoryMappedChip[] { new FakeChip(0xFF), new FakeChip(0xFF), new FakeChip(0xFF) });

        assertEquals(0xFF, mem.readMemory(0x9030));
    }

    @Test
    void writesAreBroadcastToEveryMappedChip() {
        RecordingChip a = new RecordingChip();
        RecordingChip b = new RecordingChip();
        NotFullyDecodedMemory mem = new NotFullyDecodedMemory(new MemoryMappedChip[] { a, b });

        mem.writeMemory(0x9010, 0x42);

        assertEquals(1, a.writeCount);
        assertEquals(0x9010, a.lastWrittenAddress);
        assertEquals(0x42, a.lastWrittenValue);
        assertEquals(1, b.writeCount);
        assertEquals(0x9010, b.lastWrittenAddress);
        assertEquals(0x42, b.lastWrittenValue);
    }
}
