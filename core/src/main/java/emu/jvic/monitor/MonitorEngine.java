package emu.jvic.monitor;

import java.util.ArrayList;
import java.util.List;

import emu.jvic.Machine;
import emu.jvic.debug.DebugCpu;
import emu.jvic.debug.Debugger;

/**
 * The machine-language monitor: parses VICE monitor commands and produces
 * VICE-formatted text output. It runs on the same thread as the {@link Machine}
 * (the desktop emulation thread or the web worker), never the UI thread, and every
 * command runs to completion before {@link #execute} returns.
 *
 * <p>Not translated by GWT (see JVic.gwt.xml); it talks to the emulation through the
 * GWT-safe {@link Debugger}.
 */
public final class MonitorEngine implements Debugger.Listener {

    /** The number of lines that mem shows when given no end address, as VICE does. */
    static final int DEFAULT_MEM_LINES = 22;

    /** A group of commands in the help list. */
    static final class CommandGroup {
        final String title;
        final List<MonitorCommand> commands = new ArrayList<>();

        CommandGroup(String title) {
            this.title = title;
        }
    }

    private final Machine machine;
    private final Debugger debugger;
    private final MonitorHost host;
    private final AddressSpace mainSpace;
    private final AddressSpace driveSpace;
    private final List<CommandGroup> groups;

    private AddressSpace defaultSpace;
    private Radix radix = Radix.HEXADECIMAL;
    private boolean sideEffects;

    /**
     * Creates the engine for the given machine, enabling its debugger if needed, and
     * registers itself as the debugger's listener. If the machine is re-initialised,
     * it gets a new debugger, so a new engine is needed.
     *
     * @param machine The machine to debug.
     * @param host    Platform services.
     */
    public MonitorEngine(Machine machine, MonitorHost host) {
        this.machine = machine;
        this.debugger = machine.enableDebugger();
        this.host = host;
        this.mainSpace = new AddressSpace("C", "Computer", debugger.getMainCpu(), machine.getVic(), true);
        this.driveSpace = new AddressSpace("8", "Disk8", debugger.getDriveCpu(), null, false);
        this.defaultSpace = mainSpace;
        this.groups = CommandRegistry.create();
        debugger.setListener(this);
    }

    /**
     * Executes one command line. Errors are printed in VICE's format, not thrown.
     *
     * @param line The command line.
     * @param out  Where to print the output.
     */
    public void execute(String line, MonitorOutput out) {
        try {
            executeLine(line, out);
        } catch (MonitorException e) {
            out.print(e.format(line));
        } catch (RuntimeException e) {
            out.print("ERROR -- Internal error: " + e + "\n");
        }
    }

    private void executeLine(String line, MonitorOutput out) {
        int pos = 0;
        while ((pos < line.length()) && Character.isWhitespace(line.charAt(pos))) {
            pos++;
        }
        if ((pos >= line.length()) || (line.charAt(pos) == ';')) {
            // Empty line or comment.
            return;
        }

        // The command name: one of VICE's symbol commands, or a word.
        int start = pos;
        char first = line.charAt(pos);
        if ((first == '>') || (first == '~') || (first == '?') || (first == '@')) {
            pos++;
        } else {
            while ((pos < line.length())
                    && (Character.isLetterOrDigit(line.charAt(pos)) || (line.charAt(pos) == '_'))) {
                pos++;
            }
        }
        MonitorCommand command = findCommand(line.substring(start, pos));
        if (command == null) {
            // VICE reports an unknown command as a syntax error at the end of the line.
            throw new MonitorException(MonitorException.WRONG_SYNTAX, line.length());
        }
        command.execute(new Parser(line, pos, radix), this, out);
    }

    /**
     * Finds a command by its name or abbreviation, ignoring case.
     */
    MonitorCommand findCommand(String word) {
        if (word.isEmpty()) {
            return null;
        }
        for (CommandGroup group : groups) {
            for (MonitorCommand command : group.commands) {
                if (command.matches(word)) {
                    return command;
                }
            }
        }
        return null;
    }

    List<CommandGroup> getGroups() {
        return groups;
    }

    /**
     * The prompt VICE shows while stopped, e.g. "(C:$fd22) ".
     */
    public String getPrompt() {
        return "(" + defaultSpace.getName() + ":$" + Format.hex4(defaultSpace.getDisplayPc()) + ") ";
    }

    /**
     * Leaves the stopped state and asks the host to resume emulation.
     */
    void resume() {
        debugger.resume();
        host.resume();
    }

    // ------------------------------------------------------------------------------
    // Debugger.Listener
    // ------------------------------------------------------------------------------

    @Override
    public boolean onInstructionBoundary(DebugCpu cpu, int address, int interrupt) {
        // No checkpoints or stepping yet.
        return false;
    }

    @Override
    public void onStopped(Debugger debugger) {
        // As in VICE, entering the monitor moves each memspace's dot address to its PC.
        mainSpace.setDot(mainSpace.getDisplayPc());
        driveSpace.setDot(driveSpace.getDisplayPc());
    }

    // ------------------------------------------------------------------------------
    // State used by the commands.
    // ------------------------------------------------------------------------------

    Machine getMachine() {
        return machine;
    }

    Debugger getDebugger() {
        return debugger;
    }

    MonitorHost getHost() {
        return host;
    }

    AddressSpace getMainSpace() {
        return mainSpace;
    }

    AddressSpace getDriveSpace() {
        return driveSpace;
    }

    AddressSpace getDefaultSpace() {
        return defaultSpace;
    }

    void setDefaultSpace(AddressSpace space) {
        this.defaultSpace = space;
    }

    /**
     * Returns the address space for a memspace prefix ("c", "8"), or the default
     * space for null.
     *
     * @param memspace The memspace name, lower case, without the colon.
     * @param position Where it was in the command line, for the error.
     */
    AddressSpace getSpace(String memspace, int position) {
        if (memspace == null) {
            return defaultSpace;
        }
        if (memspace.equals("c")) {
            return mainSpace;
        }
        if (memspace.equals("8")) {
            return driveSpace;
        }
        if (memspace.equals("9") || memspace.equals("10") || memspace.equals("11")) {
            throw new MonitorException("Drive " + memspace + ": is not emulated in jvic.", position);
        }
        throw new MonitorException(MonitorException.UNEXPECTED_TOKEN, position);
    }

    Radix getRadix() {
        return radix;
    }

    void setRadix(Radix radix) {
        this.radix = radix;
    }

    boolean isSideEffects() {
        return sideEffects;
    }

    void setSideEffects(boolean sideEffects) {
        this.sideEffects = sideEffects;
    }
}
