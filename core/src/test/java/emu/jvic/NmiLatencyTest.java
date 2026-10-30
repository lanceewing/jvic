package emu.jvic;

import emu.jvic.memory.RamType;
import emu.jvic.testutil.MachineTestHarness;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the minimum latency of a VIA1 timer NMI, measured the way a program does it, by
 * reading T1CL from the handler. This is the mechanism behind the Datapop demo's NMI
 * player: its handler calculates {@code $E5 - T1CL} to correct for jitter, and a single
 * NMI arriving 1 cycle sooner than on the real chip sets T1CL to $E6, leaves the carry
 * clear, and takes it down a path that starts the player before its tables are ready.
 *
 * <p>T1 reads $FF in the cycle that the interrupt goes active. The real 6502 samples
 * during the penultimate cycle of an instruction, so the earliest possible interrupt
 * sequence starts 2 cycles later. Then it's 7 cycles for the sequence, 2 for the KERNAL's
 * SEI at $FEA9, 5 for its JMP ($0318), 3 for the handler's STA zp, and 3 up to the read
 * cycle of LDA abs: 22 cycles after T1 read $FF.
 */
public class NmiLatencyTest {

    private static final int MAIN_ADDRESS = 0x1000;
    private static final int HANDLER_ADDRESS = 0x1100;
    private static final int SAVED_A = 0xFC;
    private static final int HIGHEST_T1CL = 0xFB;
    private static final int NMI_COUNT = 0xFB;

    private static final int EXPECTED_HIGHEST_T1CL = 0xFF - 22;

    private static void load(int[] mem, int address, int... bytes) {
        for (int i = 0; i < bytes.length; i++) {
            mem[address + i] = bytes[i];
        }
    }

    @Test
    void earliestPossibleNmiMatchesTheRealChip() {
        MachineTestHarness h = MachineTestHarness.withRealRoms(MachineType.PAL, RamType.RAM_UNEXPANDED);
        h.frames(50); // Boot to the READY prompt.

        int[] mem = h.getMemory().getMemoryArray();

        load(mem, MAIN_ADDRESS,
                0x78,             // SEI
                0xA9, 0x7F,       // LDA #$7F
                0x8D, 0x1E, 0x91, // STA $911E  (disable all VIA1 interrupts)
                0xA9, 0x40,       // LDA #$40
                0x8D, 0x1B, 0x91, // STA $911B  (T1 free running)
                0xA9, 0xFE,       // LDA #$FE
                0x8D, 0x14, 0x91, // STA $9114
                0xA9, 0x00,       // LDA #$00
                0x8D, 0x15, 0x91, // STA $9115  (start T1, with a period of 256 cycles)
                0xA9, 0xC0,       // LDA #$C0
                0x8D, 0x1E, 0x91, // STA $911E  (enable T1 interrupt)
                // A loop with an odd period, so that the NMI lands on every phase of it.
                0xEA,             // loop: NOP
                0x4C, (MAIN_ADDRESS + 25) & 0xFF, MAIN_ADDRESS >> 8); // JMP loop

        load(mem, HANDLER_ADDRESS,
                0x85, SAVED_A,      // STA SAVED_A
                0xAD, 0x14, 0x91,   // LDA $9114  (also acknowledges the interrupt)
                0xC5, HIGHEST_T1CL, // CMP HIGHEST_T1CL
                0x90, 0x02,         // BCC +2
                0x85, HIGHEST_T1CL, // STA HIGHEST_T1CL
                0xA5, SAVED_A,      // LDA SAVED_A
                0x40);              // RTI

        mem[0x0318] = HANDLER_ADDRESS & 0xFF;
        mem[0x0319] = HANDLER_ADDRESS >> 8;
        mem[HIGHEST_T1CL] = 0;

        h.machine.getCpu().setProgramCounter(MAIN_ADDRESS);
        h.frames(10);

        assertEquals(EXPECTED_HIGHEST_T1CL, mem[HIGHEST_T1CL]);
    }

    @Test
    void unacknowledgedTimerInterruptRaisesOnlyOneNmi() {
        // VIA1's IRQ output stays low until the flag is cleared, and NMI is edge detected,
        // so however many times T1 times out, there's only the one falling edge.
        MachineTestHarness h = MachineTestHarness.withRealRoms(MachineType.PAL, RamType.RAM_UNEXPANDED);
        h.frames(50); // Boot to the READY prompt.

        int[] mem = h.getMemory().getMemoryArray();

        load(mem, MAIN_ADDRESS,
                0x78,             // SEI
                0xA9, 0x40,       // LDA #$40
                0x8D, 0x1B, 0x91, // STA $911B  (T1 free running)
                0xA9, 0xFE,       // LDA #$FE
                0x8D, 0x14, 0x91, // STA $9114
                0xA9, 0x00,       // LDA #$00
                0x8D, 0x15, 0x91, // STA $9115  (start T1, with a period of 256 cycles)
                0xA9, 0xC0,       // LDA #$C0
                0x8D, 0x1E, 0x91, // STA $911E  (enable T1 interrupt)
                0x4C, (MAIN_ADDRESS + 21) & 0xFF, MAIN_ADDRESS >> 8); // loop: JMP loop

        load(mem, HANDLER_ADDRESS,
                0xE6, NMI_COUNT, // INC NMI_COUNT
                0x40);           // RTI  (T1 deliberately not acknowledged)

        mem[0x0318] = HANDLER_ADDRESS & 0xFF;
        mem[0x0319] = HANDLER_ADDRESS >> 8;
        mem[NMI_COUNT] = 0;

        h.machine.getCpu().setProgramCounter(MAIN_ADDRESS);
        h.frames(10);

        assertEquals(1, mem[NMI_COUNT]);
    }
}
