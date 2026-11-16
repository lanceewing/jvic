package emu.jvic.monitor;

import emu.jvic.cpu.Cpu6502;
import emu.jvic.debug.DebugCpu;
import emu.jvic.debug.Debugger;
import emu.jvic.memory.Memory;
import emu.jvic.video.Vic;

/**
 * One of the monitor's memspaces: "C" (the VIC-20) or "8" (the 1541 drive). Wraps a
 * CPU and the memory it sees, and holds the per-memspace monitor state: the "dot"
 * address, the current bank and the stopwatch base.
 */
final class AddressSpace {

    /** VICE's banks for xvic. Both are what the CPU sees. */
    static final String[] MAIN_BANKS = { "default", "cpu" };

    private final String name;
    private final String deviceName;
    private final DebugCpu debugCpu;
    private final Vic vic;
    private final boolean hasBanks;

    private int dot;
    private String bank = "default";
    private long stopwatchBase;

    /**
     * @param name       The memspace name, "C" or "8".
     * @param deviceName The name VICE uses for the device, e.g. "Computer", "Disk8".
     * @param debugCpu   The debugger's view of the CPU.
     * @param vic        The VIC chip for raster positions, or null (the drive).
     * @param hasBanks   Whether the bank command applies (VICE has no drive banks).
     */
    AddressSpace(String name, String deviceName, DebugCpu debugCpu, Vic vic, boolean hasBanks) {
        this.name = name;
        this.deviceName = deviceName;
        this.debugCpu = debugCpu;
        this.vic = vic;
        this.hasBanks = hasBanks;
        this.dot = getDisplayPc();
    }

    String getName() {
        return name;
    }

    String getDeviceName() {
        return deviceName;
    }

    DebugCpu getDebugCpu() {
        return debugCpu;
    }

    Cpu6502 getCpu() {
        return debugCpu.getCpu();
    }

    Memory getMemory() {
        return debugCpu.getMemory();
    }

    Vic getVic() {
        return vic;
    }

    boolean hasBanks() {
        return hasBanks;
    }

    String getBank() {
        return bank;
    }

    void setBank(String bank) {
        this.bank = bank;
    }

    int getDot() {
        return dot;
    }

    void setDot(int dot) {
        this.dot = dot & 0xFFFF;
    }

    long getStopwatch() {
        return getCpu().getTotalCycles() - stopwatchBase;
    }

    void resetStopwatch() {
        stopwatchBase = getCpu().getTotalCycles();
    }

    /**
     * The PC as the monitor shows it. When the CPU is stopped at an instruction
     * boundary, the opcode there has already been fetched (the fetch overlaps the
     * previous instruction's last cycle), so the CPU's own program counter is one past
     * it; this returns the instruction's address instead, as VICE shows it. For a
     * jammed CPU, it is the address of the JAM opcode.
     */
    int getDisplayPc() {
        Cpu6502 cpu = getCpu();
        if (cpu.isJammed()) {
            return (cpu.getProgramCounter() - 1) & 0xFFFF;
        }
        if (debugCpu.isAtBoundary()) {
            return debugCpu.getBoundaryAddress();
        }
        return cpu.getProgramCounter() & 0xFFFF;
    }

    /**
     * Sets the PC. Goes through redispatch, because the opcode at the old PC may
     * already be latched.
     */
    void setPc(int address) {
        debugCpu.redispatchAt(address & 0xFFFF);
    }

    /**
     * Reads a byte as the monitor's commands do.
     *
     * @param address     The address.
     * @param sideEffects true to read as the CPU would, with any side effects (VICE's
     *                    "sidefx on"); false to peek without them.
     */
    int read(int address, boolean sideEffects) {
        Memory memory = getMemory();
        int value = sideEffects ? memory.readMemory(address & 0xFFFF) : memory.peekMemory(address & 0xFFFF);
        return value & 0xFF;
    }

    /**
     * Writes a byte as the CPU would (so a write to ROM does nothing). If the machine is
     * stopped at an instruction boundary and the write changes the opcode about to run,
     * the CPU is redispatched so that the new byte is what runs on resume.
     */
    void write(int address, int value) {
        address &= 0xFFFF;
        getMemory().writeMemory(address, value & 0xFF);
        Debugger debugger = debugCpu.getDebugger();
        if (debugger.isStopped() && debugCpu.isAtBoundary() && (address == debugCpu.getBoundaryAddress())) {
            debugCpu.redispatchAt(address);
        }
    }
}
