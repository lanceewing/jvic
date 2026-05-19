package emu.jvic.io.disk.persistence;

import java.util.function.Consumer;

import emu.jvic.config.AppConfigItem;

/**
 * Resolves a persistence session for a disk image before the drive is initialised.
 */
public interface DiskImagePersistence {

    void resolve(AppConfigItem appConfigItem, byte[] originalDiskImage,
            Consumer<DiskImagePersistenceSession> onResolved);
}