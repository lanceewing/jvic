package emu.jvic.lwjgl3.headless;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import emu.jvic.MachineType;
import emu.jvic.lwjgl3.monitor.BinaryMonitorServer;
import emu.jvic.lwjgl3.monitor.RemoteTextMonitor;
import emu.jvic.memory.RamType;

/**
 * The command-line options of headless mode, all in jvic's name=value form, e.g.
 * {@code headless=true autostart=game.prg limitcycles=10000000 moncommands=mon.txt}.
 */
final class HeadlessOptions {

    private static final Set<String> NAMES = new HashSet<>(Arrays.asList("headless", "machine", "ram",
            "autostart", "autoload", "moncommands", "monlog", "initbreak", "limitcycles", "remotemonitor",
            "binarymonitor"));

    /** initbreak: stop at the first instruction, at the reset address. */
    static final int INITBREAK_RESET = -1;

    /** initbreak: stop when BASIC is ready, as jvic's auto-load waits for. */
    static final int INITBREAK_READY = -2;

    MachineType machineType = MachineType.PAL;
    /** The RAM expansion, or null for jvic's default (detected from the program, or none). */
    RamType ramType;
    /** The program to start, or null. */
    String program;
    /** Whether to RUN the program after loading it (autostart), or not (autoload). */
    boolean run;
    String monCommands;
    String monLog;
    /** INITBREAK_RESET, INITBREAK_READY, or an address. */
    int initBreak = INITBREAK_RESET;
    /** The number of CPU cycles after which to exit, or 0 for no limit. */
    long limitCycles;
    /** The remote monitor's port, or 0 if it is off. */
    int remoteMonitorPort;
    /** The binary monitor's port, or 0 if it is off. */
    int binaryMonitorPort;

    /**
     * Parses the options.
     *
     * @throws IllegalArgumentException with a message for the user if an option is
     *         unknown or its value is invalid.
     */
    static HeadlessOptions parse(Map<String, String> args) {
        HeadlessOptions options = new HeadlessOptions();
        for (Map.Entry<String, String> arg : args.entrySet()) {
            String name = arg.getKey();
            String value = arg.getValue();
            if (!NAMES.contains(name)) {
                throw new IllegalArgumentException("Unknown option for headless mode: " + name);
            }
            switch (name) {
                case "machine":
                    options.machineType = parseMachine(value);
                    break;
                case "ram":
                    options.ramType = parseRam(value);
                    break;
                case "autostart":
                case "autoload":
                    if (options.program != null) {
                        throw new IllegalArgumentException("Only one of autostart and autoload can be given.");
                    }
                    options.program = value;
                    options.run = name.equals("autostart");
                    break;
                case "moncommands":
                    options.monCommands = value;
                    break;
                case "monlog":
                    options.monLog = value;
                    break;
                case "initbreak":
                    options.initBreak = parseInitBreak(value);
                    break;
                case "limitcycles":
                    options.limitCycles = parseLimit(value);
                    break;
                case "remotemonitor":
                    options.remoteMonitorPort = parsePort(value, RemoteTextMonitor.DEFAULT_PORT, name);
                    break;
                case "binarymonitor":
                    options.binaryMonitorPort = parsePort(value, BinaryMonitorServer.DEFAULT_PORT, name);
                    break;
                default:
                    // headless itself.
                    break;
            }
        }
        return options;
    }

    private static MachineType parseMachine(String value) {
        switch (value.toLowerCase(Locale.ROOT)) {
            case "pal":
                return MachineType.PAL;
            case "ntsc":
                return MachineType.NTSC;
            default:
                throw new IllegalArgumentException("machine must be pal or ntsc, not '" + value + "'.");
        }
    }

    private static RamType parseRam(String value) {
        String name = value.toUpperCase(Locale.ROOT);
        if (!name.startsWith("RAM_")) {
            name = "RAM_" + name;
        }
        try {
            return RamType.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "ram must be one of unexpanded, 3k, 8k, 16k, 24k, 32k, 35k or auto, not '" + value + "'.");
        }
    }

    private static int parseInitBreak(String value) {
        String text = value.toLowerCase(Locale.ROOT);
        if (text.equals("reset")) {
            return INITBREAK_RESET;
        }
        if (text.equals("ready")) {
            return INITBREAK_READY;
        }
        if (text.startsWith("$")) {
            text = text.substring(1);
        } else if (text.startsWith("0x")) {
            text = text.substring(2);
        }
        try {
            int address = Integer.parseInt(text, 16);
            if ((address >= 0) && (address <= 0xFFFF)) {
                return address;
            }
        } catch (NumberFormatException e) {
            // Reported below.
        }
        throw new IllegalArgumentException(
                "initbreak must be reset, ready, or a hex address such as e518, not '" + value + "'.");
    }

    private static long parseLimit(String value) {
        try {
            long limit = Long.parseLong(value);
            if (limit > 0) {
                return limit;
            }
        } catch (NumberFormatException e) {
            // Reported below.
        }
        throw new IllegalArgumentException("limitcycles must be a positive number, not '" + value + "'.");
    }

    private static int parsePort(String value, int defaultPort, String option) {
        if (value.equalsIgnoreCase("off") || value.equalsIgnoreCase("false")) {
            return 0;
        }
        String portText = value.substring(value.lastIndexOf(':') + 1);
        if (value.equalsIgnoreCase("on") || value.equalsIgnoreCase("true") || portText.isEmpty()) {
            return defaultPort;
        }
        try {
            int port = Integer.parseInt(portText);
            if ((port > 0) && (port < 65536)) {
                return port;
            }
        } catch (NumberFormatException e) {
            // Reported below.
        }
        throw new IllegalArgumentException(option + " must be on, off or a port, not '" + value + "'.");
    }
}
