package emu.jvic.cpu;

import emu.jvic.testutil.CpuTestHarness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BRK, IRQ (maskable, level-sensitive) and NMI (non-maskable, edge-triggered), plus
 * the undocumented "interrupt delayed by one instruction after a taken same-page
 * branch" behaviour. Hardware IRQ/NMI push status with the break bit (0x10) clear;
 * BRK pushes it set. NMI is not masked by the I flag; IRQ is.
 *
 * <p>All three push PCH, PCL, then status (3 bytes) before jumping to their vector,
 * so with SP starting at $FF the pushed status byte always ends up at $01FD (PCH at
 * $01FF, PCL at $01FE), not $01FF.
 *
 * <p><b>Extra pipelining note (on top of {@link CpuTestHarness}'s general one):</b>
 * selecting IRQ_STEPS/NMI_STEPS is itself a "priming" event, exactly like fetching a
 * normal opcode - the {@code step()} call that selects it stops immediately without
 * running any of its cycles, and a further {@code step()} call is needed to run it
 * to completion. This was confirmed empirically. The one exception is an interrupt
 * that is already pending before the very first {@code step()} call after a reset:
 * there, the first call's "priming" directly selects the interrupt sequence, so only
 * one further call (2 total) is needed - which is also precisely the scenario
 * exercised by {@link #interruptServicedImmediatelyAfterResetSetsBreakFlagCorrectly()}.
 */
public class InterruptTest {

    private static final int PUSHED_STATUS_ADDRESS = 0x01FD;

    private CpuTestHarness h;

    @BeforeEach
    void setUp() {
        h = new CpuTestHarness();
    }

    @Test
    void resetSetsInterruptDisableFlag() {
        // Real 6502/6510 hardware sets the I flag (interrupts disabled) as part of
        // the reset sequence.
        h.cpu.reset();
        assertTrue(h.cpu.getInterruptDisableFlag());
    }

    @Test
    void irqIsMaskedWhenInterruptDisableFlagSet() {
        h.writeByte(0x0300, 0xEA); // NOP
        h.cpu.reset();
        h.cpu.setProgramCounter(0x0300);
        h.cpu.setInterruptDisableFlag(true);
        h.cpu.setStackPointer(0xFF);
        h.cpu.setInterrupt(Cpu6502.S_IRQ);

        h.cpu.step(); // prime: fetch NOP opcode (masked IRQ, so a normal fetch happens)
        assertEquals(0xEA, h.cpu.getInstructionRegister());

        h.cpu.step(); // run NOP; IRQ still pending but masked, so no interrupt servicing (no stack pushes)
        assertEquals(0xFF, h.cpu.getStackPointer());
    }

    @Test
    void irqIsServicedWhenEnabled() {
        h.writeByte(0xFFFE, 0x00);
        h.writeByte(0xFFFF, 0x05); // IRQ vector -> $0500
        h.writeByte(0x0300, 0xEA);
        h.cpu.reset();
        h.cpu.setProgramCounter(0x0300);
        h.cpu.setInterruptDisableFlag(false);
        h.cpu.setStackPointer(0xFF);

        h.cpu.step(); // prime: fetch NOP (no interrupt pending yet)
        h.cpu.setInterrupt(Cpu6502.S_IRQ); // raise it only now, while NOP is "in flight"
        h.cpu.step(); // run NOP, then SELECT the pending IRQ sequence (does not run it yet)
        h.cpu.step(); // run the IRQ sequence to completion

        assertEquals(0x0501, h.cpu.getProgramCounter()); // vector target + 1 (fused next fetch)
        assertTrue(h.cpu.getInterruptDisableFlag());
        assertEquals(0xFC, h.cpu.getStackPointer());
        int pushedStatus = h.readByte(PUSHED_STATUS_ADDRESS);
        assertEquals(0, pushedStatus & 0x10, "hardware IRQ must push status with the break bit clear");
    }

    @Test
    void nmiIsServicedRegardlessOfInterruptDisableFlag() {
        h.writeByte(0xFFFA, 0x00);
        h.writeByte(0xFFFB, 0x06); // NMI vector -> $0600
        h.writeByte(0x0300, 0xEA);
        h.cpu.reset();
        h.cpu.setProgramCounter(0x0300);
        h.cpu.setInterruptDisableFlag(true); // must NOT mask NMI
        h.cpu.setStackPointer(0xFF);

        h.cpu.step(); // prime: fetch NOP
        h.cpu.setInterrupt(Cpu6502.S_NMI);
        h.cpu.step(); // run NOP, select NMI sequence
        h.cpu.step(); // run NMI sequence to completion

        assertEquals(0x0601, h.cpu.getProgramCounter());
        int pushedStatus = h.readByte(PUSHED_STATUS_ADDRESS);
        assertEquals(0, pushedStatus & 0x10, "hardware NMI must push status with the break bit clear");
    }

    @Test
    void nmiIsEdgeTriggeredAndAutoClearsAfterServicing() {
        h.writeByte(0xFFFA, 0x00);
        h.writeByte(0xFFFB, 0x06);
        h.writeByte(0x0300, 0xEA);
        h.cpu.reset();
        h.cpu.setProgramCounter(0x0300);
        h.cpu.setStackPointer(0xFF);
        h.cpu.setInterrupt(Cpu6502.S_NMI);

        h.cpu.step(); // pending before the very first step(): selects the NMI sequence immediately
        h.cpu.step(); // runs it to completion

        assertFalse(h.cpu.isNmiAsserted(), "NMI is edge-triggered: it must not re-fire on its own");
    }

    @Test
    void brkPushesStatusWithBreakFlagSetUnlikeHardwareInterrupts() {
        h.writeByte(0xFFFE, 0x00);
        h.writeByte(0xFFFF, 0x05);
        h.prepareInstruction(0x0300, 0x00); // BRK
        h.cpu.setStackPointer(0xFF);
        h.runInstruction();
        int pushedStatus = h.readByte(PUSHED_STATUS_ADDRESS);
        assertEquals(0x10, pushedStatus & 0x10);
        assertEquals(0x0501, h.cpu.getProgramCounter());
        assertTrue(h.cpu.getInterruptDisableFlag());
    }

    @Test
    void interruptServicedImmediatelyAfterResetSetsBreakFlagCorrectly() {
        // Cpu6502 distinguishes a genuine BRK from a hardware IRQ/NMI by checking
        // instructionSteps[0] == BRK (see STORE_P_SP), which is only ever true
        // while actually executing a real BRK instruction - unlike
        // instructionRegister (which reset() also happens to set to 0, coincidentally
        // matching BRK's opcode value), it can never be stale. This locks in that an
        // interrupt serviced before the CPU has fetched even its first real
        // instruction still correctly leaves the break bit clear.
        h.writeByte(0xFFFE, 0x00);
        h.writeByte(0xFFFF, 0x05);
        h.cpu.reset();
        h.cpu.setProgramCounter(0x0300);
        h.cpu.setInterruptDisableFlag(false);
        h.cpu.setStackPointer(0xFF);
        h.cpu.setInterrupt(Cpu6502.S_IRQ); // pending before ANY instruction has been fetched

        h.cpu.step(); // "primes" by selecting the IRQ sequence directly (nothing fetched yet)
        h.cpu.step(); // runs it to completion

        int pushedStatus = h.readByte(PUSHED_STATUS_ADDRESS);
        assertEquals(0, pushedStatus & 0x10,
                "hardware IRQ must push status with the break bit clear, even serviced this early");
    }

    @Test
    void takenSamePageBranchDelaysAPendingInterruptByOneInstruction() {
        h.cpu.reset();
        h.cpu.setProgramCounter(0x0300);
        h.writeByte(0x0300, 0xF0); // BEQ +5 (same page: target 0x0307)
        h.writeByte(0x0301, 0x05);
        h.writeByte(0x0307, 0xA9); // LDA #$42 at the branch target
        h.writeByte(0x0308, 0x42);
        h.cpu.setInterruptDisableFlag(false);
        h.cpu.setZeroResultFlag(true); // BEQ will be taken
        h.cpu.setStackPointer(0xFF);

        h.cpu.step(); // prime: fetch BEQ opcode (no interrupt pending yet)
        // Raise the interrupt only now, i.e. while BEQ is "in flight" - this is the
        // scenario the delay logic exists for. Raising it before the first step()
        // would instead have it selected immediately, before BEQ ever got a chance
        // to run, which would not exercise the delay at all.
        h.cpu.setInterrupt(Cpu6502.S_IRQ);
        h.cpu.step(); // run BEQ (taken, same page): interrupt check is deferred, so LDA is fetched normally

        assertEquals(0xA9, h.cpu.getInstructionRegister(),
                "a pending interrupt must be delayed by one instruction after a taken same-page branch");
        assertEquals(0xFF, h.cpu.getStackPointer(), "interrupt must not have been serviced yet");

        h.cpu.step(); // run LDA to completion, then SELECT the (now due) interrupt sequence
        assertEquals(0x42, h.cpu.getAccumulator()); // LDA still ran to completion first
        assertEquals(0xFF, h.cpu.getStackPointer(), "interrupt sequence selected but not yet run");

        h.cpu.step(); // run the interrupt sequence to completion
        assertTrue(h.cpu.getInterruptDisableFlag());
        assertEquals(0xFC, h.cpu.getStackPointer());
    }

    @Test
    void takenCrossPageBranchDoesNotDelayAPendingInterrupt() {
        h.cpu.reset();
        h.cpu.setProgramCounter(0x02F0);
        h.writeByte(0x02F0, 0xF0); // BEQ +$20 -> crosses into the next page
        h.writeByte(0x02F1, 0x20);
        h.writeByte(0xFFFE, 0x00);
        h.writeByte(0xFFFF, 0x05);
        h.cpu.setInterruptDisableFlag(false);
        h.cpu.setZeroResultFlag(true);
        h.cpu.setStackPointer(0xFF);

        h.cpu.step(); // prime: fetch BEQ opcode
        h.cpu.setInterrupt(Cpu6502.S_IRQ); // raise while BEQ is in flight, same as the same-page test
        h.cpu.step(); // run branch (taken, crosses page): the interrupt sequence is SELECTED immediately

        assertEquals(0xF0, h.cpu.getInstructionRegister(),
                "no normal instruction fetch should have happened - the interrupt was not delayed");

        h.cpu.step(); // run the interrupt sequence to completion
        assertEquals(0x0501, h.cpu.getProgramCounter());
        assertEquals(0xFC, h.cpu.getStackPointer());
    }
}
