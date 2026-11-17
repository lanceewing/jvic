package emu.jvic.monitor;

import emu.jvic.debug.WatchProxyChip;
import emu.jvic.memory.MemoryMappedChip;
import emu.jvic.memory.NibbleRamChip;
import emu.jvic.memory.RamChip;
import emu.jvic.memory.RomChip;

/**
 * The memory map for memmapshow, memmapzap and memmapsave: which kinds of access the
 * main CPU has made to each address, with VICE's flag bits "ioRWXrwx" (I/O read and
 * write, ROM read/write/exec, RAM read/write/exec). Whether an address is RAM, ROM or
 * I/O comes from the chip mapped there.
 *
 * <p>While recording, the checkpoint manager puts a watch proxy on every address so
 * that loads and stores reach {@link #record}; the engine's instruction hook marks
 * opcode fetches as exec. It costs nothing while not recording.
 */
final class MemMap {

    static final int IO_READ = 0x80;
    static final int IO_WRITE = 0x40;
    static final int ROM_READ = 0x20;
    static final int ROM_WRITE = 0x10;
    static final int ROM_EXEC = 0x08;
    static final int RAM_READ = 0x04;
    static final int RAM_WRITE = 0x02;
    static final int RAM_EXEC = 0x01;
    /** Not in VICE's mask: the address has had a dummy access ("(dummy)" in the list). */
    static final int DUMMY = 0x100;

    private final int[] flags = new int[0x10000];
    private boolean recording;

    boolean isRecording() {
        return recording;
    }

    void setRecording(boolean recording) {
        this.recording = recording;
    }

    void clear() {
        java.util.Arrays.fill(flags, 0);
    }

    int get(int address) {
        return flags[address & 0xFFFF];
    }

    private static MemoryMappedChip unwrap(MemoryMappedChip chip) {
        return (chip instanceof WatchProxyChip) ? ((WatchProxyChip) chip).getTarget() : chip;
    }

    /** 0 for I/O, 1 for ROM, 2 for RAM. */
    private static int kind(MemoryMappedChip chip) {
        MemoryMappedChip target = unwrap(chip);
        if ((target instanceof RamChip) || (target instanceof NibbleRamChip)) {
            return 2;
        }
        return (target instanceof RomChip) ? 1 : 0;
    }

    /** Records a load or store by the CPU. */
    void record(int address, MemoryMappedChip chip, boolean store, boolean dummy) {
        int kind = kind(chip);
        int flag;
        if (kind == 2) {
            flag = store ? RAM_WRITE : RAM_READ;
        } else if (kind == 1) {
            flag = store ? ROM_WRITE : ROM_READ;
        } else {
            flag = store ? IO_WRITE : IO_READ;
        }
        flags[address & 0xFFFF] |= flag | (dummy ? DUMMY : 0);
    }

    /** Records an opcode fetch. */
    void exec(int address, MemoryMappedChip chip) {
        int kind = kind(chip);
        if (kind != 0) {
            flags[address & 0xFFFF] |= (kind == 2) ? RAM_EXEC : ROM_EXEC;
        }
    }

    /** VICE's line for an address, e.g. "911c: rw- --- ---". */
    String line(int address) {
        int f = flags[address & 0xFFFF];
        StringBuilder text = new StringBuilder(Format.hex4(address)).append(": ");
        text.append(((f & IO_READ) != 0) ? 'r' : '-').append(((f & IO_WRITE) != 0) ? 'w' : '-').append('-');
        text.append(' ');
        text.append(((f & ROM_READ) != 0) ? 'r' : '-').append(((f & ROM_WRITE) != 0) ? 'w' : '-')
                .append(((f & ROM_EXEC) != 0) ? 'x' : '-');
        text.append(' ');
        text.append(((f & RAM_READ) != 0) ? 'r' : '-').append(((f & RAM_WRITE) != 0) ? 'w' : '-')
                .append(((f & RAM_EXEC) != 0) ? 'x' : '-');
        if ((f & DUMMY) != 0) {
            text.append(" (dummy)");
        }
        return text.append('\n').toString();
    }

    /**
     * The map as a 256 x 256 image, one pixel per address (low byte across, high
     * byte down): green for reads, red for writes, blue for exec, dimmer for ROM and
     * dimmer still for I/O.
     */
    int[] image() {
        int[] pixels = new int[0x10000];
        for (int address = 0; address < 0x10000; address++) {
            int f = flags[address];
            boolean read = (f & (IO_READ | ROM_READ | RAM_READ)) != 0;
            boolean write = (f & (IO_WRITE | ROM_WRITE | RAM_WRITE)) != 0;
            boolean exec = (f & (ROM_EXEC | RAM_EXEC)) != 0;
            int level = ((f & (RAM_READ | RAM_WRITE | RAM_EXEC)) != 0) ? 0xFF
                    : ((f & (ROM_READ | ROM_WRITE | ROM_EXEC)) != 0) ? 0xB0 : 0x80;
            int r = write ? level : 0;
            int g = read ? level : 0;
            int b = exec ? level : 0;
            pixels[address] = (r << 24) | (g << 16) | (b << 8) | 0xFF;
        }
        return pixels;
    }
}
