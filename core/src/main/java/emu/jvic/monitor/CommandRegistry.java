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
        monitorState.commands.add(new FileCommands.Log());
        monitorState.commands.add(new FileCommands.LogName());

        CommandGroup machineState = group(groups, "Machine state commands:");
        notImplemented(machineState, "dump", null, "undump", null);
        machineState.commands.add(new MachineCommands.Bank());
        machineState.commands.add(new MachineCommands.Cpu());
        notImplemented(machineState, "backtrace", "bt", "cpuhistory", "chis");
        machineState.commands.add(new MachineCommands.Registers());
        machineState.commands.add(new MachineCommands.Stopwatch());
        machineState.commands.add(new MachineCommands.Goto());
        machineState.commands.add(new MachineCommands.Step(true));
        machineState.commands.add(new MachineCommands.Return());
        machineState.commands.add(new MachineCommands.Step(false));
        notImplemented(machineState, "profile", "prof", "reset", null, "export", "exp", "cartfreeze", null, "updb",
                null, "jpdb", null, "keybuf", null);
        machineState.commands.add(new MachineCommands.Warp());

        CommandGroup symbols = group(groups, "Symbol table commands:");
        notImplemented(symbols, "add_label", "al", "delete_label", "dl", "load_labels", "ll", "save_labels", "sl",
                "show_labels", "shl", "clear_labels", "cl");

        CommandGroup memory = group(groups, "Assembler and memory commands:");
        memory.commands.add(new MemoryCommands.Write());
        notImplemented(memory, "a", null);
        memory.commands.add(new MemoryCommands.Compare());
        memory.commands.add(new MemoryCommands.Disass());
        memory.commands.add(new MemoryCommands.Fill());
        memory.commands.add(new MemoryCommands.Hunt());
        memory.commands.add(new MemoryCommands.Text(false));
        memory.commands.add(new MemoryCommands.Text(true));
        memory.commands.add(new MemoryCommands.Mem());
        memory.commands.add(new MemoryCommands.MemChar());
        notImplemented(memory, "memmapsave", "mmsave", "memmapshow", "mmsh", "memmapzap", "mmzap", "memsprite",
                "ms");
        memory.commands.add(new MemoryCommands.Move());
        notImplemented(memory, "io", null, "screen", "sc");

        CommandGroup checkpoints = group(groups, "Checkpoint commands:");
        checkpoints.commands.add(CheckpointCommands.breakCommand());
        notImplemented(checkpoints, "command", null);
        checkpoints.commands.add(new CheckpointCommands.ConditionCommand());
        checkpoints.commands.add(new CheckpointCommands.Delete());
        checkpoints.commands.add(new CheckpointCommands.Enable(false));
        checkpoints.commands.add(new CheckpointCommands.Enable(true));
        checkpoints.commands.add(new CheckpointCommands.Ignore());
        checkpoints.commands.add(new CheckpointCommands.Until());
        checkpoints.commands.add(CheckpointCommands.watchCommand());
        checkpoints.commands.add(CheckpointCommands.traceCommand());
        notImplemented(checkpoints, "dummy", null);

        CommandGroup files = group(groups, "File commands:");
        files.commands.add(new FileCommands.Cd());
        files.commands.add(new FileCommands.Pwd());
        files.commands.add(new FileCommands.Mkdir());
        files.commands.add(new FileCommands.Rmdir());
        notImplemented(files, "@", null, "attach", null, "autostart", null, "autoload", null);
        files.commands.add(new FileCommands.Load(FileCommands.Load.BLOAD));
        notImplemented(files, "block_read", "br");
        files.commands.add(new FileCommands.Save(true));
        files.commands.add(new FileCommands.Verify(true));
        notImplemented(files, "block_write", "bw", "detach", null);
        files.commands.add(new FileCommands.Dir());
        notImplemented(files, "list", null);
        files.commands.add(new FileCommands.Load(FileCommands.Load.LOAD));
        files.commands.add(new FileCommands.Load(FileCommands.Load.LOAD_BASIC));
        files.commands.add(new FileCommands.Save(false));
        files.commands.add(new FileCommands.Verify(false));
        notImplemented(files, "screenshot", "scrsh", "tapectrl", null, "tapeoffs", null);

        CommandGroup commandFiles = group(groups, "Command file commands:");
        commandFiles.commands.add(new FileCommands.Playback());
        commandFiles.commands.add(new FileCommands.Record());
        commandFiles.commands.add(new FileCommands.Stop());

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
