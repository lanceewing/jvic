package emu.jvic;

import emu.jvic.memory.RamType;
import emu.jvic.testutil.MachineTestHarness;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The highest-value single test in this suite: boots a real, fully-wired
 * {@link emu.jvic.Machine} using the project's genuine bundled ROM images
 * (see {@link MachineTestHarness#withRealRoms}) - 100% real, unmodified
 * KERNAL/BASIC machine code, running through the full real memory map and
 * full real chip wiring simultaneously (VIC, CPU, VIA1, VIA2, colour RAM,
 * screen RAM, ROMs).
 *
 * <p>Empirically confirmed (see this test's own assertions, run against the
 * real ROMs) that an unexpanded PAL machine reaches the READY prompt well
 * within 50 frames (1 second of emulated time) and prints the well-known,
 * documented unexpanded-VIC-20 boot banner: "**** CBM BASIC V2 ****", "3583
 * BYTES FREE" (the textbook free-memory figure for an unexpanded VIC-20),
 * and "READY.".
 */
public class MachineBootTest {

    private static final int SCREEN_BASE = 0x1E00; // Default unexpanded screen RAM location.
    private static final int CHARS_PER_ROW = 22;

    /** Decodes a row of VIC-20 screen memory (PETSCII screen codes) into readable text. */
    private static String decodeScreenCodeRow(int[] mem, int base) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < CHARS_PER_ROW; i++) {
            int code = mem[base + i] & 0xFF;
            char c;
            if (code == 0x20) {
                c = ' ';
            } else if (code >= 0x01 && code <= 0x1A) { // Screen codes 1-26 are 'A'-'Z'.
                c = (char) ('A' + code - 1);
            } else if (code >= 0x30 && code <= 0x39) { // Screen codes 0x30-0x39 are '0'-'9'.
                c = (char) ('0' + code - 0x30);
            } else if (code == 0x2A) {
                c = '*';
            } else if (code == 0x2E) {
                c = '.';
            } else {
                c = '?';
            }
            sb.append(c);
        }
        return sb.toString().trim();
    }

    @Test
    void unexpandedPalMachineBootsToTheReadyPromptWithTheDocumentedFreeMemoryFigure() {
        MachineTestHarness h = MachineTestHarness.withRealRoms(MachineType.PAL, RamType.RAM_UNEXPANDED);

        h.frames(50);

        int[] mem = h.getMemory().getMemoryArray();
        assertEquals("**** CBM BASIC V2 ****", decodeScreenCodeRow(mem, SCREEN_BASE));
        assertEquals("3583 BYTES FREE", decodeScreenCodeRow(mem, SCREEN_BASE + 2 * CHARS_PER_ROW));
        assertEquals("READY.", decodeScreenCodeRow(mem, SCREEN_BASE + 4 * CHARS_PER_ROW));
    }

    @Test
    void afterReachingReadyTheMachineSettlesIntoAStableIdleLoop() {
        // The real KERNAL's post-READY behaviour (blink the cursor, scan the keyboard,
        // wait) is a tight loop over a handful of addresses - a good end-to-end signal
        // that the boot sequence is genuinely complete and stable, not still unwinding.
        MachineTestHarness h = MachineTestHarness.withRealRoms(MachineType.PAL, RamType.RAM_UNEXPANDED);
        h.frames(50);

        Set<Integer> programCountersVisited = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            h.cycle();
            programCountersVisited.add(h.machine.getCpu().getProgramCounter());
        }

        assertTrue(programCountersVisited.size() < 20,
                "post-READY idle loop should revisit only a small handful of addresses, not " +
                        programCountersVisited.size());
    }
}
