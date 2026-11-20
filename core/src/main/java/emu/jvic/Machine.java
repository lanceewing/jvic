package emu.jvic;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Callable;

import emu.jvic.config.AppConfigItem;
import emu.jvic.cpu.Cpu6502;
import emu.jvic.debug.Debugger;
import emu.jvic.debug.StateReader;
import emu.jvic.debug.StateWriter;
import emu.jvic.debug.WatchProxyChip;
import emu.jvic.io.Joystick;
import emu.jvic.io.Keyboard;
import emu.jvic.io.Via1;
import emu.jvic.io.Via2;
import emu.jvic.io.Via6522;
import emu.jvic.io.SerialBus;
import emu.jvic.io.disk.C1541Drive;
import emu.jvic.io.disk.persistence.DiskImagePersistenceSession;
import emu.jvic.io.disk.persistence.NoOpDiskImagePersistenceSession;
import emu.jvic.io.tape.C1530Datasette;
import emu.jvic.memory.Memory;
import emu.jvic.memory.MemoryMappedChip;
import emu.jvic.memory.NibbleRamChip;
import emu.jvic.memory.RamChip;
import emu.jvic.memory.RamType;
import emu.jvic.memory.Vic20Memory;
import emu.jvic.snap.PcvSnapshot;
import emu.jvic.snap.Snapshot;
import emu.jvic.sound.SoundGenerator;
import emu.jvic.sound.libgdx.GdxSoundGenerator;
import emu.jvic.video.Vic;
import emu.jvic.video.Vic44;
import emu.jvic.video.Vic44k;
import emu.jvic.video.Vic6560;
import emu.jvic.video.Vic6561;

/**
 * Represents the VIC 20 machine.
 * 
 * @author Lance Ewing
 */
public class Machine {

    public interface ResetDiskHandler {

        void onResetComplete();

        void onResetFailed();
    }

    // Machine components.
    private Vic20Memory memory;
    private Vic vic;
    private Via6522 via1;
    private Via6522 via2;
    private Cpu6502 cpu;

    // Peripherals.
    private Keyboard keyboard;
    private Joystick joystick;
    private SerialBus serialBus;
    private C1541Drive c1541Drive;
    private C1530Datasette datasette;

    // Platform specific component.
    private KeyboardMatrix keyboardMatrix;
    private PixelData pixelData;
    private SoundGenerator soundGenerator;
    
    private boolean paused = true;

    private DiskImagePersistenceSession activeDiskPersistenceSession;
    private boolean activeDiskWriteProtected;
    private boolean hasMountedDisk;

    private MachineType machineType;

    /**
     * The debug monitor's hooks into this machine, or null if not enabled (the normal
     * case, and always the case on GWT and Android). See {@link #enableDebugger()}.
     */
    private Debugger debugger;

    /**
     * Whether {@link #enableDebugger()} has been called, so that a re-init of the
     * machine re-creates the Debugger for the new chips.
     */
    private boolean debuggerEnabled;

    /**
     * Constructor for Machine.
     * 
     * @param soundGenerator 
     * @param keyboardMatrix 
     * @param pixelData 
     */
    public Machine(SoundGenerator soundGenerator, KeyboardMatrix keyboardMatrix, PixelData pixelData) {
        if (soundGenerator != null) {
            this.soundGenerator = soundGenerator;
        } else {
            this.soundGenerator = new GdxSoundGenerator();
        }
        this.keyboardMatrix = keyboardMatrix;
        this.pixelData = pixelData;
    }

    /**
     * Initialises the machine, and optionally loads the given program file (if provided).
     * 
     * @param basicRom 
     * @param kernalRom
     * @param charRom 
     * @param program 
     * @param machineType 
     * @param ramType
     * 
     * @return Callable that, if not null, should be run when BASIC is ready.
     */
    public Callable<Queue<char[]>> init(
            byte[] basicRom, byte[] kernalRom, byte[] charRom, byte[] dos1541Rom,
            Program program, MachineType machineType, RamType ramType, String palette) {
        return init(basicRom, kernalRom, charRom, dos1541Rom, program, machineType, ramType,
            palette, null);
    }

    /**
     * Initialises the machine using an optional pre-resolved disk persistence session.
     */
    public Callable<Queue<char[]>> init(
            byte[] basicRom, byte[] kernalRom, byte[] charRom, byte[] dos1541Rom,
            Program program, MachineType machineType, RamType ramType, String palette,
            DiskImagePersistenceSession diskImagePersistenceSession) {
        AppConfigItem appConfigItem = (program != null) ? program.getAppConfigItem() : null;
        return init(basicRom, kernalRom, charRom, dos1541Rom, program, appConfigItem,
                null, machineType, ramType, palette, diskImagePersistenceSession);
    }

    /**
     * Initialises the machine using the supplied launch config and optional mounted disk.
     */
    public Callable<Queue<char[]>> init(
            byte[] basicRom, byte[] kernalRom, byte[] charRom, byte[] dos1541Rom,
            Program program, AppConfigItem appConfigItem, byte[] mountedDiskImageData,
            MachineType machineType, RamType ramType, String palette,
            DiskImagePersistenceSession diskImagePersistenceSession) {
    
        byte[] programData = (program != null? program.getProgramData() : null);
        Snapshot snapshot = null;
        Callable<Queue<char[]>> autoLoadRunnable = null;
        String programType = resolveProgramType(program, appConfigItem);

        this.machineType = machineType;

        // The chips are about to be replaced, so detach any debugger from the old ones.
        if (debugger != null) {
            debugger.detach();
            debugger = null;
        }

        // If we've been given the path to a program to load, we load the data prior to all
        // other initialisation. Primarily this is to work out what expansion we need.
        if ((programData != null) && (programData.length > 0)) {
            try {
                if ("PRG".equals(programType) && (programData != null) && (programData.length > 2)
                        && (ramType.equals(RamType.RAM_AUTO))) {
                    // If the RAM type is configured to auto detect, and its a PRG file, then we use
                    // the start address to determine RAM requirements.
                    int startAddress = (programData[1] << 8) + programData[0];
        
                    if (startAddress == 0x1201) {
                        // 8K, 16K, or 24K. We'll give it the full 24K to cover all bases.
                        ramType = RamType.RAM_24K;
                    } else if (startAddress == 0x0401) {
                        // 3K expansion.
                        ramType = RamType.RAM_3K;
                    } else if (startAddress == 0x1001) {
                        // Unexpanded start address.
                        ramType = RamType.RAM_UNEXPANDED;
                    }
                } else if ("PCV".equals(programType)) {
                    // Decode PCVIC snapshot data. PCVIC is an old DOS VIC 20 emulator.
                    snapshot = new PcvSnapshot(programData);
                } else if ("CART".equals(programType) && (ramType.equals(RamType.RAM_AUTO))) {
                    // For cartridges, we always go with unexpanded for auto detection.
                    ramType = RamType.RAM_UNEXPANDED;
                } else if ("DISK".equals(programType) && (ramType.equals(RamType.RAM_AUTO))) {
                    // TODO: May need to be more intelligent that this, e.g. use disk signatures.
                    ramType = RamType.RAM_35K;
                } else if ("TAPE".equals(programType) && (ramType.equals(RamType.RAM_AUTO))) {
                    ramType = RamType.RAM_32K;
                }
            } catch (Exception e) {
                // Continue with default behaviour, which is to boot in to BASIC.
            }
        }

        // Create the microprocessor.
        cpu = new Cpu6502(snapshot);

        // Create the VIC chip and configure it as per the current TV type.
        if (machineType.isVIC44()) {
            vic = new Vic44(pixelData, machineType, snapshot);
        } else if (machineType.isVIC44K()) {
            vic = new Vic44k(pixelData, machineType, snapshot);
        } else if (machineType.isNTSC()) {
            vic = new Vic6560(pixelData, machineType, snapshot);
        } else {
            vic = new Vic6561(pixelData, machineType, snapshot);
            ((Vic6561)vic).setPalette(palette);
        }

        // Create the peripherals.
        keyboard = new Keyboard(keyboardMatrix);
        joystick = new Joystick(keyboardMatrix);
        serialBus = new SerialBus();
        c1541Drive = new C1541Drive(serialBus, dos1541Rom);
        datasette = new C1530Datasette();

        // Create two instances of the VIA chip; one for VIA1 and one for VIA2.
        via1 = new Via1(cpu, keyboard, joystick, serialBus, datasette, snapshot);
        via2 = new Via2(cpu, keyboard, joystick, serialBus, datasette, snapshot);
        
        // Now we create the memory, which will include mapping the VIC chip,
        // the VIA chips, and the creation of RAM chips and ROM chips.
        memory = new Vic20Memory(cpu, vic, via1, via2, ramType.getRamPlacement(), machineType, 
                basicRom, kernalRom, charRom, snapshot);

        // Initialise the sound generator.
        soundGenerator.init(memory.getMemoryArray(), machineType);

        activeDiskPersistenceSession = null;
        activeDiskWriteProtected = false;
        hasMountedDisk = false;

        if (shouldMountDisk(programType, appConfigItem, mountedDiskImageData)) {
            AppConfigItem.DiskWriteMode diskWriteMode = (appConfigItem != null)
                    ? appConfigItem.getDiskWriteMode()
                    : AppConfigItem.DiskWriteMode.DEFAULT;
            boolean writeProtected = (diskWriteMode == AppConfigItem.DiskWriteMode.OFF);
            DiskImagePersistenceSession persistenceSession = diskImagePersistenceSession;
            if (persistenceSession == null) {
                persistenceSession = new NoOpDiskImagePersistenceSession(mountedDiskImageData);
            }
            byte[] startupDiskImage = persistenceSession.getStartupDiskImage();
            c1541Drive.insertDisk(startupDiskImage, true, persistenceSession, writeProtected);
            activeDiskPersistenceSession = persistenceSession;
            activeDiskWriteProtected = writeProtected;
            hasMountedDisk = true;
        }
        
        // Check if the resource parameters have been set.
        if ((programData != null) && (programData.length > 0)) {
            if ("CART".equals(programType)) {
                memory.loadCart(programData, appConfigItem);
            } else if ("PRG".equals(programType)) {
                autoLoadRunnable = memory.loadBasicProgram(programData, true);
            } else if ("DISK".equals(programType)) {
                autoLoadRunnable = new Callable<Queue<char[]>>() {
                    public Queue<char[]> call() throws Exception {
                        String autoRunCmds = (appConfigItem != null)
                                ? appConfigItem.getAutoRunCommand()
                                : null;
                        Queue<char[]> autoRunCmdQueue = new LinkedList<char[]>();
                        if (autoRunCmds != null) {
                            String[] autoRunCmdArray = autoRunCmds.split("[|]");
                            for (String autoRunCmd : autoRunCmdArray) {
                                autoRunCmdQueue.add(autoRunCmd.toCharArray());
                            }
                        } else {
                            // Default.
                            autoRunCmdQueue.add(new char[] {'L','O','A','D','"','*','"',',','8'});
                            autoRunCmdQueue.add(new char[] {'R','U','N'});
                        }
                        return autoRunCmdQueue;
                    }
                };
                
            } else if ("TAPE".equals(programType)) {
                // Insert tape ready to be loaded.
                datasette.insertTape(programData);
                autoLoadRunnable = new Callable<Queue<char[]>>() {
                    public Queue<char[]> call() throws Exception {
                        Queue<char[]> autoRunCmdQueue = new LinkedList<char[]>();
                        autoRunCmdQueue.add(new char[] {'L','O','A','D'});
                        autoRunCmdQueue.add(new char[] {'R','U','N'});
                        datasette.play();
                        return autoRunCmdQueue;
                    }
                };
            } else if ("PCV".equals(programType)) {
                // Nothing to do. Snapshot was already loaded.
            }
        }

        // If the state of the machine was not loaded from a snapshot file, then we
        // begin with a reset.
        if (snapshot == null) {
            cpu.reset();
        }

        if (debuggerEnabled) {
            createDebugger();
        }

        return autoLoadRunnable;
    }

    private String resolveProgramType(Program program, AppConfigItem appConfigItem) {
        if (program != null) {
            return program.getProgramType();
        }

        return (appConfigItem != null) ? appConfigItem.getFileType() : null;
    }

    private boolean shouldMountDisk(String programType, AppConfigItem appConfigItem,
            byte[] mountedDiskImageData) {
        if ((mountedDiskImageData == null) || (mountedDiskImageData.length == 0)) {
            return false;
        }

        return "DISK".equals(programType)
                || ((appConfigItem != null)
                        && (appConfigItem.getDiskWriteMode() != AppConfigItem.DiskWriteMode.DEFAULT));
    }

    /**
     * Updates the state of the machine until a frame is complete, or until the
     * debugger stops it. Does nothing while the debugger is stopped.
     */
    public void update() {
        Debugger debugger = this.debugger;
        if (debugger == null) {
            updateFrame();
        } else if (debugger.isStopped()) {
            // Stopped in the monitor. The runner should be paused, but be safe.
        } else if (debugger.isArmed()) {
            updateFrameDebug(debugger);
        } else {
            updateFrame();
            // Nothing armed needs a per-cycle check. The exception is a JAM, which can
            // happen at any time while a debugger is attached. The jammed CPU just
            // repeats the same cycle, so stopping at the end of the frame loses nothing.
            if (debugger.isStopPending()) {
                debugger.enterStopped();
            }
        }
    }

    /**
     * Updates the machine until a frame is complete, checking for a debugger stop
     * after every machine cycle. Used only while the debugger is armed. The stop
     * happens at the end of a whole machine cycle, so no chip is left part-way
     * through one. If it stops mid-frame, the next update continues the frame.
     */
    private void updateFrameDebug(Debugger debugger) {
        boolean frameComplete = false;
        do {
            frameComplete |= emulateCycle();
            if (debugger.isStopPending()) {
                debugger.enterStopped();
                return;
            }
        } while (!frameComplete);
    }

    /**
     * Updates the machine until a frame is complete. This is the normal loop.
     */
    private void updateFrame() {
        boolean frameComplete = false;
        do {
            frameComplete |= vic.emulateCycle();
            cpu.emulateCycle();
            via1.emulateCycle();
            via2.emulateCycle();
            c1541Drive.emulateCycle();
            soundGenerator.emulateCycle();
        } while (!frameComplete);
    }

    /**
     * Emulates a single machine cycle.
     * 
     * @return true If the VIC chip has indicated that a frame should be rendered.
     */
    public boolean emulateCycle() {
        boolean render = vic.emulateCycle();
        cpu.emulateCycle();
        via1.emulateCycle();
        via2.emulateCycle();
        c1541Drive.emulateCycle();
        soundGenerator.emulateCycle();
        return render;
    }
    
    /**
     * For runners that call {@link #emulateCycle()} directly in their own loop (the
     * web workers): whether that loop needs to call {@link #checkDebuggerStop()}
     * after every cycle. While false, once per batch is enough.
     *
     * @return true if a debugger is attached and armed.
     */
    public boolean isDebuggerArmed() {
        return (debugger != null) && debugger.isArmed();
    }

    /**
     * For runners that call {@link #emulateCycle()} directly: whether the debugger is
     * stopped, in which case the runner must not emulate.
     *
     * @return true if a debugger is attached and stopped.
     */
    public boolean isDebuggerStopped() {
        return (debugger != null) && debugger.isStopped();
    }

    /**
     * For runners that call {@link #emulateCycle()} directly: enters the debugger's
     * stopped state if a stop was requested during the last cycle.
     *
     * @return true if the machine is now stopped, so the runner should end its loop.
     */
    public boolean checkDebuggerStop() {
        if ((debugger != null) && debugger.isStopPending()) {
            debugger.enterStopped();
            return true;
        }
        return false;
    }

    /**
     * Attaches the debug monitor's hooks to this machine, or returns the existing
     * Debugger if already enabled. Until something is armed, the emulation runs the
     * same code as without a debugger. Must be called after init(). A later re-init
     * creates a new Debugger for the new chips, so callers should get it again with
     * {@link #getDebugger()} after a re-init.
     *
     * @return The Debugger.
     */
    public Debugger enableDebugger() {
        debuggerEnabled = true;
        if ((debugger == null) && (cpu != null)) {
            createDebugger();
        }
        return debugger;
    }

    private void createDebugger() {
        debugger = new Debugger(cpu, memory, c1541Drive.getCpu(), c1541Drive.getMemory());
    }

    /**
     * Gets the debug monitor's hooks into this machine.
     *
     * @return The Debugger, or null if {@link #enableDebugger()} hasn't been called.
     */
    public Debugger getDebugger() {
        return debugger;
    }

    /**
     * Pauses and resumes the Machine.
     * 
     * @param paused true to pause the machine, false to resume.
     */
    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    /**
     * Returns whether the Machine is paused or not.
     * 
     * @return true if the machine is paused; otherwise false.
     */
    public boolean isPaused() {
        return paused;
    }

    public boolean canResetMountedDisk() {
        return hasMountedDisk && (c1541Drive != null) && (activeDiskPersistenceSession != null)
                && activeDiskPersistenceSession.isPersistent();
    }

    public void resetMountedDisk(ResetDiskHandler resetDiskHandler) {
        if (!canResetMountedDisk()) {
            resetDiskHandler.onResetFailed();
            return;
        }

        activeDiskPersistenceSession.resetToOriginalImage(
                new DiskImagePersistenceSession.ResetHandler() {
                    @Override
                    public void onResetComplete(byte[] diskImageBytes) {
                        if ((diskImageBytes == null) || (diskImageBytes.length == 0)) {
                            resetDiskHandler.onResetFailed();
                            return;
                        }

                        c1541Drive.replaceDisk(diskImageBytes, activeDiskWriteProtected);
                        resetDiskHandler.onResetComplete();
                    }

                    @Override
                    public void onResetFailed() {
                        resetDiskHandler.onResetFailed();
                    }
                });
    }

    /**
     * Gets the MachineType of this Machine, i.e. either PAL or NTSC.
     * 
     * @return The MachineType of this Machine, i.e. either PAL or NTSC.
     */
    public MachineType getMachineType() {
        return machineType;
    }

    /**
     * Gets the Keyboard of this Machine.
     * 
     * @return The Keyboard of this Machine.
     */
    public Keyboard getKeyboard() {
        return keyboard;
    }

    /**
     * Gets the Joystick of this Machine.
     * 
     * @return The Joystick of this Machine.
     */
    public Joystick getJoystick() {
        return joystick;
    }

    /**
     * Gets the Cpu6502 of this Machine.
     * 
     * @return The Cpu6502 of this Machine.
     */
    public Cpu6502 getCpu() {
        return cpu;
    }

    /**
     * Gets the VIC chip of this Machine.
     * 
     * @return
     */
    public Vic getVic() {
        return this.vic;
    }
    
    /**
     * Gets the Memory used by this Machine.
     *
     * @return
     */
    public Memory getMemory() {
        return memory;
    }

    /**
     * Gets the C1541Drive used by this Machine.
     *
     * @return The C1541Drive used by this Machine.
     */
    public C1541Drive getC1541Drive() {
        return c1541Drive;
    }

    /**
     * Gets VIA1 ($9110), which handles the NMI sources: RESTORE, the user port,
     * joystick and the serial bus ATN output.
     *
     * @return VIA1.
     */
    public Via6522 getVia1() {
        return via1;
    }

    /**
     * Gets VIA2 ($9120), which handles the keyboard, IRQ timer and tape.
     *
     * @return VIA2.
     */
    public Via6522 getVia2() {
        return via2;
    }

    /**
     * Gets the C1530 datasette.
     *
     * @return The datasette.
     */
    public C1530Datasette getDatasette() {
        return datasette;
    }

    /**
     * Gets the pixel data the VIC draws into. Used by the debug monitor's screenshot
     * command.
     *
     * @return The PixelData.
     */
    public PixelData getPixelData() {
        return pixelData;
    }

    // ------------------------------------------------------------------------------
    // Save states (used by the debug monitor's dump and undump).
    // ------------------------------------------------------------------------------

    /** The devices on the serial bus, in the fixed order the save state uses. */
    private Object[] serialBusDevices() {
        return new Object[] { via1, via2, c1541Drive.getVia1() };
    }

    /**
     * A number that identifies the memory map's layout (which kind of chip is at each
     * page), so that a save state is only loaded into a machine with the same RAM
     * expansions and cartridge areas.
     */
    private int memoryMapSignature() {
        MemoryMappedChip[] map = memory.getMemoryMap();
        int signature = 17;
        for (int address = 0; address < 0x10000; address += 0x100) {
            MemoryMappedChip chip = map[address];
            if (chip instanceof WatchProxyChip) {
                chip = ((WatchProxyChip) chip).getTarget();
            }
            String name = (chip != null) ? chip.getClass().getName() : "";
            signature = (signature * 31) + name.hashCode();
        }
        return signature;
    }

    /**
     * Writes the whole machine to a save state: its configuration, memory, CPU, VIC,
     * VIAs, serial bus, drive (with its disk) and datasette (with its tape). The
     * sound generator's own oscillators aren't saved, so sound resumes from silence.
     *
     * @param out The writer.
     */
    public void saveState(StateWriter out) {
        out.beginSection("CFG ");
        out.writeString(machineType.name());
        out.writeInt(memory.getRamExpansion());
        out.writeInt(memoryMapSignature());
        out.beginSection("MEM ");
        memory.saveState(out);
        out.beginSection("CPU ");
        cpu.saveState(out);
        out.beginSection("VIC ");
        vic.saveState(out);
        out.beginSection("VIA1");
        via1.saveState(out);
        out.beginSection("VIA2");
        via2.saveState(out);
        out.beginSection("BUS ");
        serialBus.saveState(out, serialBusDevices());
        out.beginSection("DRV ");
        c1541Drive.saveState(out);
        out.beginSection("TAPE");
        datasette.saveState(out);
        out.endSection();
    }

    /**
     * Restores the machine from a save state written by {@link #saveState}. The
     * machine must have been set up the same way (machine type, RAM expansions and
     * cartridge areas); otherwise nothing is changed.
     *
     * @param in The reader.
     *
     * @throws StateReader.StateException if the state doesn't fit this machine or is
     *         damaged.
     */
    public void loadState(StateReader in) {
        in.section("CFG ");
        String type = in.readString();
        int ramExpansion = in.readInt();
        int signature = in.readInt();
        if (!type.equals(machineType.name())) {
            throw new StateReader.StateException("The save state is for a " + type + " machine, not "
                    + machineType.name() + ".");
        }
        if ((ramExpansion != memory.getRamExpansion()) || (signature != memoryMapSignature())) {
            throw new StateReader.StateException("The save state was made with different RAM expansions or "
                    + "cartridge; start jvic with the same ones first.");
        }
        in.section("MEM ");
        memory.loadState(in);
        in.section("CPU ");
        cpu.loadState(in);
        in.section("VIC ");
        vic.loadState(in);
        in.section("VIA1");
        via1.loadState(in);
        in.section("VIA2");
        via2.loadState(in);
        in.section("BUS ");
        serialBus.loadState(in, serialBusDevices());
        in.section("DRV ");
        c1541Drive.loadState(in);
        in.section("TAPE");
        datasette.loadState(in);
    }

    /**
     * Resets the machine as its RESET line does: the CPU, both VIAs, and the drive
     * (through the serial bus RESET line). The VIC has no reset line, and memory is
     * left as it is. Used by the debug monitor's reset command.
     */
    /**
     * Changes a setting of the running machine, for settings that can change while it
     * runs. Called on the emulation thread. Unknown names are ignored, so a newer
     * front end never breaks an older machine.
     *
     * @param name  The setting: "palette" (value "MID" for the midpoint PAL palette,
     *              anything else for the alternating one; ignored for NTSC).
     * @param value The value.
     */
    public void applySetting(String name, String value) {
        if ("palette".equals(name) && (vic instanceof Vic6561)) {
            ((Vic6561) vic).setPalette(value);
        }
    }

    public void reset() {
        via1.reset();
        via2.reset();
        c1541Drive.reset(false);
        cpu.reset();
    }

    /**
     * Turns the machine off and on again, keeping its configuration: clears RAM and
     * the VIC's registers, then resets. Used by the debug monitor's reset 1.
     */
    public void powerCycle() {
        int[] mem = memory.getMemoryArray();
        MemoryMappedChip[] memoryMap = memory.getMemoryMap();
        for (int address = 0; address < 0x10000; address++) {
            MemoryMappedChip chip = memoryMap[address];
            if (chip instanceof WatchProxyChip) {
                chip = ((WatchProxyChip) chip).getTarget();
            }
            if ((chip instanceof RamChip) || (chip instanceof NibbleRamChip)) {
                mem[address] = 0;
            }
        }
        for (int address = 0x9000; address < 0x9010; address++) {
            mem[address] = 0;
        }
        via1.reset();
        via2.reset();
        c1541Drive.reset(true);
        if (c1541Drive.getDiskImage() != null) {
            // The drive takes longer to start than BASIC; as when jvic starts with a
            // disk, let it finish first, as if it had been switched on earlier.
            c1541Drive.warmUp();
        }
        cpu.reset();
    }
}
