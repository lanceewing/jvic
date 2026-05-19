package emu.jvic.io.disk.persistence;

/**
 * Represents the persistence lifecycle for a single mounted disk image.
 */
public interface DiskImagePersistenceSession {

    byte[] getStartupDiskImage();

    boolean isPersistent();

    void onDiskChanged(byte[] diskImageBytes);

    void close();
}