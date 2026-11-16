package emu.jvic.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import emu.jvic.MachineType;
import emu.jvic.memory.RamType;
import emu.jvic.testutil.MachineTestHarness;

/**
 * Tests for {@link MonitorSession}, driving it the way a runner's loop does: update
 * the machine, and call onMachineStopped whenever it is stopped.
 */
public class MonitorSessionTest {

    private MachineTestHarness h;
    private MonitorSession session;
    private StringBuilder output;
    private int resumes;
    private boolean warp;
    private int quits;

    @BeforeEach
    void setUp() {
        h = MachineTestHarness.withSyntheticRoms(MachineType.PAL, RamType.RAM_UNEXPANDED);
        output = new StringBuilder();
        session = new MonitorSession(new MonitorSession.Platform() {
            @Override
            public void resumeEmulation() {
                resumes++;
            }

            @Override
            public void quit() {
                quits++;
            }

            @Override
            public boolean isWarp() {
                return warp;
            }

            @Override
            public void setWarp(boolean warp) {
                MonitorSessionTest.this.warp = warp;
            }
        });
        session.addListener(output::append);
    }

    /** Runs the machine the way the desktop runner's loop does, for some frames. */
    private void runLoop(int frames) {
        for (int i = 0; i < frames; i++) {
            if (h.machine.isDebuggerStopped()) {
                session.onMachineStopped();
            } else {
                h.machine.update();
                if (h.machine.isDebuggerStopped()) {
                    session.onMachineStopped();
                }
            }
        }
    }

    private String take() {
        String text = output.toString();
        output.setLength(0);
        return text;
    }

    @Test
    void withoutAMachineCommandsAreRefused() {
        session.execute("r");
        assertEquals("No machine is running.\n", take());
    }

    @Test
    void aCommandWhileRunningStopsTheMachineFirst() {
        session.attach(h.machine);
        runLoop(3);
        assertFalse(h.machine.isDebuggerStopped());

        session.execute("p 2");
        assertEquals("", take(), "nothing runs until the machine has stopped");
        runLoop(1);
        assertTrue(h.machine.isDebuggerStopped());
        assertEquals("\t2\n(C:$e001) ", take());

        session.execute("~ 10");
        assertEquals("+16\n$10\n0020\n%00010000\n(C:$e001) ", take(), "while stopped, commands run at once");
    }

    @Test
    void stoppingSendsThePromptOnce() {
        session.attach(h.machine);
        session.requestStop();
        runLoop(5);
        assertEquals("(C:$e000) ", take(), "the first instruction after reset is the CLI at $e000");
    }

    @Test
    void commandsAfterAResumeWaitForTheNextStop() {
        session.attach(h.machine);
        session.requestStop();
        runLoop(1);
        take();

        session.execute("bk e001");
        session.execute("x");
        assertEquals(1, resumes);
        assertFalse(h.machine.isDebuggerStopped());
        session.execute("p 3");
        assertEquals("BREAK: 1  C:$e001  (Stop on exec)\n(C:$e000) ", take(), "no prompt after x");

        runLoop(2);
        String out = take();
        assertTrue(out.startsWith("#1 (Stop on  exec e001)"), out);
        assertTrue(out.endsWith("\t3\n(C:$e001) "), out);
    }

    @Test
    void hostServicesGoToThePlatform() {
        session.attach(h.machine);
        session.requestStop();
        runLoop(1);
        session.execute("warp on");
        assertTrue(warp);
        session.execute("q");
        assertEquals(1, quits);
    }

    @Test
    void detachForgetsTheMachine() {
        session.attach(h.machine);
        session.detach();
        session.execute("r");
        assertEquals("No machine is running.\n", take());
    }
}
