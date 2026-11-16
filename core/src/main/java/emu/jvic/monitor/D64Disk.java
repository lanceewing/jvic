package emu.jvic.monitor;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import emu.jvic.io.disk.GcrDiskImage;

/**
 * The 1541 DOS file system of a disk image, worked on directly by the monitor (not
 * through the emulated drive), for list, load and save on device 8, block_read,
 * block_write and the @ disk commands. VICE does the same with its virtual drive;
 * jvic only has true drive emulation, so this is a small host-side DOS.
 *
 * <p>The layout follows the 1541: the BAM at 18/0, the directory chain from 18/1, and
 * files as chains of sectors. New files are allocated the way the 1541 does it in
 * outline (tracks nearest the directory first, sector interleave 10), though not
 * necessarily on the same sectors as a real drive or VICE would choose.
 */
final class D64Disk {

    static final int DIR_TRACK = 18;
    private static final int INTERLEAVE = 10;
    private static final int DIR_INTERLEAVE = 3;
    private static final String[] TYPES = { "DEL", "SEQ", "PRG", "USR", "REL" };

    /** A problem with the disk, reported as a DOS status, e.g. "62,FILE NOT FOUND". */
    static final class DosException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        final int code;

        DosException(int code, String message) {
            super(message);
            this.code = code;
        }

        /** The status line, as the drive's error channel gives it. */
        String status() {
            return DosStatus.format(code, getMessage(), 0, 0);
        }
    }

    /** One directory entry. */
    static final class Entry {
        int dirTrack;
        int dirSector;
        int offset;
        int type;
        int track;
        int sector;
        int[] name;
        int blocks;

        boolean isClosed() {
            return (type & 0x80) != 0;
        }

        int fileType() {
            return type & 0x07;
        }
    }

    private final GcrDiskImage image;

    D64Disk(GcrDiskImage image) {
        this.image = image;
    }

    int[] readSector(int track, int sector) {
        checkSector(track, sector);
        return image.readSectorData(track, sector);
    }

    void writeSector(int track, int sector, int[] data) {
        checkSector(track, sector);
        image.writeSectorData(track, sector, data);
    }

    boolean isValidSector(int track, int sector) {
        return (track >= 1) && (track <= image.getTrackCount()) && (sector >= 0)
                && (sector < image.getSectorCount(track));
    }

    private void checkSector(int track, int sector) {
        if (!isValidSector(track, sector)) {
            throw new DosException(66, "ILLEGAL TRACK OR SECTOR");
        }
    }

    // ------------------------------------------------------------------------------
    // Directory.
    // ------------------------------------------------------------------------------

    /** All directory entries in use (type not 0), in directory order. */
    List<Entry> entries() {
        List<Entry> entries = new ArrayList<>();
        int track = DIR_TRACK;
        int sector = 1;
        int guard = 0;
        while ((track != 0) && isValidSector(track, sector) && (guard++ < 100)) {
            int[] data = readSector(track, sector);
            for (int e = 0; e < 8; e++) {
                int offset = e * 32;
                if (data[offset + 2] == 0) {
                    continue;
                }
                Entry entry = new Entry();
                entry.dirTrack = track;
                entry.dirSector = sector;
                entry.offset = offset;
                entry.type = data[offset + 2];
                entry.track = data[offset + 3];
                entry.sector = data[offset + 4];
                entry.name = new int[16];
                System.arraycopy(data, offset + 5, entry.name, 0, 16);
                entry.blocks = data[offset + 30] | (data[offset + 31] << 8);
                entries.add(entry);
            }
            track = data[0];
            sector = data[1];
        }
        return entries;
    }

    /**
     * The directory listing as VICE's list command shows it, like LOAD"$",8:
     * <pre>
     * 0 "DISK NAME       " ID 2A
     * 14   "BALLOON CAVES"    PRG
     * 650 blocks free.
     * </pre>
     */
    String listing() {
        int[] bam = readSector(DIR_TRACK, 0);
        StringBuilder text = new StringBuilder();
        text.append("0 \"");
        for (int i = 0x90; i < 0xA0; i++) {
            text.append(toAscii(bam[i]));
        }
        text.append("\" ");
        for (int i = 0xA2; i < 0xA7; i++) {
            text.append(toAscii(bam[i]));
        }
        text.append('\n');
        for (Entry entry : entries()) {
            String blocks = Integer.toString(entry.blocks);
            text.append(Format.padRight(blocks, 5));
            StringBuilder name = new StringBuilder("\"");
            for (int c : entry.name) {
                if (c == 0xA0) {
                    break;
                }
                name.append(toAscii(c));
            }
            name.append('"');
            text.append(Format.padRight(name.toString(), 18));
            text.append(entry.isClosed() ? ' ' : '*');
            text.append((entry.fileType() < TYPES.length) ? TYPES[entry.fileType()] : "???");
            text.append(((entry.type & 0x40) != 0) ? '<' : ' ');
            text.append('\n');
        }
        text.append(blocksFree()).append(" blocks free.\n");
        return text.toString();
    }

    int blocksFree() {
        int[] bam = readSector(DIR_TRACK, 0);
        int free = 0;
        for (int track = 1; track <= image.getTrackCount(); track++) {
            if (track != DIR_TRACK) {
                free += bam[4 * track];
            }
        }
        return free;
    }

    /** The first file matching the name (with * and ? wildcards), or null. */
    Entry find(String pattern) {
        int[] petscii = toPetscii(pattern);
        for (Entry entry : entries()) {
            if ((entry.fileType() != 0) && matches(petscii, entry.name)) {
                return entry;
            }
        }
        return null;
    }

    /** The n-th file (from 0) that isn't deleted, or null. */
    Entry fileAt(int index) {
        int n = 0;
        for (Entry entry : entries()) {
            if (entry.fileType() != 0) {
                if (n++ == index) {
                    return entry;
                }
            }
        }
        return null;
    }

    static boolean matches(int[] pattern, int[] name) {
        int length = 0;
        while ((length < 16) && (name[length] != 0xA0)) {
            length++;
        }
        for (int i = 0; i < pattern.length; i++) {
            if (pattern[i] == '*') {
                return true;
            }
            if (i >= length) {
                return false;
            }
            if ((pattern[i] != '?') && (pattern[i] != name[i])) {
                return false;
            }
        }
        return pattern.length == length;
    }

    // ------------------------------------------------------------------------------
    // Files.
    // ------------------------------------------------------------------------------

    /** The bytes of a file, following its sector chain. */
    byte[] read(Entry entry) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int track = entry.track;
        int sector = entry.sector;
        int guard = 0;
        while ((track != 0) && (guard++ < 1000)) {
            int[] data = readSector(track, sector);
            int last = (data[0] == 0) ? data[1] : 255;
            for (int i = 2; i <= last; i++) {
                out.write(data[i]);
            }
            track = data[0];
            sector = data[1];
        }
        return out.toByteArray();
    }

    /**
     * Writes a new PRG file, replacing any file of the same name.
     *
     * @throws DosException if the disk or directory is full.
     */
    void write(String name, byte[] data) {
        Entry existing = find(name);
        if (existing != null) {
            scratch(existing);
        }
        int[] bam = readSector(DIR_TRACK, 0);
        int sectorsNeeded = Math.max(1, (data.length + 253) / 254);
        if (sectorsNeeded > countFree(bam)) {
            throw new DosException(72, "DISK FULL");
        }
        // Find the directory slot first, so a full directory doesn't leave a file
        // allocated with no entry.
        int[] slot = findDirectorySlot(bam);

        List<int[]> sectors = new ArrayList<>();
        int[] previous = null;
        for (int i = 0; i < sectorsNeeded; i++) {
            int[] next = allocate(bam, previous);
            sectors.add(next);
            previous = next;
        }
        for (int i = 0; i < sectors.size(); i++) {
            int[] block = new int[256];
            int start = i * 254;
            int count = Math.min(254, data.length - start);
            if (i + 1 < sectors.size()) {
                block[0] = sectors.get(i + 1)[0];
                block[1] = sectors.get(i + 1)[1];
            } else {
                block[0] = 0;
                block[1] = count + 1;
            }
            for (int b = 0; b < count; b++) {
                block[2 + b] = data[start + b] & 0xFF;
            }
            writeSector(sectors.get(i)[0], sectors.get(i)[1], block);
        }

        int[] dir = readSector(slot[0], slot[1]);
        int offset = slot[2];
        dir[offset + 2] = 0x82;
        dir[offset + 3] = sectors.get(0)[0];
        dir[offset + 4] = sectors.get(0)[1];
        int[] petscii = toPetscii(name);
        for (int i = 0; i < 16; i++) {
            dir[offset + 5 + i] = (i < petscii.length) ? petscii[i] : 0xA0;
        }
        for (int i = 21; i < 30; i++) {
            dir[offset + i] = 0;
        }
        dir[offset + 30] = sectorsNeeded & 0xFF;
        dir[offset + 31] = sectorsNeeded >> 8;
        writeSector(slot[0], slot[1], dir);
        writeSector(DIR_TRACK, 0, bam);
    }

    /** Finds a free directory entry, adding a directory sector if needed: {t, s, offset}. */
    private int[] findDirectorySlot(int[] bam) {
        int track = DIR_TRACK;
        int sector = 1;
        int guard = 0;
        while (guard++ < 100) {
            int[] data = readSector(track, sector);
            for (int e = 0; e < 8; e++) {
                if (data[e * 32 + 2] == 0) {
                    return new int[] { track, sector, e * 32 };
                }
            }
            if (data[0] == 0) {
                // Add a directory sector on track 18.
                int count = image.getSectorCount(DIR_TRACK);
                for (int i = 1; i < count; i++) {
                    int candidate = (sector + DIR_INTERLEAVE * i) % count;
                    if ((candidate != 0) && isFree(bam, DIR_TRACK, candidate)) {
                        markUsed(bam, DIR_TRACK, candidate);
                        data[0] = DIR_TRACK;
                        data[1] = candidate;
                        writeSector(track, sector, data);
                        int[] fresh = new int[256];
                        fresh[1] = 0xFF;
                        writeSector(DIR_TRACK, candidate, fresh);
                        return new int[] { DIR_TRACK, candidate, 0 };
                    }
                }
                throw new DosException(72, "DISK FULL");
            }
            track = data[0];
            sector = data[1];
        }
        throw new DosException(72, "DISK FULL");
    }

    /** The order the DOS tries tracks in: nearest the directory track first. */
    private int[] trackOrder() {
        int[] order = new int[image.getTrackCount() - 1];
        int n = 0;
        for (int d = 1; n < order.length; d++) {
            if (DIR_TRACK - d >= 1) {
                order[n++] = DIR_TRACK - d;
            }
            if ((DIR_TRACK + d <= image.getTrackCount()) && (n < order.length)) {
                order[n++] = DIR_TRACK + d;
            }
        }
        return order;
    }

    private int[] allocate(int[] bam, int[] previous) {
        int[] order = trackOrder();
        int startIndex = 0;
        if (previous != null) {
            int count = image.getSectorCount(previous[0]);
            for (int i = 1; i <= count; i++) {
                int candidate = (previous[1] + INTERLEAVE + (i - 1)) % count;
                if (isFree(bam, previous[0], candidate)) {
                    markUsed(bam, previous[0], candidate);
                    return new int[] { previous[0], candidate };
                }
            }
            for (int i = 0; i < order.length; i++) {
                if (order[i] == previous[0]) {
                    startIndex = i + 1;
                }
            }
        }
        for (int i = 0; i < order.length; i++) {
            int track = order[(startIndex + i) % order.length];
            for (int sector = 0; sector < image.getSectorCount(track); sector++) {
                if (isFree(bam, track, sector)) {
                    markUsed(bam, track, sector);
                    return new int[] { track, sector };
                }
            }
        }
        throw new DosException(72, "DISK FULL");
    }

    private int countFree(int[] bam) {
        int free = 0;
        for (int track = 1; track <= image.getTrackCount(); track++) {
            if (track != DIR_TRACK) {
                free += bam[4 * track];
            }
        }
        return free;
    }

    private static boolean isFree(int[] bam, int track, int sector) {
        return (bam[4 * track + 1 + (sector >> 3)] & (1 << (sector & 7))) != 0;
    }

    private static void markUsed(int[] bam, int track, int sector) {
        if (isFree(bam, track, sector)) {
            bam[4 * track + 1 + (sector >> 3)] &= ~(1 << (sector & 7));
            bam[4 * track]--;
        }
    }

    private static void markFree(int[] bam, int track, int sector) {
        if (!isFree(bam, track, sector)) {
            bam[4 * track + 1 + (sector >> 3)] |= 1 << (sector & 7);
            bam[4 * track]++;
        }
    }

    // ------------------------------------------------------------------------------
    // Disk commands (@).
    // ------------------------------------------------------------------------------

    /** Deletes a file and frees its sectors. */
    private void scratch(Entry entry) {
        int[] bam = readSector(DIR_TRACK, 0);
        int track = entry.track;
        int sector = entry.sector;
        int guard = 0;
        while ((track != 0) && isValidSector(track, sector) && (guard++ < 1000)) {
            int[] data = readSector(track, sector);
            markFree(bam, track, sector);
            track = data[0];
            sector = data[1];
        }
        writeSector(DIR_TRACK, 0, bam);
        int[] dir = readSector(entry.dirTrack, entry.dirSector);
        dir[entry.offset + 2] = 0;
        writeSector(entry.dirTrack, entry.dirSector, dir);
    }

    /**
     * Runs a DOS command, as the drive's command channel would, and returns the
     * status line, e.g. "00, OK,00,00". Supports S (scratch), R (rename), I
     * (initialise), V (validate), N (new/format) and $ (accepted, as VICE does).
     */
    String command(String text) {
        String command = text.trim();
        try {
            if (command.isEmpty() || command.equals("$") || command.equalsIgnoreCase("i")
                    || command.toUpperCase().startsWith("I:") || command.toUpperCase().startsWith("I0")) {
                return DosStatus.OK;
            }
            char letter = Character.toUpperCase(command.charAt(0));
            int colon = command.indexOf(':');
            String argument = (colon >= 0) ? command.substring(colon + 1) : "";
            switch (letter) {
                case 'S': {
                    int count = 0;
                    for (String pattern : argument.split(",")) {
                        Entry entry;
                        while ((entry = find(pattern)) != null) {
                            scratch(entry);
                            count++;
                        }
                    }
                    return DosStatus.format(1, "FILES SCRATCHED", count, 0);
                }
                case 'R': {
                    int equals = argument.indexOf('=');
                    if ((colon < 0) || (equals < 0)) {
                        return DosStatus.format(30, "SYNTAX ERROR", 0, 0);
                    }
                    String newName = argument.substring(0, equals);
                    Entry old = find(argument.substring(equals + 1));
                    if (old == null) {
                        return DosStatus.format(62, "FILE NOT FOUND", 0, 0);
                    }
                    if (find(newName) != null) {
                        return DosStatus.format(63, "FILE EXISTS", 0, 0);
                    }
                    int[] dir = readSector(old.dirTrack, old.dirSector);
                    int[] petscii = toPetscii(newName);
                    for (int i = 0; i < 16; i++) {
                        dir[old.offset + 5 + i] = (i < petscii.length) ? petscii[i] : 0xA0;
                    }
                    writeSector(old.dirTrack, old.dirSector, dir);
                    return DosStatus.OK;
                }
                case 'V':
                    validate();
                    return DosStatus.OK;
                case 'N':
                    if (colon < 0) {
                        return DosStatus.format(34, "SYNTAX ERROR", 0, 0);
                    }
                    format(argument);
                    return DosStatus.OK;
                default:
                    return DosStatus.format(31, "SYNTAX ERROR", 0, 0);
            }
        } catch (DosException e) {
            return e.status();
        }
    }

    /** Rebuilds the BAM from the directory and the files' chains; unclosed files are deleted. */
    void validate() {
        int[] bam = readSector(DIR_TRACK, 0);
        for (int track = 1; track <= image.getTrackCount(); track++) {
            int count = image.getSectorCount(track);
            bam[4 * track] = 0;
            bam[4 * track + 1] = 0;
            bam[4 * track + 2] = 0;
            bam[4 * track + 3] = 0;
            for (int sector = 0; sector < count; sector++) {
                markFree(bam, track, sector);
            }
        }
        markUsed(bam, DIR_TRACK, 0);
        int track = DIR_TRACK;
        int sector = 1;
        int guard = 0;
        while ((track != 0) && isValidSector(track, sector) && (guard++ < 100)) {
            markUsed(bam, track, sector);
            int[] data = readSector(track, sector);
            track = data[0];
            sector = data[1];
        }
        for (Entry entry : entries()) {
            if (!entry.isClosed() || (entry.fileType() == 0)) {
                int[] dir = readSector(entry.dirTrack, entry.dirSector);
                dir[entry.offset + 2] = 0;
                writeSector(entry.dirTrack, entry.dirSector, dir);
                continue;
            }
            int t = entry.track;
            int s = entry.sector;
            int blocks = 0;
            while ((t != 0) && isValidSector(t, s) && (blocks++ < 1000)) {
                markUsed(bam, t, s);
                int[] data = readSector(t, s);
                t = data[0];
                s = data[1];
            }
        }
        writeSector(DIR_TRACK, 0, bam);
    }

    /**
     * Formats the disk, as N:name,id does. With an ID, every sector is cleared;
     * without one, only the BAM and directory are (the 1541's quick format). The new
     * ID is written to the BAM, but the sector headers keep the old one until the
     * image is reloaded.
     */
    void format(String argument) {
        int comma = argument.indexOf(',');
        String name = (comma >= 0) ? argument.substring(0, comma) : argument;
        int[] bam = readSector(DIR_TRACK, 0);
        int[] id = { bam[0xA2], bam[0xA3] };
        if (comma >= 0) {
            int[] newId = toPetscii(argument.substring(comma + 1));
            id[0] = (newId.length > 0) ? newId[0] : 0x20;
            id[1] = (newId.length > 1) ? newId[1] : 0x20;
            for (int track = 1; track <= image.getTrackCount(); track++) {
                for (int sector = 0; sector < image.getSectorCount(track); sector++) {
                    int[] blank = new int[256];
                    blank[0] = 0x4B;
                    for (int i = 1; i < 256; i++) {
                        blank[i] = 0x01;
                    }
                    writeSector(track, sector, blank);
                }
            }
        }
        int[] newBam = new int[256];
        newBam[0] = DIR_TRACK;
        newBam[1] = 1;
        newBam[2] = 0x41;
        int[] petscii = toPetscii(name);
        for (int i = 0; i < 16; i++) {
            newBam[0x90 + i] = (i < petscii.length) ? petscii[i] : 0xA0;
        }
        newBam[0xA0] = 0xA0;
        newBam[0xA1] = 0xA0;
        newBam[0xA2] = id[0];
        newBam[0xA3] = id[1];
        newBam[0xA4] = 0xA0;
        newBam[0xA5] = '2';
        newBam[0xA6] = 'A';
        for (int i = 0xA7; i <= 0xAA; i++) {
            newBam[i] = 0xA0;
        }
        for (int track = 1; track <= image.getTrackCount(); track++) {
            for (int sector = 0; sector < image.getSectorCount(track); sector++) {
                markFree(newBam, track, sector);
            }
        }
        markUsed(newBam, DIR_TRACK, 0);
        markUsed(newBam, DIR_TRACK, 1);
        writeSector(DIR_TRACK, 0, newBam);
        int[] dir = new int[256];
        dir[1] = 0xFF;
        writeSector(DIR_TRACK, 1, dir);
    }

    // ------------------------------------------------------------------------------
    // Names.
    // ------------------------------------------------------------------------------

    /** Typed text to PETSCII, as the KERNAL would read it: letters are unshifted. */
    static int[] toPetscii(String text) {
        int[] petscii = new int[text.length()];
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c >= 'a') && (c <= 'z')) {
                petscii[i] = c - 0x20;
            } else {
                petscii[i] = c & 0xFF;
            }
        }
        return petscii;
    }

    /** A PETSCII character as text: shifted space as a space, unshifted letters in capitals. */
    static char toAscii(int petscii) {
        if (petscii == 0xA0) {
            return ' ';
        }
        if ((petscii >= 0xC1) && (petscii <= 0xDA)) {
            return (char) (petscii - 0xC1 + 'a');
        }
        return MemoryCommands.Text.petsciiToAscii(petscii);
    }

    /** DOS status lines. */
    static final class DosStatus {
        static final String OK = "00, OK,00,00";

        private DosStatus() {
        }

        static String format(int code, String message, int track, int sector) {
            return Format.pad(Integer.toString(code), 2, '0') + "," + message + ","
                    + Format.pad(Integer.toString(track), 2, '0') + "," + Format.pad(Integer.toString(sector), 2, '0');
        }
    }
}
