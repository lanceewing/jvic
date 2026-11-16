package emu.jvic.debug;

import emu.jvic.cpu.Cpu6502;

/**
 * Receives instruction-level events from a {@link Cpu6502} for the machine-language
 * monitor (breakpoints, stepping, CPU history, profiling, JAM detection).
 *
 * <p>Attached with {@link Cpu6502#setDebugger(CpuDebugger)}. Each CPU (the VIC-20's
 * and the 1541's) has its own instance, so an implementation knows which address
 * space an event belongs to.
 *
 * <p>This package is translated by GWT along with the rest of {@code core}, so it
 * must stay plain Java: no {@code String.format}, reflection or java.io.
 */
public interface CpuDebugger {

    /**
     * Called at every instruction boundary while debug hooks are active (see
     * {@link Cpu6502#setDebugHooksActive(boolean)}), immediately before the CPU
     * either fetches the opcode at {@code address} or starts an IRQ/NMI sequence.
     *
     * <p>The fetch still completes in the current cycle after this returns (it
     * overlaps the previous instruction's final cycle), so an implementation must
     * not try to stop the CPU here. It should record what it needs, request a
     * stop at the end of the current machine cycle, and return. It must not
     * change CPU state.
     *
     * <p>When the machine is later stopped at this boundary, the opcode at
     * {@code address} is already latched. Any change to the PC, or to memory at
     * {@code address}, must go through {@link Cpu6502#redispatchAt(int)}.
     *
     * @param cpu       The CPU raising the event.
     * @param address   The address of the instruction about to start. When an
     *                  interrupt is about to be taken, this is the address of the
     *                  instruction that will run after RTI. That instruction has
     *                  not executed, so exec checkpoints on it should not fire.
     * @param interrupt 0 if the opcode at {@code address} will be executed,
     *                  otherwise {@link Cpu6502#S_IRQ} or {@link Cpu6502#S_NMI}
     *                  for the interrupt sequence about to start instead.
     */
    void onInstructionBoundary(Cpu6502 cpu, int address, int interrupt);

    /**
     * Called once when the CPU executes a JAM (KIL/HLT) opcode and halts. Not
     * called again for the same JAM, however many cycles the CPU stays halted.
     * It is called again only after a reset or {@link Cpu6502#redispatchAt(int)}
     * and a new JAM. Unlike {@link #onInstructionBoundary}, this is called
     * whenever a debugger is attached, even if debug hooks are not active.
     *
     * @param cpu     The CPU raising the event.
     * @param address The address of the JAM opcode.
     */
    void onJam(Cpu6502 cpu, int address);
}
