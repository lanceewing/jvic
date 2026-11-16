package emu.jvic.debug;

import emu.jvic.cpu.Cpu6502;
import emu.jvic.memory.Memory;

/**
 * The machine-wide part of the debug monitor's hooks into the emulation. One per
 * {@code Machine}, created by {@code Machine.enableDebugger()}. It owns a
 * {@link DebugCpu} for each CPU (the VIC-20's and the 1541's), and holds the stop
 * state that the Machine's update loops check.
 *
 * <p>How a stop works: a hook (a CPU boundary or JAM, or later a watchpoint) calls
 * {@link #stop}, which only sets {@link #isStopPending()}. The Machine finishes the
 * current machine cycle for every chip, then calls {@link #enterStopped()}, so the
 * chips are never left part-way through a cycle. While stopped, the Machine doesn't
 * emulate, and the runner is expected to pause until {@link #resume()}.
 *
 * <p>"Armed" means something needs the Machine's per-cycle stop check: a CPU's
 * boundary hook is active, or a stop is pending. While not armed, the Machine uses
 * its normal loop, unchanged, with the stop check once per frame (or batch), for
 * the JAM case.
 *
 * <p>Plain Java only, as this package is translated by GWT. The monitor engine,
 * which is not, talks to this class through {@link Listener}.
 */
public final class Debugger {

    /** No stop. */
    public static final int STOP_NONE = 0;

    /** A stop requested by the user, e.g. opening the monitor with Alt+H. */
    public static final int STOP_REQUEST = 1;

    /** A stop requested by the {@link Listener} at an instruction boundary. */
    public static final int STOP_LISTENER = 2;

    /** The CPU executed a JAM (KIL/HLT) opcode. */
    public static final int STOP_JAM = 3;

    /** A watchpoint (load or store) was hit. */
    public static final int STOP_WATCH = 4;

    /**
     * Implemented by the monitor engine to receive debugger events.
     */
    public interface Listener {

        /**
         * Called at each instruction boundary of a CPU whose listener hooks are on
         * (see {@link DebugCpu#setListenerHooks(boolean)}). This runs once per
         * instruction, so it should be fast.
         *
         * @param cpu       The CPU at the boundary.
         * @param address   The address of the instruction about to start.
         * @param interrupt 0, or Cpu6502.S_IRQ or S_NMI if an interrupt sequence is
         *                  about to start instead (see {@link CpuDebugger}).
         *
         * @return true to stop the machine at the end of the current cycle.
         */
        boolean onInstructionBoundary(DebugCpu cpu, int address, int interrupt);

        /**
         * Called when the machine has stopped, after the cycle that requested the stop
         * has completed for every chip.
         *
         * @param debugger The debugger, from which the stop reason, CPU and address
         *                 can be read.
         */
        void onStopped(Debugger debugger);
    }

    private final DebugCpu mainCpu;
    private final DebugCpu driveCpu;

    private Listener listener;

    private boolean stopPending;
    private boolean stopped;
    private int stopReason = STOP_NONE;
    private DebugCpu stopCpu;
    private int stopAddress = -1;

    /**
     * Creates the Debugger and attaches it to both CPUs.
     *
     * @param mainCpu     The VIC-20's CPU.
     * @param mainMemory  The VIC-20's memory.
     * @param driveCpu    The 1541's CPU.
     * @param driveMemory The 1541's memory.
     */
    public Debugger(Cpu6502 mainCpu, Memory mainMemory, Cpu6502 driveCpu, Memory driveMemory) {
        this.mainCpu = new DebugCpu(this, "C", mainCpu, mainMemory);
        this.driveCpu = new DebugCpu(this, "8", driveCpu, driveMemory);
    }

    public DebugCpu getMainCpu() {
        return mainCpu;
    }

    public DebugCpu getDriveCpu() {
        return driveCpu;
    }

    public Listener getListener() {
        return listener;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /**
     * Requests a stop at the main CPU's next instruction boundary, as when the user
     * opens the monitor. Ignored if already stopped.
     */
    public void requestStop() {
        if (stopped) {
            return;
        }
        Cpu6502 cpu = mainCpu.getCpu();
        if (cpu.isJammed()) {
            // A jammed CPU never reaches another boundary, so stop straight away.
            stop(STOP_REQUEST, mainCpu, (cpu.getProgramCounter() - 1) & 0xFFFF);
        } else {
            mainCpu.stopAtNextBoundary(STOP_REQUEST);
        }
    }

    /**
     * Called by the hooks to stop the machine at the end of the current cycle. Only
     * the first stop in a cycle is recorded, e.g. if both CPUs hit a checkpoint in
     * the same machine cycle, the main CPU's is reported (it runs first).
     *
     * @param reason  One of the STOP_ constants.
     * @param cpu     The CPU that caused the stop.
     * @param address The address to report, e.g. the instruction or JAM address.
     */
    public void stop(int reason, DebugCpu cpu, int address) {
        if (!stopPending && !stopped) {
            stopPending = true;
            stopReason = reason;
            stopCpu = cpu;
            stopAddress = address;
        }
    }

    /**
     * Whether a stop has been requested in the current cycle. The Machine's debug loop
     * checks this after every machine cycle.
     */
    public boolean isStopPending() {
        return stopPending;
    }

    /**
     * Whether the Machine needs its per-cycle stop check.
     */
    public boolean isArmed() {
        return stopPending || mainCpu.isHooksActive() || driveCpu.isHooksActive();
    }

    /**
     * Called by the Machine at the end of the machine cycle in which a stop was
     * requested. Puts the debugger into the stopped state and tells the listener.
     */
    public void enterStopped() {
        if (!stopPending) {
            return;
        }
        stopPending = false;
        stopped = true;
        // A stop for any reason satisfies any outstanding stop-at-next-boundary request.
        mainCpu.cancelStopAtNextBoundary();
        driveCpu.cancelStopAtNextBoundary();
        if (listener != null) {
            listener.onStopped(this);
        }
    }

    /**
     * Whether the machine is stopped in the monitor. The Machine doesn't emulate while
     * this is true.
     */
    public boolean isStopped() {
        return stopped;
    }

    /**
     * Leaves the stopped state, so that the Machine emulates again.
     */
    public void resume() {
        stopped = false;
    }

    /** The reason for the current or most recent stop, one of the STOP_ constants. */
    public int getStopReason() {
        return stopReason;
    }

    /** The CPU that caused the current or most recent stop. */
    public DebugCpu getStopCpu() {
        return stopCpu;
    }

    /** The address reported with the current or most recent stop. */
    public int getStopAddress() {
        return stopAddress;
    }

    /**
     * Detaches the debugger from both CPUs. Used when the Machine is re-initialised.
     */
    public void detach() {
        mainCpu.detach();
        driveCpu.detach();
        stopPending = false;
        stopped = false;
    }
}
