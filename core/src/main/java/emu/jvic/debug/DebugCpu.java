package emu.jvic.debug;

import emu.jvic.cpu.Cpu6502;
import emu.jvic.memory.Memory;

/**
 * The {@link Debugger}'s view of one CPU: the VIC-20's main CPU ("C") or the 1541
 * drive's CPU ("8"). It is the {@link CpuDebugger} attached to that CPU, so it is
 * where the CPU's instruction boundary and JAM events arrive. It records where the
 * CPU was at its last boundary, and decides from that whether to ask the
 * {@link Debugger} to stop the machine.
 *
 * <p>Plain Java only, as this package is translated by GWT.
 */
public final class DebugCpu implements CpuDebugger {

    private final Debugger debugger;
    private final String name;
    private final Cpu6502 cpu;
    private final Memory memory;

    /** True while the monitor wants to see every boundary (checkpoints, stepping, etc.). */
    private boolean listenerHooks;

    /** A stop reason to apply at this CPU's next boundary, or STOP_NONE. */
    private int stopAtNextBoundary = Debugger.STOP_NONE;

    /** The address passed to the most recent boundary hook call. */
    private int boundaryAddress = -1;

    /** The interrupt passed to the most recent boundary hook call (0, S_IRQ or S_NMI). */
    private int boundaryInterrupt;

    /** The CPU's total cycle count at the most recent boundary hook call. */
    private long boundaryCycle = -1;

    DebugCpu(Debugger debugger, String name, Cpu6502 cpu, Memory memory) {
        this.debugger = debugger;
        this.name = name;
        this.cpu = cpu;
        this.memory = memory;
        cpu.setDebugger(this);
    }

    @Override
    public void onInstructionBoundary(Cpu6502 cpu, int address, int interrupt) {
        boundaryAddress = address;
        boundaryInterrupt = interrupt;
        boundaryCycle = cpu.getTotalCycles();

        if (stopAtNextBoundary != Debugger.STOP_NONE) {
            int reason = stopAtNextBoundary;
            stopAtNextBoundary = Debugger.STOP_NONE;
            updateHooks();
            debugger.stop(reason, this, address);
            return;
        }

        if (listenerHooks) {
            Debugger.Listener listener = debugger.getListener();
            if ((listener != null) && listener.onInstructionBoundary(this, address, interrupt)) {
                debugger.stop(Debugger.STOP_LISTENER, this, address);
            }
        }
    }

    @Override
    public void onJam(Cpu6502 cpu, int address) {
        // The CPU is halted mid-instruction, so there is no boundary to wait for.
        debugger.stop(Debugger.STOP_JAM, this, address);
    }

    /**
     * Turns on or off the calls to the {@link Debugger.Listener} at every instruction
     * boundary of this CPU. The monitor turns this on while it has exec checkpoints,
     * stepping, CPU history or profiling active for this CPU, and off otherwise, so
     * that the emulation runs at full speed when nothing needs it.
     *
     * @param enabled true to call the listener at every instruction boundary.
     */
    public void setListenerHooks(boolean enabled) {
        listenerHooks = enabled;
        updateHooks();
    }

    public boolean isListenerHooks() {
        return listenerHooks;
    }

    /**
     * Requests that the machine stops at this CPU's next instruction boundary, with the
     * given reason. Used for the monitor's manual stop (Alt+H), and by watchpoints,
     * which report their stop after the accessing instruction completes, as VICE does.
     *
     * @param reason The stop reason to report, one of the Debugger.STOP_ constants.
     */
    public void stopAtNextBoundary(int reason) {
        stopAtNextBoundary = reason;
        updateHooks();
    }

    /**
     * Cancels a stop requested by {@link #stopAtNextBoundary(int)} that hasn't happened yet.
     */
    public void cancelStopAtNextBoundary() {
        stopAtNextBoundary = Debugger.STOP_NONE;
        updateHooks();
    }

    private void updateHooks() {
        cpu.setDebugHooksActive(listenerHooks || (stopAtNextBoundary != Debugger.STOP_NONE));
    }

    /**
     * Returns whether the CPU's boundary hook is currently active.
     */
    public boolean isHooksActive() {
        return cpu.isDebugHooksActive();
    }

    /**
     * Returns whether this CPU is at an instruction boundary in the machine's current
     * state: its last boundary hook call happened in the CPU's current cycle, which is
     * the case when the machine stopped at a boundary of this CPU. It is false, for
     * example, for the main CPU when a drive checkpoint stopped the machine part-way
     * through a main CPU instruction, or when the boundary hook wasn't active.
     *
     * @return true if the CPU is at an instruction boundary.
     */
    public boolean isAtBoundary() {
        return (boundaryCycle >= 0) && (boundaryCycle == cpu.getTotalCycles());
    }

    /**
     * Returns the address of the instruction at the CPU's last boundary. When stopped
     * at a boundary, this is the address the monitor should display as the PC (the
     * CPU's own program counter is already past the overlapped opcode fetch).
     *
     * @return The address of the instruction at the last boundary, or -1 if none yet.
     */
    public int getBoundaryAddress() {
        return boundaryAddress;
    }

    /**
     * Returns the interrupt being taken at the last boundary: 0, Cpu6502.S_IRQ or
     * Cpu6502.S_NMI.
     */
    public int getBoundaryInterrupt() {
        return boundaryInterrupt;
    }

    /**
     * Moves execution to the given address while stopped, through
     * {@link Cpu6502#redispatchAt(int)}, and keeps this view's boundary state in
     * step, so that the CPU is still shown as at a boundary at the new address.
     *
     * @param address The address to continue execution from.
     */
    public void redispatchAt(int address) {
        cpu.redispatchAt(address);
        boundaryAddress = address & 0xFFFF;
        boundaryCycle = cpu.getTotalCycles();
        if (!cpu.isInterruptDispatched()) {
            boundaryInterrupt = 0;
        }
    }

    /**
     * The memspace name as VICE writes it, without the colon: "C" or "8".
     */
    public String getName() {
        return name;
    }

    public Cpu6502 getCpu() {
        return cpu;
    }

    public Memory getMemory() {
        return memory;
    }

    public Debugger getDebugger() {
        return debugger;
    }

    /**
     * Detaches this view from its CPU, turning the CPU's hooks off.
     */
    void detach() {
        listenerHooks = false;
        stopAtNextBoundary = Debugger.STOP_NONE;
        if (cpu.getDebugger() == this) {
            cpu.setDebugger(null);
        }
    }
}
