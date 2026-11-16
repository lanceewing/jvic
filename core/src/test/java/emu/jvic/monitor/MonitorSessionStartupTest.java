package emu.jvic.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import emu.jvic.MachineType;
import emu.jvic.memory.RamType;
import emu.jvic.testutil.MachineTestHarness;

/**
 * Tests for the session's startup options (moncommands, monlog, initbreak), driving it
 * the way a runner's loop does.
 */
public class MonitorSessionStartupTest {

    private MachineTestHarness h;
    private MonitorSession session;
    private InMemoryMonitorFileSystem fs;
    private final StringBuilder output = new StringBuilder();

    private void start(String monCommands, String monLog, int initBreak, boolean realRoms) {
        h = realRoms ? MachineTestHarness.withRealRoms(MachineType.PAL, RamType.RAM_UNEXPANDED)
                : MachineTestHarness.withSyntheticRoms(MachineType.PAL, RamType.RAM_UNEXPANDED);
        session = new MonitorSession(new MonitorSession.Platform() {
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
        fs = new InMemoryMonitorFileSystem();
        session.setFileSystem(fs);
        session.addListener(output::append);
        session.setStartup(monCommands, monLog, initBreak);
        session.attach(h.machine);
    }

    private void file(String name, String text) {
        fs.write(name, text.getBytes(StandardCharsets.ISO_8859_1));
    }

    /** The desktop runner's loop, for some frames. */
    private void runLoop(int frames) {
        for (int i = 0; i < frames; i++) {
            if (h.machine.isDebuggerStopped()) {
                session.onMachineStopped();
            } else {
                h.machine.update();
                session.onFrame();
                if (h.machine.isDebuggerStopped()) {
                    session.onMachineStopped();
                }
            }
        }
    }

    @Test
    void moncommandsRunAtTheFirstInstructionThenTheMachineResumes() {
        // The file is read at the startup stop, so it can be written after start().
        start("mon.txt", "log.txt", MonitorSession.INITBREAK_NONE, false);
        file("mon.txt", "p 5\n");
        runLoop(5);
        assertFalse(h.machine.isDebuggerStopped());
        assertEquals("\t5\n", output.toString());
        assertEquals("\t5\n", new String(fs.read("log.txt"), StandardCharsets.ISO_8859_1));
    }

    @Test
    void linesAfterAResumeRunAtTheNextStop() {
        start("mon.txt", null, MonitorSession.INITBREAK_NONE, false);
        file("mon.txt", "break exec e001\nx\np 7\n");
        runLoop(5);
        assertTrue(h.machine.isDebuggerStopped());
        String text = output.toString();
        assertTrue(text.startsWith("BREAK: 1  C:$e001  (Stop on exec)\n#1 (Stop on  exec e001)"), text);
        assertTrue(text.endsWith("\t7\n(C:$e001) "), text);
    }

    @Test
    void initbreakAtAnAddressStaysStopped() {
        start(null, null, 0xE001, false);
        runLoop(5);
        assertTrue(h.machine.isDebuggerStopped());
        assertTrue(output.toString().endsWith("(C:$e001) "), output.toString());
    }

    @Test
    void initbreakReadyStopsWhenBasicIsReady() {
        start(null, null, MonitorSession.INITBREAK_READY, true);
        runLoop(300);
        assertTrue(h.machine.isDebuggerStopped());
        assertEquals(110, h.machine.getMemory().getMemoryArray()[0xD1]);
    }

    @Test
    void parsesInitbreak() {
        assertEquals(MonitorSession.INITBREAK_RESET, MonitorSession.parseInitBreak("reset"));
        assertEquals(MonitorSession.INITBREAK_READY, MonitorSession.parseInitBreak("READY"));
        assertEquals(0xE518, MonitorSession.parseInitBreak("$e518"));
        assertEquals(0xE518, MonitorSession.parseInitBreak("0xE518"));
        assertThrows(IllegalArgumentException.class, () -> MonitorSession.parseInitBreak("later"));
    }
}
