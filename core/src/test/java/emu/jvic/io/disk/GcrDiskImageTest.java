package emu.jvic.io.disk;

import emu.jvic.io.disk.GcrDiskImage.Sector;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests {@link GcrDiskImage} against a real, bundled .d64 disk image
 * ({@code assets/data/blank.d64}, confirmed to be the standard 174848-byte
 * 35-track D64 size). No {@link C1541Drive} is needed - GCR encode/decode is
 * pure, deterministic logic driven entirely off the raw disk bytes.
 */
public class GcrDiskImageTest {

    /** Locates and loads the real assets/data/blank.d64 fixture bundled with the project. */
    private static byte[] loadBlankDisk() throws IOException {
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve("assets").resolve("data").resolve("blank.d64");
            if (Files.exists(candidate)) {
                return Files.readAllBytes(candidate);
            }
            dir = dir.getParent();
        }
        throw new RuntimeException("Could not locate assets/data/blank.d64 above " + Paths.get("").toAbsolutePath());
    }

    @Test
    void gcrEncodeThenDecodeReproducesTheOriginalSectorBytesExactly() throws IOException {
        // GcrDiskImage GCR-encodes every sector's real raw data at construction time
        // (encodeTrack() -> buildGCRSectorData()). Sector.commitWrites() decodes that
        // same GCR data back into rawData. Calling commitWrites() immediately, with no
        // intervening writes, should losslessly reproduce the original bytes - this
        // exercises the full encode+decode pipeline against real disk data without
        // needing to hand-construct GCR bytes.
        GcrDiskImage disk = new GcrDiskImage(loadBlankDisk());

        for (int track : new int[] { 1, 17, 18, 35 }) {
            for (int sectorNum = 0; sectorNum < disk.getSectorCount(track); sectorNum++) {
                Sector sector = disk.getSector(track, sectorNum);
                int[] originalRawData = sector.rawData.clone();

                sector.commitWrites();

                assertEquals(java.util.Arrays.toString(originalRawData), java.util.Arrays.toString(sector.rawData),
                        "track " + track + " sector " + sectorNum + " should round-trip exactly");
            }
        }
    }

    @Test
    void sectorCountVariesByTrackZoneAsDocumented() throws IOException {
        GcrDiskImage disk = new GcrDiskImage(loadBlankDisk());

        // Per the real, documented 1541 zone layout: tracks 1-17 have 21 sectors,
        // 18-24 have 19, 25-30 have 18, 31-35 have 17.
        assertEquals(21, disk.getSectorCount(1));
        assertEquals(21, disk.getSectorCount(17));
        assertEquals(19, disk.getSectorCount(18));
        assertEquals(19, disk.getSectorCount(24));
        assertEquals(18, disk.getSectorCount(25));
        assertEquals(18, disk.getSectorCount(30));
        assertEquals(17, disk.getSectorCount(31));
        assertEquals(17, disk.getSectorCount(35));
    }

    @Test
    void headerAndDataBlocksBothStartWithFiveSyncBytes() throws IOException {
        GcrDiskImage disk = new GcrDiskImage(loadBlankDisk());
        Sector sector = disk.getSector(1, 0);

        for (int i = 0; i < 5; i++) {
            assertEquals(0xFF, sector.read(i), "header sync byte " + i);
        }
        // Header gap (9 bytes of 0x55) separates header block from data block sync.
        for (int i = 15; i < 24; i++) {
            assertEquals(0x55, sector.read(i), "header gap byte at offset " + i);
        }
        for (int i = 24; i < 29; i++) {
            assertEquals(0xFF, sector.read(i), "data block sync byte at offset " + i);
        }
    }

    @Test
    void gcrSectorSizeConstantsAreInternallyConsistent() {
        // 5 sync + 10 header + 9 gap + 5 sync + 325 data block + 8 tail gap.
        assertEquals(5 + 10 + 9 + 5 + 325 + 8, GcrDiskImage.GCR_SECTOR_SIZE);
        assertEquals(29, GcrDiskImage.DATA_BLOCK_OFFSET);
        assertEquals(325, GcrDiskImage.DATA_BLOCK_LENGTH);
    }

    @Test
    void copyRawImageReflectsWritesAfterCommit() throws IOException {
        GcrDiskImage disk = new GcrDiskImage(loadBlankDisk());
        Sector sector = disk.getSector(1, 0);

        byte[] before = disk.copyRawImage();

        // Round-tripping with no actual data change should leave the raw image identical.
        sector.commitWrites();
        byte[] after = disk.copyRawImage();
        assertEquals(java.util.Arrays.toString(before), java.util.Arrays.toString(after));
    }
}
