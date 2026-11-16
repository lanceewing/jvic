package emu.jvic.monitor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A {@link MonitorFileSystem} held in memory: a tree of directories rooted at "/",
 * and a map from path to file contents. The web build uses it in front of OPFS: the
 * TeaVM layer preloads it, and writes back the paths that {@link #takeDirtyPaths()}
 * reports. It has no browser dependencies, so tests use it too.
 *
 * <p>Paths use '/' (a '\' is treated the same). The total size of all files is
 * capped; a write that would go over the cap fails.
 */
public final class InMemoryMonitorFileSystem implements MonitorFileSystem {

    /** The default cap on the total size of all files. */
    public static final long DEFAULT_SIZE_LIMIT = 16L * 1024 * 1024;

    private final Map<String, byte[]> files = new TreeMap<>();
    private final Set<String> directories = new TreeSet<>();
    private final Set<String> dirtyPaths = new LinkedHashSet<>();
    private final long sizeLimit;

    private long totalSize;
    private String cwd = "/";

    public InMemoryMonitorFileSystem() {
        this(DEFAULT_SIZE_LIMIT);
    }

    /**
     * @param sizeLimit The cap on the total size of all files, in bytes.
     */
    public InMemoryMonitorFileSystem(long sizeLimit) {
        this.sizeLimit = sizeLimit;
        directories.add("/");
    }

    /**
     * Resolves a path against the current directory to an absolute, normalised path
     * such as "/dir/file.prg".
     */
    public String resolve(String path) {
        String full = path.replace('\\', '/');
        if (!full.startsWith("/")) {
            full = cwd + "/" + full;
        }
        List<String> parts = new ArrayList<>();
        for (String part : full.split("/")) {
            if (part.isEmpty() || part.equals(".")) {
                continue;
            }
            if (part.equals("..")) {
                if (!parts.isEmpty()) {
                    parts.remove(parts.size() - 1);
                }
            } else {
                parts.add(part);
            }
        }
        if (parts.isEmpty()) {
            return "/";
        }
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            result.append('/').append(part);
        }
        return result.toString();
    }

    private static String parentOf(String path) {
        int slash = path.lastIndexOf('/');
        return (slash <= 0) ? "/" : path.substring(0, slash);
    }

    private static String nameOf(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    @Override
    public String absolute(String path) {
        return resolve(path);
    }

    @Override
    public String pwd() {
        return cwd;
    }

    @Override
    public void cd(String path) {
        String target = resolve(path);
        if (!directories.contains(target)) {
            throw new MonitorFileException(
                    files.containsKey(target) ? MonitorFileException.NOT_A_DIRECTORY : MonitorFileException.NOT_FOUND);
        }
        cwd = target;
    }

    @Override
    public List<MonitorFileEntry> list(String path) {
        String dir = resolve((path == null) ? "." : path);
        if (!directories.contains(dir)) {
            throw new MonitorFileException(MonitorFileException.NOT_FOUND);
        }
        String prefix = dir.equals("/") ? "/" : dir + "/";
        List<MonitorFileEntry> entries = new ArrayList<>();
        entries.add(new MonitorFileEntry(".", true, 0));
        entries.add(new MonitorFileEntry("..", true, 0));
        for (String d : directories) {
            if (!d.equals(dir) && d.startsWith(prefix) && (d.indexOf('/', prefix.length()) < 0)) {
                entries.add(new MonitorFileEntry(nameOf(d), true, 0));
            }
        }
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            String f = file.getKey();
            if (f.startsWith(prefix) && (f.indexOf('/', prefix.length()) < 0)) {
                entries.add(new MonitorFileEntry(nameOf(f), false, file.getValue().length));
            }
        }
        return entries;
    }

    @Override
    public void mkdir(String path) {
        String dir = resolve(path);
        if (directories.contains(dir) || files.containsKey(dir)) {
            throw new MonitorFileException(MonitorFileException.EXISTS);
        }
        if (!directories.contains(parentOf(dir))) {
            throw new MonitorFileException(MonitorFileException.NOT_FOUND);
        }
        directories.add(dir);
        dirtyPaths.add(dir);
    }

    @Override
    public void rmdir(String path) {
        String dir = resolve(path);
        if (!directories.contains(dir)) {
            throw new MonitorFileException(
                    files.containsKey(dir) ? MonitorFileException.NOT_A_DIRECTORY : MonitorFileException.NOT_FOUND);
        }
        if (dir.equals("/") || isAncestorOrSelf(dir, cwd)) {
            throw new MonitorFileException(MonitorFileException.ACCESS_DENIED);
        }
        String prefix = dir + "/";
        for (String d : directories) {
            if (d.startsWith(prefix)) {
                throw new MonitorFileException(MonitorFileException.NOT_EMPTY);
            }
        }
        for (String f : files.keySet()) {
            if (f.startsWith(prefix)) {
                throw new MonitorFileException(MonitorFileException.NOT_EMPTY);
            }
        }
        directories.remove(dir);
        dirtyPaths.add(dir);
    }

    private static boolean isAncestorOrSelf(String dir, String path) {
        return path.equals(dir) || path.startsWith(dir + "/");
    }

    @Override
    public boolean exists(String path) {
        String target = resolve(path);
        return files.containsKey(target) || directories.contains(target);
    }

    @Override
    public byte[] read(String path) {
        String target = resolve(path);
        byte[] data = files.get(target);
        if (data == null) {
            throw new MonitorFileException(
                    directories.contains(target) ? MonitorFileException.IS_A_DIRECTORY : MonitorFileException.NOT_FOUND);
        }
        return data.clone();
    }

    @Override
    public void write(String path, byte[] data) {
        put(resolve(path), data.clone());
    }

    @Override
    public void append(String path, byte[] data) {
        String target = resolve(path);
        byte[] old = files.get(target);
        if (old == null) {
            put(target, data.clone());
            return;
        }
        byte[] joined = new byte[old.length + data.length];
        System.arraycopy(old, 0, joined, 0, old.length);
        System.arraycopy(data, 0, joined, old.length, data.length);
        put(target, joined);
    }

    private void put(String target, byte[] data) {
        if (directories.contains(target)) {
            throw new MonitorFileException(MonitorFileException.IS_A_DIRECTORY);
        }
        if (!directories.contains(parentOf(target))) {
            throw new MonitorFileException(MonitorFileException.NOT_FOUND);
        }
        byte[] old = files.get(target);
        long newTotal = totalSize - ((old != null) ? old.length : 0) + data.length;
        if (newTotal > sizeLimit) {
            throw new MonitorFileException(MonitorFileException.NO_SPACE);
        }
        files.put(target, data);
        totalSize = newTotal;
        dirtyPaths.add(target);
    }

    /** The total size of all files, in bytes. */
    public long getTotalSize() {
        return totalSize;
    }

    /**
     * Adds a file or directory without marking it dirty, e.g. when preloading the
     * store from OPFS. Missing parent directories are created.
     *
     * @param path      An absolute path.
     * @param data      The file's contents, or null to add a directory.
     */
    public void preload(String path, byte[] data) {
        String target = resolve(path);
        String parent = (data == null) ? target : parentOf(target);
        while (!directories.contains(parent)) {
            directories.add(parent);
            parent = parentOf(parent);
        }
        if (data != null) {
            byte[] old = files.put(target, data.clone());
            totalSize += data.length - ((old != null) ? old.length : 0);
        }
    }

    /**
     * Returns the absolute paths changed since the last call, in the order they were
     * changed, and clears the list. A path that no longer exists was removed.
     */
    public List<String> takeDirtyPaths() {
        List<String> paths = new ArrayList<>(dirtyPaths);
        dirtyPaths.clear();
        return paths;
    }

    /** Whether the path is a directory (after resolving it). */
    public boolean isDirectory(String path) {
        return directories.contains(resolve(path));
    }
}
