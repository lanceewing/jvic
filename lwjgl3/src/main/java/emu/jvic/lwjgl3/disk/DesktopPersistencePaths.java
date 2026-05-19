package emu.jvic.lwjgl3.disk;

import java.nio.file.Path;
import java.nio.file.Paths;

import emu.jvic.io.disk.persistence.DiskPersistenceKey;

/**
 * Resolves the desktop storage paths used for persisted disk images.
 */
public final class DesktopPersistencePaths {

    private final Path rootDirectory;
    private final Path diskImagesRootDirectory;
    private final Path versionDirectory;

    private DesktopPersistencePaths(Path rootDirectory) {
        this.rootDirectory = rootDirectory;
        this.diskImagesRootDirectory = rootDirectory.resolve("Disk Images");
        this.versionDirectory = diskImagesRootDirectory.resolve("v1");
    }

    public static DesktopPersistencePaths createDefault() {
        return new DesktopPersistencePaths(resolveRootDirectory());
    }

    public Path getRootDirectory() {
        return rootDirectory;
    }

    public Path getDiskImagesRootDirectory() {
        return diskImagesRootDirectory;
    }

    public Path getVersionDirectory() {
        return versionDirectory;
    }

    public Path getDiskDirectory(DiskPersistenceKey key) {
        return versionDirectory.resolve(key.getProgramKey()).resolve(key.getOriginalDiskHash());
    }

    public Path getDiskImageFile(DiskPersistenceKey key) {
        return getDiskDirectory(key).resolve("disk.d64");
    }

    public Path getMetadataFile(DiskPersistenceKey key) {
        return getDiskDirectory(key).resolve("meta.json");
    }

    private static Path resolveRootDirectory() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        String userHome = System.getProperty("user.home", ".");

        if (osName.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if ((localAppData != null) && !localAppData.trim().isEmpty()) {
                return Paths.get(localAppData, "JVic");
            }
            return Paths.get(userHome, "AppData", "Local", "JVic");
        }

        if (osName.contains("mac")) {
            return Paths.get(userHome, "Library", "Application Support", "JVic");
        }

        String xdgDataHome = System.getenv("XDG_DATA_HOME");
        if ((xdgDataHome != null) && !xdgDataHome.trim().isEmpty()) {
            return Paths.get(xdgDataHome, "JVic");
        }
        return Paths.get(userHome, ".local", "share", "JVic");
    }
}