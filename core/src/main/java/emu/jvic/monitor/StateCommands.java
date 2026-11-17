package emu.jvic.monitor;

import emu.jvic.debug.DebugCpu;
import emu.jvic.debug.StateReader;
import emu.jvic.debug.StateWriter;

/**
 * dump and undump: jvic's own save states (not VICE .vsf snapshots). A state holds
 * the whole machine, including the disk in the drive and the tape, but not the ROMs,
 * and loads only into a machine set up the same way (machine type, RAM expansions,
 * cartridge). The syntax lines are VICE's; the help descriptions are jvic's own.
 */
final class StateCommands {

    private StateCommands() {
    }

    /** Saves the machine and the monitor's view of where each CPU stopped. */
    static byte[] save(MonitorEngine engine) {
        StateWriter out = new StateWriter();
        engine.getMachine().saveState(out);
        out.beginSection("DBG ");
        for (AddressSpace space : new AddressSpace[] { engine.getMainSpace(), engine.getDriveSpace() }) {
            DebugCpu cpu = space.getDebugCpu();
            out.writeBoolean(cpu.isAtBoundary());
            out.writeInt(cpu.getBoundaryAddress());
            out.writeInt(cpu.getBoundaryInterrupt());
        }
        return out.toByteArray();
    }

    /**
     * Loads a state into the machine.
     *
     * @throws StateReader.StateException if it doesn't fit or is damaged.
     */
    static void load(MonitorEngine engine, byte[] data) {
        StateReader in = new StateReader(data);
        engine.getMachine().loadState(in);
        if (in.hasSection("DBG ")) {
            for (AddressSpace space : new AddressSpace[] { engine.getMainSpace(), engine.getDriveSpace() }) {
                boolean atBoundary = in.readBoolean();
                int address = in.readInt();
                int interrupt = in.readInt();
                space.getDebugCpu().restoreBoundary(atBoundary, address, interrupt);
            }
        }
        engine.onStateLoaded();
    }

    static final class Dump extends MonitorCommand {

        Dump() {
            super("dump", null, "dump \"<filename>\"",
                    "Saves the whole machine to a jvic save state (not a VICE snapshot): the\n"
                    + "CPUs, chips, memory, and the disk and tape. ROM images aren't included.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            String name = FileCommands.parseFilename(args);
            args.expectEnd();
            try {
                engine.getFileSystem().write(name, save(engine));
            } catch (MonitorFileException e) {
                out.print("Cannot open '" + name + "'.\n");
            }
        }
    }

    static final class Undump extends MonitorCommand {

        Undump() {
            super("undump", null, "undump \"<filename>\"",
                    "Loads a jvic save state written by dump. The machine must be set up the\n"
                    + "same way as when it was saved (machine type, RAM expansions, cartridge).");
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
            try {
                load(engine, data);
            } catch (StateReader.StateException e) {
                out.print(e.getMessage() + "\n");
            }
        }
    }
}
