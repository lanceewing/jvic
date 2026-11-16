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
        machineState.commands.add(new InspectCommands.Backtrace());
        machineState.commands.add(new InspectCommands.CpuHistoryCommand());
        machineState.commands.add(new MachineCommands.Registers());
        machineState.commands.add(new MachineCommands.Stopwatch());
        machineState.commands.add(new MachineCommands.Goto());
        machineState.commands.add(new MachineCommands.Step(true));
        machineState.commands.add(new MachineCommands.Return());
        machineState.commands.add(new MachineCommands.Step(false));
        notImplemented(machineState, "profile", "prof");
        machineState.commands.add(new InspectCommands.Reset());
        machineState.commands.add(new InspectCommands.Export());
        machineState.commands.add(new MediaCommands.NoDevice("cartfreeze", "cartfreeze ",
                "Accepted, as in VICE, but jvic has no freezer cartridges."));
        machineState.commands.add(new MediaCommands.NoDevice("updb", "updb <value>",
                "Accepted, as in VICE, but jvic has no user port devices."));
        machineState.commands.add(new MediaCommands.Jpdb());
        machineState.commands.add(new InspectCommands.Keybuf());
        machineState.commands.add(new MachineCommands.Warp());

        CommandGroup symbols = group(groups, "Symbol table commands:");
        symbols.commands.add(new SymbolCommands.AddLabel());
        symbols.commands.add(new SymbolCommands.DeleteLabel());
        symbols.commands.add(new SymbolCommands.LoadLabels());
        symbols.commands.add(new SymbolCommands.SaveLabels());
        symbols.commands.add(new SymbolCommands.ShowLabels());
        symbols.commands.add(new SymbolCommands.ClearLabels());

        CommandGroup memory = group(groups, "Assembler and memory commands:");
        memory.commands.add(new MemoryCommands.Write());
        memory.commands.add(new InspectCommands.Assemble());
        memory.commands.add(new MemoryCommands.Compare());
        memory.commands.add(new MemoryCommands.Disass());
        memory.commands.add(new MemoryCommands.Fill());
        memory.commands.add(new MemoryCommands.Hunt());
        memory.commands.add(new MemoryCommands.Text(false));
        memory.commands.add(new MemoryCommands.Text(true));
        memory.commands.add(new MemoryCommands.Mem());
        memory.commands.add(new MemoryCommands.MemChar());
        notImplemented(memory, "memmapsave", "mmsave", "memmapshow", "mmsh", "memmapzap", "mmzap");
        memory.commands.add(new InspectCommands.MemSprite());
        memory.commands.add(new MemoryCommands.Move());
        memory.commands.add(new InspectCommands.Io());
        memory.commands.add(new InspectCommands.Screen());

        CommandGroup checkpoints = group(groups, "Checkpoint commands:");
        checkpoints.commands.add(CheckpointCommands.breakCommand());
        checkpoints.commands.add(new InspectCommands.Command());
        checkpoints.commands.add(new CheckpointCommands.ConditionCommand());
        checkpoints.commands.add(new CheckpointCommands.Delete());
        checkpoints.commands.add(new CheckpointCommands.Enable(false));
        checkpoints.commands.add(new CheckpointCommands.Enable(true));
        checkpoints.commands.add(new CheckpointCommands.Ignore());
        checkpoints.commands.add(new CheckpointCommands.Until());
        checkpoints.commands.add(CheckpointCommands.watchCommand());
        checkpoints.commands.add(CheckpointCommands.traceCommand());
        checkpoints.commands.add(new InspectCommands.Dummy());

        CommandGroup files = group(groups, "File commands:");
        files.commands.add(new FileCommands.Cd());
        files.commands.add(new FileCommands.Pwd());
        files.commands.add(new FileCommands.Mkdir());
        files.commands.add(new FileCommands.Rmdir());
        files.commands.add(new DiskCommands.DiskCommand());
        files.commands.add(new DiskCommands.Attach());
        files.commands.add(new DiskCommands.Autostart(true));
        files.commands.add(new DiskCommands.Autostart(false));
        files.commands.add(new FileCommands.Load(FileCommands.Load.BLOAD));
        files.commands.add(new DiskCommands.BlockRead());
        files.commands.add(new FileCommands.Save(true));
        files.commands.add(new FileCommands.Verify(true));
        files.commands.add(new DiskCommands.BlockWrite());
        files.commands.add(new DiskCommands.Detach());
        files.commands.add(new FileCommands.Dir());
        files.commands.add(new DiskCommands.ListCommand());
        files.commands.add(new FileCommands.Load(FileCommands.Load.LOAD));
        files.commands.add(new FileCommands.Load(FileCommands.Load.LOAD_BASIC));
        files.commands.add(new FileCommands.Save(false));
        files.commands.add(new FileCommands.Verify(false));
        files.commands.add(new MediaCommands.Screenshot());
        files.commands.add(new MediaCommands.TapeControl());
        files.commands.add(new MediaCommands.TapeOffset());

        CommandGroup commandFiles = group(groups, "Command file commands:");
        commandFiles.commands.add(new FileCommands.Playback());
        commandFiles.commands.add(new FileCommands.Record());
        commandFiles.commands.add(new FileCommands.Stop());

        CommandGroup resources = group(groups, "Resource state commands:");
        resources.commands.add(new MediaCommands.ResourceGet());
        resources.commands.add(new MediaCommands.ResourceSet());
        resources.commands.add(new MediaCommands.LoadResources());
        resources.commands.add(new MediaCommands.SaveResources());

        // jvic's own commands for the browser's file store. help doesn't list them,
        // so that its list stays VICE's; "help upload" and so on still work.
        CommandGroup web = group(groups, "jvic web commands:");
        web.hidden = true;
        web.commands.add(new FileCommands.Upload());
        web.commands.add(new FileCommands.Fetch());
        web.commands.add(new FileCommands.Download());
        web.commands.add(new FileCommands.DomConsole());

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
