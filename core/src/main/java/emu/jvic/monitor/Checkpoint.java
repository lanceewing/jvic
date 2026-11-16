package emu.jvic.monitor;

/**
 * A breakpoint, watchpoint or tracepoint: VICE's single "checkpoint" concept. It has
 * a number, a memspace and address range, the operations it applies to (load, store,
 * exec), whether it stops or only traces, and its enabled state, ignore count and
 * condition.
 */
final class Checkpoint {

    static final int LOAD = 1;
    static final int STORE = 2;
    static final int EXEC = 4;

    private final int number;
    private final AddressSpace space;
    private final int start;
    private final int end;
    private final int operations;
    private final boolean stop;
    private final boolean temporary;

    private boolean enabled = true;
    private int ignoreCount;
    private Condition condition;

    /**
     * @param operations A combination of LOAD, STORE and EXEC.
     * @param stop       true to stop the machine when hit; false to trace and continue.
     * @param temporary  true for until's checkpoint, which is deleted when hit.
     */
    Checkpoint(int number, AddressSpace space, int start, int end, int operations, boolean stop,
            boolean temporary) {
        this.number = number;
        this.space = space;
        this.start = start & 0xFFFF;
        this.end = end & 0xFFFF;
        this.operations = operations;
        this.stop = stop;
        this.temporary = temporary;
    }

    int getNumber() {
        return number;
    }

    AddressSpace getSpace() {
        return space;
    }

    int getStart() {
        return start;
    }

    /** The number of addresses covered (the range may wrap past $FFFF). */
    int getLength() {
        return ((end - start) & 0xFFFF) + 1;
    }

    boolean contains(int address) {
        return ((address - start) & 0xFFFF) < getLength();
    }

    boolean has(int operation) {
        return (operations & operation) != 0;
    }

    boolean isStop() {
        return stop;
    }

    boolean isTemporary() {
        return temporary;
    }

    boolean isEnabled() {
        return enabled;
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    int getIgnoreCount() {
        return ignoreCount;
    }

    void setIgnoreCount(int ignoreCount) {
        this.ignoreCount = ignoreCount;
    }

    Condition getCondition() {
        return condition;
    }

    void setCondition(Condition condition) {
        this.condition = condition;
    }

    /**
     * The kind VICE shows: UNTIL for until's checkpoint, TRACE for a tracepoint,
     * BREAK for an exec-only stop, and WATCH for any other stop.
     */
    String getKind() {
        if (temporary) {
            return "UNTIL";
        }
        if (!stop) {
            return "TRACE";
        }
        return (operations == EXEC) ? "BREAK" : "WATCH";
    }

    /** The operation list, e.g. "load store exec". */
    String getOperationsText() {
        StringBuilder text = new StringBuilder();
        if (has(LOAD)) {
            text.append("load");
        }
        if (has(STORE)) {
            text.append((text.length() > 0) ? " " : "").append("store");
        }
        if (has(EXEC)) {
            text.append((text.length() > 0) ? " " : "").append("exec");
        }
        return text.toString();
    }

    /**
     * The line VICE prints when the checkpoint is set or listed, e.g.
     * "WATCH: 2  C:$0281-$0282  (Stop on store) disabled", without the condition.
     */
    String describe() {
        StringBuilder text = new StringBuilder();
        text.append(getKind()).append(": ").append(number).append("  ");
        text.append(space.getName()).append(":$").append(Format.hex4(start));
        if (end != start) {
            text.append("-$").append(Format.hex4(end));
        }
        text.append("  (").append(stop ? "Stop on " : "Trace ").append(getOperationsText()).append(')');
        if (!enabled) {
            text.append(" disabled");
        }
        return text.append('\n').toString();
    }

    /** The listing entry: {@link #describe()} plus the condition line, if any. */
    String list() {
        String text = describe();
        if (condition != null) {
            text += "\tCondition: " + condition.getText() + "\n";
        }
        return text;
    }
}
