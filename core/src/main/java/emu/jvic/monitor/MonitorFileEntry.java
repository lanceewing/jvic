package emu.jvic.monitor;

/**
 * One entry in a directory listing from {@link MonitorFileSystem#list(String)}.
 */
public final class MonitorFileEntry {

    private final String name;
    private final boolean directory;
    private final long size;

    public MonitorFileEntry(String name, boolean directory, long size) {
        this.name = name;
        this.directory = directory;
        this.size = size;
    }

    public String getName() {
        return name;
    }

    public boolean isDirectory() {
        return directory;
    }

    /** The size in bytes; 0 for a directory. */
    public long getSize() {
        return size;
    }
}
