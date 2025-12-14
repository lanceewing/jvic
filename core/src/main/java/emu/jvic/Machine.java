package emu.jvic;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Callable;

import emu.jvic.cpu.Cpu6502;
import emu.jvic.io.Joystick;
import emu.jvic.io.Keyboard;
import emu.jvic.io.Via1;
import emu.jvic.io.Via2;
import emu.jvic.io.Via6522;
import emu.jvic.io.SerialBus;
import emu.jvic.io.disk.C1541Drive;
import emu.jvic.memory.Memory;
import emu.jvic.memory.RamType;
import emu.jvic.memory.Vic20Memory;
import emu.jvic.snap.PcvSnapshot;
import emu.jvic.snap.Snapshot;
import emu.jvic.sound.SoundGenerator;
import emu.jvic.sound.libgdx.GdxSoundGenerator;
import emu.jvic.video.Vic;

/**
 * Represents the VIC 20 machine.
 * 
 * @author Lance Ewing
 */
public class Machine {

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

    // Platform specific component.
    private KeyboardMatrix keyboardMatrix;
    private PixelData pixelData;
    private SoundGenerator soundGenerator;
    
    private boolean paused = true;

    private MachineType machineType;

    // These control what part of the generate pixel data is rendered to the screen.
    private int screenLeft;
    private int screenRight;
    private int screenTop;
    private int screenBottom;
    private int screenWidth;
    private int screenHeight;
    
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
            Program program, MachineType machineType, RamType ramType) {
        
        byte[] programData = (program != null? program.getProgramData() : null);
        Snapshot snapshot = null;
        Callable<Queue<char[]>> autoLoadRunnable = null;

        this.machineType = machineType;

        // If we've been given the path to a program to load, we load the data prior to all
        // other initialisation. Primarily this is to work out what expansion we need.
        if ((programData != null) && (programData.length > 0)) {
            String programType = program.getProgramType();
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
                }
            } catch (Exception e) {
                // Continue with default behaviour, which is to boot in to BASIC.
            }
        }

        // Create the microprocessor.
        cpu = new Cpu6502(snapshot);

        // Create the VIC chip and configure it as per the current TV type.
        vic = new Vic(pixelData, machineType, snapshot);

        // Create the peripherals.
        keyboard = new Keyboard(keyboardMatrix);
        joystick = new Joystick(keyboardMatrix);
        serialBus = new SerialBus();
        c1541Drive = new C1541Drive(serialBus, dos1541Rom);

        // Create two instances of the VIA chip; one for VIA1 and one for VIA2.
        via1 = new Via1(cpu, joystick, serialBus, snapshot);
        via2 = new Via2(cpu, keyboard, joystick, serialBus, snapshot);
        
        // Now we create the memory, which will include mapping the VIC chip,
        // the VIA chips, and the creation of RAM chips and ROM chips.
        memory = new Vic20Memory(cpu, vic, via1, via2, ramType.getRamPlacement(), machineType, 
                basicRom, kernalRom, charRom, snapshot);

        // Initialise the sound generator.
        soundGenerator.init(memory.getMemoryArray(), machineType);
        
        // Set up the screen dimensions based on the VIC chip settings. Aspect ratio of 4:3.
        screenWidth = (machineType.getVisibleScreenHeight() / 3) * 4;
        screenHeight = machineType.getVisibleScreenHeight();
        screenLeft = machineType.getHorizontalOffset();
        screenRight = screenLeft + machineType.getVisibleScreenWidth();
        screenTop = machineType.getVerticalOffset();
        screenBottom = screenTop + machineType.getVisibleScreenHeight();
        
        // Check if the resource parameters have been set.
        if ((programData != null) && (programData.length > 0)) {
            String programType = program.getProgramType();
            if ("CART".equals(programType)) {
                memory.loadCart(programData);
            } else if ("PRG".equals(programType)) {
                autoLoadRunnable = memory.loadBasicProgram(programData, true);
            } else if ("DISK".equals(programType)) {
                // Insert the disk ready to be booted.
                c1541Drive.insertDisk(programData);
                autoLoadRunnable = new Callable<Queue<char[]>>() {
                    public Queue<char[]> call() throws Exception {
                        Queue<char[]> autoRunCmds = new LinkedList<char[]>();
                        autoRunCmds.add(new char[] {'L','O','A','D','"','*','"',',','8'});
                        autoRunCmds.add(new char[] {'R','U','N'});
                        return autoRunCmds;
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
        
        return autoLoadRunnable;
    }

    /**
     * Updates the state of the machine of the machine until a frame is complete
     */
    public void update() {
        if (machineType.isNTSC()) {
            updateNTSC();
        } else {
            updatePAL();
        }
    }

    /**
     * Updates the state of the machine of the machine until a frame is complete.
     */
    public void updatePAL() {
        boolean frameComplete = false;
        do {
            frameComplete |= vic.emulateCyclePal();
            cpu.emulateCycle();
            via1.emulateCycle();
            via2.emulateCycle();
            c1541Drive.emulateCycle();
        } while (!frameComplete);
    }

    /**
     * Updates the state of the machine of the machine until a frame is complete.
     */
    public void updateNTSC() {
        boolean frameComplete = false;
        do {
            frameComplete |= vic.emulateCycleNtsc();
            cpu.emulateCycle();
            via1.emulateCycle();
            via2.emulateCycle();
            c1541Drive.emulateCycle();
        } while (!frameComplete);
    }

    /**
     * @return the screenLeft
     */
    public int getScreenLeft() {
        return screenLeft;
    }

    /**
     * @return the screenRight
     */
    public int getScreenRight() {
        return screenRight;
    }

    /**
     * @return the screenTop
     */
    public int getScreenTop() {
        return screenTop;
    }

    /**
     * @return the screenBottom
     */
    public int getScreenBottom() {
        return screenBottom;
    }

    /**
     * @return the screenWidth
     */
    public int getScreenWidth() {
        return screenWidth;
    }

    /**
     * @return the screenHeight
     */
    public int getScreenHeight() {
        return screenHeight;
    }

    /**
     * Emulates a single machine cycle.
     * 
     * @return true If the VIC chip has indicated that a frame should be rendered.
     */
    public boolean emulateCycle() {
        boolean render = machineType.isNTSC()? 
                vic.emulateCycleNtsc() : 
                vic.emulateCyclePal(); 
        cpu.emulateCycle();
        via1.emulateCycle();
        via2.emulateCycle();
        c1541Drive.emulateCycle();
        soundGenerator.emulateCycle();
        return render;
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
}
