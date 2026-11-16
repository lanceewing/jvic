package emu.jvic.monitor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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

    /** VICE's default log file name. */
    static final String DEFAULT_LOG_NAME = "monitor.log";

    /** How deeply playback files may play other files, to catch a file that plays itself. */
    static final int MAX_PLAYBACK_DEPTH = 16;

    /** Log output is written to the file once this much has built up, or sooner. */
    private static final int LOG_FLUSH_SIZE = 8192;

    /** A line from a playback file, and how deeply nested that playback is (1 or more). */
    private static final class ScriptLine {
        final String line;
        final int depth;

        ScriptLine(String line, int depth) {
            this.line = line;
            this.depth = depth;
        }
    }

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

    private final CheckpointManager checkpoints;

    private MonitorFileSystem fileSystem = new InMemoryMonitorFileSystem();

    // log and logname. The log file's path is fixed when logging starts, so a later
    // cd doesn't move it.
    private String logName = DEFAULT_LOG_NAME;
    private String logPath;
    private final StringBuilder logBuffer = new StringBuilder();

    // record: the name as given, and the file's path.
    private String recordName;
    private String recordPath;

    // playback: lines still to run. Lines after one that resumes the machine run
    // when it next stops.
    private final Deque<ScriptLine> script = new ArrayDeque<>();
    private int scriptDepth;
    private boolean runningScript;
    private boolean resumed;

    private AddressSpace defaultSpace;
    private Radix radix = Radix.HEXADECIMAL;
    private boolean sideEffects;

    /** The message to print when the machine stops for a checkpoint or a step. */
    private String stopMessage;

    // Stepping state: step (z), next (n) and return (ret). Cleared on any stop.
    private static final int STEP_NONE = 0;
    private static final int STEP_INTO = 1;
    private static final int STEP_OVER = 2;
    private static final int STEP_RETURN = 3;

    private int stepMode = STEP_NONE;
    private AddressSpace stepSpace;
    private int stepRemaining;
    /** step: an interrupt started as the count ran out; stop at the handler's first instruction. */
    private boolean stepStopNext;
    /** next: waiting for a JSR or an interrupt to return. */
    private boolean stepWaiting;
    private int stepReturnAddress;
    private int stepReturnSp;
    private boolean stepCountOnReturn;
    /** return: the stack pointer when it started. */
    private int stepBaseSp;

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
        this.checkpoints = new CheckpointManager(this);
        debugger.setListener(this);
    }

    /**
     * Executes one command line. Errors are printed in VICE's format, not thrown.
     *
     * @param line The command line.
     * @param out  Where to print the output.
     */
    public void execute(String line, MonitorOutput out) {
        MonitorOutput teed = tee(out);
        resumed = false;
        executeOne(line, teed);
        flushLog();
    }

    /**
     * Executes a command line that a user typed, or that came from a front end such
     * as the remote monitor, rather than from a playback file. As in VICE, such a line
     * is written to the log after the prompt, and to the file being recorded.
     *
     * @param line The command line.
     * @param out  Where to print the output.
     */
    public void executeTyped(String line, MonitorOutput out) {
        if (logPath != null) {
            log(getPrompt() + line + "\n");
        }
        if ((recordPath != null) && !(findCommandIn(line) instanceof FileCommands.Stop)) {
            try {
                fileSystem.append(recordPath, (line + "\n").getBytes(StandardCharsets.ISO_8859_1));
            } catch (MonitorFileException e) {
                // Keep going, as VICE does.
            }
        }
        execute(line, out);
    }

    private void executeOne(String line, MonitorOutput out) {
        try {
            executeLine(line, out);
        } catch (MonitorException e) {
            out.print(e.format(line));
        } catch (RuntimeException e) {
            out.print("ERROR -- Internal error: " + e + "\n");
        }
    }

    /** Wraps an output so that everything printed to it is also logged. */
    private MonitorOutput tee(MonitorOutput out) {
        return text -> {
            out.print(text);
            log(text);
        };
    }

    /** Finds the command a line starts with, or null. */
    private MonitorCommand findCommandIn(String line) {
        int pos = 0;
        while ((pos < line.length()) && Character.isWhitespace(line.charAt(pos))) {
            pos++;
        }
        int start = pos;
        if ((pos < line.length()) && ("<>~?@".indexOf(line.charAt(pos)) >= 0)) {
            pos++;
        } else {
            while ((pos < line.length())
                    && (Character.isLetterOrDigit(line.charAt(pos)) || (line.charAt(pos) == '_'))) {
                pos++;
            }
        }
        return findCommand(line.substring(start, pos));
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
        resumed = true;
        debugger.resume();
        host.resume();
    }

    // ------------------------------------------------------------------------------
    // Debugger.Listener
    // ------------------------------------------------------------------------------

    @Override
    public boolean onInstructionBoundary(DebugCpu cpu, int address, int interrupt) {
        AddressSpace space = (cpu == mainSpace.getDebugCpu()) ? mainSpace : driveSpace;
        int previous = space.getCurrentInstruction();
        boolean previousWasInterrupt = space.isCurrentInterrupt();
        // The previous instruction has completed, so report its watched accesses.
        checkpoints.reportAccesses(space);
        space.setCurrentInstruction(address, interrupt != 0);

        // An exec checkpoint doesn't fire where an interrupt pre-empts the instruction;
        // the instruction runs, and the checkpoint fires, after RTI.
        boolean stop = false;
        if ((interrupt == 0) && checkpoints.hasExec(space, address)) {
            stop = checkpoints.checkExec(space, address);
        }
        if ((stepMode != STEP_NONE) && (space == stepSpace)) {
            boolean stepStop;
            switch (stepMode) {
                case STEP_INTO: stepStop = stepIntoBoundary(interrupt); break;
                case STEP_OVER: stepStop = stepOverBoundary(space, address, interrupt); break;
                default: stepStop = returnBoundary(space, previous, previousWasInterrupt); break;
            }
            if (stepStop && !stop) {
                stop = true;
                stopMessage = Disassembler6502.stateLine(space, address);
            }
        }
        return stop;
    }

    private boolean stepIntoBoundary(int interrupt) {
        if (stepStopNext) {
            return true;
        }
        // The previous instruction has completed.
        stepRemaining--;
        if (interrupt != 0) {
            // As in VICE, the interrupt sequence is part of the step: stop at the
            // handler's first instruction, not here.
            stepStopNext = (stepRemaining <= 0);
            return false;
        }
        return stepRemaining <= 0;
    }

    private boolean stepOverBoundary(AddressSpace space, int address, int interrupt) {
        int sp = space.getCpu().getStackPointer();
        if (stepWaiting) {
            // Wait for the JSR or interrupt to return to the same stack level.
            if ((interrupt != 0) || (address != stepReturnAddress) || (sp < stepReturnSp)) {
                return false;
            }
            stepWaiting = false;
            if (stepCountOnReturn) {
                stepRemaining--;
            }
            if (stepRemaining <= 0) {
                return true;
            }
        } else {
            stepRemaining--;
            if (interrupt != 0) {
                // Step over the interrupt too: wait for it to return here.
                waitForReturn(address, sp, false);
                return false;
            }
            if (stepRemaining <= 0) {
                return true;
            }
        }
        if (space.read(address, false) == 0x20) {
            waitForReturn(address + 3, sp, true);
        }
        return false;
    }

    private void waitForReturn(int address, int sp, boolean count) {
        stepWaiting = true;
        stepReturnAddress = address & 0xFFFF;
        stepReturnSp = sp;
        stepCountOnReturn = count;
    }

    private boolean returnBoundary(AddressSpace space, int previous, boolean previousWasInterrupt) {
        // Stop once an RTS or RTI has taken the stack above where it was when return
        // started, i.e. the current subroutine or interrupt handler has returned.
        if (previousWasInterrupt) {
            return false;
        }
        int opcode = space.read(previous, false);
        return ((opcode == 0x60) || (opcode == 0x40)) && (space.getCpu().getStackPointer() > stepBaseSp);
    }

    @Override
    public void onStopped(Debugger debugger) {
        // As in VICE, entering the monitor moves each memspace's dot address to its PC.
        mainSpace.setDot(mainSpace.getDisplayPc());
        driveSpace.setDot(driveSpace.getDisplayPc());

        String message = stopMessage;
        stopMessage = null;
        if ((message == null) && (debugger.getStopReason() == Debugger.STOP_JAM)) {
            AddressSpace space = (debugger.getStopCpu() == mainSpace.getDebugCpu()) ? mainSpace : driveSpace;
            message = "JAM at " + space.getName() + ":$" + Format.hex4(debugger.getStopAddress()) + "\n"
                    + Disassembler6502.stateLine(space, debugger.getStopAddress());
        }
        stepMode = STEP_NONE;
        stepSpace = null;
        updateHooks();
        if (message != null) {
            printAsync(message);
        }
        flushLog();
    }

    // ------------------------------------------------------------------------------
    // Checkpoints and stepping, used by the commands.
    // ------------------------------------------------------------------------------

    CheckpointManager getCheckpoints() {
        return checkpoints;
    }

    void printAsync(String text) {
        host.print(text);
        log(text);
    }

    // ------------------------------------------------------------------------------
    // Files, logging, recording and playback.
    // ------------------------------------------------------------------------------

    /**
     * Sets the file system that the file commands use. The default is an empty
     * in-memory one.
     */
    public void setFileSystem(MonitorFileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    MonitorFileSystem getFileSystem() {
        return fileSystem;
    }

    String getLogName() {
        return logName;
    }

    /**
     * Sets the log file's name, as logname does. If logging is on, it continues in
     * the new file.
     *
     * @return null, or an error message if logging is on and the new file can't be
     *         written.
     */
    public String setLogName(String name) {
        flushLog();
        logName = name;
        return (logPath != null) ? startLog() : null;
    }

    boolean isLogEnabled() {
        return logPath != null;
    }

    /**
     * Turns logging on or off, as log on and log off do. As in VICE, output is
     * added to the end of an existing log file.
     *
     * @return null, or an error message if the log file can't be written.
     */
    public String setLogEnabled(boolean enabled) {
        if (!enabled) {
            flushLog();
            logPath = null;
            return null;
        }
        if (logPath != null) {
            return null;
        }
        return startLog();
    }

    private String startLog() {
        String path = fileSystem.absolute(logName);
        try {
            // As in VICE, the log is added to, not started afresh.
            fileSystem.append(path, new byte[0]);
            logPath = path;
            return null;
        } catch (MonitorFileException e) {
            logPath = null;
            return "Cannot open log file '" + logName + "': " + e.getMessage() + "\n";
        }
    }

    private void log(String text) {
        if (logPath != null) {
            logBuffer.append(text);
            if (logBuffer.length() >= LOG_FLUSH_SIZE) {
                flushLog();
            }
        }
    }

    /** Writes any buffered log output to the log file. */
    public void flushLog() {
        if ((logPath != null) && (logBuffer.length() > 0)) {
            try {
                fileSystem.append(logPath, logBuffer.toString().getBytes(StandardCharsets.ISO_8859_1));
            } catch (MonitorFileException e) {
                // Lost; there is nowhere better to report it.
            }
        }
        logBuffer.setLength(0);
    }

    String getRecordName() {
        return recordName;
    }

    /** Starts recording typed commands to a file, which is started afresh. */
    void startRecording(String name) {
        String path = fileSystem.absolute(name);
        fileSystem.write(path, new byte[0]);
        recordName = name;
        recordPath = path;
    }

    void stopRecording() {
        recordName = null;
        recordPath = null;
    }

    /**
     * Runs the commands in a file, as playback does, e.g. for a startup script. Lines
     * after one that resumes the machine (x, g, z...) run when it next stops.
     *
     * @param name The file.
     * @param out  Where to print the output.
     */
    public void playback(String name, MonitorOutput out) {
        if (runningScript) {
            playbackFile(name, out);
            return;
        }
        resumed = false;
        playbackFile(name, tee(out));
        flushLog();
    }

    /** playback, from a command: out is already teed to the log. */
    void playbackFile(String name, MonitorOutput out) {
        int depth = scriptDepth + 1;
        if (depth > MAX_PLAYBACK_DEPTH) {
            out.print("Playback files are nested too deeply; not playing '" + name + "'.\n");
            return;
        }
        byte[] data;
        try {
            data = fileSystem.read(name);
        } catch (MonitorFileException e) {
            out.print("Cannot open '" + name + "'.\n");
            return;
        }
        String[] lines = new String(data, StandardCharsets.ISO_8859_1).split("\r?\n|\r", -1);
        for (int i = lines.length - 1; i >= 0; i--) {
            if ((i == lines.length - 1) && lines[i].isEmpty()) {
                continue;
            }
            script.addFirst(new ScriptLine(lines[i], depth));
        }
        if (!runningScript) {
            runScript(out);
        }
    }

    private void runScript(MonitorOutput out) {
        runningScript = true;
        try {
            while (!script.isEmpty() && !resumed) {
                ScriptLine next = script.poll();
                scriptDepth = next.depth;
                executeOne(next.line, out);
            }
        } finally {
            scriptDepth = 0;
            runningScript = false;
        }
    }

    /**
     * Whether playback has lines waiting for the machine to stop.
     */
    public boolean hasPendingScript() {
        return !script.isEmpty();
    }

    /**
     * Runs playback lines that were waiting for the machine to stop. The runner's
     * session calls this whenever the machine has stopped.
     */
    public void continueScript(MonitorOutput out) {
        if (!script.isEmpty() && !runningScript) {
            resumed = false;
            runScript(tee(out));
            flushLog();
        }
    }

    /** The depth of playback the current command came from: 0 if typed. */
    int getScriptDepth() {
        return scriptDepth;
    }

    /**
     * Ends the playback file that the current command came from, as stop does when
     * it is read from one.
     */
    void stopPlayback() {
        while (!script.isEmpty() && (script.peek().depth >= scriptDepth)) {
            script.poll();
        }
    }

    void setStopMessage(String message) {
        this.stopMessage = message;
    }

    boolean hasStopMessage() {
        return stopMessage != null;
    }

    /**
     * Turns each CPU's instruction boundary hook on only while checkpoints or stepping
     * need it, so the emulation runs at full speed otherwise.
     */
    void updateHooks() {
        for (AddressSpace space : new AddressSpace[] { mainSpace, driveSpace }) {
            boolean needed = checkpoints.needsHooks(space) || ((stepMode != STEP_NONE) && (stepSpace == space));
            space.getDebugCpu().setListenerHooks(needed);
        }
    }

    /**
     * Sets a temporary exec breakpoint on the computer, which is deleted when hit,
     * without printing anything. For starting up with initbreak.
     */
    public void addInitialBreak(int address) {
        checkpoints.add(mainSpace, address & 0xFFFF, address & 0xFFFF, Checkpoint.EXEC, true, true);
    }

    /**
     * Starts step (z) or next (n) on the default memspace for the given number of
     * instructions, then resumes.
     */
    void startStep(boolean over, int count) {
        AddressSpace space = defaultSpace;
        stepMode = over ? STEP_OVER : STEP_INTO;
        stepSpace = space;
        stepRemaining = count;
        stepStopNext = false;
        stepWaiting = false;
        if (over) {
            int pc = space.getDisplayPc();
            if (space.getCpu().isInterruptDispatched()) {
                waitForReturn(pc, space.getCpu().getStackPointer(), true);
            } else if (space.read(pc, false) == 0x20) {
                waitForReturn(pc + 3, space.getCpu().getStackPointer(), true);
            }
        }
        updateHooks();
        resume();
    }

    /** Starts return (ret) on the default memspace, then resumes. */
    void startReturn() {
        AddressSpace space = defaultSpace;
        stepMode = STEP_RETURN;
        stepSpace = space;
        stepBaseSp = space.getCpu().getStackPointer();
        space.setCurrentInstruction(space.getDisplayPc(), space.getCpu().isInterruptDispatched());
        updateHooks();
        resume();
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
