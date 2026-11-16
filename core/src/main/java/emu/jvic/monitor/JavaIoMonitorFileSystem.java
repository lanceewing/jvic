package emu.jvic.monitor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A {@link MonitorFileSystem} over the real file system, for desktop and headless
 * jvic. Java can't change the process's working directory, so this keeps its own,
 * starting at the process's working directory.
 *
 * <p>Only desktop code creates this class. It is in core so that the monitor tests
 * can run the file commands against both file systems; TeaVM never reaches it, and
 * GWT doesn't compile the monitor package.
 */
public final class JavaIoMonitorFileSystem implements MonitorFileSystem {

    private File cwd;

    public JavaIoMonitorFileSystem() {
        this(new File(System.getProperty("user.dir")));
    }

    /**
     * @param cwd The directory to start in.
     */
    public JavaIoMonitorFileSystem(File cwd) {
        File dir = cwd.getAbsoluteFile();
        try {
            dir = dir.getCanonicalFile();
        } catch (IOException e) {
            // Use the absolute path.
        }
        this.cwd = dir;
    }

    /** Resolves a path against the current directory. */
    public File resolve(String path) {
        File file = new File(path);
        if (!file.isAbsolute()) {
            file = new File(cwd, path);
        }
        try {
            return file.getCanonicalFile();
        } catch (IOException e) {
            return file.getAbsoluteFile();
        }
    }

    @Override
    public String absolute(String path) {
        return resolve(path).getPath();
    }

    @Override
    public String pwd() {
        return cwd.getPath();
    }

    @Override
    public void cd(String path) {
        File dir = resolve(path);
        if (!dir.isDirectory()) {
            throw new MonitorFileException(dir.exists() ? MonitorFileException.NOT_A_DIRECTORY
                    : MonitorFileException.NOT_FOUND);
        }
        cwd = dir;
    }

    @Override
    public List<MonitorFileEntry> list(String path) {
        File dir = (path == null) ? cwd : resolve(path);
        File[] children = dir.listFiles();
        if (children == null) {
            throw new MonitorFileException(MonitorFileException.NOT_FOUND);
        }
        Arrays.sort(children, (a, b) -> a.getName().compareTo(b.getName()));
        List<MonitorFileEntry> entries = new ArrayList<>();
        entries.add(new MonitorFileEntry(".", true, 0));
        entries.add(new MonitorFileEntry("..", true, 0));
        for (File child : children) {
            if (child.isDirectory()) {
                entries.add(new MonitorFileEntry(child.getName(), true, 0));
            }
        }
        for (File child : children) {
            if (!child.isDirectory()) {
                entries.add(new MonitorFileEntry(child.getName(), false, child.length()));
            }
        }
        return entries;
    }

    @Override
    public void mkdir(String path) {
        File dir = resolve(path);
        if (dir.exists()) {
            throw new MonitorFileException(MonitorFileException.EXISTS);
        }
        if ((dir.getParentFile() != null) && !dir.getParentFile().isDirectory()) {
            throw new MonitorFileException(MonitorFileException.NOT_FOUND);
        }
        if (!dir.mkdir()) {
            throw new MonitorFileException(MonitorFileException.ACCESS_DENIED);
        }
    }

    @Override
    public void rmdir(String path) {
        File dir = resolve(path);
        if (!dir.exists()) {
            throw new MonitorFileException(MonitorFileException.NOT_FOUND);
        }
        if (!dir.isDirectory()) {
            throw new MonitorFileException(MonitorFileException.NOT_A_DIRECTORY);
        }
        String[] children = dir.list();
        if ((children != null) && (children.length > 0)) {
            throw new MonitorFileException(MonitorFileException.NOT_EMPTY);
        }
        if (!dir.delete()) {
            throw new MonitorFileException(MonitorFileException.ACCESS_DENIED);
        }
    }

    @Override
    public boolean exists(String path) {
        return resolve(path).exists();
    }

    @Override
    public byte[] read(String path) {
        File file = resolve(path);
        if (file.isDirectory()) {
            throw new MonitorFileException(MonitorFileException.IS_A_DIRECTORY);
        }
        try {
            return Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            throw new MonitorFileException(file.exists() ? MonitorFileException.ACCESS_DENIED
                    : MonitorFileException.NOT_FOUND);
        }
    }

    @Override
    public void write(String path, byte[] data) {
        output(path, data, false);
    }

    @Override
    public void append(String path, byte[] data) {
        output(path, data, true);
    }

    private void output(String path, byte[] data, boolean append) {
        File file = resolve(path);
        if (file.isDirectory()) {
            throw new MonitorFileException(MonitorFileException.IS_A_DIRECTORY);
        }
        try (OutputStream out = new FileOutputStream(file, append)) {
            out.write(data);
        } catch (IOException e) {
            File parent = file.getParentFile();
            throw new MonitorFileException(((parent != null) && !parent.isDirectory())
                    ? MonitorFileException.NOT_FOUND : MonitorFileException.ACCESS_DENIED);
        }
    }
}
