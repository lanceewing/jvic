package emu.jvic.monitor;

import emu.jvic.monitor.MonitorEngine.CommandGroup;

/**
 * The general and monitor state commands: help, ~, print, device, exit, quit, radix
 * and sidefx. Output matches VICE 3.10's. The syntax lines are VICE's; the help
 * descriptions are jvic's own.
 */
final class GeneralCommands {

    private GeneralCommands() {
    }

    static final class Help extends MonitorCommand {

        Help() {
            super("help", "?", "help [<Command>]",
                    "With no argument, lists all of the monitor's commands. With a command\n"
                    + "name or abbreviation, shows the help for that command.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            if (args.atEnd()) {
                out.print(commandList(engine));
                return;
            }
            String name = args.readToken();
            MonitorCommand command = engine.findCommand(name);
            if (command == null) {
                out.print("\nNo help available for '" + name + "'. Use \"help\" to get the full list.\n");
            } else {
                out.print(command.getHelp());
            }
        }

        private static String commandList(MonitorEngine engine) {
            StringBuilder text = new StringBuilder("Available commands are:\n\n");
            for (CommandGroup group : engine.getGroups()) {
                if (group.title != null) {
                    text.append(group.title).append('\n');
                }
                int column = 0;
                for (MonitorCommand command : group.commands) {
                    text.append(Format.padRight(command.getListEntry(), 24)).append(' ');
                    if (++column == 3) {
                        text.append('\n');
                        column = 0;
                    }
                }
                if (column != 0) {
                    text.append('\n');
                }
                text.append('\n');
            }
            text.append("Use \"help <command>\" for details.\n");
            return text.toString();
        }
    }

    /** The ~ command: shows a number in decimal, hex, octal and binary. */
    static final class Convert extends MonitorCommand {

        Convert() {
            super("~", null, "~ <number>", "Shows the number in decimal, hexadecimal, octal and binary.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            int value = args.parseExpression();
            args.expectEnd();
            out.print(convert(value));
        }

        static String convert(int value) {
            boolean small = (value >= 0) && (value < 0x100);
            StringBuilder text = new StringBuilder();
            text.append('+').append(value).append('\n');
            text.append('$').append(small ? Format.hex2(value) : Format.hex(value, 4)).append('\n');
            text.append('0').append(Format.octal(value, small ? 3 : 6)).append('\n');
            text.append('%');
            if (small) {
                text.append(Format.binary8(value));
            } else {
                // As in VICE, only the low 16 bits are shown in binary.
                text.append(Format.binary8(value >> 8)).append(' ').append(Format.binary8(value));
            }
            text.append('\n');
            return text.toString();
        }
    }

    static final class Print extends MonitorCommand {

        Print() {
            super("print", "p", "print <expression>",
                    "Evaluates the expression and shows the result in decimal.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            int value = args.parseExpression();
            args.expectEnd();
            out.print("\t" + value + "\n");
        }
    }

    static final class Device extends MonitorCommand {

        Device() {
            super("device", "dev", "device [c:|8:|9:|10:|11:]",
                    "Sets the memspace that commands use when an address has no prefix: the\n"
                    + "computer (`c:') or the disk drive (`8:'). With no argument, sets the\n"
                    + "computer. jvic emulates one drive, unit 8.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            AddressSpace space = engine.getMainSpace();
            if (!args.atEnd()) {
                int position = args.position();
                String memspace = args.tryMemspace();
                if (memspace == null) {
                    throw args.unexpected();
                }
                space = engine.getSpace(memspace, position);
                args.expectEnd();
            }
            engine.setDefaultSpace(space);
            out.print("Setting default device to `" + space.getDeviceName() + "'\n");
        }
    }

    static final class Exit extends MonitorCommand {

        Exit() {
            super("exit", "x", "exit ", "Leaves the monitor and resumes emulation.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            args.expectEnd();
            engine.resume();
        }
    }

    static final class Quit extends MonitorCommand {

        Quit() {
            super("quit", "q", "quit ", "Quits the emulator.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            args.expectEnd();
            engine.getHost().quit();
        }
    }

    static final class RadixCommand extends MonitorCommand {

        RadixCommand() {
            super("radix", "rad", "radix [H|D|O|B]",
                    "Sets the radix used for numbers typed without a prefix, and for showing\n"
                    + "memory: hexadecimal, decimal, octal or binary. With no argument, shows\n"
                    + "the current radix. Prefixes override it: $ hex, + decimal, & octal,\n"
                    + "% binary.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            if (args.atEnd()) {
                out.print("Default radix is " + engine.getRadix().getDisplayName() + "\n");
                return;
            }
            Radix radix = Radix.fromLetter(args.peek());
            if (radix == null) {
                throw args.unexpected();
            }
            args.advance();
            args.expectEnd();
            engine.setRadix(radix);
        }
    }

    static final class SideEffects extends MonitorCommand {

        SideEffects() {
            super("sidefx", "sfx", "sidefx [on|off|toggle]",
                    "Sets whether the monitor's own memory reads can trigger the side effects\n"
                    + "that the CPU's reads have on some I/O registers, such as clearing VIA\n"
                    + "interrupt flags. 'on' allows them, 'off' prevents them, and 'toggle'\n"
                    + "switches. With no argument, shows the current setting. The default is\n"
                    + "off.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            if (args.atEnd()) {
                out.print("I/O side effects are " + (engine.isSideEffects() ? "enabled" : "disabled") + "\n");
                return;
            }
            int position = args.position();
            String word = args.readWord().toLowerCase();
            if (word.equals("on")) {
                engine.setSideEffects(true);
            } else if (word.equals("off")) {
                engine.setSideEffects(false);
            } else if (word.equals("toggle")) {
                engine.setSideEffects(!engine.isSideEffects());
            } else {
                throw new MonitorException(MonitorException.UNEXPECTED_TOKEN, position);
            }
            args.expectEnd();
        }
    }
}
