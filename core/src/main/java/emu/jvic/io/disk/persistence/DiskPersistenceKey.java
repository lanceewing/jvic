package emu.jvic.io.disk.persistence;

/**
 * Deterministic key used to map a logical disk program to an OPFS location.
 */
public class DiskPersistenceKey {

    private final String programKey;
    private final String originalDiskHash;

    public DiskPersistenceKey(String programKey, String originalDiskHash) {
        this.programKey = programKey;
        this.originalDiskHash = originalDiskHash;
    }

    public String getProgramKey() {
        return programKey;
    }

    public String getOriginalDiskHash() {
        return originalDiskHash;
    }
}