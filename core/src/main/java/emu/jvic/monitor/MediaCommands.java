package emu.jvic.monitor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import emu.jvic.MachineType;
import emu.jvic.PixelData;
import emu.jvic.debug.WatchProxyChip;
import emu.jvic.io.tape.C1530Datasette;
import emu.jvic.memory.MemoryMappedChip;
import emu.jvic.memory.RamChip;

/**
 * The datasette, screenshot, resource and port commands: tapectrl, tapeoffs,
 * screenshot, resourceget, resourceset, load_resources, save_resources, jpdb, updb
 * and cartfreeze. Output matches VICE 3.10's where the concept exists in jvic. The
 * syntax lines are VICE's; the help descriptions are jvic's own.
 */
final class MediaCommands {

    private MediaCommands() {
    }

    // ------------------------------------------------------------------------------
    // Datasette.
    // ------------------------------------------------------------------------------

    static final class TapeControl extends MonitorCommand {

        TapeControl() {
            super("tapectrl", null, "tapectrl <Command>",
                    "Controls the datasette: 0 stop, 1 play, 2 wind to the end, 3 rewind,\n"
                    + "4 record (not supported), 5 reset (stop and rewind), 6 reset the counter.\n"
                    + "jvic winds at once, not at the speed of a real datasette.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            if (args.atEnd()) {
                throw args.wrongSyntax();
            }
            int command = args.parseExpression();
            args.expectEnd();
            if ((command < 0) || (command > 6)) {
                out.print("Unknown command.\n");
                return;
            }
            C1530Datasette datasette = engine.getMachine().getDatasette();
            if (!datasette.hasTape()) {
                return;
            }
            switch (command) {
                case 0:
                    datasette.stop();
                    break;
                case 1:
                    datasette.play();
                    break;
                case 2:
                    datasette.setTapeOffset(datasette.getTapeLength());
                    break;
                case 3:
                    datasette.setTapeOffset(0);
                    break;
                case 4:
                    out.print("Recording to tape is not supported by jvic.\n");
                    break;
                case 5:
                    datasette.stop();
                    datasette.setTapeOffset(0);
                    break;
                default:
                    // jvic has no tape counter.
                    break;
            }
        }
    }

    static final class TapeOffset extends MonitorCommand {

        TapeOffset() {
            super("tapeoffs", null, "tapeoffs <Offset>",
                    "Winds the tape to the offset in its pulse data. With no offset, shows\n"
                    + "the current offset.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            C1530Datasette datasette = engine.getMachine().getDatasette();
            Integer offset = null;
            if (!args.atEnd()) {
                offset = args.parseExpression();
                args.expectEnd();
            }
            if (!datasette.hasTape()) {
                out.print("No tape attached.\n");
                return;
            }
            if (offset == null) {
                out.print("Current tape offset is: " + datasette.getTapeOffset() + "\n");
            } else {
                out.print("Setting tape to offset: " + offset + "\n");
                datasette.setTapeOffset(offset);
            }
        }
    }

    // ------------------------------------------------------------------------------
    // Screenshot.
    // ------------------------------------------------------------------------------

    static final class Screenshot extends MonitorCommand {

        Screenshot() {
            super("screenshot", "scrsh", "screenshot \"<filename>\" [<Format>]",
                    "Saves the visible screen as an image: format 0 (the default) is BMP and\n"
                    + "2 is PNG; VICE's other formats (1 PCX, 3 GIF, 4 IFF) aren't supported.\n"
                    + "As in VICE, the format's extension is added if the name lacks it.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            String name = FileCommands.parseFilename(args);
            int format = 0;
            if (!args.atEnd()) {
                format = args.parseExpression();
            }
            args.expectEnd();
            if ((format != 0) && (format != 2)) {
                out.print("Screenshot format " + format + " is not supported by jvic; use 0 (BMP) or 2 (PNG).\n");
                return;
            }
            String extension = (format == 2) ? ".png" : ".bmp";
            if (!name.toLowerCase().endsWith(extension)) {
                name += extension;
            }
            MachineType type = engine.getMachine().getMachineType();
            PixelData pixels = engine.getMachine().getPixelData();
            int width = type.getVisibleScreenWidth();
            int height = type.getVisibleScreenHeight();
            int stride = type.getTotalScreenWidth();
            int left = type.getHorizontalOffset();
            int top = type.getVerticalOffset();
            int[] image = new int[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    image[y * width + x] = pixels.getPixel((top + y) * stride + left + x);
                }
            }
            byte[] data = (format == 2) ? ImageEncoder.png(image, width, height)
                    : ImageEncoder.bmp(image, width, height);
            try {
                engine.getFileSystem().write(name, data);
                engine.noteFileWritten(name, out);
            } catch (MonitorFileException e) {
                out.print("Cannot open '" + name + "'.\n");
            }
        }
    }

    // ------------------------------------------------------------------------------
    // Resources.
    // ------------------------------------------------------------------------------

    /** jvic's resources: VICE's names where the setting exists in both. */
    static final String[] RESOURCES = { "MachineVideoStandard", "RAMBlock0", "RAMBlock1", "RAMBlock2",
        "RAMBlock3", "RAMBlock5", "WarpMode", "MonitorLogEnabled", "MonitorLogFileName", "MonitorChisLines" };

    private static final int[] RAM_BLOCKS = { 0x0400, 0x2000, 0x4000, 0x6000, -1, 0xA000 };

    /**
     * The value of a resource as VICE shows it (strings in quotes), or null if there
     * is no such resource.
     */
    static String getResource(MonitorEngine engine, String name) {
        switch (name) {
            case "MachineVideoStandard":
                return engine.getMachine().getMachineType().isNTSC() ? "2" : "1";
            case "WarpMode":
                return engine.getHost().isWarp() ? "1" : "0";
            case "MonitorLogEnabled":
                return engine.isLogEnabled() ? "1" : "0";
            case "MonitorLogFileName":
                return "\"" + engine.getLogName() + "\"";
            case "MonitorChisLines":
                return Integer.toString(CpuHistory.SIZE);
            default:
                break;
        }
        if (name.startsWith("RAMBlock") && (name.length() == 9)) {
            int block = name.charAt(8) - '0';
            if ((block >= 0) && (block < RAM_BLOCKS.length) && (RAM_BLOCKS[block] >= 0)) {
                MemoryMappedChip chip = engine.getMachine().getMemory().getMemoryMap()[RAM_BLOCKS[block]];
                if (chip instanceof WatchProxyChip) {
                    chip = ((WatchProxyChip) chip).getTarget();
                }
                return (chip instanceof RamChip) ? "1" : "0";
            }
        }
        return null;
    }

    /**
     * Sets a resource.
     *
     * @return null, or a message saying why it couldn't be set.
     */
    static String setResource(MonitorEngine engine, String name, String value) {
        if (getResource(engine, name) == null) {
            return "Unknown resource \"" + name + "\".";
        }
        String text = value.trim();
        switch (name) {
            case "WarpMode":
                engine.getHost().setWarp(!text.equals("0"));
                return null;
            case "MonitorLogEnabled":
                return trim(engine.setLogEnabled(!text.equals("0")));
            case "MonitorLogFileName":
                if (text.startsWith("\"") && text.endsWith("\"") && (text.length() >= 2)) {
                    text = text.substring(1, text.length() - 1);
                }
                return trim(engine.setLogName(text));
            default:
                return "Resource \"" + name + "\" can't be changed while jvic is running.";
        }
    }

    private static String trim(String message) {
        return (message != null) ? message.trim() : null;
    }

    static final class ResourceGet extends MonitorCommand {

        ResourceGet() {
            super("resourceget", "resget", "resourceget \"<resource>\"",
                    "Shows a resource's value. jvic has: MachineVideoStandard, RAMBlock0-3,\n"
                    + "RAMBlock5, WarpMode, MonitorLogEnabled, MonitorLogFileName and\n"
                    + "MonitorChisLines.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            String name = FileCommands.parseFilename(args);
            args.expectEnd();
            String value = getResource(engine, name);
            out.print((value != null) ? name + "=" + value + "\n" : "Unknown resource \"" + name + "\".\n");
        }
    }

    static final class ResourceSet extends MonitorCommand {

        ResourceSet() {
            super("resourceset", "resset", "resourceset \"<resource>\" \"<value>\"",
                    "Sets a resource's value. Of jvic's resources, WarpMode, MonitorLogEnabled\n"
                    + "and MonitorLogFileName can be changed while running.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            String name = FileCommands.parseFilename(args);
            String value = FileCommands.parseFilename(args);
            args.expectEnd();
            String error = setResource(engine, name, value);
            if (error != null) {
                out.print(error + "\n");
            }
        }
    }

    static final class SaveResources extends MonitorCommand {

        SaveResources() {
            super("save_resources", "ressave", "save_resources \"<filename>\"",
                    "Saves jvic's resources to a file, in VICE's format.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            String name = FileCommands.parseFilename(args);
            args.expectEnd();
            StringBuilder text = new StringBuilder("[VIC20]\n");
            for (String resource : RESOURCES) {
                text.append(resource).append('=').append(getResource(engine, resource)).append('\n');
            }
            try {
                engine.getFileSystem().write(name, text.toString().getBytes(StandardCharsets.ISO_8859_1));
                engine.noteFileWritten(name, out);
            } catch (MonitorFileException e) {
                out.print("Cannot open '" + name + "'.\n");
            }
        }
    }

    static final class LoadResources extends MonitorCommand {

        LoadResources() {
            super("load_resources", "resload", "load_resources \"<filename>\"",
                    "Loads resources from a file in VICE's format, setting those that can be\n"
                    + "changed while running and ignoring the rest, as VICE ignores unknown ones.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            String name = FileCommands.parseFilename(args);
            args.expectEnd();
            byte[] data;
            try {
                data = engine.getFileSystem().read(name);
            } catch (MonitorFileException e) {
                out.print("Cannot open '" + name + "'.\n");
                return;
            }
            List<String> errors = new ArrayList<>();
            for (String line : new String(data, StandardCharsets.ISO_8859_1).split("\r?\n")) {
                int equals = line.indexOf('=');
                if (line.startsWith("[") || (equals <= 0)) {
                    continue;
                }
                String resource = line.substring(0, equals).trim();
                String value = line.substring(equals + 1);
                if ((getResource(engine, resource) != null) && !value.equals(getResource(engine, resource))) {
                    String error = setResource(engine, resource, value);
                    if (error != null) {
                        errors.add(error);
                    }
                }
            }
            for (String error : errors) {
                out.print(error + "\n");
            }
        }
    }

    // ------------------------------------------------------------------------------
    // Ports and cartridges.
    // ------------------------------------------------------------------------------

    static final class Jpdb extends MonitorCommand {

        Jpdb() {
            super("jpdb", null, "jpdb <port> <value>",
                    "Holds joystick lines down on the VIC-20's control port (port 1, or 0), on\n"
                    + "top of the real input, for scripted testing: bit 0 up, 1 down, 2 left,\n"
                    + "3 right, 4 fire. 0 releases them. (In VICE, jpdb drives joyport devices.)");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            if (args.atEnd()) {
                throw args.wrongSyntax();
            }
            int port = args.parseExpression();
            if (args.atEnd()) {
                throw args.wrongSyntax();
            }
            int value = args.parseExpression();
            args.expectEnd();
            if ((port != 0) && (port != 1)) {
                out.print("Illegal port.\n");
                return;
            }
            int pressed = (((value & 0x01) != 0) ? 0x04 : 0) | (((value & 0x02) != 0) ? 0x08 : 0)
                    | (((value & 0x04) != 0) ? 0x10 : 0) | (((value & 0x08) != 0) ? 0x80 : 0)
                    | (((value & 0x10) != 0) ? 0x20 : 0);
            engine.getMachine().getJoystick().setMonitorPressed(pressed);
        }
    }

    /** updb and cartfreeze: accepted, as in VICE, with nothing to act on in jvic. */
    static final class NoDevice extends MonitorCommand {

        NoDevice(String name, String syntax, String description) {
            super(name, null, syntax, description);
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            while (!args.atEnd()) {
                args.parseExpression();
            }
        }
    }
}
