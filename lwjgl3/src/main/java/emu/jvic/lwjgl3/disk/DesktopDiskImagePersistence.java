package emu.jvic.lwjgl3.disk;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;

import emu.jvic.config.AppConfigItem;
import emu.jvic.config.AppConfigItem.FileLocation;
import emu.jvic.io.disk.persistence.DiskImagePersistence;
import emu.jvic.io.disk.persistence.DiskImagePersistenceSession;
import emu.jvic.io.disk.persistence.DiskPersistenceKey;
import emu.jvic.io.disk.persistence.DiskPersistenceSupport;
import emu.jvic.io.disk.persistence.NoOpDiskImagePersistenceSession;

/**
 * Desktop implementation of disk-image persistence using app-data sidecar files.
 */
public class DesktopDiskImagePersistence implements DiskImagePersistence {

    private final DesktopPersistencePaths persistencePaths;

    public DesktopDiskImagePersistence() {
        this(DesktopPersistencePaths.createDefault());
    }

    public DesktopDiskImagePersistence(DesktopPersistencePaths persistencePaths) {
        this.persistencePaths = persistencePaths;
    }

    @Override
    public void resolve(AppConfigItem appConfigItem, byte[] originalDiskImage,
            Consumer<DiskImagePersistenceSession> onResolved) {
        if (originalDiskImage == null) {
            onResolved.accept(new NoOpDiskImagePersistenceSession(null));
            return;
        }

        String normalizedPath = normalizeIdentityPath(appConfigItem);
        DiskPersistenceKey key = DiskPersistenceSupport.createKey(appConfigItem,
                originalDiskImage, normalizedPath);
        String programIdSource = DiskPersistenceSupport.getProgramIdSource(appConfigItem,
                normalizedPath);

        byte[] startupDiskImage = originalDiskImage;
        boolean persistent = false;

        try {
            Path persistedDiskImageFile = persistencePaths.getDiskImageFile(key);
            if (Files.isRegularFile(persistedDiskImageFile)) {
                byte[] persistedDiskImage = Files.readAllBytes(persistedDiskImageFile);
                if ((persistedDiskImage != null) && (persistedDiskImage.length > 0)) {
                    startupDiskImage = persistedDiskImage;
                    persistent = true;
                }
            }
        } catch (IOException e) {
            startupDiskImage = originalDiskImage;
            persistent = false;
        }

        DesktopDiskImagePersistenceSession session = new DesktopDiskImagePersistenceSession(appConfigItem, key,
            persistencePaths, originalDiskImage, startupDiskImage, persistent,
                originalDiskImage.length, programIdSource);

        if (!persistent && (appConfigItem.getDiskWriteMode() == AppConfigItem.DiskWriteMode.PERSIST)) {
            session.onDiskChanged(originalDiskImage);
        }

        onResolved.accept(session);
    }

    private String normalizeIdentityPath(AppConfigItem appConfigItem) {
        FileLocation fileLocation = appConfigItem.getFileLocation();
        if ((fileLocation != FileLocation.ABSOLUTE) && (fileLocation != FileLocation.LOCAL)) {
            return null;
        }

        String filePath = appConfigItem.getFilePath();
        if ((filePath == null) || filePath.trim().isEmpty()) {
            return null;
        }

        try {
            return Paths.get(filePath).toRealPath().toString();
        } catch (IOException e) {
            return Paths.get(filePath).toAbsolutePath().normalize().toString();
        }
    }
}