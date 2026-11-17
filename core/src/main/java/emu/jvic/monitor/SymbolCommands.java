package emu.jvic.monitor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The symbol table commands: add_label, delete_label, load_labels, save_labels,
 * show_labels and clear_labels. Labels belong to a memspace, and are used in
 * addresses (".loop"), by the assembler, and in disassembly. Output matches VICE
 * 3.10's, except that labels are listed in address order. The syntax lines are
 * VICE's; the help descriptions are jvic's own.
 */
final class SymbolCommands {

    private SymbolCommands() {
    }

    /** Reads an optional memspace prefix, returning the default memspace without one. */
    static AddressSpace parseOptionalMemspace(Parser args, MonitorEngine engine) {
        int position = args.position();
        String memspace = args.tryMemspace();
        return engine.getSpace(memspace, position);
    }

    /** The memspaces a command with an optional memspace applies to: one, or all. */
    static List<AddressSpace> spacesFor(Parser args, MonitorEngine engine) {
        List<AddressSpace> spaces = new ArrayList<>();
        int position = args.position();
        String memspace = args.tryMemspace();
        if (memspace != null) {
            spaces.add(engine.getSpace(memspace, position));
        } else {
            spaces.add(engine.getMainSpace());
            spaces.add(engine.getDriveSpace());
        }
        return spaces;
    }

    static final class AddLabel extends MonitorCommand {

        AddLabel() {
            super("add_label", "al", "add_label [<memspace>] <address> <label>",
                    "Gives an address a label, which must start with a dot, e.g.\n"
                    + "`al c:1000 .start'. Labels can be used wherever an address is expected,\n"
                    + "in the assembler, and are shown by disass.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            int position = args.position();
            String memspace = args.tryMemspace();
            if (args.atEnd()) {
                throw args.wrongSyntax();
            }
            int address = args.parseAddress();
            if (args.getLastMemspace() != null) {
                memspace = args.getLastMemspace();
            }
            AddressSpace space = engine.getSpace(memspace, position);
            int labelPosition = args.position();
            String label = args.readLabelName();
            if (label.length() < 2) {
                throw new MonitorException(args.atEnd() ? MonitorException.WRONG_SYNTAX
                        : MonitorException.UNEXPECTED_TOKEN, labelPosition);
            }
            args.expectEnd();
            space.getLabels().add(label, address);
        }
    }

    static final class DeleteLabel extends MonitorCommand {

        DeleteLabel() {
            super("delete_label", "dl", "delete_label [<memspace>] <label>", "Deletes a label.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            AddressSpace space = parseOptionalMemspace(args, engine);
            int position = args.position();
            String label = args.readLabelName();
            if (label.length() < 2) {
                throw new MonitorException(args.atEnd() ? MonitorException.WRONG_SYNTAX
                        : MonitorException.UNEXPECTED_TOKEN, position);
            }
            args.expectEnd();
            if (!space.getLabels().remove(label)) {
                out.print("Symbol " + label + " not found.\n");
            }
        }
    }

    static final class ShowLabels extends MonitorCommand {

        ShowLabels() {
            super("show_labels", "shl", "show_labels [<memspace>]",
                    "Lists the labels of the memspace, or of the default memspace if none is\n"
                    + "given, in address order.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            AddressSpace space = parseOptionalMemspace(args, engine);
            args.expectEnd();
            StringBuilder text = new StringBuilder();
            for (Map.Entry<String, Integer> label : space.getLabels().sorted()) {
                text.append('$').append(Format.hex4(label.getValue())).append(' ').append(label.getKey())
                        .append('\n');
            }
            out.print(text.toString());
        }
    }

    static final class ClearLabels extends MonitorCommand {

        ClearLabels() {
            super("clear_labels", "cl", "clear_labels [<memspace>]",
                    "Deletes all of the labels of the memspace, or of the default memspace if\n"
                    + "none is given.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            AddressSpace space = parseOptionalMemspace(args, engine);
            args.expectEnd();
            space.getLabels().clear();
        }
    }

    static final class SaveLabels extends MonitorCommand {

        SaveLabels() {
            super("save_labels", "sl", "save_labels [<memspace>] \"<filename>\"",
                    "Saves labels to a file as add_label commands, which load_labels reads.\n"
                    + "Without a memspace, saves the labels of all memspaces.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            List<AddressSpace> spaces = spacesFor(args, engine);
            String name = FileCommands.parseFilename(args);
            args.expectEnd();
            MonitorFileSystem fileSystem = engine.getFileSystem();
            out.print("Saving symbol table to `" + fileSystem.absolute(name) + "'...\n");
            StringBuilder text = new StringBuilder();
            for (AddressSpace space : spaces) {
                for (Map.Entry<String, Integer> label : space.getLabels().sorted()) {
                    text.append("al ").append(space.getName()).append(':').append(Format.hex4(label.getValue()))
                            .append(' ').append(label.getKey()).append('\n');
                }
            }
            try {
                fileSystem.write(name, text.toString().getBytes(StandardCharsets.ISO_8859_1));
                engine.noteFileWritten(name, out);
            } catch (MonitorFileException e) {
                out.print("Cannot open '" + name + "'.\n");
            }
        }
    }

    static final class LoadLabels extends MonitorCommand {

        LoadLabels() {
            super("load_labels", "ll", "load_labels [<memspace>] \"<filename>\"",
                    "Loads labels from a file. It reads VICE's format (add_label commands, as\n"
                    + "save_labels writes and cc65's -Ln option makes) and ACME's\n"
                    + "(`name = $1234'). Labels go into the memspace given, or the default one,\n"
                    + "unless a line names its own.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            AddressSpace space = parseOptionalMemspace(args, engine);
            String name = FileCommands.parseFilename(args);
            args.expectEnd();
            byte[] data;
            try {
                data = engine.getFileSystem().read(name);
            } catch (MonitorFileException e) {
                out.print("Cannot open '" + name + "'.\n");
                return;
            }
            String[] lines = new String(data, StandardCharsets.ISO_8859_1).split("\r?\n|\r");
            for (int i = 0; i < lines.length; i++) {
                String error = loadLine(lines[i], space, engine);
                if (error != null) {
                    out.print("Line " + (i + 1) + " of '" + name + "' ignored: " + error + "\n");
                }
            }
        }

        /** Reads one line of a label file. Returns null, or why it couldn't be read. */
        private static String loadLine(String line, AddressSpace defaultSpace, MonitorEngine engine) {
            String text = line;
            int semicolon = text.indexOf(';');
            if (semicolon >= 0) {
                text = text.substring(0, semicolon);
            }
            text = text.trim();
            if (text.isEmpty()) {
                return null;
            }
            String[] words = text.split("\\s+");
            AddressSpace space = defaultSpace;
            String label;
            String addressText;
            if (words[0].equalsIgnoreCase("al") && (words.length == 3)) {
                // VICE and cc65: "al C:1234 .name", "al 001234 .name".
                addressText = words[1];
                int colon = addressText.indexOf(':');
                if (colon >= 0) {
                    String memspace = addressText.substring(0, colon).toLowerCase();
                    if (!memspace.equals("c") && !memspace.equals("8")) {
                        return "unknown memspace";
                    }
                    space = engine.getSpace(memspace, 0);
                    addressText = addressText.substring(colon + 1);
                }
                addressText = "$" + addressText;
                label = words[2];
            } else if ((words.length == 3) && words[1].equals("=")) {
                // ACME: "name = $1234".
                label = words[0].startsWith(".") ? words[0] : "." + words[0];
                addressText = words[2];
            } else {
                return "not a label";
            }
            int address;
            try {
                if (addressText.startsWith("$")) {
                    address = Integer.parseInt(addressText.substring(1), 16);
                } else if (addressText.startsWith("0x")) {
                    address = Integer.parseInt(addressText.substring(2), 16);
                } else {
                    address = Integer.parseInt(addressText);
                }
            } catch (NumberFormatException e) {
                return "bad address";
            }
            if (!label.startsWith(".") || (label.length() < 2)) {
                return "bad label";
            }
            space.getLabels().add(label, address);
            return null;
        }
    }
}
