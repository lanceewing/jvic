package emu.jvic.monitor;

/**
 * A monitor command: its names and help text, and what it does.
 */
abstract class MonitorCommand {

    private final String name;
    private final String alias;
    private final String syntax;
    private final String description;

    /**
     * @param name        The full name, e.g. "registers".
     * @param alias       The abbreviation, e.g. "r", or null if none.
     * @param syntax      The syntax line, as VICE prints it after "Syntax: ".
     * @param description The help text, already wrapped to fit 80 columns.
     */
    MonitorCommand(String name, String alias, String syntax, String description) {
        this.name = name;
        this.alias = alias;
        this.syntax = syntax;
        this.description = description;
    }

    String getName() {
        return name;
    }

    String getAlias() {
        return alias;
    }

    /** The entry in the help command list, e.g. "registers (r)". */
    String getListEntry() {
        return (alias != null) ? name + " (" + alias + ")" : name;
    }

    boolean matches(String word) {
        return name.equalsIgnoreCase(word) || ((alias != null) && alias.equalsIgnoreCase(word));
    }

    /** The output of "help name", in VICE's layout. */
    String getHelp() {
        StringBuilder help = new StringBuilder();
        help.append("\nSyntax: ").append(syntax).append('\n');
        if (alias != null) {
            help.append("Abbreviation: ").append(alias).append('\n');
        }
        help.append(description).append('\n');
        return help.toString();
    }

    /**
     * Runs the command.
     *
     * @param args   The parser, positioned after the command name.
     * @param engine The engine, for its state.
     * @param out    Where to print output.
     */
    abstract void execute(Parser args, MonitorEngine engine, MonitorOutput out);

    /**
     * A command from VICE's list that jvic doesn't have yet. It is listed by help, as
     * in VICE, so that the list matches, and says so when used.
     */
    static final class NotImplemented extends MonitorCommand {

        NotImplemented(String name, String alias) {
            super(name, alias, name, "This command is not implemented in jvic yet.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            out.print("The " + getName() + " command is not implemented in jvic yet.\n");
        }
    }
}
