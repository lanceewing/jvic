package emu.jvic.io.disk.persistence;

import emu.jvic.config.AppConfigItem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link DiskPersistenceSupport}'s pure, dependency-free key/hash
 * generation logic. No setup needed - {@link AppConfigItem} is a plain POJO.
 */
public class DiskPersistenceSupportTest {

    // ------------------------------------------------------------ stableHashHex(byte[]) ----

    @Test
    void byteArrayHashIsDeterministic() {
        byte[] data = { 1, 2, 3, 4, 5 };

        assertEquals(DiskPersistenceSupport.stableHashHex(data), DiskPersistenceSupport.stableHashHex(data.clone()));
    }

    @Test
    void differentByteArraysProduceDifferentHashes() {
        assertNotEquals(DiskPersistenceSupport.stableHashHex(new byte[] { 1, 2, 3 }),
                DiskPersistenceSupport.stableHashHex(new byte[] { 3, 2, 1 }));
    }

    @Test
    void byteArrayHashIsAlways16HexCharsEvenForNullOrEmptyInput() {
        assertEquals(16, DiskPersistenceSupport.stableHashHex((byte[]) null).length());
        assertEquals(16, DiskPersistenceSupport.stableHashHex(new byte[0]).length());
        assertEquals(DiskPersistenceSupport.stableHashHex((byte[]) null),
                DiskPersistenceSupport.stableHashHex(new byte[0]),
                "null and empty should hash the same, since the loop over a null array never runs");
    }

    // ------------------------------------------------------------ stableHashHex(String) ----

    @Test
    void stringHashIsDeterministicAndDiffersForDifferentStrings() {
        assertEquals(DiskPersistenceSupport.stableHashHex("hello"), DiskPersistenceSupport.stableHashHex("hello"));
        assertNotEquals(DiskPersistenceSupport.stableHashHex("hello"), DiskPersistenceSupport.stableHashHex("world"));
    }

    @Test
    void nullAndEmptyStringHashTheSame() {
        assertEquals(DiskPersistenceSupport.stableHashHex((String) null), DiskPersistenceSupport.stableHashHex(""));
    }

    // ------------------------------------------------------------ getProgramIdSource ----

    @Test
    void programIdSourcePrefersGameIdFirst() {
        AppConfigItem item = new AppConfigItem();
        item.setGameId("game123");
        item.setFilePath("/some/path.d64");
        item.setName("Some Name");

        assertEquals("gameId", DiskPersistenceSupport.getProgramIdSource(item));
    }

    @Test
    void programIdSourceFallsBackToFilePathWhenGameIdIsBlank() {
        AppConfigItem item = new AppConfigItem();
        item.setFilePath("/some/path.d64");
        item.setName("Some Name");

        assertEquals("filePath", DiskPersistenceSupport.getProgramIdSource(item));
    }

    @Test
    void programIdSourceUsesNormalizedPathOverrideBeforeFilePath() {
        AppConfigItem item = new AppConfigItem();
        item.setFilePath(""); // Blank.
        item.setName("Some Name");

        assertEquals("filePath", DiskPersistenceSupport.getProgramIdSource(item, "/overridden/path.d64"));
    }

    @Test
    void programIdSourceFallsBackToNameWhenGameIdAndFilePathAreBlank() {
        AppConfigItem item = new AppConfigItem();
        item.setName("Some Name");

        assertEquals("name", DiskPersistenceSupport.getProgramIdSource(item));
    }

    @Test
    void programIdSourceFallsBackToProgramWhenEverythingIsBlank() {
        AppConfigItem item = new AppConfigItem();

        assertEquals("program", DiskPersistenceSupport.getProgramIdSource(item));
    }

    // ------------------------------------------------------------ createKey / slugify ----

    @Test
    void createKeyBuildsASlugifiedNamePrefixWithLowercaseAndDashes() {
        AppConfigItem item = new AppConfigItem();
        item.setName("My Game! 2000");

        DiskPersistenceKey key = DiskPersistenceSupport.createKey(item, new byte[] { 1, 2, 3 });

        assertTrue(key.getProgramKey().startsWith("name-my-game-2000--"),
                "expected a lowercase, dash-separated slug of the name; got: " + key.getProgramKey());
    }

    @Test
    void createKeyFallsBackToProgramForACompletelyBlankAppConfigItem() {
        AppConfigItem item = new AppConfigItem();
        item.setName(""); // AppConfigItem defaults name to "", not null.

        DiskPersistenceKey key = DiskPersistenceSupport.createKey(item, new byte[] { 1 });

        assertTrue(key.getProgramKey().startsWith("program--"));
    }

    @Test
    void createKeySlugifyCollapsesConsecutivePunctuationAndTrimsTrailingDashes() {
        AppConfigItem item = new AppConfigItem();
        item.setName("---Weird!!!Name???---");

        DiskPersistenceKey key = DiskPersistenceSupport.createKey(item, new byte[] { 1 });

        // Consecutive punctuation collapses to a single dash, and only *trailing* dashes are
        // trimmed - a leading dash from the input's own leading punctuation run survives,
        // which is why this is "name--weird-name" (prefix "name-" + slug "-weird-name") and
        // not "name-weird-name".
        assertTrue(key.getProgramKey().startsWith("name--weird-name--"),
                "expected collapsed punctuation and no trailing dash before the '--' separator; got: "
                        + key.getProgramKey());
    }

    @Test
    void createKeyOriginalDiskHashMatchesStableHashHexOfTheSameBytes() {
        AppConfigItem item = new AppConfigItem();
        item.setGameId("game123");
        byte[] diskImage = { 10, 20, 30 };

        DiskPersistenceKey key = DiskPersistenceSupport.createKey(item, diskImage);

        assertEquals(DiskPersistenceSupport.stableHashHex(diskImage), key.getOriginalDiskHash());
    }

    @Test
    void createKeyIsStableForTheSameInputs() {
        AppConfigItem item = new AppConfigItem();
        item.setGameId("game123");
        item.setFilePath("/games/foo.d64");
        item.setEntryName("FOO");
        item.setFileType("DISK");
        byte[] diskImage = { 1, 2, 3, 4 };

        DiskPersistenceKey first = DiskPersistenceSupport.createKey(item, diskImage);
        DiskPersistenceKey second = DiskPersistenceSupport.createKey(item, diskImage);

        assertEquals(first.getProgramKey(), second.getProgramKey());
        assertEquals(first.getOriginalDiskHash(), second.getOriginalDiskHash());
    }

    @Test
    void createKeyDiffersWhenEntryNameDiffersEvenWithTheSameGameId() {
        AppConfigItem item1 = new AppConfigItem();
        item1.setGameId("game123");
        item1.setEntryName("FOO");

        AppConfigItem item2 = new AppConfigItem();
        item2.setGameId("game123");
        item2.setEntryName("BAR");

        DiskPersistenceKey key1 = DiskPersistenceSupport.createKey(item1, new byte[] { 1 });
        DiskPersistenceKey key2 = DiskPersistenceSupport.createKey(item2, new byte[] { 1 });

        assertNotEquals(key1.getProgramKey(), key2.getProgramKey(),
                "the identity hash half of the key should differ when entryName differs");
    }
}
