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
 * to completion. This was confirmed empirically.
 *
 * <p><b>Interrupt sampling:</b> as on the real 6502, the pins are sampled in the
 * penultimate cycle of an instruction, not its last, so an interrupt raised only as
 * the last cycle completes waits until after the following instruction. An interrupt
 * raised after priming an instruction is always early enough to be seen at that
 * instruction's end, which is why most tests here raise it at that point.
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

        h.cpu.step(); // prime: fetch NOP (no interrupt pending yet)
        h.cpu.setInterrupt(Cpu6502.S_NMI);
        h.cpu.step(); // run NOP, then SELECT the pending NMI sequence (does not run it yet)
        h.cpu.step(); // run the NMI sequence to completion

        assertFalse(h.cpu.isNmiAsserted(), "NMI is edge-triggered: it must not re-fire on its own");
        assertEquals(0xFC, h.cpu.getStackPointer());

        h.cpu.step(); // run the handler's first instruction (memory is 0, so a BRK, fetched normally)

        assertEquals(0xF9, h.cpu.getStackPointer(), "only the BRK's pushes: the NMI must not be taken twice");
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
    void hardwareInterruptDirectlyAfterBrkPushesStatusWithBreakFlagClear() {
        // Cpu6502 distinguishes a genuine BRK from a hardware IRQ/NMI by checking
        // instructionSteps[0] == BRK (see STORE_P_SP), which is only ever true while
        // actually executing a real BRK instruction. instructionRegister isn't updated
        // when an interrupt sequence is selected, so straight after a BRK it is still 0,
        // coincidentally BRK's opcode. This locks in that an NMI taken at that point
        // still correctly leaves the break bit clear.
        h.writeByte(0xFFFE, 0x00);
        h.writeByte(0xFFFF, 0x05); // BRK vector -> $0500
        h.writeByte(0xFFFA, 0x00);
        h.writeByte(0xFFFB, 0x06); // NMI vector -> $0600
        h.writeByte(0x0300, 0x00); // BRK
        h.cpu.reset();
        h.cpu.setProgramCounter(0x0300);
        h.cpu.setStackPointer(0xFF);

        h.cpu.step(); // prime: fetch BRK

        // Run BRK as far as its status push, so the NMI is too late to hijack the vector.
        h.cpu.emulateCycle();
        h.cpu.emulateCycle();
        h.cpu.emulateCycle();
        h.cpu.emulateCycle();
        h.cpu.setInterrupt(Cpu6502.S_NMI);

        h.cpu.step(); // finish BRK, then SELECT the pending NMI sequence (does not run it yet)
        assertEquals(0x00, h.cpu.getInstructionRegister(), "still BRK's opcode when the NMI is taken");

        h.cpu.step(); // run the NMI sequence to completion

        assertEquals(0x0601, h.cpu.getProgramCounter());
        assertEquals(0x10, h.readByte(PUSHED_STATUS_ADDRESS) & 0x10, "pushed by BRK");
        assertEquals(0, h.readByte(0x01FA) & 0x10,
                "hardware NMI must push status with the break bit clear, even straight after a BRK");
    }

    @Test
    void interruptRaisedInTheLastCycleOfAnInstructionWaitsForTheNextInstruction() {
        h.writeByte(0xFFFE, 0x00);
        h.writeByte(0xFFFF, 0x05); // IRQ vector -> $0500
        h.writeByte(0x0300, 0xEA); // NOP
        h.writeByte(0x0301, 0xA9); // LDA #$42
        h.writeByte(0x0302, 0x42);
        h.cpu.reset();
        h.cpu.setProgramCounter(0x0300);
        h.cpu.setInterruptDisableFlag(false);
        h.cpu.setStackPointer(0xFF);

        h.cpu.step(); // prime: fetch NOP
        h.cpu.emulateCycle(); // NOP's last cycle
        h.cpu.setInterrupt(Cpu6502.S_IRQ); // only now, too late for NOP's penultimate cycle sample
        h.cpu.emulateCycle(); // dispatch what comes after NOP

        assertEquals(0xA9, h.cpu.getInstructionRegister(), "too late to be taken after the NOP");

        h.cpu.step(); // run LDA to completion, then SELECT the IRQ sequence (does not run it yet)
        assertEquals(0x42, h.cpu.getAccumulator());
        h.cpu.step(); // run the IRQ sequence to completion

        assertEquals(0x0501, h.cpu.getProgramCounter());
    }

    @Test
    void irqReleasedInTheLastCycleOfAnInstructionIsStillTaken() {
        // The poll already saw it during the penultimate cycle, so it's too late to
        // stop it, even though nothing is asserting IRQ by the time it is serviced.
        h.writeByte(0xFFFE, 0x00);
        h.writeByte(0xFFFF, 0x05); // IRQ vector -> $0500
        h.writeByte(0x0300, 0xEA); // NOP
        h.cpu.reset();
        h.cpu.setProgramCounter(0x0300);
        h.cpu.setInterruptDisableFlag(false);
        h.cpu.setStackPointer(0xFF);

        h.cpu.step(); // prime: fetch NOP
        h.cpu.setInterrupt(Cpu6502.S_IRQ);
        h.cpu.emulateCycle(); // NOP's last cycle
        h.cpu.clearInterrupt(Cpu6502.S_IRQ);
        h.cpu.emulateCycle(); // SELECTs the IRQ sequence (does not run it yet)
        h.cpu.step(); // run the IRQ sequence to completion

        assertEquals(0x0501, h.cpu.getProgramCounter());
        assertEquals(0xFC, h.cpu.getStackPointer());
    }

    @Test
    void pendingIrqIsDelayedByOneInstructionAfterCli() {
        // Real hardware quirk: CLI/SEI/PLP's own effect on the I flag isn't visible
        // to the interrupt-recognition poll that happens right after them - that poll
        // still uses the OLD (pre-CLI) value, so a pending IRQ during CLI is NOT
        // serviced immediately after it. It only becomes visible to the poll that
        // runs after the NEXT instruction (here, the NOP), one instruction later.
        h.writeByte(0xFFFE, 0x00);
        h.writeByte(0xFFFF, 0x05); // IRQ vector -> $0500
        h.writeByte(0x0300, 0x58); // CLI
        h.writeByte(0x0301, 0xEA); // NOP
        h.cpu.reset();
        h.cpu.setProgramCounter(0x0300);
        h.cpu.setInterruptDisableFlag(true); // interrupts initially masked
        h.cpu.setStackPointer(0xFF);

        h.cpu.step(); // prime: fetch CLI (nothing pending yet)
        h.cpu.setInterrupt(Cpu6502.S_IRQ); // raise it while CLI is "in flight"
        h.cpu.step(); // run CLI (I becomes false), then dispatch what comes after using
                       // the OLD (still masked) value: a normal fetch, not the interrupt

        assertEquals(0xEA, h.cpu.getInstructionRegister(),
                "IRQ must not be taken immediately after CLI despite now being unmasked");
        assertEquals(0xFF, h.cpu.getStackPointer(), "interrupt must not have been serviced yet");

        h.cpu.step(); // run NOP, then dispatch what comes after using the NOW-current
                       // (post-CLI) value: the IRQ sequence is selected

        assertEquals(0xFF, h.cpu.getStackPointer(), "interrupt sequence selected but not yet run");

        h.cpu.step(); // run the interrupt sequence to completion

        assertEquals(0x0501, h.cpu.getProgramCounter());
        assertTrue(h.cpu.getInterruptDisableFlag());
        assertEquals(0xFC, h.cpu.getStackPointer());
    }

    @Test
    void pendingIrqIsTakenStraightAfterAnRtiThatClearsTheInterruptDisableFlag() {
        // Unlike CLI/SEI/PLP, RTI restores P well before it ends, so the restored I is
        // already in effect for the poll at the end of the RTI itself.
        h.writeByte(0xFFFE, 0x00);
        h.writeByte(0xFFFF, 0x05); // IRQ vector -> $0500
        h.writeByte(0x0300, 0x40); // RTI
        h.writeByte(0x01FD, 0x00); // Pulled P: I clear
        h.writeByte(0x01FE, 0x00); // Pulled PCL
        h.writeByte(0x01FF, 0x04); // Pulled PCH: returns to $0400
        h.writeByte(0x0400, 0xEA); // NOP (must not run)
        h.cpu.reset();
        h.cpu.setProgramCounter(0x0300);
        h.cpu.setInterruptDisableFlag(true);
        h.cpu.setStackPointer(0xFC);
        h.cpu.setInterrupt(Cpu6502.S_IRQ);

        h.cpu.step(); // prime: fetch RTI (masked, so not taken)
        h.cpu.step(); // run RTI, then SELECT the IRQ sequence (does not run it yet)
        h.cpu.step(); // run the IRQ sequence to completion

        assertEquals(0x0501, h.cpu.getProgramCounter());
        assertEquals(0x04, h.readByte(0x01FF), "the pushed PCH must be the RTI's return address");
        assertEquals(0x00, h.readByte(0x01FE), "the NOP at the return address must not have run first");
    }

    @Test
    void nmiLineStayingLowDoesNotRaiseAnotherNmi() {
        h.writeByte(0xFFFA, 0x00);
        h.writeByte(0xFFFB, 0x06); // NMI vector -> $0600
        h.writeByte(0x0300, 0xEA);
        h.writeByte(0x0600, 0xEA); // NOP
        h.writeByte(0x0601, 0xEA); // NOP
        h.cpu.reset();
        h.cpu.setProgramCounter(0x0300);
        h.cpu.setStackPointer(0xFF);

        h.cpu.step(); // prime: fetch NOP
        h.cpu.setInterrupt(Cpu6502.S_NMI);
        h.cpu.step(); // run NOP, then SELECT the NMI sequence (does not run it yet)
        h.cpu.step(); // run the NMI sequence to completion

        // A device reasserting its already low output is not a new edge.
        h.cpu.setInterrupt(Cpu6502.S_NMI);
        assertFalse(h.cpu.isNmiAsserted());

        h.cpu.step(); // run the handler's first NOP
        h.cpu.step(); // and the second, which must not be replaced by another NMI
        assertEquals(0xFC, h.cpu.getStackPointer());
    }

    @Test
    void nmiLineGoingHighThenLowAgainRaisesAnotherNmi() {
        h.writeByte(0xFFFA, 0x00);
        h.writeByte(0xFFFB, 0x06); // NMI vector -> $0600
        h.writeByte(0x0300, 0xEA);
        h.writeByte(0x0600, 0xEA);
        h.cpu.reset();
        h.cpu.setProgramCounter(0x0300);
        h.cpu.setStackPointer(0xFF);

        h.cpu.step(); // prime: fetch NOP
        h.cpu.setInterrupt(Cpu6502.S_NMI);
        h.cpu.step(); // run NOP, then SELECT the NMI sequence (does not run it yet)
        h.cpu.step(); // run the NMI sequence to completion

        h.cpu.clearInterrupt(Cpu6502.S_NMI);
        h.cpu.setInterrupt(Cpu6502.S_NMI);
        assertTrue(h.cpu.isNmiAsserted());
    }

    @Test
    void nmiPulseIsLatchedEvenIfTheLineIsReleasedStraightAway() {
        h.writeByte(0xFFFA, 0x00);
        h.writeByte(0xFFFB, 0x06); // NMI vector -> $0600
        h.writeByte(0x0300, 0xEA);
        h.cpu.reset();
        h.cpu.setProgramCounter(0x0300);
        h.cpu.setStackPointer(0xFF);

        h.cpu.step(); // prime: fetch NOP
        h.cpu.setInterrupt(Cpu6502.S_NMI);
        h.cpu.clearInterrupt(Cpu6502.S_NMI);
        h.cpu.step(); // run NOP, then SELECT the NMI sequence (does not run it yet)
        h.cpu.step(); // run the NMI sequence to completion

        assertEquals(0x0601, h.cpu.getProgramCounter());
    }

    @Test
    void lateArrivingNmiHijacksAnInFlightIrqsVectorFetch() {
        // Real hardware: if NMI is recognised no later than the end of the PCL-push
        // cycle of an in-flight BRK/IRQ sequence (i.e. before the status-byte push
        // cycle begins), it hijacks the vector fetch - the CPU ends up at the NMI
        // handler instead of the IRQ handler, even though the status byte already
        // being pushed still reflects the original (hardware IRQ, break bit clear)
        // sequence. step() runs a whole 7-cycle interrupt sequence in one go, so this
        // test drives emulateCycle() directly to inject the NMI mid-sequence.
        h.writeByte(0xFFFE, 0x00);
        h.writeByte(0xFFFF, 0x05); // IRQ vector -> $0500 (must NOT be where we land)
        h.writeByte(0xFFFA, 0x00);
        h.writeByte(0xFFFB, 0x06); // NMI vector -> $0600 (hijacked target)
        h.writeByte(0x0300, 0xEA); // NOP
        h.cpu.reset();
        h.cpu.setProgramCounter(0x0300);
        h.cpu.setInterruptDisableFlag(false);
        h.cpu.setStackPointer(0xFF);

        h.cpu.step(); // prime: fetch NOP (no interrupt pending yet)
        h.cpu.setInterrupt(Cpu6502.S_IRQ);
        h.cpu.step(); // run NOP, then SELECT the pending IRQ sequence (does not run it yet)

        // Manually run the first 3 cycles of IRQ_STEPS: FETCH_DIS_PC, STORE_PCH_SP,
        // STORE_PCL_SP - i.e. up to (but not including) the status-byte push cycle.
        h.cpu.emulateCycle();
        h.cpu.emulateCycle();
        h.cpu.emulateCycle();

        // NMI arrives now - right at the edge of the real hijack window.
        h.cpu.setInterrupt(Cpu6502.S_NMI);

        // Run the remaining cycles: STORE_P_SP (hijack detected here), the (redirected)
        // vector fetch, and the fused next-opcode fetch.
        h.cpu.emulateCycle();
        h.cpu.emulateCycle();
        h.cpu.emulateCycle();
        h.cpu.emulateCycle();

        assertEquals(0x0601, h.cpu.getProgramCounter(), "must land at the NMI vector, not the IRQ vector");
        assertFalse(h.cpu.isNmiAsserted(), "the hijacking NMI must be consumed (edge-triggered)");
        int pushedStatus = h.readByte(PUSHED_STATUS_ADDRESS);
        assertEquals(0, pushedStatus & 0x10,
                "the pushed status byte must still reflect the original hardware IRQ (break bit clear)");
    }

    @Test
    void interruptArrivingAfterTheOperandFetchOfATakenSamePageBranchIsDelayed() {
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
        h.cpu.emulateCycle(); // BEQ's operand fetch
        h.cpu.setInterrupt(Cpu6502.S_IRQ); // too late for the only poll a taken branch does
        h.cpu.step(); // finish BEQ: interrupt not seen, so LDA is fetched normally

        assertEquals(0xA9, h.cpu.getInstructionRegister(),
                "an interrupt arriving this late must be delayed until after the next instruction");
        assertEquals(0xFF, h.cpu.getStackPointer(), "interrupt must not have been serviced yet");

        h.cpu.step(); // run LDA to completion, then SELECT the (now due) interrupt sequence
        assertEquals(0x42, h.cpu.getAccumulator()); // LDA still ran to completion first
        assertEquals(0xFF, h.cpu.getStackPointer(), "interrupt sequence selected but not yet run");

        h.cpu.step(); // run the interrupt sequence to completion
        assertTrue(h.cpu.getInterruptDisableFlag());
        assertEquals(0xFC, h.cpu.getStackPointer());
    }

    @Test
    void interruptArrivingBeforeTheOperandFetchOfATakenSamePageBranchIsNotDelayed() {
        h.writeByte(0xFFFE, 0x00);
        h.writeByte(0xFFFF, 0x05);
        h.cpu.reset();
        h.cpu.setProgramCounter(0x0300);
        h.writeByte(0x0300, 0xF0); // BEQ +5 (same page: target 0x0307)
        h.writeByte(0x0301, 0x05);
        h.cpu.setInterruptDisableFlag(false);
        h.cpu.setZeroResultFlag(true); // BEQ will be taken
        h.cpu.setStackPointer(0xFF);

        h.cpu.step(); // prime: fetch BEQ opcode
        h.cpu.setInterrupt(Cpu6502.S_IRQ); // in time for the poll at the end of the operand fetch
        h.cpu.step(); // run branch: the interrupt sequence is SELECTED straight after it

        assertEquals(0xF0, h.cpu.getInstructionRegister(),
                "no normal instruction fetch should have happened - the interrupt was not delayed");

        h.cpu.step(); // run the interrupt sequence to completion
        assertEquals(0x0501, h.cpu.getProgramCounter());
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
