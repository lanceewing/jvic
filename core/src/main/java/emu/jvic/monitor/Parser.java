package emu.jvic.monitor;

/**
 * Reads the arguments of one monitor command. Scanning is driven by each command,
 * because, as in VICE, what a word means depends on where it is: "d" is a radix name
 * after {@code radix}, but the hex number $0D in an expression, and "foo" in an
 * expression is the number $0F followed by an unexpected "oo".
 *
 * <p>Number syntax, matching VICE 3.10: digits in the current radix; or a prefix
 * that sets the base: {@code $} hex, {@code +} decimal (only when a digit follows,
 * otherwise it is the plus operator), {@code %} binary, {@code &} octal. VICE's
 * {@code p} does not accept {@code 0x}, unary minus, or registers.
 *
 * <p>Expressions support {@code + - * /} and parentheses. {@code *} and {@code /} bind
 * tighter than {@code +} and {@code -}, and operators of the same level are left
 * associative. This is what the xvic 3.10 binary does ({@code p 2 - 3 * 4} is -10),
 * even though its manual says expressions are evaluated strictly left to right.
 */
final class Parser {

    /** Literals above this are rejected, as VICE rejects 8-digit hex values. */
    private static final long MAX_LITERAL = 0x0FFFFFFFL;

    private final String line;
    private final Radix radix;
    private int pos;

    /**
     * @param line  The whole command line, as typed (positions in errors refer to it).
     * @param start The position after the command name.
     * @param radix The current default radix.
     */
    Parser(String line, int start, Radix radix) {
        this.line = line;
        this.pos = start;
        this.radix = radix;
    }

    String getLine() {
        return line;
    }

    /** The current position, after skipping spaces. */
    int position() {
        skipSpaces();
        return pos;
    }

    void skipSpaces() {
        while ((pos < line.length()) && Character.isWhitespace(line.charAt(pos))) {
            pos++;
        }
    }

    boolean atEnd() {
        skipSpaces();
        return pos >= line.length();
    }

    /** The next character after spaces, or 0 at the end of the line. */
    char peek() {
        skipSpaces();
        return (pos < line.length()) ? line.charAt(pos) : 0;
    }

    private char charAt(int index) {
        return (index < line.length()) ? line.charAt(index) : 0;
    }

    /** Moves back to a position previously returned by {@link #position()}. */
    void rewind(int position) {
        pos = position;
    }

    /** Skips the next character, which the caller has already peeked at. */
    void advance() {
        pos++;
    }

    /** Throws an Unexpected token error unless at the end of the line. */
    void expectEnd() {
        if (!atEnd()) {
            throw unexpected();
        }
    }

    /** An Unexpected token error at the current position. */
    MonitorException unexpected() {
        return new MonitorException(MonitorException.UNEXPECTED_TOKEN, position());
    }

    /** A Wrong syntax error at the current position. */
    MonitorException wrongSyntax() {
        return new MonitorException(MonitorException.WRONG_SYNTAX, position());
    }

    /**
     * Reads a word of letters, digits and underscores, or returns an empty string.
     */
    String readWord() {
        skipSpaces();
        int start = pos;
        while (pos < line.length()) {
            char c = line.charAt(pos);
            if (Character.isLetterOrDigit(c) || (c == '_')) {
                pos++;
            } else {
                break;
            }
        }
        return line.substring(start, pos);
    }

    /**
     * Reads everything up to the next space, or returns an empty string.
     */
    String readToken() {
        skipSpaces();
        int start = pos;
        while ((pos < line.length()) && !Character.isWhitespace(line.charAt(pos))) {
            pos++;
        }
        return line.substring(start, pos);
    }

    /**
     * Reads a memspace prefix such as "c:" or "8:", if there is one.
     *
     * @return The memspace name in lower case without the colon (e.g. "c", "8"), or
     *         null if the next token isn't a memspace prefix (nothing is consumed).
     */
    String tryMemspace() {
        skipSpaces();
        int start = pos;
        String word = readWord();
        if (!word.isEmpty() && (charAt(pos) == ':')) {
            pos++;
            return word.toLowerCase();
        }
        pos = start;
        return null;
    }

    /**
     * Reads a "string" in double quotes, or returns null if the next token isn't one.
     */
    String tryString() {
        if (peek() != '"') {
            return null;
        }
        int start = pos;
        int end = line.indexOf('"', pos + 1);
        if (end < 0) {
            throw new MonitorException(MonitorException.WRONG_SYNTAX, start);
        }
        pos = end + 1;
        return line.substring(start + 1, end);
    }

    /**
     * Whether a number starts at the current position.
     */
    boolean atNumber() {
        char c = peek();
        switch (c) {
            case '$':
            case '%':
            case '&':
                return true;
            case '+':
                return Radix.digitValue(charAt(pos + 1), 10) >= 0;
            default:
                return radix.digitValue(c) >= 0;
        }
    }

    /**
     * Reads a number literal.
     *
     * @param first true if this is the first thing in an expression, which decides
     *              which of VICE's two error messages an invalid number gives.
     */
    int parseNumber(boolean first) {
        int start = position();
        int base = radix.getBase();
        switch (charAt(pos)) {
            case '$': base = 16; pos++; break;
            case '%': base = 2; pos++; break;
            case '&': base = 8; pos++; break;
            case '+': base = 10; pos++; break;
            default: break;
        }
        int digitsStart = pos;
        long value = 0;
        while ((pos < line.length()) && (Radix.digitValue(line.charAt(pos), base) >= 0)) {
            value = (value * base) + Radix.digitValue(line.charAt(pos), base);
            if (value > MAX_LITERAL) {
                value = MAX_LITERAL + 1;
            }
            pos++;
        }
        if ((pos == digitsStart) || (value > MAX_LITERAL)) {
            throw new MonitorException(first ? MonitorException.WRONG_SYNTAX : MonitorException.UNEXPECTED_TOKEN,
                    start);
        }
        return (int) value;
    }

    /**
     * Reads an expression.
     */
    int parseExpression() {
        return parseSum(true);
    }

    private int parseSum(boolean first) {
        int value = parseProduct(first);
        while (true) {
            char c = peek();
            if (c == '-') {
                pos++;
                value -= parseProduct(false);
            } else if ((c == '+') && (Radix.digitValue(charAt(pos + 1), 10) < 0)) {
                // A '+' followed by a digit is a decimal number, not the operator.
                pos++;
                value += parseProduct(false);
            } else {
                return value;
            }
        }
    }

    private int parseProduct(boolean first) {
        int value = parseFactor(first);
        while (true) {
            char c = peek();
            if (c == '*') {
                pos++;
                value *= parseFactor(false);
            } else if (c == '/') {
                int operatorPos = pos;
                pos++;
                int divisor = parseFactor(false);
                if (divisor == 0) {
                    // VICE 3.10 silently gives 1. Reporting it is more useful.
                    throw new MonitorException("Division by zero.", operatorPos);
                }
                value /= divisor;
            } else {
                return value;
            }
        }
    }

    private int parseFactor(boolean first) {
        if (peek() == '(') {
            pos++;
            int value = parseSum(first);
            if (peek() != ')') {
                throw new MonitorException(MonitorException.PAREN_EXPECTED, position());
            }
            pos++;
            return value;
        }
        if (atNumber()) {
            return parseNumber(first);
        }
        throw first ? wrongSyntax() : unexpected();
    }

    /**
     * Whether an address starts at the current position (a memspace prefix, a number
     * or a parenthesised expression).
     */
    boolean atAddress() {
        int start = pos;
        boolean memspace = (tryMemspace() != null);
        boolean result = memspace || atNumber() || (peek() == '(');
        pos = start;
        return result;
    }

    /**
     * Reads an address: an optional memspace prefix, then a number or a parenthesised
     * expression. As in VICE, an unparenthesised expression isn't allowed, so
     * "m 1000 + 2" is an error at the '+'.
     *
     * @return The address; its memspace (or null) is available from
     *         {@link #getLastMemspace()}.
     */
    int parseAddress() {
        lastMemspace = tryMemspace();
        int value;
        if (peek() == '(') {
            value = parseFactor(true);
        } else if (atNumber()) {
            value = parseNumber(false);
        } else {
            throw unexpected();
        }
        return value & 0xFFFF;
    }

    private String lastMemspace;

    /** The memspace prefix of the last address read, or null if it had none. */
    String getLastMemspace() {
        return lastMemspace;
    }
}
