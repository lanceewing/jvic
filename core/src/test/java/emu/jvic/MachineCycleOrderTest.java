package emu.jvic;

import emu.jvic.memory.RamType;
import emu.jvic.testutil.MachineTestHarness;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the emergent behaviour of {@link Machine}'s fixed per-cycle chip order
 * ({@code vic -> cpu -> via1 -> via2 -> c1541Drive -> soundGenerator}, see
 * {@code Machine.emulateCycle()}) and a real, documented VIC-20 hardware fact
 * that only shows up once VIA1, VIA2 and the CPU are wired together: VIA1's
 * IRQ output is connected to the 6502's <b>NMI</b> pin, while VIA2's IRQ output
 * is connected to the 6502's (maskable) <b>IRQ</b> pin - see
 * {@code Via1.updateIrqPin}/{@code Via2.updateIrqPin}. This is not a
 * "wired-OR of both VIAs into one IRQ line" the way some other 6502 machines
 * do it - on the VIC-20 the two VIAs drive genuinely separate CPU interrupt
 * inputs.
 */
public class MachineCycleOrderTest {

    private static final int VIA1_IER = 0x911E;
    private static final int VIA1_T1C_L = 0x9114;
    private static final int VIA1_T1C_H = 0x9115;
    private static final int VIA1_IFR = 0x911D;

    private static final int VIA2_IER = 0x912E;
    private static final int VIA2_T1C_L = 0x9124;
    private static final int VIA2_T1C_H = 0x9125;

    private void loadTimer1(MachineTestHarness h, int ierAddress, int t1cLAddress, int t1cHAddress, int value) {
        h.getMemory().writeMemory(ierAddress, 0xC0); // Set flag (bit7) + enable Timer1 (bit6).
        h.getMemory().writeMemory(t1cLAddress, value & 0xFF);
        h.getMemory().writeMemory(t1cHAddress, (value >> 8) & 0xFF); // Writing high byte starts the timer.
    }

    private boolean cpuReachedHandler(MachineTestHarness h, int handlerAddress, int cyclesToRun) {
        for (int i = 0; i < cyclesToRun; i++) {
            h.cycle();
            // Pipelined fetch means the observed PC lands one past the handler's own
            // address once the CPU is actually executing there (see the CPU test
            // harness's documented pipelining caveat).
            if (h.machine.getCpu().getProgramCounter() == handlerAddress + 1) {
                return true;
            }
        }
        return false;
    }

    @Test
    void via1TimerInterruptReachesTheCpusNmiPinOnly() {
        MachineTestHarness h = MachineTestHarness.withSyntheticRoms(MachineType.PAL, RamType.RAM_UNEXPANDED);
        h.cycles(10);

        loadTimer1(h, VIA1_IER, VIA1_T1C_L, VIA1_T1C_H, 5);

        assertTrue(cpuReachedHandler(h, MachineTestHarness.SYNTHETIC_NMI_HANDLER_ADDRESS, 20),
                "VIA1's Timer1 interrupt should reach the CPU's NMI vector");
    }

    @Test
    void via1TimerInterruptNeverReachesTheCpusIrqVector() {
        MachineTestHarness h = MachineTestHarness.withSyntheticRoms(MachineType.PAL, RamType.RAM_UNEXPANDED);
        h.cycles(10);

        loadTimer1(h, VIA1_IER, VIA1_T1C_L, VIA1_T1C_H, 5);

        assertFalse(cpuReachedHandler(h, MachineTestHarness.SYNTHETIC_IRQ_HANDLER_ADDRESS, 20),
                "VIA1 is wired to NMI, not IRQ - it must never land on the IRQ vector");
    }

    @Test
    void via2TimerInterruptReachesTheCpusIrqPinOnly() {
        MachineTestHarness h = MachineTestHarness.withSyntheticRoms(MachineType.PAL, RamType.RAM_UNEXPANDED);
        h.cycles(10); // Executes the synthetic ROM's CLI, unmasking IRQ.

        loadTimer1(h, VIA2_IER, VIA2_T1C_L, VIA2_T1C_H, 5);

        assertTrue(cpuReachedHandler(h, MachineTestHarness.SYNTHETIC_IRQ_HANDLER_ADDRESS, 20),
                "VIA2's Timer1 interrupt should reach the CPU's IRQ vector");
    }

    @Test
    void via2TimerInterruptNeverReachesTheCpusNmiVector() {
        MachineTestHarness h = MachineTestHarness.withSyntheticRoms(MachineType.PAL, RamType.RAM_UNEXPANDED);
        h.cycles(10);

        loadTimer1(h, VIA2_IER, VIA2_T1C_L, VIA2_T1C_H, 5);

        assertFalse(cpuReachedHandler(h, MachineTestHarness.SYNTHETIC_NMI_HANDLER_ADDRESS, 20),
                "VIA2 is wired to IRQ, not NMI - it must never land on the NMI vector");
    }

    @Test
    void via2IrqIsMaskedByTheInterruptDisableFlagButVia1NmiIsNot() {
        // cliAtReset=false leaves the CPU's post-reset default (interrupts disabled)
        // in place, so IRQ - unlike NMI - should be blocked until something clears it.
        MachineTestHarness irqHarness = MachineTestHarness.withSyntheticRoms(MachineType.PAL,
                RamType.RAM_UNEXPANDED, false);
        loadTimer1(irqHarness, VIA2_IER, VIA2_T1C_L, VIA2_T1C_H, 5);
        assertFalse(cpuReachedHandler(irqHarness, MachineTestHarness.SYNTHETIC_IRQ_HANDLER_ADDRESS, 30),
                "IRQ must be masked while the CPU's interrupt-disable flag is set");

        MachineTestHarness nmiHarness = MachineTestHarness.withSyntheticRoms(MachineType.PAL,
                RamType.RAM_UNEXPANDED, false);
        loadTimer1(nmiHarness, VIA1_IER, VIA1_T1C_L, VIA1_T1C_H, 5);
        assertTrue(cpuReachedHandler(nmiHarness, MachineTestHarness.SYNTHETIC_NMI_HANDLER_ADDRESS, 30),
                "NMI is non-maskable, so it must still reach the CPU even with interrupts disabled");
    }

    @Test
    void via1InterruptFlagBecomingSetIsNeverVisibleToTheCpuInTheSameCycleItsSetIn() {
        // Machine.emulateCycle() runs vic, then cpu, then via1/via2, every cycle - so
        // whatever a VIA does to the interrupt line during its own emulateCycle() call
        // can only be seen by the CPU's emulateCycle() on a LATER call, never during the
        // one it happened in. This test finds the cycle where VIA1's Timer1 IFR bit
        // first reads as set, and confirms the CPU doesn't start executing the NMI
        // handler until a strictly later cycle - the direct, observable consequence of
        // the CPU always running before the VIAs within a given cycle.
        MachineTestHarness h = MachineTestHarness.withSyntheticRoms(MachineType.PAL, RamType.RAM_UNEXPANDED);
        h.cycles(10);

        loadTimer1(h, VIA1_IER, VIA1_T1C_L, VIA1_T1C_H, 5);

        int ifrSetAtCycle = -1;
        int cpuReactedAtCycle = -1;
        for (int i = 0; i < 20 && (ifrSetAtCycle < 0 || cpuReactedAtCycle < 0); i++) {
            h.cycle();
            if (ifrSetAtCycle < 0 && (h.getMemory().readMemory(VIA1_IFR) & 0x40) != 0) {
                ifrSetAtCycle = i;
            }
            if (cpuReactedAtCycle < 0
                    && h.machine.getCpu().getProgramCounter() == MachineTestHarness.SYNTHETIC_NMI_HANDLER_ADDRESS
                            + 1) {
                cpuReactedAtCycle = i;
            }
        }

        assertTrue(ifrSetAtCycle >= 0, "VIA1's Timer1 flag should have become set within the observed window");
        assertTrue(cpuReactedAtCycle > ifrSetAtCycle,
                "the CPU must not reach the NMI handler in the same or an earlier cycle than the one where "
                        + "VIA1's interrupt flag first became set");
    }
}
