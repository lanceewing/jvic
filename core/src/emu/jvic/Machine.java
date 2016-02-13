package emu.jvic;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;

import emu.jvic.cpu.Cpu6502;
import emu.jvic.io.Joystick;
import emu.jvic.io.Keyboard;
import emu.jvic.io.Via;
import emu.jvic.io.Via1;
import emu.jvic.io.Via2;
import emu.jvic.memory.Memory;
import emu.jvic.snap.PcvSnapshot;
import emu.jvic.snap.Snapshot;
import emu.jvic.video.Vic;

/**
 * Represents the VIC 20 machine.
 * 
 * @author Lance Ewing
 */
public class Machine extends InputAdapter {

  // Machine components.
  private Memory memory;
  private Vic vic;
  private Via via1;
  private Via via2;
  private Cpu6502 cpu;
  
  // Peripherals.
  private Keyboard keyboard;
  private Joystick joystick;

  private boolean paused;

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
   */
  public Machine() {
    //init("snaps/pcv/ROCKMAN.PCV", "PCV", MachineType.PAL);
    //init("carts/8k/demonata.crt", "CART", MachineType.PAL);
    //init("carts/8k/gorf.crt", "CART", MachineType.NTSC);
    //init("tapes/16k/GALABDCT.PRG", "PRG", MachineType.PAL);
    //init("carts/8k/dragon.crt", "CART", MachineType.PAL);
    //init("carts/8k/atlantis.crt", "CART", MachineType.NTSC);
    //init("carts/8k/terragua.crt", "CART", MachineType.PAL);
    //init("tapes/16k/BONGO.PRG", "PRG", MachineType.PAL);
    //init("tapes/16k/PERILS.PRG", "PRG", MachineType.PAL);
    //init("tapes/16k/SKRAMBLE.PRG", "PRG", MachineType.PAL);
    
    init();
  }
  
  /**
   * Initialises the machine. It will boot in to BASIC.
   */
  public void init() {
    init(null, null, MachineType.PAL);
  }
  
  /**
   * Initialises the machine, and optionally loads the given program file (if provided).
   * 
   * @param programFile The internal path to the program file to automatically load and run.
   * @param programType The type of program data, e.g. CART, PRG, V20, PCV, VICE, S20, etc.
   * @param machineType The type of VIC 20 machine, i.e. PAL or NTSC.
   * 
   */
  public void init(String programFile, String programType, MachineType machineType) {
    byte[] programData = null;
    Snapshot snapshot = null;
    int ramExpansion = 0;
    
    this.machineType = machineType;
    
    // If we've been given the path to a program to load, we load the data prior to all
    // other initialisation. Primarily this is to work out what expansion we need.
    if ((programFile != null) && (programFile.length() > 0)) {
      try {
        programData = Gdx.files.internal(programFile).readBytes();
        
        if ("PRG".equals(programType) && (programData != null) && (programData.length > 2)) {
          int startAddress = (programData[1] << 8) + programData[0];
          
          if (startAddress == 0x1201) {
            // 8K, 16K, or 24K. We'll give it the full 24K to cover all bases.
            ramExpansion = Memory.EXP_24K;
          } else if (startAddress == 0x0401) {
            // 3K expansion.
            ramExpansion = Memory.EXP_3K;
          } else if (startAddress == 0x1001) {
            // Unexpanded start address.
            ramExpansion = Memory.EXP_UNEXPANDED;
          }
        } else if ("PCV".equals(programType)) {
          // Decode PCVIC snapshot data. PCVIC is an old DOS VIC 20 emulator.
          snapshot = new PcvSnapshot(programData);
        }
      } catch (Exception e) {
        // Continue with default behaviour, which is to boot in to BASIC.
      }
    }
    
    // Create the microprocessor.
    cpu = new Cpu6502(snapshot);
    
    // Create the VIC chip and configure it as per the current TV type.
    vic = new Vic(machineType, snapshot);
    
    // Create the peripherals.
    keyboard = new Keyboard();
    joystick = new Joystick();
    
    // Create two instances of the VIA chip; one for VIA1 and one for VIA2.
    via1 = new Via1(cpu, joystick, snapshot);
    via2 = new Via2(cpu, keyboard, joystick, snapshot);
    
    // Now we create the memory, which will include mapping the VIC chip,
    // the VIA chips, and the creation of RAM chips and ROM chips.
    memory = new Memory(cpu, vic, via1, via2, ramExpansion, snapshot);
    
    // Set up the screen dimensions based on the VIC chip settings.
    screenWidth = (machineType.getVisibleScreenHeight() / 3) * 4;
    screenHeight = machineType.getVisibleScreenHeight();
    screenLeft = machineType.getHorizontalOffset();
    screenRight = screenLeft + machineType.getVisibleScreenWidth();
    screenTop = machineType.getVerticalOffset();
    screenBottom = screenTop + machineType.getVisibleScreenHeight();

    // Register this Machine instance as the input processor for keys, etc.
    Gdx.input.setInputProcessor(this);

    // Check if the resource parameters have been set.
    if ((programData != null) && (programData.length > 0)) {
      if ("PRG".equals(programType)) {
        memory.loadBasicProgram(programData, true);
        
      } else if ("PCV".equals(programType)) {
        // Nothing to do. Snapshot was already loaded.
      } else {
        // Default resource type is CART.
        memory.loadCart(programData);
      }
    }
    
    // If the state of the machine was not loaded from a snapshot file, then we begin with a reset.
    if (snapshot == null) {
      cpu.reset();
    }
  }

  /**
   * Updates the state of the machine based on the given elapsed time since the previous
   * invocation of this method.
   * 
   * @param delta The time in seconds since the last render.
   * 
   * @return true If a frame should be rendered; otherwise false.
   */
  public boolean update(float delta) {
    // Execute the number of cycles equivalent to the delta. 
    int cyclesToExecute = (int)(machineType.getCyclesPerSecond() * delta);
    
    boolean render = false;
    
    for (int i=0; i<cyclesToExecute; i++) {
      render |= emulateCycle();
    }
    
    return render;
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
   * Gets the pixels for the current frame from the VIC chip.
   * 
   * @return The pixels for the current frame.
   */
  public int[] getFramePixels() {
    return vic.getFramePixels();
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
   * Gets the MachineType of this Machine, i.e. either PAL or NTSC.
   * 
   * @return The MachineType of this Machine, i.e. either PAL or NTSC.
   */
  public MachineType getMachineType() {
    return machineType;
  }
  
  /** 
   * Called when a key was pressed
   * 
   * @param keycode one of the constants in {@link Input.Keys}
   * 
   * @return whether the input was processed 
   */
  public boolean keyDown (int keycode) {
    keyboard.keyPressed(keycode);
    joystick.keyPressed(keycode);
    return true;
  }

  /** 
   * Called when a key was released
   * 
   * @param keycode one of the constants in {@link Input.Keys}
   * 
   * @return whether the input was processed 
   */
  public boolean keyUp (int keycode) {
    keyboard.keyReleased(keycode);
    joystick.keyReleased(keycode);
    return true;
  }
}
