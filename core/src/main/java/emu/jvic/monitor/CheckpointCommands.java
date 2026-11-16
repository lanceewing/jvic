package emu.jvic.monitor;

/**
 * The checkpoint commands: break, watch, trace, until, enable, disable, delete,
 * condition and ignore. Output matches VICE 3.10's. The syntax lines are VICE's; the
 * help descriptions are jvic's own.
 */
final class CheckpointCommands {

    private CheckpointCommands() {
    }

    /**
     * Reads a checkpoint number. As in VICE, it is always decimal, whatever the radix.
     */
    static int parseCheckpointNumber(Parser args) {
        int position = args.position();
        String word = args.readWord();
        if (word.isEmpty() || !word.chars().allMatch(Character::isDigit) || (word.length() > 9)) {
            throw new MonitorException(MonitorException.CHECKPOINT_NUMBER_EXPECTED, position);
        }
        return Integer.parseInt(word);
    }

    private static String notValid(int number) {
        return "#" + number + " not a valid checkpoint\n";
    }

    /**
     * break, watch and trace: "[load|store|exec]... [address [address]] [if condition]".
     * With no arguments, lists all checkpoints.
     */
    static final class Set extends MonitorCommand {

        private final int defaultOperations;
        private final boolean stop;

        Set(String name, String alias, String description, int defaultOperations, boolean stop) {
            super(name, alias, name + " [load|store|exec] [address [address] [if <cond_expr>]]", description);
            this.defaultOperations = defaultOperations;
            this.stop = stop;
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            CheckpointManager checkpoints = engine.getCheckpoints();
            if (args.atEnd()) {
                out.print(checkpoints.list());
                return;
            }
            int operations = 0;
            while (true) {
                int position = args.position();
                String word = args.readWord().toLowerCase();
                if (word.equals("load")) {
                    operations |= Checkpoint.LOAD;
                } else if (word.equals("store")) {
                    operations |= Checkpoint.STORE;
                } else if (word.equals("exec")) {
                    operations |= Checkpoint.EXEC;
                } else {
                    args.rewind(position);
                    break;
                }
            }
            if (args.atEnd()) {
                throw args.unexpected();
            }
            int position = args.position();
            int start = args.parseAddress();
            AddressSpace space = engine.getSpace(args.getLastMemspace(), position);
            int end = start;
            if (!args.atEnd() && !atIf(args)) {
                end = args.parseAddress();
            }
            Condition condition = null;
            if (!args.atEnd()) {
                if (!atIf(args)) {
                    throw args.unexpected();
                }
                args.readWord();
                condition = Condition.parse(args);
            }
            Checkpoint checkpoint = checkpoints.add(space, start, end,
                    (operations != 0) ? operations : defaultOperations, stop, false);
            out.print(checkpoint.describe());
            if (condition != null) {
                checkpoint.setCondition(condition);
                out.print("Setting checkpoint " + checkpoint.getNumber() + " condition to: " + condition.getText()
                        + "\n");
            }
        }

        private static boolean atIf(Parser args) {
            int position = args.position();
            boolean result = args.readWord().equalsIgnoreCase("if");
            args.rewind(position);
            return result;
        }
    }

    static Set breakCommand() {
        return new Set("break", "bk",
                "Sets a breakpoint, which stops the machine when the CPU executes an\n"
                + "instruction in the address range, or loads or stores there if load or\n"
                + "store is given. 'if' adds a condition. With no arguments, lists all\n"
                + "checkpoints.",
                Checkpoint.EXEC, true);
    }

    static Set watchCommand() {
        return new Set("watch", "w",
                "Sets a watchpoint, which stops the machine after the CPU loads from or\n"
                + "stores to an address in the range (both, unless load or store is\n"
                + "given). 'if' adds a condition. With no arguments, lists all\n"
                + "checkpoints.",
                Checkpoint.LOAD | Checkpoint.STORE, true);
    }

    static Set traceCommand() {
        return new Set("trace", "tr",
                "Sets a tracepoint, which prints the CPU's state and carries on when the\n"
                + "CPU executes, loads or stores in the range (all three, unless given).\n"
                + "'if' adds a condition. With no arguments, lists all checkpoints.",
                Checkpoint.LOAD | Checkpoint.STORE | Checkpoint.EXEC, false);
    }

    static final class Until extends MonitorCommand {

        Until() {
            super("until", "un", "until [<address>]",
                    "Sets a temporary breakpoint at the address and resumes. The breakpoint\n"
                    + "is deleted when it is hit. With no address, lists all checkpoints.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            if (args.atEnd()) {
                out.print(engine.getCheckpoints().list());
                return;
            }
            int position = args.position();
            int address = args.parseAddress();
            AddressSpace space = engine.getSpace(args.getLastMemspace(), position);
            args.expectEnd();
            Checkpoint checkpoint = engine.getCheckpoints().add(space, address, address, Checkpoint.EXEC, true, true);
            out.print(checkpoint.describe());
            engine.resume();
        }
    }

    /** enable and disable. */
    static final class Enable extends MonitorCommand {

        private final boolean enable;

        Enable(boolean enable) {
            super(enable ? "enable" : "disable", enable ? "en" : "dis",
                    (enable ? "enable" : "disable") + " <checkpoint num>",
                    (enable ? "Enables" : "Disables") + " the checkpoint. With no number, "
                            + (enable ? "enables" : "disables") + " all checkpoints.");
            this.enable = enable;
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            CheckpointManager checkpoints = engine.getCheckpoints();
            if (args.atEnd()) {
                for (Checkpoint checkpoint : checkpoints.getAll()) {
                    checkpoint.setEnabled(enable);
                }
                checkpoints.refresh();
                out.print("Set all checkpoints to state: " + (enable ? "enabled" : "disabled") + "\n");
                return;
            }
            int number = parseCheckpointNumber(args);
            args.expectEnd();
            Checkpoint checkpoint = checkpoints.find(number);
            if (checkpoint == null) {
                out.print(notValid(number));
                return;
            }
            checkpoint.setEnabled(enable);
            checkpoints.refresh();
        }
    }

    static final class Delete extends MonitorCommand {

        Delete() {
            super("delete", "del", "delete <checkpoint num>",
                    "Deletes the checkpoint. With no number, deletes all checkpoints.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            CheckpointManager checkpoints = engine.getCheckpoints();
            if (args.atEnd()) {
                checkpoints.removeAll();
                out.print("Deleting all checkpoints\n");
                return;
            }
            int number = parseCheckpointNumber(args);
            args.expectEnd();
            Checkpoint checkpoint = checkpoints.find(number);
            if (checkpoint == null) {
                out.print(notValid(number));
                return;
            }
            checkpoints.remove(checkpoint);
        }
    }

    static final class ConditionCommand extends MonitorCommand {

        ConditionCommand() {
            super("condition", "cond", "condition <checkpoint num> if <cond_expr>",
                    "Sets the checkpoint's condition. The checkpoint is only hit when the\n"
                    + "condition is true. Operands are numbers and the registers A, X, Y, PC,\n"
                    + "SP, FL, RL (raster line) and CY (raster cycle). Operators are\n"
                    + "== != < > <= >= && || & | + - * / and parentheses.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            int number = parseCheckpointNumber(args);
            int position = args.position();
            if (!args.readWord().equalsIgnoreCase("if")) {
                // VICE gives this message here too.
                throw new MonitorException(MonitorException.CHECKPOINT_NUMBER_EXPECTED, position);
            }
            Condition condition = Condition.parse(args);
            Checkpoint checkpoint = engine.getCheckpoints().find(number);
            if (checkpoint == null) {
                out.print(notValid(number));
                return;
            }
            checkpoint.setCondition(condition);
            out.print("Setting checkpoint " + number + " condition to: " + condition.getText() + "\n");
        }
    }

    static final class Ignore extends MonitorCommand {

        Ignore() {
            super("ignore", null, "ignore <checkpoint num> [<count>]",
                    "Ignores the next count hits of the checkpoint (hits where its condition\n"
                    + "is false don't count).");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            int number = parseCheckpointNumber(args);
            // VICE 3.10 sets the count to -1 when it is left out; jvic uses 0.
            int count = args.atEnd() ? 0 : args.parseExpression();
            args.expectEnd();
            Checkpoint checkpoint = engine.getCheckpoints().find(number);
            if (checkpoint == null) {
                out.print(notValid(number));
                return;
            }
            checkpoint.setIgnoreCount(Math.max(count, 0));
            out.print("Will ignore the next " + count + " hits of checkpoint #" + number + "\n");
        }
    }
}
