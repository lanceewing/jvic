package emu.jvic.io.disk.persistence;

/**
 * Represents the persistence lifecycle for a single mounted disk image.
 */
public interface DiskImagePersistenceSession {

    interface ResetHandler {

        void onResetComplete(byte[] diskImageBytes);

        void onResetFailed();
    }

    byte[] getStartupDiskImage();

    boolean isPersistent();

    void onDiskChanged(byte[] diskImageBytes);

    void resetToOriginalImage(ResetHandler resetHandler);

    void close();
}