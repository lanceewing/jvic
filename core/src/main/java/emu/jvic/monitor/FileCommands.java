package emu.jvic.monitor;

import java.util.List;

/**
 * The file commands that work on the host file system (device 0), and the log and
 * command file commands: cd, pwd, mkdir, rmdir, dir, load, loadbasic, bload, save,
 * bsave, verify, bverify, log, logname, playback, record and stop. Output matches
 * VICE 3.10's. The syntax lines are VICE's; the help descriptions are jvic's own.
 *
 * <p>Files go through the engine's {@link MonitorFileSystem}. Device 8 (the disk in
 * the emulated drive) is not supported yet.
 */
final class FileCommands {

    /** VICE's message when a filename is missing. */
    static final String EXPECTING_FILENAME = "Expecting a filename:";

    /** VICE's message when an address is missing. */
    static final String EXPECTING_ADDRESS = "Expecting an address.";

    private FileCommands() {
    }

    /** Reads a required "filename". */
    static String parseFilename(Parser args) {
        String name = args.tryString();
        if (name == null) {
            throw new MonitorException(args.atEnd() ? EXPECTING_FILENAME : MonitorException.UNEXPECTED_TOKEN,
                    args.position());
        }
        return name;
    }

    /**
     * Reads the device number of load, save and the others. Returns true for device
     * 0, the host file system. For any other device, prints why it can't be used and
     * returns false.
     */
    static boolean parseDevice(Parser args, MonitorOutput out) {
        if (args.atEnd()) {
            throw args.wrongSyntax();
        }
        int device = args.parseExpression();
        if (device == 0) {
            return true;
        }
        if (device == 8) {
            out.print("Device 8 (the emulated disk) is not supported by this command in jvic yet.\n");
        } else {
            out.print("Device " + device + " is not supported by this command in jvic.\n");
        }
        return false;
    }

    /** Reads an address that VICE requires, with its message if it is missing. */
    static int parseRequiredAddress(Parser args) {
        if (args.atEnd()) {
            throw new MonitorException(EXPECTING_ADDRESS, args.position());
        }
        return args.parseAddress();
    }

    // ------------------------------------------------------------------------------
    // Directories.
    // ------------------------------------------------------------------------------

    static final class Cd extends MonitorCommand {

        Cd() {
            super("cd", null, "cd <Directory>",
                    "Changes the current directory, which relative file names in the other\n"
                    + "file commands start from.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            if (args.atEnd()) {
                throw args.wrongSyntax();
            }
            String path = args.readRest();
            try {
                engine.getFileSystem().cd(path);
                out.print("Changing to directory: '" + path + "'\n");
            } catch (MonitorFileException e) {
                out.print("Cannot change to directory: '" + path + "'\n");
            }
        }
    }

    static final class Pwd extends MonitorCommand {

        Pwd() {
            super("pwd", null, "pwd ", "Shows the current directory.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            args.expectEnd();
            out.print(engine.getFileSystem().pwd() + "\n");
        }
    }

    static final class Mkdir extends MonitorCommand {

        Mkdir() {
            super("mkdir", null, "mkdir <Directory>", "Creates a directory.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            if (args.atEnd()) {
                throw args.wrongSyntax();
            }
            String path = args.readRest();
            try {
                engine.getFileSystem().mkdir(path);
                out.print("Created directory '" + path + "'\n");
            } catch (MonitorFileException e) {
                out.print("Cannot create directory '" + path + "': " + e.getMessage() + "\n");
            }
        }
    }

    static final class Rmdir extends MonitorCommand {

        Rmdir() {
            super("rmdir", null, "rmdir <Directory>", "Removes a directory, which must be empty.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            if (args.atEnd()) {
                throw args.wrongSyntax();
            }
            String path = args.readRest();
            try {
                engine.getFileSystem().rmdir(path);
                out.print("Removed directory '" + path + "'\n");
            } catch (MonitorFileException e) {
                out.print("Cannot remove directory '" + path + "': " + e.getMessage() + "\n");
            }
        }
    }

    static final class Dir extends MonitorCommand {

        Dir() {
            super("dir", "ls", "dir [<Directory>]",
                    "Lists a directory: the current one if none is given. Shows each file's\n"
                    + "size in bytes, and <dir> for directories.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            MonitorFileSystem fileSystem = engine.getFileSystem();
            String path = args.atEnd() ? null : args.readRest();
            StringBuilder text = new StringBuilder();
            text.append("Displaying directory: `").append((path == null) ? fileSystem.pwd() : path).append("'\n");
            try {
                List<MonitorFileEntry> entries = fileSystem.list(path);
                for (MonitorFileEntry entry : entries) {
                    if (entry.isDirectory()) {
                        text.append("     <dir> ");
                    } else {
                        text.append(Format.decimal(entry.getSize(), 10)).append(' ');
                    }
                    text.append(entry.getName()).append('\n');
                }
            } catch (MonitorFileException e) {
                text.append("Couldn't open directory.\n");
            }
            out.print(text.toString());
        }
    }

    // ------------------------------------------------------------------------------
    // load, loadbasic, bload, verify and bverify.
    // ------------------------------------------------------------------------------

    /** The data a load or verify uses, and where it goes. */
    private static final class FileData {
        AddressSpace space;
        int start;
        byte[] data;
        int offset;
        int count;
    }

    /**
     * Reads the arguments of load, loadbasic, bload, verify and bverify, and the file.
     *
     * @return The data, or null if there was nothing to do (a message has been printed).
     */
    private static FileData readFile(Parser args, MonitorEngine engine, MonitorOutput out, boolean binary) {
        String name = parseFilename(args);
        if (!parseDevice(args, out)) {
            return null;
        }
        FileData file = new FileData();
        file.space = engine.getDefaultSpace();
        file.start = -1;
        if (binary) {
            int position = args.position();
            file.start = parseRequiredAddress(args);
            file.space = engine.getSpace(args.getLastMemspace(), position);
        } else if (!args.atEnd()) {
            int position = args.position();
            file.start = args.parseAddress();
            file.space = engine.getSpace(args.getLastMemspace(), position);
        }
        args.expectEnd();

        try {
            file.data = engine.getFileSystem().read(name);
        } catch (MonitorFileException e) {
            out.print("Cannot open '" + name + "'.\n");
            return null;
        }
        if (binary) {
            file.offset = 0;
        } else {
            // A two-byte load address comes first. As in VICE, a missing byte reads as 0.
            int header = ((file.data.length > 0) ? (file.data[0] & 0xFF) : 0)
                    | ((file.data.length > 1) ? ((file.data[1] & 0xFF) << 8) : 0);
            if (file.start < 0) {
                file.start = header;
            }
            file.offset = Math.min(2, file.data.length);
        }
        file.start &= 0xFFFF;
        file.count = file.data.length - file.offset;
        return file;
    }

    /** VICE's "from 1000 to 1005 (0006 bytes)". The end wraps at $FFFF, as in VICE. */
    private static String range(FileData file) {
        return "from " + Format.hex4Upper(file.start) + " to " + Format.hex4Upper(file.start + file.count - 1) + " ("
                + Format.hex4Upper(file.count) + " bytes)\n";
    }

    /** load, loadbasic and bload. */
    static final class Load extends MonitorCommand {

        static final int LOAD = 0;
        static final int LOAD_BASIC = 1;
        static final int BLOAD = 2;

        private final int kind;

        Load(int kind) {
            super(name(kind), alias(kind), syntax(kind), description(kind));
            this.kind = kind;
        }

        private static String name(int kind) {
            return (kind == LOAD) ? "load" : (kind == LOAD_BASIC) ? "loadbasic" : "bload";
        }

        private static String alias(int kind) {
            return (kind == LOAD) ? "l" : (kind == LOAD_BASIC) ? "ldb" : "bl";
        }

        private static String syntax(int kind) {
            return name(kind) + " \"<filename>\" <device> " + ((kind == BLOAD) ? "<address>" : "[<address>]");
        }

        private static String description(int kind) {
            switch (kind) {
                case LOAD:
                    return "Loads a file into memory. The file starts with a two-byte load address,\n"
                            + "which is used if no address is given. Device 0 is the host file system.";
                case LOAD_BASIC:
                    return "Loads a file as load does, then sets the BASIC pointers to the loaded\n"
                            + "program, as if BASIC had loaded it. Device 0 is the host file system.";
                default:
                    return "Loads a whole file into memory at the address; the file has no load\n"
                            + "address. Device 0 is the host file system.";
            }
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            String name = peekFilename(args);
            FileData file = readFile(args, engine, out, kind == BLOAD);
            if (file == null) {
                return;
            }
            for (int i = 0; i < file.count; i++) {
                file.space.write((file.start + i) & 0xFFFF, file.data[file.offset + i] & 0xFF);
            }
            out.print("Loading '" + name + "' " + range(file));
            if ((kind == LOAD_BASIC) && (file.space == engine.getMainSpace())) {
                setBasicPointers(file.space, file.start, file.start + file.count);
            }
        }

        /** Sets the pointers that the VIC-20's BASIC sets after a load, as VICE does. */
        private static void setBasicPointers(AddressSpace space, int start, int end) {
            int[] startPointers = { 0x2B, 0xAC };
            int[] endPointers = { 0x2D, 0x2F, 0x31, 0xAE };
            for (int pointer : startPointers) {
                space.write(pointer, start & 0xFF);
                space.write(pointer + 1, (start >> 8) & 0xFF);
            }
            for (int pointer : endPointers) {
                space.write(pointer, end & 0xFF);
                space.write(pointer + 1, (end >> 8) & 0xFF);
            }
        }
    }

    /** Reads the filename without consuming it, for the messages. */
    private static String peekFilename(Parser args) {
        int position = args.position();
        String name = args.tryString();
        args.rewind(position);
        return name;
    }

    /** verify and bverify. */
    static final class Verify extends MonitorCommand {

        private final boolean binary;

        Verify(boolean binary) {
            super(binary ? "bverify" : "verify", binary ? "bv" : "v",
                    (binary ? "bverify" : "verify") + " \"<filename>\" <device> "
                            + (binary ? "<address>" : "[<address>]"),
                    binary ? "Compares a whole file with memory at the address, and lists the bytes\n"
                                    + "that differ. Device 0 is the host file system."
                            : "Compares a file with memory, as load would load it, and lists the bytes\n"
                                    + "that differ. Device 0 is the host file system.");
            this.binary = binary;
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            String name = peekFilename(args);
            FileData file = readFile(args, engine, out, binary);
            if (file == null) {
                return;
            }
            // VICE's layout: the differences come between "from" and "to".
            StringBuilder text = new StringBuilder();
            text.append("Verifying '").append(name).append("' from ").append(Format.hex4Upper(file.start)).append(' ');
            int differences = 0;
            for (int i = 0; i < file.count; i++) {
                int address = (file.start + i) & 0xFFFF;
                int inMemory = file.space.read(address, engine.isSideEffects());
                int inFile = file.data[file.offset + i] & 0xFF;
                if (inMemory != inFile) {
                    if (differences == 0) {
                        text.append("\naddr:mem file\n");
                    }
                    differences++;
                    text.append(Format.hex4(address)).append(": ").append(Format.hex2(inMemory)).append(' ')
                            .append(Format.hex2(inFile)).append('\n');
                }
            }
            text.append("to ").append(Format.hex4Upper(file.start + file.count - 1)).append(" (")
                    .append(Format.hex4Upper(file.count)).append(" bytes)\n");
            if (differences > 0) {
                text.append(differences).append(" byte(s) different\n");
            }
            out.print(text.toString());
        }
    }

    /** save and bsave. */
    static final class Save extends MonitorCommand {

        private final boolean binary;

        Save(boolean binary) {
            super(binary ? "bsave" : "save", binary ? "bs" : "s",
                    (binary ? "bsave" : "save") + " \"<filename>\" <device> <address1> <address2>",
                    binary ? "Saves memory from address1 to address2 to a file, with no load address.\n"
                                    + "Device 0 is the host file system."
                            : "Saves memory from address1 to address2 to a file, starting with a\n"
                                    + "two-byte load address. Device 0 is the host file system.");
            this.binary = binary;
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            String name = parseFilename(args);
            if (!parseDevice(args, out)) {
                return;
            }
            int position = args.position();
            int start = parseRequiredAddress(args);
            AddressSpace space = engine.getSpace(args.getLastMemspace(), position);
            int end = parseRequiredAddress(args);
            args.expectEnd();
            start &= 0xFFFF;
            end &= 0xFFFF;
            if (start > end) {
                out.print("Start address must be below end address.\n");
                return;
            }
            int header = binary ? 0 : 2;
            byte[] data = new byte[header + (end - start) + 1];
            if (!binary) {
                data[0] = (byte) start;
                data[1] = (byte) (start >> 8);
            }
            for (int address = start; address <= end; address++) {
                data[header + address - start] = (byte) space.read(address, engine.isSideEffects());
            }
            out.print("Saving file '" + name + "' from $" + Format.hex4(start) + " to $" + Format.hex4(end) + "\n");
            try {
                engine.getFileSystem().write(name, data);
            } catch (MonitorFileException e) {
                out.print("Cannot open '" + name + "'.\n");
            }
        }
    }

    // ------------------------------------------------------------------------------
    // upload, fetch and download (jvic's own, for the browser).
    // ------------------------------------------------------------------------------

    static final class Upload extends MonitorCommand {

        Upload() {
            super("upload", null, "upload [\"<filename>\"]",
                    "Browser only: opens the file picker, and adds the chosen file to the\n"
                    + "monitor's file store (under the given name, or its own), where load,\n"
                    + "playback and the other file commands can read it. The file arrives\n"
                    + "after the command has finished.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            String name = args.atEnd() ? null : parseFilename(args);
            args.expectEnd();
            if (engine.getHost().pickFile(name)) {
                out.print("Choose a file...\n");
            } else {
                out.print("upload is only needed in the browser; here, use a path on this computer.\n");
            }
        }
    }

    static final class Fetch extends MonitorCommand {

        Fetch() {
            super("fetch", null, "fetch \"<url>\" [\"<filename>\"]",
                    "Browser only: downloads a URL into the monitor's file store, under the\n"
                    + "given name or the last part of the URL. It arrives after the command\n"
                    + "has finished.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            String url = parseFilename(args);
            String name = args.atEnd() ? null : parseFilename(args);
            args.expectEnd();
            if (engine.getHost().fetchUrl(url, name)) {
                out.print("Fetching " + url + "...\n");
            } else {
                out.print("fetch is only supported in the browser.\n");
            }
        }
    }

    static final class Download extends MonitorCommand {

        Download() {
            super("download", null, "download \"<filename>\"",
                    "Browser only: offers a file from the monitor's file store as a\n"
                    + "download, e.g. one written by save or log.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            String name = parseFilename(args);
            args.expectEnd();
            byte[] data;
            try {
                data = engine.getFileSystem().read(name);
            } catch (MonitorFileException e) {
                out.print("Cannot open '" + name + "'.\n");
                return;
            }
            String shortName = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
            if (!engine.getHost().downloadFile(shortName, data)) {
                out.print("download is only supported in the browser; the file is at "
                        + engine.getFileSystem().absolute(name) + "\n");
            }
        }
    }

    // ------------------------------------------------------------------------------
    // log and logname.
    // ------------------------------------------------------------------------------

    static final class Log extends MonitorCommand {

        Log() {
            super("log", null, "log [on|off|toggle]",
                    "Sets whether monitor output is also written to the log file (see\n"
                    + "logname): 'on' adds to the end of the file, 'off' stops, and 'toggle'\n"
                    + "switches. Typed commands are logged after the prompt. With no argument,\n"
                    + "shows the current setting.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            if (args.atEnd()) {
                out.print(engine.isLogEnabled() ? "Logging to '" + engine.getLogName() + "' is enabled.\n"
                        : "Logging is disabled.\n");
                return;
            }
            int position = args.position();
            String word = args.readWord().toLowerCase();
            boolean enable;
            if (word.equals("on")) {
                enable = true;
            } else if (word.equals("off")) {
                enable = false;
            } else if (word.equals("toggle")) {
                enable = !engine.isLogEnabled();
            } else {
                throw new MonitorException(MonitorException.UNEXPECTED_TOKEN, position);
            }
            args.expectEnd();
            String error = engine.setLogEnabled(enable);
            if (error != null) {
                out.print(error);
            }
        }
    }

    static final class LogName extends MonitorCommand {

        LogName() {
            super("logname", null, "logname \"<filename>\"",
                    "Sets the name of the log file. The default is monitor.log.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            String name = parseFilename(args);
            args.expectEnd();
            String error = engine.setLogName(name);
            if (error != null) {
                out.print(error);
            }
        }
    }

    // ------------------------------------------------------------------------------
    // playback, record and stop.
    // ------------------------------------------------------------------------------

    static final class Playback extends MonitorCommand {

        Playback() {
            super("playback", "pb", "playback \"<filename>\"",
                    "Runs the monitor commands in a file, until its end or a stop command.\n"
                    + "Commands after one that resumes emulation (such as x or g) run when\n"
                    + "the monitor is next entered.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            String name = parseFilename(args);
            args.expectEnd();
            engine.playbackFile(name, out);
        }
    }

    static final class Record extends MonitorCommand {

        Record() {
            super("record", "rec", "record \"<filename>\"",
                    "Writes each command typed after this one to a file, until the stop\n"
                    + "command. Commands run from playback files are not recorded.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            String name = parseFilename(args);
            args.expectEnd();
            if (engine.getRecordName() != null) {
                out.print("Recording already in progress. Use 'stop' to end recording.\n");
                return;
            }
            try {
                engine.startRecording(name);
            } catch (MonitorFileException e) {
                out.print("Cannot create '" + name + "'.\n");
            }
        }
    }

    static final class Stop extends MonitorCommand {

        Stop() {
            super("stop", null, "stop ",
                    "Stops recording commands (see record). In a playback file, ends the\n"
                    + "playback of that file.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            args.expectEnd();
            if (engine.getScriptDepth() > 0) {
                engine.stopPlayback();
                return;
            }
            String name = engine.getRecordName();
            if (name == null) {
                out.print("No file is currently being recorded.\n");
                return;
            }
            engine.stopRecording();
            out.print("Closed file " + name + ".\n");
        }
    }
}
