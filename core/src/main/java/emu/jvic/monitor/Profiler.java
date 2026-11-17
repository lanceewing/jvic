package emu.jvic.monitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emu.jvic.cpu.Cpu6502;

/**
 * The main CPU profiler (profile): a tree of call contexts built at each instruction
 * boundary. A JSR starts a context for its target, called from the JSR's address; an
 * interrupt starts an "IRQ" or "NMI" context; RTS and RTI end the current one. Each
 * instruction's cycles are added to the current context, and to that context's count
 * for the instruction's address. The reports follow VICE's layout.
 */
final class Profiler {

    /** Call sites that aren't instructions. */
    static final int FROM_ROOT = -1;
    static final int FROM_IRQ = -2;
    static final int FROM_NMI = -3;

    /** Contexts deeper than this aren't created (runaway recursion). */
    private static final int MAX_DEPTH = 256;

    /** One call context: a function, called from a call site, under a parent context. */
    static final class Context {
        final int from;
        final int function;
        final Context parent;
        final int depth;
        final Map<Long, Context> children = new LinkedHashMap<>();
        final Map<Integer, long[]> instructions = new HashMap<>();
        long self;
        int number;

        Context(int from, int function, Context parent) {
            this.from = from;
            this.function = function;
            this.parent = parent;
            this.depth = (parent != null) ? parent.depth + 1 : 0;
        }

        long total() {
            long total = self;
            for (Context child : children.values()) {
                total += child.total();
            }
            return total;
        }

        boolean hasAncestorFunction(int f) {
            for (Context c = parent; c != null; c = c.parent) {
                if ((c.function == f) && (c.from != FROM_ROOT)) {
                    return true;
                }
            }
            return false;
        }
    }

    private boolean running;
    private Context root;
    private Context current;
    private long lastCycles;
    private int lastAddress = -1;
    private int lastOpcode = -1;
    private int lastInterrupt;

    boolean isRunning() {
        return running;
    }

    boolean hasData() {
        return root != null;
    }

    Context getRoot() {
        return root;
    }

    /** Starts profiling afresh. */
    void start() {
        root = new Context(FROM_ROOT, FROM_ROOT, null);
        current = root;
        lastAddress = -1;
        running = true;
    }

    void stop() {
        running = false;
        lastAddress = -1;
    }

    /** Called at each main CPU instruction boundary while running. */
    void onBoundary(AddressSpace space, int address, int interrupt) {
        Cpu6502 cpu = space.getCpu();
        long now = cpu.getTotalCycles();
        if (lastAddress >= 0) {
            long cycles = now - lastCycles;
            current.self += cycles;
            long[] count = current.instructions.get(lastAddress);
            if (count == null) {
                current.instructions.put(lastAddress, new long[] { cycles });
            } else {
                count[0] += cycles;
            }
            if (lastInterrupt != 0) {
                enter(lastInterrupt == Cpu6502.S_NMI ? FROM_NMI : FROM_IRQ, address);
            } else if (lastOpcode == 0x20) {
                enter(lastAddress, address);
            } else if ((lastOpcode == 0x60) || (lastOpcode == 0x40)) {
                if (current.parent != null) {
                    current = current.parent;
                }
            }
        }
        lastAddress = address;
        lastCycles = now;
        lastInterrupt = interrupt;
        lastOpcode = (interrupt != 0) ? -1 : space.read(address, false);
    }

    private void enter(int from, int function) {
        if (current.depth >= MAX_DEPTH) {
            return;
        }
        long key = ((long) from << 32) | (function & 0xFFFFFFFFL);
        Context child = current.children.get(key);
        if (child == null) {
            child = new Context(from, function, current);
            current.children.put(key, child);
        }
        current = child;
    }

    /** All contexts in depth-first order, numbered from 1. */
    List<Context> numbered() {
        List<Context> all = new ArrayList<>();
        if (root != null) {
            number(root, all);
        }
        return all;
    }

    private void number(Context context, List<Context> all) {
        all.add(context);
        context.number = all.size();
        for (Context child : context.children.values()) {
            number(child, all);
        }
    }

    /** Clears the counts of a function's contexts. */
    void clear(int function) {
        for (Context context : numbered()) {
            if (context.function == function) {
                context.self = 0;
                context.instructions.clear();
            }
        }
    }

    // ------------------------------------------------------------------------------
    // Reports.
    // ------------------------------------------------------------------------------

    static final String COLUMNS = "        Total      %          Self      %";
    static final String DASHES = "------------- ------ ------------- ------";

    /** A cycle count with thousands separators, right-aligned in 13 columns. */
    static String count(long value) {
        String digits = Long.toString(value);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if ((i > 0) && (((digits.length() - i) % 3) == 0)) {
                text.append(',');
            }
            text.append(digits.charAt(i));
        }
        return Format.pad(text.toString(), 13, ' ');
    }

    /** A percentage as VICE shows it, e.g. " 54.1%", in 6 columns ("  nan%" for 0 of 0). */
    static String percent(long part, long whole) {
        if (whole == 0) {
            return "  nan%";
        }
        long tenths = Math.round((part * 1000.0) / whole);
        return Format.pad((tenths / 10) + "." + (tenths % 10) + "%", 6, ' ');
    }

    /** The four number columns. */
    static String numbers(long total, long self, long whole) {
        return count(total) + " " + percent(total, whole) + " " + count(self) + " " + percent(self, whole);
    }

    /** A function's name: its label, or its address; ROOT for the root. */
    static String name(int function, AddressSpace space) {
        if (function == FROM_ROOT) {
            return "ROOT";
        }
        String label = space.getLabels().nameAt(function);
        return (label != null) ? label : Format.hex4(function);
    }

    /** A context's name in the graph, e.g. "fd2f -> fd8d" or "IRQ  -> ff72". */
    static String contextName(Context context, AddressSpace space) {
        if (context.parent == null) {
            return "START";
        }
        String from;
        switch (context.from) {
            case FROM_IRQ: from = "IRQ "; break;
            case FROM_NMI: from = "NMI "; break;
            default: from = name(context.from, space); break;
        }
        return from + " -> " + name(context.function, space);
    }

    /** prof flat: functions sorted by self time. */
    String flat(AddressSpace space, int count) {
        long whole = root.total();
        Map<Integer, long[]> functions = new LinkedHashMap<>();
        for (Context context : numbered()) {
            long[] sums = functions.get(context.function);
            if (sums == null) {
                sums = new long[2];
                functions.put(context.function, sums);
            }
            sums[1] += context.self;
            if (!context.hasAncestorFunction(context.function)) {
                sums[0] += context.total();
            }
        }
        List<Map.Entry<Integer, long[]>> sorted = new ArrayList<>(functions.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue()[1], a.getValue()[1]));
        StringBuilder text = new StringBuilder();
        text.append(COLUMNS).append('\n').append(DASHES).append('\n');
        for (int i = 0; (i < sorted.size()) && (i < count); i++) {
            long[] sums = sorted.get(i).getValue();
            text.append(numbers(sums[0], sums[1], whole)).append(' ')
                    .append(Format.padRight(name(sorted.get(i).getKey(), space), 40)).append('\n');
        }
        return text.toString();
    }

    /** prof graph: the call tree from a context, to a depth. */
    String graph(AddressSpace space, Context from, int depth) {
        long whole = root.total();
        StringBuilder text = new StringBuilder();
        text.append(Format.pad("", 35, ' ')).append(COLUMNS).append('\n');
        text.append(Format.pad("", 35, ' ')).append(DASHES).append('\n');
        graph(text, space, from, 0, depth, whole);
        return text.toString();
    }

    private void graph(StringBuilder text, AddressSpace space, Context context, int level, int depth, long whole) {
        String indent = Format.pad("", 4 + (2 * level), ' ');
        String label = indent + "[" + context.number + "] " + contextName(context, space);
        text.append(Format.padRight(label, 35)).append(numbers(context.total(), context.self, whole)).append('\n');
        if (context.children.isEmpty()) {
            return;
        }
        if (level >= depth) {
            text.append(indent).append("           ...\n");
            return;
        }
        for (Context child : context.children.values()) {
            graph(text, space, child, level + 1, depth, whole);
        }
    }

    /** prof func: a function's callers and callees. */
    String function(AddressSpace space, int function) {
        long total = 0;
        long self = 0;
        Map<Integer, long[]> callers = new LinkedHashMap<>();
        Map<Integer, long[]> callees = new LinkedHashMap<>();
        for (Context context : numbered()) {
            if ((context.function != function) || context.hasAncestorFunction(function)) {
                continue;
            }
            long contextTotal = context.total();
            total += contextTotal;
            self += context.self;
            int caller = (context.parent != null) ? context.parent.function : FROM_ROOT;
            long[] callerSums = callers.computeIfAbsent(caller, k -> new long[2]);
            callerSums[0] += contextTotal;
            callerSums[1] += context.self;
            for (Context child : context.children.values()) {
                long[] calleeSums = callees.computeIfAbsent(child.function, k -> new long[2]);
                calleeSums[0] += child.total();
                calleeSums[1] += child.self;
            }
        }
        StringBuilder text = new StringBuilder();
        text.append(Format.pad("", 42, ' ')).append("Callers\n");
        text.append(COLUMNS).append("         Callees\n");
        text.append(DASHES).append(" |---|---|------\n");
        for (Map.Entry<Integer, long[]> caller : callers.entrySet()) {
            text.append(numbers(caller.getValue()[0], caller.getValue()[1], total)).append(' ')
                    .append(Format.padRight(name(caller.getKey(), space), 40)).append('\n');
        }
        text.append(numbers(total, self, total)).append("     ").append(Format.padRight(name(function, space), 40))
                .append('\n');
        for (Map.Entry<Integer, long[]> callee : callees.entrySet()) {
            text.append(numbers(callee.getValue()[0], callee.getValue()[1], total)).append("         ")
                    .append(Format.padRight(name(callee.getKey(), space), 40)).append('\n');
        }
        return text.toString();
    }

    /** prof disass and context: each instruction's cycles, with its disassembly. */
    String instructions(AddressSpace space, List<Context> contexts) {
        Map<Integer, long[]> byAddress = new HashMap<>();
        long whole = 0;
        for (Context context : contexts) {
            for (Map.Entry<Integer, long[]> instruction : context.instructions.entrySet()) {
                byAddress.computeIfAbsent(instruction.getKey(), k -> new long[1])[0] += instruction.getValue()[0];
                whole += instruction.getValue()[0];
            }
        }
        List<Integer> addresses = new ArrayList<>(byAddress.keySet());
        addresses.sort(Integer::compare);
        StringBuilder text = new StringBuilder();
        text.append("       Cycles      %\n------------- ------\n");
        for (int address : addresses) {
            StringBuilder line = new StringBuilder();
            Disassembler6502.disassembleLine(space, address, false, line);
            String disassembly = line.toString();
            // Drop a label line; the instruction is the last line.
            int lastLine = disassembly.lastIndexOf('\n', disassembly.length() - 2);
            disassembly = disassembly.substring(lastLine + 1);
            text.append(count(byAddress.get(address)[0])).append(' ').append(percent(byAddress.get(address)[0], whole))
                    .append(' ').append(disassembly);
        }
        return text.toString();
    }
}
