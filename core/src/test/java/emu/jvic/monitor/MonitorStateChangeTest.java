package emu.jvic.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import emu.jvic.MachineType;
import emu.jvic.debug.StateReader;
import emu.jvic.debug.StateWriter;
import emu.jvic.memory.RamType;
import emu.jvic.testutil.MachineTestHarness;

/**
 * Tests for {@link MonitorSession#onMachineStateChanged()}, which the runners call
 * after the reset (Alt+F9/Alt+F12) and quick load (Alt+F10) hot keys replace the
 * machine's state while the monitor is attached.
 */
public class MonitorStateChangeTest {

    private static MonitorSession attachedSession(MachineTestHarness h) {
        MonitorSession session = new MonitorSession(new MonitorSession.Platform() {
            @Override
            public void resumeEmulation() {
            }

            @Override
            public void quit() {
            }

            @Override
            public boolean isWarp() {
                return false;
            }

            @Override
            public void setWarp(boolean warp) {
            }
        });
        session.attach(h.machine);
        return session;
    }

    @Test
    void afterAResetTheMonitorShowsTheResetPcAndDropsPendingTyping() {
        MachineTestHarness h = MachineTestHarness.withRealRoms(MachineType.PAL, RamType.RAM_UNEXPANDED);
        MonitorSession session = attachedSession(h);
        h.frames(50);
        // Part-way through an instruction, as a hot key between frames can be.
        h.cycles(1001);

        int[] mem = h.machine.getMemory().getMemoryArray();
        mem[0xC6] = 10;
        session.getEngine().queueKeys("RUN\r");

        h.machine.reset();
        session.onMachineStateChanged();

        AddressSpace main = session.getEngine().getMainSpace();
        assertFalse(main.getDebugCpu().isAtBoundary());
        assertEquals(h.machine.getCpu().getProgramCounter() & 0xFFFF, main.getDisplayPc());
        // The KERNAL's reset vector.
        assertEquals(0xFD22, main.getDisplayPc());

        mem[0xC6] = 0;
        session.onFrame();
        assertEquals(0, mem[0xC6], "the keybuf typing queued before the reset is dropped");
    }

    @Test
    void aQuickLoadWhileAttachedLeavesTheMachineRunningOnFromTheState() {
        MachineTestHarness h = MachineTestHarness.withRealRoms(MachineType.PAL, RamType.RAM_UNEXPANDED);
        MonitorSession session = attachedSession(h);
        h.frames(50);
        h.cycles(777);
        StateWriter out = new StateWriter();
        h.machine.saveState(out);
        byte[] state = out.toByteArray();
        int pc = h.machine.getCpu().getProgramCounter();

        h.frames(20);
        h.machine.loadState(new StateReader(state));
        session.onMachineStateChanged();

        assertEquals(pc & 0xFFFF, session.getEngine().getMainSpace().getDisplayPc());
        h.frames(5);
        assertFalse(h.machine.isDebuggerStopped());
    }
}
