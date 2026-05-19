package emu.jvic.io.disk.persistence;

import java.util.function.Consumer;

import emu.jvic.config.AppConfigItem;

/**
 * Default persistence implementation for platforms with no disk-image storage.
 */
public class NoOpDiskImagePersistence implements DiskImagePersistence {

    @Override
    public void resolve(AppConfigItem appConfigItem, byte[] originalDiskImage,
            Consumer<DiskImagePersistenceSession> onResolved) {
        onResolved.accept(new NoOpDiskImagePersistenceSession(originalDiskImage));
    }
}