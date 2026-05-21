package emu.jvic.io.disk.persistence;

/**
 * Persistence session that always boots from the bundled disk image and ignores writes.
 */
public class NoOpDiskImagePersistenceSession implements DiskImagePersistenceSession {

    private final byte[] startupDiskImage;

    public NoOpDiskImagePersistenceSession(byte[] startupDiskImage) {
        this.startupDiskImage = startupDiskImage;
    }

    @Override
    public byte[] getStartupDiskImage() {
        return startupDiskImage;
    }

    @Override
    public boolean isPersistent() {
        return false;
    }

    @Override
    public void onDiskChanged(byte[] diskImageBytes) {
    }

    @Override
    public void resetToOriginalImage(ResetHandler resetHandler) {
        resetHandler.onResetComplete(startupDiskImage);
    }

    @Override
    public void close() {
    }
}