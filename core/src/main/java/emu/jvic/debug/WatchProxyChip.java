package emu.jvic.debug;

import emu.jvic.memory.MemoryMappedChip;

/**
 * Stands in for a chip in a {@code Memory}'s memoryMap[] at a watched address, so that
 * the debug monitor sees the CPU's loads and stores there. Only watched addresses get
 * a proxy, so unwatched addresses cost nothing. VIC fetches don't go through
 * memoryMap[], so they never trigger a watchpoint, which matches VICE.
 *
 * <p>Monitor reads use {@link #peekMemory(int)} or go to the target directly, so they
 * don't trigger watchpoints either.
 *
 * <p>Plain Java only, as this package is translated by GWT.
 */
public final class WatchProxyChip extends MemoryMappedChip {

    /**
     * Receives the accesses to watched addresses.
     */
    public interface Listener {

        /** The CPU read the value from the address. */
        void onLoad(int address, int value);

        /** The CPU is writing the value to the address. */
        void onStore(int address, int value);
    }

    private final MemoryMappedChip target;
    private final Listener listener;

    /**
     * @param target   The chip that was mapped at the address.
     * @param listener Where to report accesses.
     */
    public WatchProxyChip(MemoryMappedChip target, Listener listener) {
        this.target = target;
        this.listener = listener;
    }

    /** The chip this proxy stands in for. */
    public MemoryMappedChip getTarget() {
        return target;
    }

    @Override
    public int readMemory(int address) {
        int value = target.readMemory(address);
        listener.onLoad(address, value);
        return value;
    }

    @Override
    public void writeMemory(int address, int value) {
        listener.onStore(address, value);
        target.writeMemory(address, value);
    }

    @Override
    public int peekMemory(int address) {
        return target.peekMemory(address);
    }
}
