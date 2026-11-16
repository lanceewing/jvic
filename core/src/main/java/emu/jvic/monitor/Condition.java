package emu.jvic.monitor;

import emu.jvic.cpu.Cpu6502;
import emu.jvic.video.Vic;

/**
 * A checkpoint condition, such as {@code A == $05 && X > 3}, parsed into a tree that
 * can be evaluated each time the checkpoint is hit, and printed back in VICE's
 * normalised form ({@code A == $05 && X > $03}).
 *
 * <p>Operands are numbers (in the current radix, with the usual prefixes) and the
 * registers A, X, Y, PC, SP and FL (status flags), plus RL and CY (the VIC's raster
 * line and cycle), optionally with a memspace prefix such as {@code 8:A}. Register
 * names take priority over hex digits, so {@code A} is the accumulator, as in VICE.
 *
 * <p>Operators, lowest precedence first: {@code ||}, {@code &&}, {@code |}, {@code &},
 * {@code == !=}, {@code < > <= >=}, {@code + -}, {@code * /}, as in C. Comparisons and
 * logical operators give 1 or 0; a condition is true when it is non-zero.
 *
 * <p>Differences from VICE 3.10: RL works (VICE 3.10 rejects it), and a memspace prefix
 * is kept when the condition is printed (VICE drops it).
 */
final class Condition {

    private static final String[] REGISTERS = { "A", "X", "Y", "PC", "SP", "FL", "RL", "CY" };

    private static final String[][] LEVELS = {
        { "||" }, { "&&" }, { "|" }, { "&" }, { "==", "!=" }, { "<=", ">=", "<", ">" }, { "+", "-" }, { "*", "/" },
    };

    /** A node of the condition tree. */
    private abstract static class Node {
        abstract int evaluate(MonitorEngine engine, AddressSpace space);

        abstract void text(StringBuilder text);
    }

    private static final class NumberNode extends Node {
        final int value;

        NumberNode(int value) {
            this.value = value;
        }

        @Override
        int evaluate(MonitorEngine engine, AddressSpace space) {
            return value;
        }

        @Override
        void text(StringBuilder text) {
            text.append('$').append(((value >= 0) && (value < 0x100)) ? Format.hex2(value) : Format.hex(value, 4));
        }
    }

    private static final class RegisterNode extends Node {
        final String memspace;
        final String name;

        RegisterNode(String memspace, String name) {
            this.memspace = memspace;
            this.name = name;
        }

        @Override
        int evaluate(MonitorEngine engine, AddressSpace space) {
            AddressSpace target = (memspace != null) ? engine.getSpace(memspace, 0) : space;
            Cpu6502 cpu = target.getCpu();
            Vic vic = engine.getMainSpace().getVic();
            switch (name) {
                case "A": return cpu.getAccumulator();
                case "X": return cpu.getIndexRegisterX();
                case "Y": return cpu.getIndexRegisterY();
                case "PC": return target.getDisplayPc();
                case "SP": return cpu.getStackPointer();
                case "FL": return cpu.getProcessorStatus();
                case "RL": return vic.getRasterLine();
                default: return vic.getRasterCycle();
            }
        }

        @Override
        void text(StringBuilder text) {
            if (memspace != null) {
                text.append(memspace.toUpperCase()).append(':');
            }
            text.append(name);
        }
    }

    private static final class ParenNode extends Node {
        final Node inner;

        ParenNode(Node inner) {
            this.inner = inner;
        }

        @Override
        int evaluate(MonitorEngine engine, AddressSpace space) {
            return inner.evaluate(engine, space);
        }

        @Override
        void text(StringBuilder text) {
            text.append("( ");
            inner.text(text);
            text.append(" )");
        }
    }

    private static final class BinaryNode extends Node {
        final String operator;
        final Node left;
        final Node right;

        BinaryNode(String operator, Node left, Node right) {
            this.operator = operator;
            this.left = left;
            this.right = right;
        }

        @Override
        int evaluate(MonitorEngine engine, AddressSpace space) {
            int l = left.evaluate(engine, space);
            if (operator.equals("&&") && (l == 0)) {
                return 0;
            }
            if (operator.equals("||") && (l != 0)) {
                return 1;
            }
            int r = right.evaluate(engine, space);
            switch (operator) {
                case "||":
                case "&&": return (r != 0) ? 1 : 0;
                case "|": return l | r;
                case "&": return l & r;
                case "==": return (l == r) ? 1 : 0;
                case "!=": return (l != r) ? 1 : 0;
                case "<=": return (l <= r) ? 1 : 0;
                case ">=": return (l >= r) ? 1 : 0;
                case "<": return (l < r) ? 1 : 0;
                case ">": return (l > r) ? 1 : 0;
                case "+": return l + r;
                case "-": return l - r;
                case "*": return l * r;
                default: return (r == 0) ? 0 : l / r;
            }
        }

        @Override
        void text(StringBuilder text) {
            left.text(text);
            text.append(' ').append(operator).append(' ');
            right.text(text);
        }
    }

    private final Node root;
    private final String text;

    private Condition(Node root) {
        this.root = root;
        StringBuilder builder = new StringBuilder();
        root.text(builder);
        this.text = builder.toString();
    }

    /** The condition in VICE's normalised form, e.g. "A == $05". */
    String getText() {
        return text;
    }

    /**
     * Whether the condition holds now.
     *
     * @param space The memspace of the checkpoint, whose CPU's registers are used
     *              unless a register has its own memspace prefix.
     */
    boolean isTrue(MonitorEngine engine, AddressSpace space) {
        return root.evaluate(engine, space) != 0;
    }

    /**
     * Parses a condition from the parser's position to the end of the line.
     */
    static Condition parse(Parser args) {
        if (args.atEnd()) {
            throw new MonitorException(MonitorException.MISSING_OPERAND, args.position());
        }
        Node root = parseLevel(args, 0);
        args.expectEnd();
        return new Condition(root);
    }

    private static Node parseLevel(Parser args, int level) {
        if (level == LEVELS.length) {
            return parseOperand(args);
        }
        Node node = parseLevel(args, level + 1);
        while (true) {
            String operator = matchOperator(args, LEVELS[level]);
            if (operator == null) {
                return node;
            }
            node = new BinaryNode(operator, node, parseLevel(args, level + 1));
        }
    }

    /**
     * Consumes one of the operators if it is next. A single '&' or '|' is not taken
     * when it is the start of '&&' or '||', and '+' followed by a digit is a decimal
     * number, not the operator.
     */
    private static String matchOperator(Parser args, String[] operators) {
        int position = args.position();
        String rest = args.getLine().substring(position);
        for (String operator : operators) {
            if (!rest.startsWith(operator)) {
                continue;
            }
            char after = (rest.length() > operator.length()) ? rest.charAt(operator.length()) : 0;
            if ((operator.equals("&") && (after == '&')) || (operator.equals("|") && (after == '|'))) {
                continue;
            }
            if ((operator.equals("<") || operator.equals(">")) && (after == '=')) {
                continue;
            }
            if (operator.equals("+") && (Radix.digitValue(after, 10) >= 0)) {
                continue;
            }
            args.rewind(position + operator.length());
            return operator;
        }
        return null;
    }

    private static Node parseOperand(Parser args) {
        if (args.atEnd()) {
            throw new MonitorException(MonitorException.MISSING_OPERAND, args.position());
        }
        if (args.peek() == '(') {
            args.advance();
            Node inner = parseLevel(args, 0);
            if (args.peek() != ')') {
                throw new MonitorException(MonitorException.PAREN_EXPECTED, args.position());
            }
            args.advance();
            return new ParenNode(inner);
        }
        int position = args.position();
        String memspace = args.tryMemspace();
        String word = args.readWord().toUpperCase();
        for (String register : REGISTERS) {
            if (register.equals(word)) {
                return new RegisterNode(memspace, register);
            }
        }
        if (memspace != null) {
            throw new MonitorException(MonitorException.UNEXPECTED_TOKEN, position);
        }
        args.rewind(position);
        if (args.atNumber()) {
            return new NumberNode(args.parseNumber(false));
        }
        throw args.unexpected();
    }
}
