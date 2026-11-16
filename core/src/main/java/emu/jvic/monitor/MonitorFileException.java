package emu.jvic.monitor;

/**
 * A {@link MonitorFileSystem} operation failed. The message is the reason in the
 * form VICE prints after a failed mkdir or rmdir, e.g. "2: No such file or
 * directory" (a C errno and its text).
 */
public class MonitorFileException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public static final String NOT_FOUND = "2: No such file or directory";
    public static final String ACCESS_DENIED = "13: Permission denied";
    public static final String EXISTS = "17: File exists";
    public static final String NOT_A_DIRECTORY = "20: Not a directory";
    public static final String IS_A_DIRECTORY = "21: Is a directory";
    public static final String NO_SPACE = "28: No space left on device";
    /** ENOTEMPTY is 41 in the Windows C runtime, which VICE for Windows uses. */
    public static final String NOT_EMPTY = "41: Directory not empty";

    public MonitorFileException(String message) {
        super(message);
    }
}
