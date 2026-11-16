package emu.jvic.monitor;

import java.util.ArrayList;
import java.util.List;

import emu.jvic.debug.Debugger;
import emu.jvic.debug.WatchProxyChip;
import emu.jvic.memory.MemoryMappedChip;
import emu.jvic.video.Vic;

/**
 * Holds the monitor's checkpoints and connects them to the emulation:
 * <ul>
 * <li>Exec checkpoints are found in O(1) at each instruction boundary through a
 * per-memspace count of exec checkpoints per address.</li>
 * <li>Load and store checkpoints work by putting a {@link WatchProxyChip} into
 * memoryMap[] at each watched address only, so unwatched addresses cost nothing.</li>
 * </ul>
 * After any change, {@link #refresh()} rebuilds both and tells the engine to update
 * which CPUs need their instruction boundary hook.
 */
final class CheckpointManager {

    /** The per-memspace lookup state. */
    private static final class SpaceState {
        final AddressSpace space;
        final int[] execCounts = new int[0x10000];
        final WatchProxyChip[] proxies = new WatchProxyChip[0x10000];
        final WatchProxyChip.Listener proxyListener;
        /** Accesses by the current instruction, reported when it completes. */
        final List<Object[]> pendingAccesses = new ArrayList<>();

        SpaceState(AddressSpace space, WatchProxyChip.Listener proxyListener) {
            this.space = space;
            this.proxyListener = proxyListener;
        }
    }

    private final MonitorEngine engine;
    private final List<Checkpoint> checkpoints = new ArrayList<>();
    private final SpaceState[] states;
    private int nextNumber = 1;

    CheckpointManager(MonitorEngine engine) {
        this.engine = engine;
        this.states = new SpaceState[] { createState(engine.getMainSpace()), createState(engine.getDriveSpace()) };
    }

    private SpaceState createState(final AddressSpace space) {
        return new SpaceState(space, new WatchProxyChip.Listener() {
            @Override
            public void onLoad(int address, int value) {
                onAccess(space, address, Checkpoint.LOAD);
            }

            @Override
            public void onStore(int address, int value) {
                onAccess(space, address, Checkpoint.STORE);
            }
        });
    }

    private SpaceState state(AddressSpace space) {
        return (space == states[0].space) ? states[0] : states[1];
    }

    // ------------------------------------------------------------------------------
    // The list
    // ------------------------------------------------------------------------------

    Checkpoint add(AddressSpace space, int start, int end, int operations, boolean stop, boolean temporary) {
        Checkpoint checkpoint = new Checkpoint(nextNumber++, space, start, end, operations, stop, temporary);
        checkpoints.add(checkpoint);
        refresh();
        return checkpoint;
    }

    Checkpoint find(int number) {
        for (Checkpoint checkpoint : checkpoints) {
            if (checkpoint.getNumber() == number) {
                return checkpoint;
            }
        }
        return null;
    }

    List<Checkpoint> getAll() {
        return checkpoints;
    }

    void remove(Checkpoint checkpoint) {
        checkpoints.remove(checkpoint);
        refresh();
    }

    /** Deletes all checkpoints. As in VICE, numbering then starts again at 1. */
    void removeAll() {
        checkpoints.clear();
        nextNumber = 1;
        refresh();
    }

    /** The checkpoint listing, or VICE's message when there are none. */
    String list() {
        if (checkpoints.isEmpty()) {
            return "No breakpoints are set\n";
        }
        StringBuilder text = new StringBuilder();
        for (Checkpoint checkpoint : checkpoints) {
            text.append(checkpoint.list());
        }
        return text.toString();
    }

    // ------------------------------------------------------------------------------
    // Connecting to the emulation
    // ------------------------------------------------------------------------------

    /**
     * Rebuilds the exec lookup and the watch proxies from the enabled checkpoints, and
     * updates the CPU hooks.
     */
    void refresh() {
        for (SpaceState state : states) {
            java.util.Arrays.fill(state.execCounts, 0);
            boolean[] watched = new boolean[0x10000];
            for (Checkpoint checkpoint : checkpoints) {
                if ((checkpoint.getSpace() != state.space) || !checkpoint.isEnabled()) {
                    continue;
                }
                boolean exec = checkpoint.has(Checkpoint.EXEC);
                boolean watch = checkpoint.has(Checkpoint.LOAD) || checkpoint.has(Checkpoint.STORE);
                for (int i = 0; i < checkpoint.getLength(); i++) {
                    int address = (checkpoint.getStart() + i) & 0xFFFF;
                    if (exec) {
                        state.execCounts[address]++;
                    }
                    if (watch) {
                        watched[address] = true;
                    }
                }
            }
            reconcileProxies(state, watched);
        }
        engine.updateHooks();
    }

    private static void reconcileProxies(SpaceState state, boolean[] watched) {
        MemoryMappedChip[] map = state.space.getMemory().getMemoryMap();
        for (int address = 0; address < 0x10000; address++) {
            WatchProxyChip proxy = state.proxies[address];
            if (watched[address] && (proxy == null)) {
                proxy = new WatchProxyChip(map[address], state.proxyListener);
                map[address] = proxy;
                state.proxies[address] = proxy;
            } else if (!watched[address] && (proxy != null)) {
                // Put the original back, unless something else has remapped the address.
                if (map[address] == proxy) {
                    map[address] = proxy.getTarget();
                }
                state.proxies[address] = null;
            }
        }
    }

    /**
     * Whether the memspace's CPU needs its instruction boundary hook for checkpoints:
     * it has enabled exec checkpoints, or watchpoints (whose messages need to know
     * which instruction made the access).
     */
    boolean needsHooks(AddressSpace space) {
        for (Checkpoint checkpoint : checkpoints) {
            if ((checkpoint.getSpace() == space) && checkpoint.isEnabled()) {
                return true;
            }
        }
        return false;
    }

    boolean hasExec(AddressSpace space, int address) {
        return state(space).execCounts[address & 0xFFFF] > 0;
    }

    /**
     * Checks the exec checkpoints at an instruction boundary. Tracepoints print
     * straight away. If a stopping checkpoint is hit, the stop message is passed to the
     * engine, and until's temporary checkpoint is deleted.
     *
     * @return true to stop the machine.
     */
    boolean checkExec(AddressSpace space, int address) {
        boolean stop = false;
        for (Checkpoint checkpoint : new ArrayList<>(checkpoints)) {
            if ((checkpoint.getSpace() != space) || !checkpoint.isEnabled() || !checkpoint.has(Checkpoint.EXEC)
                    || !checkpoint.contains(address) || !passes(checkpoint)) {
                continue;
            }
            String text = header(checkpoint, "exec", address) + Disassembler6502.stateLine(space, address);
            if (!checkpoint.isStop()) {
                engine.printAsync(text);
            } else if (!stop) {
                stop = true;
                engine.setStopMessage(text);
                if (checkpoint.isTemporary()) {
                    remove(checkpoint);
                }
            }
        }
        return stop;
    }

    /**
     * Called by a watch proxy when the CPU loads from or stores to a watched address,
     * part-way through an instruction. The hit is reported when the instruction
     * completes (see {@link #reportAccesses}), as VICE does, and a stopping checkpoint
     * stops the machine there.
     */
    private void onAccess(AddressSpace space, int address, int operation) {
        if ((operation == Checkpoint.LOAD) && (address == (space.getCpu().getProgramCounter() & 0xFFFF))) {
            // A read at the PC is an opcode or operand fetch (or a dummy read there),
            // not a data load. VICE doesn't count fetches as loads either.
            return;
        }
        for (Checkpoint checkpoint : checkpoints) {
            if ((checkpoint.getSpace() != space) || !checkpoint.isEnabled() || !checkpoint.has(operation)
                    || !checkpoint.contains(address) || !passes(checkpoint)) {
                continue;
            }
            state(space).pendingAccesses.add(new Object[] {
                checkpoint, (operation == Checkpoint.LOAD) ? "load" : "store", address,
                space.getCurrentInstruction(),
            });
            if (checkpoint.isStop()) {
                space.getDebugCpu().stopAtNextBoundary(Debugger.STOP_WATCH);
            }
        }
    }

    /**
     * Reports the load and store hits of the instruction that has just completed, at
     * the next instruction boundary: the header, then the instruction that made the
     * access with the registers as it left them. Tracepoints print; the first
     * stopping checkpoint provides the stop message.
     */
    void reportAccesses(AddressSpace space) {
        List<Object[]> pending = state(space).pendingAccesses;
        if (pending.isEmpty()) {
            return;
        }
        for (Object[] access : pending) {
            Checkpoint checkpoint = (Checkpoint) access[0];
            String text = header(checkpoint, (String) access[1], (Integer) access[2])
                    + Disassembler6502.stateLine(space, (Integer) access[3]);
            if (!checkpoint.isStop()) {
                engine.printAsync(text);
            } else if (!engine.hasStopMessage()) {
                engine.setStopMessage(text);
            }
        }
        pending.clear();
    }

    /**
     * Whether a checkpoint whose address and operation match counts as hit: its
     * condition (if any) is true, and it has no hits left to ignore.
     */
    private boolean passes(Checkpoint checkpoint) {
        if ((checkpoint.getCondition() != null) && !checkpoint.getCondition().isTrue(engine, checkpoint.getSpace())) {
            return false;
        }
        if (checkpoint.getIgnoreCount() > 0) {
            checkpoint.setIgnoreCount(checkpoint.getIgnoreCount() - 1);
            return false;
        }
        return true;
    }

    /**
     * The first line of a hit message, e.g.
     * "#1 (Stop on  exec fd27)    0/$000,  14/$0e": the checkpoint, the operation
     * (right-aligned in 5 columns), the address, then the raster line and cycle.
     */
    private String header(Checkpoint checkpoint, String operation, int address) {
        Vic vic = engine.getMainSpace().getVic();
        int line = vic.getRasterLine();
        int cycle = vic.getRasterCycle();
        return "#" + checkpoint.getNumber() + " (" + (checkpoint.isStop() ? "Stop on " : "Trace ")
                + Format.pad(operation, 5, ' ') + " " + Format.hex4(address) + ") "
                + Format.decimal(line, 4) + "/$" + Format.hex(line, 3) + ", "
                + Format.decimal(cycle, 3) + "/$" + Format.hex(cycle, 2) + "\n";
    }
}
