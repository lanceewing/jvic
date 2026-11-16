package emu.jvic.monitor;

import java.util.ArrayList;
import java.util.List;

import emu.jvic.cpu.Cpu6502;
import emu.jvic.video.Vic;

/**
 * The machine state commands: bank, cpu and registers. Output matches VICE 3.10's.
 * The syntax lines are VICE's; the help descriptions are jvic's own.
 */
final class MachineCommands {

    private MachineCommands() {
    }

    static final class Bank extends MonitorCommand {

        Bank() {
            super("bank", null, "bank [<memspace>] [bankname]",
                    "With no bank name, lists the banks of the memspace. With a bank name,\n"
                    + "makes it the bank that the memspace's commands use. The VIC-20 has the\n"
                    + "banks 'default' and 'cpu', which are both what the CPU sees.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            int position = args.position();
            AddressSpace space = engine.getSpace(args.tryMemspace(), position);
            if (!space.hasBanks()) {
                out.print("Banks not available in this memspace\n");
                return;
            }
            if (args.atEnd()) {
                StringBuilder text = new StringBuilder("Available banks (some may be equivalent to others):\n");
                for (String bank : AddressSpace.MAIN_BANKS) {
                    text.append('*').append(bank).append(" \t");
                }
                out.print(text.append('\n').toString());
                return;
            }
            String name = args.readToken();
            for (String bank : AddressSpace.MAIN_BANKS) {
                if (bank.equalsIgnoreCase(name)) {
                    space.setBank(bank);
                    return;
                }
            }
            out.print("Unknown bank name `" + name + "'\n");
        }
    }

    static final class Cpu extends MonitorCommand {

        Cpu() {
            super("cpu", null, "cpu <Type>",
                    "Sets the type of CPU in the current memspace. Both of jvic's CPUs are\n"
                    + "6502s, so only 6502 is accepted. With no argument, lists the types.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            String supported = "This device (" + engine.getDefaultSpace().getDeviceName()
                    + ") supports the following CPU types: 6502\n";
            if (args.atEnd()) {
                out.print(supported);
                return;
            }
            String type = args.readToken();
            if (!type.equals("6502")) {
                out.print("Unknown CPU type `" + type + "'\n" + supported);
            }
        }
    }

    static final class Registers extends MonitorCommand {

        private static final String[] NAMES = { "A", "X", "Y", "PC", "SP", "FL" };

        Registers() {
            super("registers", "r", "registers [<reg_name> = <number> [, <reg_name> = <number>]*]",
                    "With no arguments, shows the registers of the current memspace's CPU.\n"
                    + "Otherwise sets the given registers: A, X, Y, PC, SP, and FL for the\n"
                    + "status flags.");
        }

        @Override
        void execute(Parser args, MonitorEngine engine, MonitorOutput out) {
            AddressSpace space = engine.getDefaultSpace();
            if (args.atEnd()) {
                out.print(display(space));
                return;
            }

            // Parse all of the assignments before applying any.
            List<int[]> assignments = new ArrayList<>();
            while (true) {
                int namePosition = args.position();
                if (args.tryMemspace() != null) {
                    // VICE's registers command takes no memspace prefix; it uses the device.
                    throw new MonitorException(MonitorException.UNEXPECTED_TOKEN, namePosition);
                }
                int register = indexOf(args.readWord());
                if (register < 0) {
                    throw new MonitorException(MonitorException.INVALID_REGISTER, namePosition);
                }
                if (args.peek() != '=') {
                    throw args.unexpected();
                }
                args.advance();
                if (args.atEnd()) {
                    throw args.unexpected();
                }
                assignments.add(new int[] { register, args.parseExpression() });
                if (args.atEnd()) {
                    break;
                }
                if (args.peek() != ',') {
                    throw args.unexpected();
                }
                args.advance();
                if (args.atEnd()) {
                    throw args.unexpected();
                }
            }

            Cpu6502 cpu = space.getCpu();
            for (int[] assignment : assignments) {
                int value = assignment[1];
                switch (NAMES[assignment[0]]) {
                    case "A": cpu.setAccumulator(value & 0xFF); break;
                    case "X": cpu.setIndexRegisterX(value & 0xFF); break;
                    case "Y": cpu.setIndexRegisterY(value & 0xFF); break;
                    case "SP": cpu.setStackPointer(value & 0xFF); break;
                    case "FL": cpu.setProcessorStatus(value & 0xFF); break;
                    case "PC": space.setPc(value & 0xFFFF); break;
                    default: break;
                }
            }
        }

        private static int indexOf(String name) {
            for (int i = 0; i < NAMES.length; i++) {
                if (NAMES[i].equalsIgnoreCase(name)) {
                    return i;
                }
            }
            return -1;
        }

        /**
         * The register display, in VICE's format. The 00 and 01 columns are the bytes
         * at those addresses (the C64's processor port, which VICE shows for all
         * machines). LIN and CYC are the VIC's raster line and cycle, and are not shown
         * for the drive.
         */
        static String display(AddressSpace space) {
            Cpu6502 cpu = space.getCpu();
            Vic vic = space.getVic();
            StringBuilder text = new StringBuilder();
            text.append("  ADDR A  X  Y  SP 00 01 NV-BDIZC ");
            text.append((vic != null) ? "LIN CYC  STOPWATCH\n" : " STOPWATCH\n");
            text.append(".;").append(Format.hex4(space.getDisplayPc()));
            text.append(' ').append(Format.hex2(cpu.getAccumulator()));
            text.append(' ').append(Format.hex2(cpu.getIndexRegisterX()));
            text.append(' ').append(Format.hex2(cpu.getIndexRegisterY()));
            text.append(' ').append(Format.hex2(cpu.getStackPointer()));
            text.append(' ').append(Format.hex2(space.read(0x00, false)));
            text.append(' ').append(Format.hex2(space.read(0x01, false)));
            text.append(' ').append(Format.binary8(cpu.getProcessorStatus()));
            if (vic != null) {
                text.append(' ').append(Format.pad(Integer.toString(vic.getRasterLine()), 3, '0'));
                text.append(' ').append(Format.pad(Integer.toString(vic.getRasterCycle()), 3, '0'));
            }
            text.append(' ').append(Format.decimal(space.getStopwatch(), 10));
            text.append('\n');
            return text.toString();
        }
    }
}
