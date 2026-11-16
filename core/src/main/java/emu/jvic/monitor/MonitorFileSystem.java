package emu.jvic.monitor;

import java.util.List;

/**
 * The file system that the monitor's file commands use (cd, dir, load, save, log,
 * record, playback, and so on). Every method is synchronous: it returns its result,
 * or throws a {@link MonitorFileException}, before it returns. Desktop uses the real
 * file system ({@link JavaIoMonitorFileSystem}); the web uses an in-memory store
 * ({@link InMemoryMonitorFileSystem}) that the TeaVM layer keeps in step with OPFS.
 *
 * <p>Relative paths are resolved against the current directory ({@link #pwd()}).
 */
public interface MonitorFileSystem {

    /** The current directory, as an absolute path in the platform's own form. */
    String pwd();

    /**
     * Resolves a path against the current directory, giving an absolute path in the
     * platform's own form. The file need not exist.
     */
    String absolute(String path);

    /** Changes the current directory. */
    void cd(String path);

    /**
     * Lists a directory, including the "." and ".." entries, as VICE does: directories
     * first, then files, each sorted by name.
     *
     * @param path The directory, or null for the current one.
     */
    List<MonitorFileEntry> list(String path);

    void mkdir(String path);

    void rmdir(String path);

    boolean exists(String path);

    /** Reads a whole file. */
    byte[] read(String path);

    /** Creates or replaces a file. */
    void write(String path, byte[] data);

    /** Adds to the end of a file, creating it if needed (log, record). */
    void append(String path, byte[] data);
}
