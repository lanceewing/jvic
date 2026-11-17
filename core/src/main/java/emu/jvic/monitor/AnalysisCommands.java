package emu.jvic.monitor;

import java.util.ArrayList;
import java.util.List;

/**
 * profile, memmapshow, memmapzap and memmapsave. Output follows VICE 3.10's layout.
 * The syntax lines are VICE's; the help descriptions are jvic's own.
 */
final class AnalysisCommands {

    private static final String MORE = "Use \"help prof\" for more information.\n";

    private static final String RECORDING_STARTED =
            "The memory map wasn't being recorded; it is now. Run the machine, then look again.\n";

    private AnalysisCommands() {
    }

    // ------------------------------------------------------------------------------
    // profile
    // ------------------------------------------------------------------------------

    static final class Profile extends MonitorCommand {

        Profile() {
            super("profile", "prof",
                    "profile [on|off]|[flat [num]]|[graph [context] [depth]]|[func <function>]",
                    "Profiles the main CPU, by function (the code a JSR or an interrupt goes to):\n"
                    + "prof on - start, discarding earlier data; prof off - stop.\n"
                    + "prof flat [<num=20>] - the num functions with the most time of their own.\n"
                    + "prof graph [<ctx>] [depth <d>] - the call graph, from context ctx, d deep.\n"
                    + "prof func <function> - a function's callers and callees.\n"
                    + "prof disass <function> - the cycles spent on each of its instructions.\n"
                    + "prof context <ctx> - the same for one context of the call graph.\n"
                    + "prof clear <function> - clears a function's counts.\n"
                    + "Profiling slows emulation a little while it runs.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            Profiler profiler = engine.getProfiler();
            AddressSpace space = engine.getMainSpace();
            if (args.atEnd()) {
                if (profiler.isRunning()) {
                    out.print("Profiling running.\n" + MORE);
                } else if (profiler.hasData()) {
                    out.print("Profiling data available.\n" + MORE);
                } else {
                    out.print("Profiling not started.\n" + MORE);
                }
                return;
            }
            int position = args.position();
            String word = args.readWord().toLowerCase();
            switch (word) {
                case "on":
                    args.expectEnd();
                    profiler.start();
                    engine.updateHooks();
                    out.print("Profiling restarted.\n");
                    return;
                case "off":
                    args.expectEnd();
                    profiler.stop();
                    engine.updateHooks();
                    out.print("Profiling stopped.\n");
                    return;
                default:
                    break;
            }
            if (!profiler.hasData()) {
                out.print("Profiling not started.\n" + MORE);
                return;
            }
            switch (word) {
                case "flat": {
                    int count = 20;
                    if (!args.atEnd()) {
                        count = decimal(args);
                    }
                    args.expectEnd();
                    out.print(profiler.flat(space, count));
                    return;
                }
                case "graph": {
                    List<Profiler.Context> contexts = profiler.numbered();
                    Profiler.Context from = profiler.getRoot();
                    int depth = Integer.MAX_VALUE;
                    while (!args.atEnd()) {
                        int p = args.position();
                        if (args.readWord().equalsIgnoreCase("depth")) {
                            depth = decimal(args);
                        } else {
                            args.rewind(p);
                            from = context(contexts, decimal(args), p);
                        }
                    }
                    out.print(profiler.graph(space, from, depth));
                    return;
                }
                case "func": {
                    int function = function(args);
                    out.print(profiler.function(space, function));
                    return;
                }
                case "disass": {
                    int function = function(args);
                    List<Profiler.Context> contexts = new ArrayList<>();
                    for (Profiler.Context context : profiler.numbered()) {
                        if (context.function == function) {
                            contexts.add(context);
                        }
                    }
                    out.print(profiler.instructions(space, contexts));
                    return;
                }
                case "context": {
                    List<Profiler.Context> contexts = profiler.numbered();
                    int p = args.position();
                    Profiler.Context context = context(contexts, decimal(args), p);
                    args.expectEnd();
                    List<Profiler.Context> one = new ArrayList<>();
                    one.add(context);
                    out.print("[" + context.number + "] " + Profiler.contextName(context, space) + "\n"
                            + profiler.instructions(space, one));
                    return;
                }
                case "clear": {
                    profiler.clear(function(args));
                    return;
                }
                default:
                    throw new MonitorException(MonitorException.UNEXPECTED_TOKEN, position);
            }
        }

        /** A decimal number, as the graph's context numbers and counts are. */
        private static int decimal(Parser args) {
            int position = args.position();
            String digits = args.readWord();
            try {
                return Integer.parseInt(digits);
            } catch (NumberFormatException e) {
                throw new MonitorException(MonitorException.UNEXPECTED_TOKEN, position);
            }
        }

        private static Profiler.Context context(List<Profiler.Context> contexts, int number, int position) {
            if ((number < 1) || (number > contexts.size())) {
                throw new MonitorException("No such context.", position);
            }
            return contexts.get(number - 1);
        }

        private static int function(Parser args) {
            if (args.atEnd()) {
                throw args.wrongSyntax();
            }
            int address = args.parseAddress();
            args.expectEnd();
            return address;
        }
    }

    // ------------------------------------------------------------------------------
    // memmapshow, memmapzap, memmapsave
    // ------------------------------------------------------------------------------

    /** Starts recording if it hasn't started, printing that it has. Returns true if it had. */
    private static boolean ensureRecording(MonitorEngine engine, MonitorOutput out) {
        if (engine.getMemMap().isRecording()) {
            return true;
        }
        engine.setMemMapRecording(true);
        out.print(RECORDING_STARTED);
        return false;
    }

    static final class MemMapShow extends MonitorCommand {

        MemMapShow() {
            super("memmapshow", "mmsh", "memmapshow [<mask>] [<address_opt_range>]",
                    "Shows the memory map: each address the main CPU has accessed, and how,\n"
                    + "as I/O, ROM and RAM read/write/exec. The mask selects access kinds, with\n"
                    + "the bits \"ioRWXrwx\" (I/O read and write, ROM rwx, RAM rwx); as in VICE,\n"
                    + "a first number is the mask. jvic starts recording the first time a\n"
                    + "memmap command is used; `mmzap off' stops it.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            int mask = 0xFF;
            int start = 0;
            int end = 0xFFFF;
            List<Integer> numbers = new ArrayList<>();
            boolean firstIsAddress = false;
            while (!args.atEnd()) {
                int position = args.position();
                if (numbers.isEmpty() && (args.tryMemspace() != null)) {
                    firstIsAddress = true;
                }
                args.rewind(position);
                numbers.add(numbers.isEmpty() && !firstIsAddress ? args.parseExpression() : args.parseAddress());
            }
            int index = 0;
            if (!firstIsAddress && !numbers.isEmpty()) {
                mask = numbers.get(index++);
            }
            if (index < numbers.size()) {
                start = numbers.get(index++) & 0xFFFF;
            }
            if (index < numbers.size()) {
                end = numbers.get(index) & 0xFFFF;
            }
            if (!ensureRecording(engine, out)) {
                return;
            }
            MemMap memMap = engine.getMemMap();
            StringBuilder text = new StringBuilder("addr: IO  ROM RAM\n");
            for (int address = start; address <= end; address++) {
                if ((memMap.get(address) & mask & 0xFF) != 0) {
                    text.append(memMap.line(address));
                }
            }
            out.print(text.toString());
        }
    }

    static final class MemMapZap extends MonitorCommand {

        MemMapZap() {
            super("memmapzap", "mmzap", "memmapzap ",
                    "Clears the memory map, starting recording if it hasn't started.\n"
                    + "`mmzap off' (jvic's own) clears it and stops recording.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            boolean off = false;
            if (!args.atEnd()) {
                int position = args.position();
                if (!args.readWord().equalsIgnoreCase("off")) {
                    throw new MonitorException(MonitorException.UNEXPECTED_TOKEN, position);
                }
                off = true;
            }
            args.expectEnd();
            engine.getMemMap().clear();
            engine.setMemMapRecording(!off);
        }
    }

    static final class MemMapSave extends MonitorCommand {

        MemMapSave() {
            super("memmapsave", "mmsave", "memmapsave \"<filename>\" <Format>",
                    "Saves the memory map as a 256x256 picture, one pixel per address: green\n"
                    + "read, red written, blue executed (dimmer for ROM, dimmest for I/O).\n"
                    + "Format 0 is BMP and 2 is PNG; VICE's others aren't supported.");
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
                out.print("Memmap format " + format + " is not supported by jvic; use 0 (BMP) or 2 (PNG).\n");
                return;
            }
            ensureRecording(engine, out);
            int[] pixels = engine.getMemMap().image();
            byte[] data = (format == 2) ? ImageEncoder.png(pixels, 256, 256) : ImageEncoder.bmp(pixels, 256, 256);
            try {
                engine.getFileSystem().write(name, data);
            } catch (MonitorFileException e) {
                out.print("Cannot open '" + name + "'.\n");
            }
        }
    }
}
