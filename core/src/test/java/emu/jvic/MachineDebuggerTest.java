package emu.jvic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import emu.jvic.cpu.Cpu6502;
import emu.jvic.debug.DebugCpu;
import emu.jvic.debug.Debugger;
import emu.jvic.memory.RamType;
import emu.jvic.testutil.MachineTestHarness;

/**
 * Tests for the Machine side of the debug monitor hooks: the {@link Debugger}, the
 * debug update loop, and stopping at the end of a whole machine cycle.
 */
public class MachineDebuggerTest {

    /** The synthetic KERNAL idles in "JMP $E001" at $E001. */
    private static final int IDLE_LOOP = MachineTestHarness.SYNTHETIC_RESET_ADDRESS + 1;

    /** A listener that stops after a given number of boundaries of a given CPU. */
    private static final class CountingListener implements Debugger.Listener {
        final DebugCpu target;
        final int stopAfter;
        int count;
        long hookCycle = -1;
        int stoppedCount;

        CountingListener(DebugCpu target, int stopAfter) {
            this.target = target;
            this.stopAfter = stopAfter;
        }

        @Override
        public boolean onInstructionBoundary(DebugCpu cpu, int address, int interrupt) {
            if (cpu != target) {
                return false;
            }
            count++;
            if (count == stopAfter) {
                hookCycle = cpu.getCpu().getTotalCycles();
                return true;
            }
            return false;
        }

        @Override
        public void onStopped(Debugger debugger) {
            stoppedCount++;
        }
    }

    private static MachineTestHarness synthetic() {
        return MachineTestHarness.withSyntheticRoms(MachineType.PAL, RamType.RAM_UNEXPANDED);
    }

    private static MachineTestHarness real() {
        return MachineTestHarness.withRealRoms(MachineType.PAL, RamType.RAM_UNEXPANDED);
    }

    /**
     * The VIC reports its first frame as complete after only 2 cycles of a freshly
     * initialised machine. Tests that expect one update() to cover a whole frame call
     * this first.
     */
    private static void skipShortFirstFrame(Machine machine) {
        machine.update();
    }

    private static void runFrames(Machine machine, int frames) {
        for (int i = 0; i < frames; i++) {
            machine.update();
        }
    }

    private static void assertSameState(Machine expected, Machine actual) {
        Cpu6502 e = expected.getCpu();
        Cpu6502 a = actual.getCpu();
        assertEquals(e.getTotalCycles(), a.getTotalCycles(), "total cycles");
        assertEquals(e.getProgramCounter(), a.getProgramCounter(), "PC");
        assertEquals(e.getAccumulator(), a.getAccumulator(), "A");
        assertEquals(e.getIndexRegisterX(), a.getIndexRegisterX(), "X");
        assertEquals(e.getIndexRegisterY(), a.getIndexRegisterY(), "Y");
        assertEquals(e.getStackPointer(), a.getStackPointer(), "SP");
        assertEquals(e.getProcessorStatus(), a.getProcessorStatus(), "P");
        assertArrayEquals(expected.getMemory().getMemoryArray(), actual.getMemory().getMemoryArray(), "memory");
    }

    @Test
    void noDebuggerUnlessEnabled() {
        MachineTestHarness h = synthetic();
        assertNull(h.machine.getDebugger());
        assertNull(h.machine.getCpu().getDebugger());
        assertFalse(h.machine.isDebuggerArmed());
        assertFalse(h.machine.isDebuggerStopped());
        assertFalse(h.machine.checkDebuggerStop());
    }

    @Test
    void enableDebuggerAttachesToBothCpusWithHooksOff() {
        MachineTestHarness h = synthetic();
        Debugger debugger = h.machine.enableDebugger();

        assertNotNull(debugger);
        assertSame(debugger, h.machine.enableDebugger(), "enabling twice returns the same Debugger");
        assertSame(debugger.getMainCpu(), h.machine.getCpu().getDebugger());
        assertSame(debugger.getDriveCpu(), h.machine.getC1541Drive().getCpu().getDebugger());
        assertSame(h.machine.getC1541Drive().getMemory(), debugger.getDriveCpu().getMemory());
        assertEquals("C", debugger.getMainCpu().getName());
        assertEquals("8", debugger.getDriveCpu().getName());
        assertFalse(h.machine.getCpu().isDebugHooksActive());
        assertFalse(debugger.isArmed());

        long before = h.machine.getCpu().getTotalCycles();
        runFrames(h.machine, 3);
        assertTrue(h.machine.getCpu().getTotalCycles() > before);
        assertFalse(debugger.isStopped());
    }

    @Test
    void requestStopStopsAtNextMainCpuBoundary() {
        MachineTestHarness h = synthetic();
        Debugger debugger = h.machine.enableDebugger();
        CountingListener listener = new CountingListener(null, 0);
        debugger.setListener(listener);
        runFrames(h.machine, 5);

        debugger.requestStop();
        assertTrue(debugger.isArmed());
        h.machine.update();

        assertTrue(debugger.isStopped());
        assertEquals(1, listener.stoppedCount);
        assertEquals(Debugger.STOP_REQUEST, debugger.getStopReason());
        assertSame(debugger.getMainCpu(), debugger.getStopCpu());
        assertEquals(IDLE_LOOP, debugger.getStopAddress());

        DebugCpu main = debugger.getMainCpu();
        assertTrue(main.isAtBoundary());
        assertEquals(IDLE_LOOP, main.getBoundaryAddress());
        assertEquals(0, main.getBoundaryInterrupt());
        // The overlapped fetch: the JMP opcode is latched and PC is past it.
        assertEquals(0x4C, h.machine.getCpu().getInstructionRegister());
        assertEquals(IDLE_LOOP + 1, h.machine.getCpu().getProgramCounter());
        assertFalse(main.isHooksActive(), "the one-shot stop turns the hook off again");

        // While stopped, update() does nothing.
        long cycles = h.machine.getCpu().getTotalCycles();
        runFrames(h.machine, 3);
        assertEquals(cycles, h.machine.getCpu().getTotalCycles());

        debugger.resume();
        assertFalse(debugger.isArmed());
        runFrames(h.machine, 2);
        assertTrue(h.machine.getCpu().getTotalCycles() > cycles);
        assertFalse(debugger.isStopped());
        assertEquals(1, listener.stoppedCount);
    }

    @Test
    void listenerStopHappensAtTheEndOfTheHookCycle() {
        MachineTestHarness h = real();
        Debugger debugger = h.machine.enableDebugger();
        CountingListener listener = new CountingListener(debugger.getMainCpu(), 5000);
        debugger.setListener(listener);
        debugger.getMainCpu().setListenerHooks(true);

        for (int i = 0; (i < 100) && !debugger.isStopped(); i++) {
            h.machine.update();
        }
        assertTrue(debugger.isStopped());
        assertEquals(Debugger.STOP_LISTENER, debugger.getStopReason());
        assertEquals(listener.hookCycle, h.machine.getCpu().getTotalCycles(),
                "no cycles run after the one in which the hook asked to stop");
        assertTrue(debugger.getMainCpu().isAtBoundary());
        assertEquals(5000, listener.count);
    }

    @Test
    void debugLoopEmulatesExactlyLikeTheNormalLoop() {
        MachineTestHarness plain = real();
        MachineTestHarness debugged = real();
        Debugger debugger = debugged.machine.enableDebugger();
        // Hooks on, but never stops: every frame goes through the debug loop.
        debugger.setListener(new CountingListener(debugger.getMainCpu(), -1));
        debugger.getMainCpu().setListenerHooks(true);
        debugger.getDriveCpu().setListenerHooks(true);
        assertTrue(debugger.isArmed());

        runFrames(plain.machine, 150);
        runFrames(debugged.machine, 150);
        assertSameState(plain.machine, debugged.machine);
    }

    @Test
    void stoppingMidFrameAndResumingMatchesUninterruptedRun() {
        MachineTestHarness plain = real();
        MachineTestHarness debugged = real();
        Debugger debugger = debugged.machine.enableDebugger();
        CountingListener listener = new CountingListener(debugger.getMainCpu(), 20000);
        debugger.setListener(listener);
        debugger.getMainCpu().setListenerHooks(true);

        int updates = 0;
        while (!debugger.isStopped()) {
            debugged.machine.update();
            updates++;
            if (updates > 100) {
                fail("never stopped");
            }
        }
        debugger.getMainCpu().setListenerHooks(false);
        debugger.resume();
        // The update after the stop finishes the interrupted frame.
        runFrames(debugged.machine, 60 - updates + 1);
        runFrames(plain.machine, 60);
        assertSameState(plain.machine, debugged.machine);
    }

    @Test
    void jamStopsTheMachineWithoutAnyHooks() {
        MachineTestHarness h = synthetic();
        Debugger debugger = h.machine.enableDebugger();
        h.getMemory().getMemoryArray()[0x1000] = 0xEA; // NOP
        h.getMemory().getMemoryArray()[0x1001] = 0x02; // JAM
        skipShortFirstFrame(h.machine);

        debugger.requestStop();
        h.machine.update();
        assertTrue(debugger.isStopped());
        debugger.getMainCpu().redispatchAt(0x1000);
        debugger.resume();
        assertFalse(debugger.isArmed(), "nothing armed: the normal loop runs");

        h.machine.update();
        assertTrue(debugger.isStopped());
        assertEquals(Debugger.STOP_JAM, debugger.getStopReason());
        assertEquals(0x1001, debugger.getStopAddress());
        assertSame(debugger.getMainCpu(), debugger.getStopCpu());
        assertTrue(h.machine.getCpu().isJammed());
    }

    @Test
    void requestStopWhileJammedStopsAtOnce() {
        MachineTestHarness h = synthetic();
        Debugger debugger = h.machine.enableDebugger();
        h.getMemory().getMemoryArray()[0x1000] = 0x02; // JAM
        debugger.requestStop();
        h.machine.update();
        debugger.getMainCpu().redispatchAt(0x1000);
        debugger.resume();
        h.machine.update();
        assertEquals(Debugger.STOP_JAM, debugger.getStopReason());

        // Leave the monitor with the CPU still jammed, then ask to stop again.
        debugger.resume();
        h.machine.update();
        assertFalse(debugger.isStopped(), "the JAM is reported once");
        debugger.requestStop();
        long cycles = h.machine.getCpu().getTotalCycles();
        h.machine.update();
        assertTrue(debugger.isStopped());
        assertEquals(Debugger.STOP_REQUEST, debugger.getStopReason());
        assertEquals(0x1000, debugger.getStopAddress());
        assertEquals(cycles + 1, h.machine.getCpu().getTotalCycles());
    }

    @Test
    void driveCpuBoundaryCanStopTheMachine() throws IOException {
        MachineTestHarness h = real();
        h.machine.getC1541Drive().insertDisk(loadFixture("assets/data/blank.d64"), false, null, false);
        skipShortFirstFrame(h.machine);
        Debugger debugger = h.machine.enableDebugger();
        CountingListener listener = new CountingListener(debugger.getDriveCpu(), 100);
        debugger.setListener(listener);
        debugger.getDriveCpu().setListenerHooks(true);

        h.machine.update();
        assertTrue(debugger.isStopped());
        assertEquals(Debugger.STOP_LISTENER, debugger.getStopReason());
        assertSame(debugger.getDriveCpu(), debugger.getStopCpu());
        assertTrue(debugger.getDriveCpu().isAtBoundary());
        assertEquals(listener.hookCycle, h.machine.getC1541Drive().getCpu().getTotalCycles());
        assertFalse(debugger.getMainCpu().isAtBoundary(), "main CPU hooks were off");
    }

    @Test
    void runnerLoopApiStopsAfterTheRequestedCycle() {
        // The web workers drive emulateCycle() themselves.
        MachineTestHarness h = synthetic();
        Debugger debugger = h.machine.enableDebugger();
        debugger.requestStop();
        assertTrue(h.machine.isDebuggerArmed());

        int cycles = 0;
        do {
            h.machine.emulateCycle();
            cycles++;
        } while (!h.machine.checkDebuggerStop() && (cycles < 100));

        assertTrue(cycles <= 3, "JMP is 3 cycles, so a boundary is at most 3 cycles away");
        assertTrue(h.machine.isDebuggerStopped());
        assertTrue(debugger.getMainCpu().isAtBoundary());
        assertFalse(h.machine.checkDebuggerStop(), "already stopped, nothing pending");
    }

    @Test
    void stopsFromBothCpusInOneCycleReportTheFirst() {
        MachineTestHarness h = synthetic();
        Debugger debugger = h.machine.enableDebugger();
        debugger.stop(Debugger.STOP_LISTENER, debugger.getMainCpu(), 0x1234);
        debugger.stop(Debugger.STOP_JAM, debugger.getDriveCpu(), 0x5678);
        h.machine.update();
        assertSame(debugger.getMainCpu(), debugger.getStopCpu());
        assertEquals(0x1234, debugger.getStopAddress());
        assertEquals(Debugger.STOP_LISTENER, debugger.getStopReason());
    }

    @Test
    void viaAndDatasetteGetters() {
        MachineTestHarness h = synthetic();
        assertSame(h.machine.getMemory().getMemoryMap()[0x9110], h.machine.getVia1());
        assertSame(h.machine.getMemory().getMemoryMap()[0x9120], h.machine.getVia2());
        assertNotNull(h.machine.getDatasette());
        assertSame(h.machine.getC1541Drive().getMemory().getMemoryMap()[0x1800],
                h.machine.getC1541Drive().getVia1());
        assertSame(h.machine.getC1541Drive().getMemory().getMemoryMap()[0x1C00],
                h.machine.getC1541Drive().getVia2());
        assertNull(h.machine.getC1541Drive().getDiskImage());
    }

    private static byte[] loadFixture(String relativePath) throws IOException {
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve(relativePath);
            if (Files.exists(candidate)) {
                return Files.readAllBytes(candidate);
            }
            dir = dir.getParent();
        }
        throw new IOException("Fixture not found: " + relativePath);
    }
}
