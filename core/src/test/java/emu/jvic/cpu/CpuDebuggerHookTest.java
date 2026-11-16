package emu.jvic.cpu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import emu.jvic.debug.CpuDebugger;
import emu.jvic.testutil.CpuTestHarness;

/**
 * Tests for the debug monitor's hooks in {@link Cpu6502}: the instruction boundary
 * hook in dispatchNextInstruction(), the JAM hook, and redispatchAt().
 *
 * <p>"Stopping" here works the way the Machine debug loop will: the hook only sets a
 * flag, the current cycle runs to completion (including the overlapped opcode fetch),
 * and the test stops calling emulateCycle() after it.
 */
public class CpuDebuggerHookTest {

    private static final int PROGRAM = 0x0300;
    private static final int IRQ_HANDLER = 0x0500;
    private static final int NMI_HANDLER = 0x0600;

    private static final int NOP = 0xEA;
    private static final int JAM = 0x02;
    private static final int RTI = 0x40;

    /** One instruction boundary event. */
    private static final class Boundary {
        final int address;
        final int interrupt;
        final long cycle;

        Boundary(int address, int interrupt, long cycle) {
            this.address = address;
            this.interrupt = interrupt;
            this.cycle = cycle;
        }

        @Override
        public String toString() {
            return String.format("%04X/%d@%d", address, interrupt, cycle);
        }
    }

    /** Records events and requests a stop when the given boundary is reached. */
    private static final class RecordingDebugger implements CpuDebugger {
        final List<Boundary> boundaries = new ArrayList<>();
        final List<Integer> jams = new ArrayList<>();
        int stopAddress = -1;
        int stopInterrupt = 0;
        boolean stopPending;

        @Override
        public void onInstructionBoundary(Cpu6502 cpu, int address, int interrupt) {
            boundaries.add(new Boundary(address, interrupt, cpu.getTotalCycles()));
            if ((address == stopAddress) && (interrupt == stopInterrupt)) {
                stopPending = true;
            }
        }

        @Override
        public void onJam(Cpu6502 cpu, int address) {
            jams.add(address);
            stopPending = true;
        }

        void stopAt(int address, int interrupt) {
            stopAddress = address;
            stopInterrupt = interrupt;
            stopPending = false;
        }
    }

    private CpuTestHarness h;
    private RecordingDebugger dbg;

    @BeforeEach
    void setUp() {
        h = new CpuTestHarness();
        h.writeWord(0xFFFE, IRQ_HANDLER);
        h.writeWord(0xFFFA, NMI_HANDLER);
        h.writeByte(IRQ_HANDLER, RTI);
        h.writeByte(NMI_HANDLER, RTI);
        dbg = new RecordingDebugger();
    }

    private void start() {
        h.cpu.reset();
        h.cpu.setDebugger(dbg);
        h.cpu.setDebugHooksActive(true);
    }

    /** Emulates whole cycles until the debugger requests a stop, as the Machine will. */
    private void runUntilStop() {
        for (int i = 0; i < 10000; i++) {
            h.cpu.emulateCycle();
            if (dbg.stopPending) {
                return;
            }
        }
        fail("Debugger never requested a stop. Boundaries: " + dbg.boundaries);
    }

    private void runCycles(int cycles) {
        for (int i = 0; i < cycles; i++) {
            h.cpu.emulateCycle();
        }
    }

    private Boundary lastBoundary() {
        return dbg.boundaries.get(dbg.boundaries.size() - 1);
    }

    @Test
    void reportsEveryBoundaryWithCorrectCycleSpacing() {
        // LDA #$01 ends in EXECUTE_LAST (fetch overlaps its last cycle); STA $10 and
        // JMP don't. The hook must fire after both kinds.
        h.loadBytes(PROGRAM,
                0xA9, 0x01,         // $0300 LDA #$01   2 cycles
                0x85, 0x10,         // $0302 STA $10    3 cycles
                NOP,                // $0304 NOP        2 cycles
                0x4C, 0x00, 0x03);  // $0305 JMP $0300  3 cycles
        start();
        dbg.stopAt(PROGRAM, 0);
        runCycles(1);  // first dispatch after reset
        dbg.stopPending = false;
        runUntilStop(); // round the loop once, back to $0300

        int[] expectedAddresses = { 0x0300, 0x0302, 0x0304, 0x0305, 0x0300 };
        int[] expectedCycleDeltas = { 2, 3, 2, 3 };
        assertEquals(expectedAddresses.length, dbg.boundaries.size(), dbg.boundaries.toString());
        for (int i = 0; i < expectedAddresses.length; i++) {
            assertEquals(expectedAddresses[i], dbg.boundaries.get(i).address, "boundary " + i);
            assertEquals(0, dbg.boundaries.get(i).interrupt, "boundary " + i);
        }
        for (int i = 0; i < expectedCycleDeltas.length; i++) {
            assertEquals(expectedCycleDeltas[i],
                    dbg.boundaries.get(i + 1).cycle - dbg.boundaries.get(i).cycle, "delta " + i);
        }
    }

    @Test
    void stoppedAfterExecuteLastHasOpcodeLatchedAndPcPastIt() {
        h.loadBytes(PROGRAM, 0xA9, 0x01, 0x85, 0x10);  // LDA #$01; STA $10
        start();
        dbg.stopAt(0x0302, 0);
        runUntilStop();

        // The overlapped-fetch rule: the STA opcode is already latched.
        assertEquals(0x85, h.cpu.getInstructionRegister());
        assertEquals(0x0303, h.cpu.getProgramCounter());
        assertEquals(0x01, h.cpu.getAccumulator());
        assertEquals(0x00, h.readByte(0x10));
    }

    @Test
    void noBoundaryEventsWhileHooksInactive() {
        h.loadBytes(PROGRAM, NOP, NOP, NOP, 0x4C, 0x00, 0x03);
        h.cpu.reset();
        h.cpu.setDebugger(dbg);
        runCycles(200);
        assertTrue(dbg.boundaries.isEmpty());

        h.cpu.setDebugHooksActive(true);
        runCycles(20);
        assertFalse(dbg.boundaries.isEmpty());

        h.cpu.setDebugHooksActive(false);
        int count = dbg.boundaries.size();
        runCycles(200);
        assertEquals(count, dbg.boundaries.size());
    }

    @Test
    void hooksCannotBeActivatedWithoutDebugger() {
        assertThrows(IllegalStateException.class, () -> h.cpu.setDebugHooksActive(true));

        h.cpu.setDebugger(dbg);
        h.cpu.setDebugHooksActive(true);
        h.cpu.setDebugger(null);
        assertFalse(h.cpu.isDebugHooksActive());
        assertNull(h.cpu.getDebugger());
    }

    @Test
    void irqBoundaryIsTaggedAndInterruptedInstructionRunsAfterRti() {
        h.loadBytes(PROGRAM, NOP, NOP, JAM);
        start();
        h.cpu.setInterruptDisableFlag(false);
        h.cpu.setInterrupt(Cpu6502.S_IRQ);

        // The IRQ is taken at the boundary before the second NOP. The second NOP has
        // not executed, so this boundary is tagged as an interrupt, not an exec of $0301.
        dbg.stopAt(0x0301, Cpu6502.S_IRQ);
        runUntilStop();
        assertTrue(h.cpu.isInterruptDispatched());
        h.cpu.clearInterrupt(Cpu6502.S_IRQ);

        dbg.stopAt(IRQ_HANDLER, 0);
        runUntilStop();
        assertFalse(h.cpu.isInterruptDispatched());

        // After RTI, the NOP at $0301 is reached again, this time as a normal exec.
        dbg.stopAt(0x0301, 0);
        runUntilStop();

        List<Boundary> b = dbg.boundaries;
        assertEquals("[0300/0@1, 0301/1@3, 0500/0@10, 0301/0@16]", b.toString());
    }

    @Test
    void nmiBoundaryIsTagged() {
        h.loadBytes(PROGRAM, NOP, NOP, JAM);
        start();
        h.cpu.setInterrupt(Cpu6502.S_NMI); // not masked by I, which reset set

        dbg.stopAt(0x0301, Cpu6502.S_NMI);
        runUntilStop();
        assertTrue(h.cpu.isInterruptDispatched());

        dbg.stopAt(NMI_HANDLER, 0);
        runUntilStop();
    }

    @Test
    void redispatchAtExecutesNewAddressInsteadOfLatchedOpcode() {
        h.loadBytes(PROGRAM, 0xA9, 0x01, 0x85, 0x10, JAM);  // LDA #$01; STA $10
        h.loadBytes(0x0400, 0xA2, 0x42, JAM);               // LDX #$42
        start();
        dbg.stopAt(0x0302, 0);
        runUntilStop();
        long cycles = h.cpu.getTotalCycles();

        h.cpu.redispatchAt(0x0400);
        assertEquals(cycles, h.cpu.getTotalCycles(), "redispatchAt must not consume cycles");
        assertEquals(0xA2, h.cpu.getInstructionRegister());
        assertEquals(0x0401, h.cpu.getProgramCounter());

        dbg.stopAt(0x0402, 0);
        runUntilStop();
        assertEquals(0x42, h.cpu.getIndexRegisterX());
        assertEquals(0x00, h.readByte(0x10), "the latched STA must not run");
        assertEquals(cycles + 2, h.cpu.getTotalCycles(), "LDX # takes 2 cycles");
    }

    @Test
    void redispatchAtAfterNonExecuteLastInstruction() {
        h.loadBytes(PROGRAM, 0x85, 0x10, 0xA9, 0x07, JAM);  // STA $10; LDA #$07
        h.loadBytes(0x0400, 0xA0, 0x99, JAM);               // LDY #$99
        start();
        dbg.stopAt(0x0302, 0);
        runUntilStop();
        assertEquals(0xA9, h.cpu.getInstructionRegister());

        h.cpu.redispatchAt(0x0400);
        dbg.stopAt(0x0402, 0);
        runUntilStop();
        assertEquals(0x99, h.cpu.getIndexRegisterY());
        assertEquals(0x00, h.cpu.getAccumulator(), "the latched LDA must not run");
    }

    @Test
    void memoryWriteUnderLatchedOpcodeTakesEffectAfterRedispatch() {
        h.loadBytes(PROGRAM, 0xA9, 0x01, 0x85, 0x10, JAM);  // LDA #$01; STA $10
        start();
        dbg.stopAt(0x0302, 0);
        runUntilStop();

        // As the monitor's ">" command would: patch STA $10 into LDY #$33, then
        // redispatch at the boundary address so the new byte is what runs.
        h.loadBytes(0x0302, 0xA0, 0x33);
        h.cpu.redispatchAt(0x0302);

        dbg.stopAt(0x0304, 0);
        runUntilStop();
        assertEquals(0x33, h.cpu.getIndexRegisterY());
        assertEquals(0x00, h.readByte(0x10));
    }

    @Test
    void redispatchAtWithDispatchedInterruptChangesOnlyReturnAddress() {
        h.loadBytes(PROGRAM, NOP, NOP, JAM);
        h.loadBytes(0x0400, 0xA2, 0x55, JAM);  // LDX #$55
        start();
        h.cpu.setInterruptDisableFlag(false);
        h.cpu.setInterrupt(Cpu6502.S_IRQ);
        dbg.stopAt(0x0301, Cpu6502.S_IRQ);
        runUntilStop();
        h.cpu.clearInterrupt(Cpu6502.S_IRQ);

        h.cpu.redispatchAt(0x0400);
        assertTrue(h.cpu.isInterruptDispatched(), "the IRQ stays dispatched");

        dbg.stopAt(IRQ_HANDLER, 0);
        runUntilStop();
        assertEquals(0x04, h.readByte(0x01FF), "pushed PCH");
        assertEquals(0x00, h.readByte(0x01FE), "pushed PCL");

        // RTI returns to the new address.
        dbg.stopAt(0x0402, 0);
        runUntilStop();
        assertEquals(0x55, h.cpu.getIndexRegisterX());
        assertEquals(Integer.valueOf(0x0402), Integer.valueOf(lastBoundary().address));
    }

    @Test
    void jamIsReportedOnceAtJamOpcodeAddress() {
        h.loadBytes(PROGRAM, NOP, JAM);
        start();
        runUntilStop();
        assertEquals(List.of(0x0301), dbg.jams);
        assertTrue(h.cpu.isJammed());

        dbg.stopPending = false;
        runCycles(500);
        assertEquals(List.of(0x0301), dbg.jams, "reported once, not every cycle");
        assertFalse(dbg.stopPending);
        assertEquals("[0300/0@1, 0301/0@3]", dbg.boundaries.toString());
    }

    @Test
    void jamIsReportedEvenWhileHooksInactive() {
        h.loadBytes(PROGRAM, NOP, JAM);
        h.cpu.reset();
        h.cpu.setDebugger(dbg);
        runCycles(50);
        assertEquals(List.of(0x0301), dbg.jams);
        assertTrue(dbg.boundaries.isEmpty());
    }

    @Test
    void jamIsTrackedWithoutDebugger() {
        h.loadBytes(PROGRAM, NOP, JAM);
        h.cpu.reset();
        runCycles(3);
        assertFalse(h.cpu.isJammed(), "JAM fetched but not yet executed");
        runCycles(1);
        assertTrue(h.cpu.isJammed());
        h.cpu.reset();
        assertFalse(h.cpu.isJammed());
    }

    @Test
    void redispatchAtRecoversFromJamAndALaterJamIsReportedAgain() {
        h.loadBytes(PROGRAM, NOP, JAM);
        h.loadBytes(0x0400, 0xA2, 0x42, JAM);  // LDX #$42; JAM
        start();
        runUntilStop();
        assertTrue(h.cpu.isJammed());

        h.cpu.redispatchAt(0x0400);
        assertFalse(h.cpu.isJammed());
        dbg.stopPending = false;
        runUntilStop();

        assertEquals(0x42, h.cpu.getIndexRegisterX());
        assertEquals(List.of(0x0301, 0x0402), dbg.jams);
        assertTrue(h.cpu.isJammed());
    }

    @Test
    void resetClearsJamSoNextJamIsReported() {
        h.loadBytes(PROGRAM, JAM);
        start();
        runUntilStop();
        h.cpu.reset();
        dbg.stopPending = false;
        runUntilStop();
        assertEquals(List.of(0x0300, 0x0300), dbg.jams);
    }
}
