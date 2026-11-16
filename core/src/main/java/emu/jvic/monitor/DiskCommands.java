package emu.jvic.monitor;

import emu.jvic.io.disk.C1541Drive;
import emu.jvic.io.disk.persistence.DiskImagePersistenceSession;

/**
 * The commands for the disk in drive 8 and for attaching media: list, @, block_read,
 * block_write, attach, detach, autostart and autoload. Output matches VICE 3.10's
 * where the concept exists in jvic. The syntax lines are VICE's; the help
 * descriptions are jvic's own.
 *
 * <p>The disk commands work on the image directly ({@link D64Disk}), as VICE's do on
 * its virtual drive, and any change is persisted as if the drive had written it. If
 * the emulated DOS has the BAM or a directory sector in its own memory at the time,
 * it won't see the change until it reads the disk again (e.g. after @i), as with a
 * real drive whose disk was changed behind its back.
 */
final class DiskCommands {

    /** The smallest .d64 image: 683 sectors of 256 bytes. */
    static final int D64_SIZE = 174848;

    private DiskCommands() {
    }

    /** The disk, or null after printing VICE's "not ready" message. */
    private static D64Disk disk(MonitorEngine engine, MonitorOutput out) {
        D64Disk disk = engine.getDisk();
        if (disk == null) {
            out.print("Drive 8 not ready.\n");
        }
        return disk;
    }

    static final class ListCommand extends MonitorCommand {

        ListCommand() {
            super("list", null, "list [<device>]",
                    "Lists the directory of the disk in the drive, as LOAD\"$\",8 would.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            int device = 8;
            if (!args.atEnd()) {
                device = args.parseExpression();
                args.expectEnd();
            }
            if (device != 8) {
                out.print("Drive " + device + " not ready.\n");
                return;
            }
            D64Disk disk = disk(engine, out);
            if (disk != null) {
                out.print(disk.listing());
            }
        }
    }

    static final class DiskCommand extends MonitorCommand {

        DiskCommand() {
            super("@", null, "@ <disk command>",
                    "Sends a command to the disk in drive 8 and shows the drive's status, e.g.\n"
                    + "`@s:name' (scratch), `@r:new=old' (rename), `@v' (validate), `@n:name,id'\n"
                    + "(format), `@i'. With no command, shows the status.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            String command = args.getLine().substring(args.position()).trim();
            D64Disk disk = engine.getDisk();
            if (disk == null) {
                out.print(D64Disk.DosStatus.format(74, "DRIVE NOT READY", 0, 0) + "\n");
                return;
            }
            boolean changes = !command.isEmpty() && ("SRVN".indexOf(Character.toUpperCase(command.charAt(0))) >= 0);
            C1541Drive drive = engine.getMachine().getC1541Drive();
            if (changes && drive.isWriteProtected()) {
                out.print(D64Disk.DosStatus.format(26, "WRITE PROTECT ON", 0, 0) + "\n");
                return;
            }
            out.print(disk.command(command) + "\n");
            if (changes) {
                drive.onDiskChangedByHost();
            }
        }
    }

    static final class BlockRead extends MonitorCommand {

        BlockRead() {
            super("block_read", "br", "block_read <track> <sector> [<address>]",
                    "Reads a sector of the disk in drive 8. With an address, copies its 256\n"
                    + "bytes into memory there; otherwise shows them.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            if (args.atEnd()) {
                throw args.wrongSyntax();
            }
            int track = args.parseExpression();
            if (args.atEnd()) {
                throw args.wrongSyntax();
            }
            int sector = args.parseExpression();
            AddressSpace space = null;
            int address = -1;
            if (!args.atEnd()) {
                int position = args.position();
                address = args.parseAddress();
                space = engine.getSpace(args.getLastMemspace(), position);
            }
            args.expectEnd();
            D64Disk disk = disk(engine, out);
            if (disk == null) {
                return;
            }
            if (!disk.isValidSector(track, sector)) {
                out.print("Error reading track " + track + " sector " + sector + "\n");
                return;
            }
            int[] data = disk.readSector(track, sector);
            if (space != null) {
                for (int i = 0; i < 256; i++) {
                    space.write((address + i) & 0xFFFF, data[i]);
                }
                out.print("Read track " + track + " sector " + sector + " into address $" + Format.hex4(address) + "\n");
                return;
            }
            // VICE's layout: no memspace and no character column.
            StringBuilder text = new StringBuilder();
            for (int line = 0; line < 256; line += 16) {
                text.append('>').append(Format.hex4(line));
                for (int i = 0; i < 16; i++) {
                    if ((i % 4) == 0) {
                        text.append(' ');
                    }
                    text.append(' ').append(Format.hex2(data[line + i]));
                }
                text.append('\n');
            }
            out.print(text.toString());
        }
    }

    static final class BlockWrite extends MonitorCommand {

        BlockWrite() {
            super("block_write", "bw", "block_write <track> <sector> <address>",
                    "Writes the 256 bytes of memory at the address to a sector of the disk in\n"
                    + "drive 8.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            if (args.atEnd()) {
                throw args.wrongSyntax();
            }
            int track = args.parseExpression();
            if (args.atEnd()) {
                throw args.wrongSyntax();
            }
            int sector = args.parseExpression();
            if (args.atEnd()) {
                throw args.wrongSyntax();
            }
            int position = args.position();
            int address = args.parseAddress();
            AddressSpace space = engine.getSpace(args.getLastMemspace(), position);
            args.expectEnd();
            D64Disk disk = disk(engine, out);
            if (disk == null) {
                return;
            }
            C1541Drive drive = engine.getMachine().getC1541Drive();
            if (drive.isWriteProtected()) {
                out.print("The disk in drive 8 is write protected.\n");
                return;
            }
            if (!disk.isValidSector(track, sector)) {
                out.print("Error writing track " + track + " sector " + sector + "\n");
                return;
            }
            int[] data = new int[256];
            for (int i = 0; i < 256; i++) {
                data[i] = space.read((address + i) & 0xFFFF, engine.isSideEffects());
            }
            disk.writeSector(track, sector, data);
            drive.onDiskChangedByHost();
            out.print("Write data from address $" + Format.hex4(address) + " to track " + track + " sector " + sector
                    + "\n");
        }
    }

    // ------------------------------------------------------------------------------
    // attach, detach, autostart, autoload
    // ------------------------------------------------------------------------------

    /**
     * A disk attached by the monitor: changes are written back to the image file, as
     * VICE does with an attached image.
     */
    static final class FileDiskSession implements DiskImagePersistenceSession {
        private final MonitorFileSystem fileSystem;
        private final String path;
        private final byte[] startup;

        FileDiskSession(MonitorFileSystem fileSystem, String path, byte[] startup) {
            this.fileSystem = fileSystem;
            this.path = path;
            this.startup = startup;
        }

        @Override
        public byte[] getStartupDiskImage() {
            return startup;
        }

        @Override
        public boolean isPersistent() {
            return true;
        }

        @Override
        public void onDiskChanged(byte[] diskImageBytes) {
            try {
                fileSystem.write(path, diskImageBytes);
            } catch (MonitorFileException e) {
                // Kept in the drive; the file can't be updated.
            }
        }

        @Override
        public void resetToOriginalImage(ResetHandler resetHandler) {
            resetHandler.onResetComplete(startup);
        }

        @Override
        public void close() {
        }
    }

    /** What kind of file this is, by its contents and name: "DISK", "TAPE" or "PRG". */
    static String kindOf(String name, byte[] data) {
        String lower = name.toLowerCase();
        if ((data.length >= 12) && new String(data, 0, 12, java.nio.charset.StandardCharsets.ISO_8859_1)
                .equals("C64-TAPE-RAW")) {
            return "TAPE";
        }
        if (lower.endsWith(".d64") || (data.length == D64_SIZE) || (data.length == D64_SIZE + 683)) {
            return "DISK";
        }
        if (lower.endsWith(".crt") || lower.endsWith(".zip")) {
            return "OTHER";
        }
        return "PRG";
    }

    /** Puts a disk image in drive 8, writing changes back to its file. */
    static boolean insertDisk(MonitorEngine engine, String name, byte[] data) {
        if (data.length < D64_SIZE) {
            return false;
        }
        MonitorFileSystem fileSystem = engine.getFileSystem();
        String path = fileSystem.absolute(name);
        C1541Drive drive = engine.getMachine().getC1541Drive();
        // jvic's drive only runs with a disk in, so an empty drive has never started
        // its DOS: warm it up, as jvic does when it starts with a disk.
        boolean warmUp = (drive.getDiskImage() == null);
        drive.insertDisk(data, warmUp, new FileDiskSession(fileSystem, path, data), false);
        return true;
    }

    static final class Attach extends MonitorCommand {

        Attach() {
            super("attach", null, "attach \"<filename>\" <device>",
                    "Attaches a file: a .d64 disk image to drive 8, or a .tap tape image to\n"
                    + "the datasette (device 1). Changes to an attached disk are written back to\n"
                    + "its file. Cartridges (device 32) need a restart, so aren't supported here.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            String name = FileCommands.parseFilename(args);
            if (args.atEnd()) {
                throw args.wrongSyntax();
            }
            int device = args.parseExpression();
            args.expectEnd();
            if ((device != 1) && (device != 8) && (device != 32)) {
                out.print("Unknown device " + device + ".\n");
                return;
            }
            if (device == 32) {
                out.print("Attaching a cartridge needs a restart; start jvic with the cartridge instead.\n");
                return;
            }
            byte[] data;
            try {
                data = engine.getFileSystem().read(name);
            } catch (MonitorFileException e) {
                out.print("Failed.\n");
                return;
            }
            if (device == 8) {
                if (!insertDisk(engine, name, data)) {
                    out.print("Failed.\n");
                }
            } else if ("TAPE".equals(kindOf(name, data))) {
                engine.getMachine().getDatasette().insertTape(data);
            } else {
                out.print("Failed.\n");
            }
        }
    }

    static final class Detach extends MonitorCommand {

        Detach() {
            super("detach", null, "detach <device>",
                    "Detaches the disk from drive 8, or the tape from the datasette (1).");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            if (args.atEnd()) {
                throw args.wrongSyntax();
            }
            int device = args.parseExpression();
            args.expectEnd();
            if (device == 8) {
                engine.getMachine().getC1541Drive().ejectDisk();
            } else if (device == 1) {
                engine.getMachine().getDatasette().ejectTape();
            } else if (device == 32) {
                out.print("Detaching a cartridge needs a restart; start jvic without it instead.\n");
            } else {
                out.print("Unknown device " + device + ".\n");
            }
        }
    }

    /** autostart and autoload. */
    static final class Autostart extends MonitorCommand {

        private final boolean run;

        Autostart(boolean run) {
            super(run ? "autostart" : "autoload", null,
                    (run ? "autostart" : "autoload") + " \"<filename>\" [file_index]",
                    (run ? "Turns the machine off and on and loads and runs a program, disk image\n"
                            : "Turns the machine off and on and loads a program, disk image\n")
                            + "or tape image (for a disk, the file at file_index, or the first).\n"
                            + "The memory configuration stays as it is, as in VICE.");
            this.run = run;
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            String name = FileCommands.parseFilename(args);
            int index = 0;
            if (!args.atEnd()) {
                index = args.parseExpression();
            }
            args.expectEnd();
            byte[] data;
            try {
                data = engine.getFileSystem().read(name);
            } catch (MonitorFileException e) {
                out.print("Cannot open '" + name + "'.\n");
                return;
            }
            String kind = kindOf(name, data);
            String keys;
            if ("DISK".equals(kind)) {
                if (!insertDisk(engine, name, data)) {
                    out.print("Cannot open '" + name + "'.\n");
                    return;
                }
                String file = "*";
                if (index > 0) {
                    D64Disk.Entry entry = engine.getDisk().fileAt(index);
                    if (entry == null) {
                        out.print("There is no file " + index + " on '" + name + "'.\n");
                        return;
                    }
                    StringBuilder fileName = new StringBuilder();
                    for (int c : entry.name) {
                        if (c != 0xA0) {
                            fileName.append(D64Disk.toAscii(c));
                        }
                    }
                    file = fileName.toString().toLowerCase();
                }
                keys = "load\"" + file + "\",8,1\\n";
            } else if ("TAPE".equals(kind)) {
                engine.getMachine().getDatasette().insertTape(data);
                keys = "load\\n";
            } else if ("PRG".equals(kind) && (data.length > 2)) {
                keys = "";
            } else {
                out.print("Autostarting this kind of file needs a restart; start jvic with it instead.\n");
                return;
            }
            if (run) {
                keys += "run\\n";
            }
            out.print((run ? "autostarting " : "autoloading ") + name + " #" + index + "\n");
            engine.startAutostart(kind, data, keys);
        }
    }
}
