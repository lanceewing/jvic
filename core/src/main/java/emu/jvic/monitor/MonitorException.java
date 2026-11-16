package emu.jvic.monitor;

/**
 * An error in a monitor command, reported in VICE's format: the message, then the
 * command line indented by two spaces, then a caret under the column at fault.
 *
 * <pre>
 * ERROR -- Unexpected token:
 *   p 5==5
 *      ^
 * </pre>
 */
public class MonitorException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** VICE's message when the command can't be parsed at all at the position. */
    public static final String WRONG_SYNTAX = "Wrong syntax:";

    /** VICE's message when a token is not allowed at the position. */
    public static final String UNEXPECTED_TOKEN = "Unexpected token:";

    /** VICE's message when a closing parenthesis is missing. */
    public static final String PAREN_EXPECTED = "')' expected:";

    /** VICE's message when a condition ends where an operand is needed. */
    public static final String MISSING_OPERAND = "Conditional operation missing an operand:";

    /** VICE's message when a checkpoint number is missing or not a decimal number. */
    public static final String CHECKPOINT_NUMBER_EXPECTED = "Checkpoint number expected:";

    /** VICE's message for an unknown register name in the registers command. */
    public static final String INVALID_REGISTER = "Invalid register.";

    private final int position;

    /**
     * @param message  The message, e.g. one of the constants in this class.
     * @param position The column in the command line to put the caret under.
     */
    public MonitorException(String message, int position) {
        super(message);
        this.position = position;
    }

    public int getPosition() {
        return position;
    }

    /**
     * Formats the error the way VICE does.
     *
     * @param line The command line that was being executed.
     *
     * @return The error text, ending with a newline.
     */
    public String format(String line) {
        StringBuilder text = new StringBuilder();
        text.append("ERROR -- ").append(getMessage()).append('\n');
        text.append("  ").append(line).append('\n');
        for (int i = 0; i < position + 2; i++) {
            text.append(' ');
        }
        text.append("^\n");
        return text.toString();
    }
}
