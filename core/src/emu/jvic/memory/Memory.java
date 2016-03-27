package emu.jvic.memory;

import com.badlogic.gdx.Gdx;

import emu.jvic.MachineType;
import emu.jvic.cpu.Cpu6502;
import emu.jvic.io.Via;
import emu.jvic.snap.Snapshot;
import emu.jvic.video.Vic;

/**
 * This class emulators the VIC 20's memory.
 * 
 * @author Lance Ewing
 */
public class Memory {

  // There are seven possible slots for RAM expansion.
  public static final int RAM_1 = 0x01;
  public static final int RAM_2 = 0x02;
  public static final int RAM_3 = 0x04;
  public static final int BLK_1 = 0x08;
  public static final int BLK_2 = 0x10;
  public static final int BLK_3 = 0x20;
  public static final int BLK_5 = 0x40;
  
  /**
   * Holds the machines memory.
   */
  private int mem[];

  /**
   * Holds an array of references to instances of MemoryMappedChip where each
   * instance determines the behaviour of reading or writing to the given memory
   * address.
   */
  private MemoryMappedChip memoryMap[];

  /**
   * The amount of RAM expansion. Supports various configurations.
   */
  private int ramExpansion;
  
  /**
   * The type of VIC 20 machine that is being emulated, i.e. PAL or NTSC.
   */
  private MachineType machineType;
  
  /**
   * Constructor for Memory.
   * 
   * @param cpu The CPU that will access this Memory.
   * @param vic The VIC chip to map to memory.
   * @param via1 The VIA#1 chip to map to memory.
   * @param via2 The VIA#2 chip to map to memory.
   * @param ramExpansion The amount of RAM expansion.
   * @param machineType The type of VIC 20 machine that is being emulated, i.e. PAL or NTSC.
   * @param snapshot Optional snapshot of the machine state to start with.
   */
  public Memory(Cpu6502 cpu, Vic vic, Via via1, Via via2, int ramExpansion, MachineType machineType, Snapshot snapshot) {
    if (snapshot != null) {
      this.mem = snapshot.getMemoryArray();
      this.ramExpansion = snapshot.getRamExpansion();
    } else {
      this.mem = new int[65536];
      this.ramExpansion = ramExpansion;
    }
    this.memoryMap = new MemoryMappedChip[65536];
    this.machineType = machineType;
    initVicMemory(vic, via1, via2);
    cpu.setMemory(this);
  }

  /**
   * Initialise the VIC 20's memory.
   * 
   * @param vic The VIC chip to map to memory.
   * @param via1 The VIA #1 chip to map to memory. 
   * @param via2 The VIA #2 chip to map to memory.
   */
  private void initVicMemory(Vic vic, Via via1, Via via2) {
    // This 1K of RAM is always present.
    mapChipToMemory(new RamChip(), 0x0000, 0x03FF);
    
    // The next 3K of memory may have RAM or may be unconnected.
    mapChipToMemory((ramExpansion & RAM_1) != 0? new RamChip() : new UnconnectedMemory(), 0x0400, 0x07FF);
    mapChipToMemory((ramExpansion & RAM_2) != 0? new RamChip() : new UnconnectedMemory(), 0x0800, 0x0BFF);
    mapChipToMemory((ramExpansion & RAM_3) != 0? new RamChip() : new UnconnectedMemory(), 0x0C00, 0x0FFF);

    // This 4K of RAM is always present.
    mapChipToMemory(new RamChip(), 0x1000, 0x1FFF);
    
    // The next three 8K blocks may have RAM or may be unconnected.
    mapChipToMemory((ramExpansion & BLK_1) != 0? new RamChip() : new UnconnectedMemory(), 0x2000, 0x3FFF);
    mapChipToMemory((ramExpansion & BLK_2) != 0? new RamChip() : new UnconnectedMemory(), 0x4000, 0x5FFF);
    mapChipToMemory((ramExpansion & BLK_3) != 0? new RamChip() : new UnconnectedMemory(), 0x6000, 0x7FFF);
    
    mapChipToMemory(new RomChip(), 0x8000, 0x8FFF, Gdx.files.internal("roms/char.rom").readBytes());

    // These are the standard locations for the VIC, VIA1 and VIA2 chips.
    mapChipToMemory(vic, 0x9000, 0x900F);
    mapChipToMemory(via1, 0x9110, 0x911F);
    mapChipToMemory(via2, 0x9120, 0x912F);

    // The rest of the $9XXX address space, other than colour RAM, is not fully decoded. Every 
    // address in that range is going to select at least one of the VIC, VIA1 or VIA2 chips though,
    // but sometimes it will select 2 or 3 of them at once!! So we emulate this behaviour below.
    NotFullyDecodedMemory vicVia1 = new NotFullyDecodedMemory(new MemoryMappedChip[] { vic, via1});
    NotFullyDecodedMemory vicVia2 = new NotFullyDecodedMemory(new MemoryMappedChip[] { vic, via2 });
    NotFullyDecodedMemory via1Via2 = new NotFullyDecodedMemory(new MemoryMappedChip[] { via1, via2 });
    NotFullyDecodedMemory vicVia1Via2 = new NotFullyDecodedMemory(new MemoryMappedChip[] { vic, via1, via2 });

    mapChipToMemory(vicVia1, 0x9010, 0x901F);
    mapChipToMemory(vicVia2, 0x9020, 0x902F);
    mapChipToMemory(vicVia1Via2, 0x9030, 0x903F);
    mapChipToMemory(vic, 0x9040, 0x904F);
    mapChipToMemory(vicVia1, 0x9050, 0x905F);
    mapChipToMemory(vicVia2, 0x9060, 0x906F);
    mapChipToMemory(vicVia1Via2, 0x9070, 0x907F);
    mapChipToMemory(vic, 0x9080, 0x908F);
    mapChipToMemory(vicVia1, 0x9090, 0x909F);
    mapChipToMemory(vicVia2, 0x90A0, 0x90AF);
    mapChipToMemory(vicVia1Via2, 0x90B0, 0x90BF);
    mapChipToMemory(vic, 0x90C0, 0x90CF);
    mapChipToMemory(vicVia1, 0x90D0, 0x90DF);
    mapChipToMemory(vicVia2, 0x90E0, 0x90EF);
    mapChipToMemory(vicVia1Via2, 0x90F0, 0x90FF);
    mapChipToMemory(vic, 0x9100, 0x910F); // Unconnected VIC address bus space.
    mapChipToMemory(via1Via2, 0x9130, 0x913F);
    mapChipToMemory(vic, 0x9140, 0x914F); // Unconnected VIC address bus space.
    mapChipToMemory(via1, 0x9150, 0x915F);
    mapChipToMemory(via2, 0x9160, 0x916F);
    mapChipToMemory(via1Via2, 0x9170, 0x917F);
    mapChipToMemory(vic, 0x9180, 0x918F); // Unconnected VIC address bus space.
    mapChipToMemory(via1, 0x9190, 0x919F);
    mapChipToMemory(via2, 0x91A0, 0x91AF);
    mapChipToMemory(via1Via2, 0x91B0, 0x91BF);
    mapChipToMemory(vic, 0x91C0, 0x91CF); // Unconnected VIC address bus space.
    mapChipToMemory(via1, 0x91D0, 0x91DF);
    mapChipToMemory(via2, 0x91E0, 0x91EF);
    mapChipToMemory(via1Via2, 0x91F0, 0x91FF);
    mapChipToMemory(vic, 0x9200, 0x920F); // Unconnected VIC address bus space.
    mapChipToMemory(via1, 0x9210, 0x921F);
    mapChipToMemory(via2, 0x9220, 0x922F);
    mapChipToMemory(via1Via2, 0x9230, 0x923F);
    mapChipToMemory(vic, 0x9240, 0x924F); // Unconnected VIC address bus space.
    mapChipToMemory(via1, 0x9250, 0x925F);
    mapChipToMemory(via2, 0x9260, 0x926F);
    mapChipToMemory(via1Via2, 0x9270, 0x927F);
    mapChipToMemory(vic, 0x9280, 0x928F); // Unconnected VIC address bus space.
    mapChipToMemory(via1, 0x9290, 0x929F);
    mapChipToMemory(via2, 0x92A0, 0x92AF);
    mapChipToMemory(via1Via2, 0x92B0, 0x92BF);
    mapChipToMemory(vic, 0x92C0, 0x92CF); // Unconnected VIC address bus space.
    mapChipToMemory(via1, 0x92D0, 0x92DF);
    mapChipToMemory(via2, 0x92E0, 0x92EF);
    mapChipToMemory(via1Via2, 0x92F0, 0x92FF);
    mapChipToMemory(vic, 0x9300, 0x930F); // Unconnected VIC address bus space.
    mapChipToMemory(via1, 0x9310, 0x931F);
    mapChipToMemory(via2, 0x9320, 0x932F);
    mapChipToMemory(via1Via2, 0x9330, 0x933F);
    mapChipToMemory(vic, 0x9340, 0x934F); // Unconnected VIC address bus space.
    mapChipToMemory(via1, 0x9350, 0x935F);
    mapChipToMemory(via2, 0x9360, 0x936F);
    mapChipToMemory(via1Via2, 0x9370, 0x937F);
    mapChipToMemory(vic, 0x9380, 0x938F); // Unconnected VIC address bus space.
    mapChipToMemory(via1, 0x9390, 0x939F);
    mapChipToMemory(via2, 0x93A0, 0x93AF);
    mapChipToMemory(via1Via2, 0x93B0, 0x93BF);
    mapChipToMemory(vic, 0x93C0, 0x93CF); // Unconnected VIC address bus space.
    mapChipToMemory(via1, 0x93D0, 0x93DF);
    mapChipToMemory(via2, 0x93E0, 0x93EF);
    mapChipToMemory(via1Via2, 0x93F0, 0x93FF);

    // The colour RAM lives at this spot by default.
    mapChipToMemory(new NibbleRamChip(), 0x9400, 0x97FF);
    
    mapChipToMemory(vic, 0x9800, 0x9FFF); // Unconnected VIC address bus space.

    // It is possible to connect RAM in this block, even though it would normally be a cartridge ROM.
    mapChipToMemory((ramExpansion & BLK_5) != 0? new RamChip() : new UnconnectedMemory(), 0xA000, 0xBFFF);
    
    mapChipToMemory(new RomChip(), 0xC000, 0xDFFF, Gdx.files.internal("roms/basic.rom").readBytes());
    if (machineType.equals(MachineType.PAL)) { 
      mapChipToMemory(new RomChip(), 0xE000, 0xFFFF, Gdx.files.internal("roms/kernel.pal").readBytes());
    } else { 
      mapChipToMemory(new RomChip(), 0xE000, 0xFFFF, Gdx.files.internal("roms/kernel.ntsc").readBytes());
    }
  }
  
  /**
   * Maps the given chip instance at the given address range.
   * 
   * @param chip The chip to map at the given address range.
   * @param startAddress The start of the address range.
   * @param endAddress The end of the address range.
   */
  private void mapChipToMemory(MemoryMappedChip chip, int startAddress, int endAddress) {
    mapChipToMemory(chip, startAddress, endAddress, null);
  }
  
  /**
   * Maps the given chip instance at the given address range, optionally loading the
   * given initial state data into that address range. This state data is intended to be
   * used for things such as ROM images (e.g. char, kernel, basic).
   * 
   * @param chip The chip to map at the given address range.
   * @param startAddress The start of the address range.
   * @param endAddress The end of the address range.
   * @param state byte array containing initial state (can be null).
   */
  private void mapChipToMemory(MemoryMappedChip chip, int startAddress, int endAddress, byte[] state) {
    int statePos = 0;
    
    // Load the initial state into memory if provided.
    if (state != null) {
      for (int i=startAddress; i<=endAddress; i++) {
        mem[i] = (state[statePos++] & 0xFF);
      }
    }
    
    // Configure the chip into the memory map between the given start and end addresses.
    for (int i = startAddress; i <= endAddress; i++) {
      memoryMap[i] = chip;
    }

    chip.setMemory(this);
  }

  /**
   * Loads the given data into memory with the assumption that it has a BASIC loader
   * at the start. This will support both the loading of pure BASIC programs and also
   * machine language programs that have a small BASIC loader at the start.
   *  
   * @param programData The byte array containing the BASIC program data.
   * @param waitForBasic If true then it assumes the machine hasn't yet started, so will wait for BASIC to load first.
   */
  public void loadBasicProgram(final byte[] programData, boolean waitForBasic) {
    final int startAddress = (programData[1] << 8) + programData[0];
    final int endAddress = startAddress + programData.length;
    
    // This is what we will run regardless of whether we do it immediately or after BASIC has loaded.
    final Runnable loadProgramTask = new Runnable() {
      public void run() {
        // Start by loading the program data in to the VIC 20's memory.
        for (int i=2; i<programData.length; i++) {
          mem[startAddress + (i-2)] = (programData[i] & 0xFF);
        }
        
        // We now need to adjust the BASIC pointers to simulate a BASIC program load.
        mem[0x2b] = mem[0xac] = (startAddress & 0xff);
        mem[0x2c] = mem[0xad] = (startAddress >> 8);
        mem[0x2d] = mem[0x2f] = mem[0x31] = mem[0xae] = (endAddress & 0xff);
        mem[0x2e] = mem[0x30] = mem[0x32] = mem[0xaf] = (endAddress >> 8);
      }
    };
    
    if (waitForBasic) {
      // We need to wait for BASIC to boot up before loading the program.
      Thread basicWaitThread = new Thread() {
        public void run() {
          // The simplest way to wait for BASIC to be ready is to check for
          // the starting cursor position.
          while (mem[0xD1] != 110) {
            try { 
              Thread.sleep(10); 
            } catch (InterruptedException ie) {
            }
          }
          
          // Now that the BASIC cursor is in the start position, let's load the 
          // program data in to memory.
          loadProgramTask.run();
          
          // And finally pretend that the user typed RUN
          mem[631] = 'R';
          mem[632] = 'U';
          mem[633] = 'N';
          mem[634] = 0x0D;
          mem[198] = 4;
        }
      };
      basicWaitThread.start();
      
    } else {
      // In this case we assume BASIC is already loaded, so we load the program immediately.
      loadProgramTask.run();
    }
  }
  
  /**
   * Loads a cartridge file from the given byte array in to the cartridge memory area.
   * 
   * @param cartData The byte array containing the cartridge program data to load.
   */
  public void loadCart(byte[] cartData) {
    if (cartData != null) {
      if (cartData.length == 16384) {
        mapChipToMemory(new RomChip(), 0x6000, 0x7FFF, cartData);
        byte data2ndHalf[] = new byte[8192];
        System.arraycopy(cartData, 8192, data2ndHalf, 0, 8192);
        mapChipToMemory(new RomChip(), 0xA000, 0xBFFF, data2ndHalf);
      } else {
        mapChipToMemory(new RomChip(), 0xA000, 0xA000 + (cartData.length - 1), cartData);
      }
    }
  }
  
  /**
   * Gets the int array that represents the VIC 20s memory.
   * 
   * @return an int array represents the VIC 20 memory.
   */
  public int[] getMemoryArray() {
    return mem;
  }

  /**
   * Reads the value of the given VIC 20 memory address.
   * 
   * @param address The address to read the byte from.
   * 
   * @return The contents of the memory address.
   */
  public int readMemory(int address) {
    return (memoryMap[address].readMemory(address));
  }

  /**
   * Writes a value to the give VIC 20 memory address.
   * 
   * @param address The address to write the value to.
   * @param value The value to write to the given address.
   */
  public void writeMemory(int address, int value) {
    memoryMap[address].writeMemory(address, value);
  }
}
