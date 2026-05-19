package emu.jvic.lwjgl3.disk;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import emu.jvic.config.AppConfigItem;
import emu.jvic.io.disk.persistence.DiskImagePersistenceSession;
import emu.jvic.io.disk.persistence.DiskPersistenceKey;
import emu.jvic.io.disk.persistence.DiskPersistenceMetadata;
import emu.jvic.io.disk.persistence.DiskPersistenceSupport;

/**
 * Desktop persistence session backed by a sidecar D64 in the user data directory.
 */
public class DesktopDiskImagePersistenceSession implements DiskImagePersistenceSession {

    private final AppConfigItem appConfigItem;
    private final DiskPersistenceKey key;
    private final DesktopPersistencePaths persistencePaths;
    private final byte[] startupDiskImage;
    private final int originalDiskSize;
    private final String programIdSource;

    private boolean persistent;
    private long createdAtEpochMs;
    private long persistenceActivatedAtEpochMs;

    public DesktopDiskImagePersistenceSession(AppConfigItem appConfigItem,
            DiskPersistenceKey key, DesktopPersistencePaths persistencePaths,
            byte[] startupDiskImage, boolean persistent, int originalDiskSize,
            String programIdSource) {
        this.appConfigItem = appConfigItem;
        this.key = key;
        this.persistencePaths = persistencePaths;
        this.startupDiskImage = startupDiskImage;
        this.persistent = persistent;
        this.originalDiskSize = originalDiskSize;
        this.programIdSource = programIdSource;

        if (persistent) {
            long now = System.currentTimeMillis();
            this.createdAtEpochMs = now;
            this.persistenceActivatedAtEpochMs = now;
        }
    }

    @Override
    public byte[] getStartupDiskImage() {
        return startupDiskImage;
    }

    @Override
    public boolean isPersistent() {
        return persistent;
    }

    @Override
    public void onDiskChanged(byte[] diskImageBytes) {
        long now = System.currentTimeMillis();
        if (!persistent) {
            persistent = true;
            createdAtEpochMs = now;
            persistenceActivatedAtEpochMs = now;
        }

        try {
            ensureDirectories();
            writeDiskImage(diskImageBytes);
            writeMetadata(diskImageBytes, now);
        } catch (IOException e) {
            // Ignore persistence errors and keep the emulator running on desktop.
        }
    }

    @Override
    public void close() {
    }

    private void ensureDirectories() throws IOException {
        Files.createDirectories(persistencePaths.getDiskDirectory(key));
    }

    private void writeDiskImage(byte[] diskImageBytes) throws IOException {
        atomicWrite(persistencePaths.getDiskImageFile(key), diskImageBytes);
    }

    private void writeMetadata(byte[] diskImageBytes, long now) throws IOException {
        DiskPersistenceMetadata metadata = new DiskPersistenceMetadata(appConfigItem, key,
                programIdSource, originalDiskSize);
        metadata.setCreatedAtEpochMs(createdAtEpochMs);
        metadata.setUpdatedAtEpochMs(now);
        metadata.setPersistenceActivatedAtEpochMs(persistenceActivatedAtEpochMs);
        metadata.setPersistedDiskHash(DiskPersistenceSupport.stableHashHex(diskImageBytes));
        atomicWrite(persistencePaths.getMetadataFile(key),
                metadata.toJson().getBytes(StandardCharsets.UTF_8));
    }

    private void atomicWrite(Path target, byte[] data) throws IOException {
        Path tempFile = target.resolveSibling(target.getFileName().toString() + ".tmp");
        Files.write(tempFile, data);
        try {
            Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}