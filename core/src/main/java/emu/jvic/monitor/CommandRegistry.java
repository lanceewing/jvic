package emu.jvic.monitor;

import java.util.ArrayList;
import java.util.List;

import emu.jvic.monitor.MonitorEngine.CommandGroup;

/**
 * Builds the monitor's command list, in the groups and order that VICE 3.10's help
 * command shows. Commands jvic doesn't have yet are still listed, so that the help
 * list matches VICE's, and report that they are not implemented when used.
 */
final class CommandRegistry {

    private CommandRegistry() {
    }

    static List<CommandGroup> create() {
        List<CommandGroup> groups = new ArrayList<>();

        CommandGroup general = group(groups, null);
        general.commands.add(new GeneralCommands.Help());
        general.commands.add(new GeneralCommands.Convert());
        general.commands.add(new GeneralCommands.Print());

        CommandGroup monitorState = group(groups, "Monitor state commands:");
        monitorState.commands.add(new GeneralCommands.Device());
        monitorState.commands.add(new GeneralCommands.Exit());
        monitorState.commands.add(new GeneralCommands.Quit());
        monitorState.commands.add(new GeneralCommands.RadixCommand());
        monitorState.commands.add(new GeneralCommands.SideEffects());
        notImplemented(monitorState, "log", null, "logname", null);

        CommandGroup machineState = group(groups, "Machine state commands:");
        notImplemented(machineState, "dump", null, "undump", null);
        machineState.commands.add(new MachineCommands.Bank());
        machineState.commands.add(new MachineCommands.Cpu());
        notImplemented(machineState, "backtrace", "bt", "cpuhistory", "chis");
        machineState.commands.add(new MachineCommands.Registers());
        notImplemented(machineState, "stopwatch", "sw", "goto", "g", "next", "n", "return", "ret", "step", "z",
                "profile", "prof", "reset", null, "export", "exp", "cartfreeze", null, "updb", null, "jpdb", null,
                "keybuf", null, "warp", null);

        CommandGroup symbols = group(groups, "Symbol table commands:");
        notImplemented(symbols, "add_label", "al", "delete_label", "dl", "load_labels", "ll", "save_labels", "sl",
                "show_labels", "shl", "clear_labels", "cl");

        CommandGroup memory = group(groups, "Assembler and memory commands:");
        memory.commands.add(new MemoryCommands.Write());
        notImplemented(memory, "a", null, "compare", "c", "disass", "d", "fill", "f", "hunt", "h", "i", null,
                "ii", null);
        memory.commands.add(new MemoryCommands.Mem());
        notImplemented(memory, "memchar", "mc", "memmapsave", "mmsave", "memmapshow", "mmsh", "memmapzap",
                "mmzap", "memsprite", "ms", "move", "t", "io", null, "screen", "sc");

        CommandGroup checkpoints = group(groups, "Checkpoint commands:");
        notImplemented(checkpoints, "break", "bk", "command", null, "condition", "cond", "delete", "del",
                "disable", "dis", "enable", "en", "ignore", null, "until", "un", "watch", "w", "trace", "tr",
                "dummy", null);

        CommandGroup files = group(groups, "File commands:");
        notImplemented(files, "cd", null, "pwd", null, "mkdir", null, "rmdir", null, "@", null, "attach", null,
                "autostart", null, "autoload", null, "bload", "bl", "block_read", "br", "bsave", "bs",
                "bverify", "bv", "block_write", "bw", "detach", null, "dir", "ls", "list", null, "load", "l",
                "loadbasic", "ldb", "save", "s", "verify", "v", "screenshot", "scrsh", "tapectrl", null,
                "tapeoffs", null);

        CommandGroup commandFiles = group(groups, "Command file commands:");
        notImplemented(commandFiles, "playback", "pb", "record", "rec", "stop", null);

        CommandGroup resources = group(groups, "Resource state commands:");
        notImplemented(resources, "resourceget", "resget", "resourceset", "resset", "load_resources", "resload",
                "save_resources", "ressave");

        return groups;
    }

    private static CommandGroup group(List<CommandGroup> groups, String title) {
        CommandGroup group = new CommandGroup(title);
        groups.add(group);
        return group;
    }

    /**
     * Adds not-yet-implemented commands, given as name, alias pairs.
     */
    private static void notImplemented(CommandGroup group, String... namesAndAliases) {
        for (int i = 0; i < namesAndAliases.length; i += 2) {
            group.commands.add(new MonitorCommand.NotImplemented(namesAndAliases[i], namesAndAliases[i + 1]));
        }
    }
}
