package emu.jvic.io.disk;

import com.badlogic.gdx.Gdx;

import emu.jvic.cpu.Cpu6502;
import emu.jvic.io.Via6522;
import emu.jvic.io.disk.C1541FullDrive.GcrDiskImage.Sector;
import emu.jvic.memory.Memory;
import emu.jvic.memory.RamChip;
import emu.jvic.memory.RomChip;
import emu.jvic.memory.UnconnectedMemory;

/**
 * Emulates the Commodore 1541 disk drive. This is a whole machine in and of itself.
 * 
 * @author Lance Ewing
 */
public class C1541FullDrive {

  private Via6522 via1;
  private Via6522 via2;
  private Cpu6502 cpu;
  
  /**
   * Constructor for Drive1541.
   */
  public C1541FullDrive() {
    cpu = new Cpu6502(null);
    via1 = createVia1();
    via2 = createVia2();
    createMemory(cpu, via1, via2);
  }
  
  /**
   * 
   */
  public void emulateCycle() {
    
    cpu.emulateCycle();
    via1.emulateCycle();
    via2.emulateCycle();
    
  }
  
  
  private Memory createMemory(final Cpu6502 cpu, final Via6522 via1, final Via6522 via2) {
    return new Memory(cpu, null) {{
      // UB2 is a 2048 x 8 bit RAM. UB2 resides at memory locations $0000-$07FF. This memory is
      // used for processor stack operations, general processor housekeeping, use program storage,
      // and 4 temporary buffer areas. UC5, UC6 and UC7 decode the addresses output from the
      // processor when selecting RAM.
      RamChip ram = new RamChip();
      mapChipToMemory(ram, 0x0000, 0x07FF, 0x6000);
      
      // The Serial Interface
      // UC3 is a 6522 Versatile Interface Adapter (VIA). Two parallel ports, handshake control, programmable timers,
      // and interrupt control are standard features of the VIA. Port B signals (PB0-PB7) control the serial interface
      // driver ICs (UB1 and UA1). CLK and DATA signals are bidirectional signals connected to pins 4 and 5 of P2 and
      // P3. ANT (Attention) is an input on pin 3 of P2 and P3 that is sensed at PB7 and CA1 of UC3 after being inverted
      // by UA1. ATNA (Attention Acknowledge) is an output from PB4 of UC3 which is sensed on the data line pin 5
      // of P2 and P4 after being exclusively "ored" by UD3 and inverted by UB1. UC3 is selected by UC7 pin 7 going
      // "low" when the proper address is output from the processor. UC3 resides at memory locations $1C00-$1C0F. 
      mapChipToMemory(via1, 0x1800, 0x180F, 0x63F0);
      
      // Microprocessor R/W and Motor Control Logic
      // UC2 is a VIA also. During a write operation the microprocessor passes the data to be recorded to Port A of UC2.
      // The data is then loaded into the PLA parallel port (YB0-YB7). The PLA contains a shift register which converts
      // the parallel data into serial data. The PLA generates signals on pins 2, 3, 4, and 40 which control the write
      // amplifier circuits on D-IN input on pin 24 of the PLA. The PLA shift register converts serial data into parallel
      // data that is latched at the parallel port (YB0-YB7). The register converts serial data into parallel data that is
      // latched at the parallel port (YB0-YB7). The microprocessor reads the parallel data that is latched at the parallel
      // port (YB0-YB7). The microprocessor reads the parallel PLA output by reading Port A of UC2 when BYTE
      // READY on pin 39 goes "low."
      // The stepper motor is controlled by two outputs on port B of UC2 (STP0, and STP1). A binary
      // four count is developed from these two lines, driving the four phases of the stepper motor.
      // The PLA converts STP0 and STP1 into four outputs that represent one of the four states in the
      // count (Y0,Y1,Y2,Y3). The Spindle motor is controlled by the output MTR of UC2. The PLA
      // inverts this signal. It is then passed to the motor speed control pcb.
      // UC2 pin 14 is an input that monitors the state of the write protect sensor, and pin 13 is an
      // output that controls the activity light (RED LED). UC7 decodes the addresses output from the
      // processor when selecting UC2. UC2 resides at memory locations $1800-$180F. 
      mapChipToMemory(via2, 0x1C00, 0x1C0F, 0x63F0);
      
      // UB3 and UB4 are 8192 x 8 bit ROMS that store the Disk Operating System (DOS). UB3 resides at memory
      // locations $C000-$DFFF. UB4 resides at memory locations $E000-$FFFF. UC5 and UC6 decodes the addresses
      // output from the microprocessor when selecting these ROMS. However, it isn't fully decoded, so also
      // mirrors from $8000-$9FFF and $A000-$BFFF.
      mapChipToMemory(new RomChip(), 0x8000, 0xBFFF, 0x4000, Gdx.files.internal("roms/dos1541.rom").readBytes());
      
      // Everything else is unmapped.
      UnconnectedMemory unconnectedMemory = new UnconnectedMemory();
      for (int address=0; address<=0xFFFF; address++) {
        if (memoryMap[address] == null) {
          memoryMap[address] = unconnectedMemory;
        }
      }
      
      // Read/Write Control Logic
      // During a write operation, UD3 converts parallel data into serial data. The output on pin 9 is input to 'NAND' gate
      // UF5 pin 4. UF5 outputs the serial data on pin 6 at the clock rate determined by input signal on pin 5. The output
      // clocks the D flip flop UF6. The outputs of UF6, Q and _Q, drive the write amplifiers.
      // During a read operation, data from the read amplifiers is applied to the CLR input of counter
      // UF4. The outputs, C and D, are shaped by the 'NOR' gate UE5. UE5 outputs the serial data on
      // pin 1, then it is converted to parallel data by UD2. The output of UD2 is latched by UC3. The
      // serial bits are counted by UE4, when 8 bits have been counted, UF3 pin 12 goes "low", UC1
      // pin 10 goes "high", and UF3 pin 8 goes "low" indicating a byte is ready to be read by the
      // processor. UC2 monitors the parallel output of UD2, when all 8 bits are "1", the output pin 9
      // goes "low" indicating a sync bit has been read. 
    }};
  }
  
  public Via6522 createVia1() {
    return new Via6522(false) {
      /**
       * Returns the current values of the Port B pins.
       *
       * @return the current values of the Port B pins.
       */
      public int getPortBPins() {
        int value = 0;
        
        // PB0: Data IN
        // PB1: Data OUT
        // PB2: Clk IN
        // PB3: Clk OUT
        // PB4: Atn ACK
        // PB5: Switch to GND (device address)
        // PB6: Switch to GND
        // PB7: Atn IN
        
        return value;
      }
      
      /**
       * @return the ca1
       */
      public int getCa1() {
        // CA1: Atn IN
        return ca1;
      }
      
      
      /**
       * Notifies the 6502 of the change in the state of the IRQ pin. In this case 
       * it is the 6502's IRQ pin that this 6522 IRQ is connected to.
       * 
       * @param The current state of this VIA chip's IRQ pin (1 or 0).
       */
      protected void updateIrqPin(int pinState) {
        if (pinState == 1) {
          cpu.setInterrupt(Cpu6502.S_IRQ);
        } else {
          cpu.clearInterrupt(Cpu6502.S_IRQ);
        }
      }
    };
  }
  
  private int currentTrack;            // Currently selected track
  private Sector currentSector;        // Pointers to the current sector in the disk image being used by an active read or write operation
  private int currentSectorOffset;     // Current offset into the above sector
  
  /**
   * Reads a single byte from the current disk position.
   * 
   * @return
   */
  private int readByteFromDisk() {
    if (currentSectorOffset == -1) return 0;
    // TODO: Check when this needs to increment position.
    return currentSector.read(currentSectorOffset);
  }
  
  /**
   * Creates the instance of Via6522 that handles the Microprocessor R/W and Motor Control Logic.
   * 
   * @return
   */
  public Via6522 createVia2() {
    return new Via6522(false) {

      /**
       * Returns the current values of the Port A pins.
       *
       * @return the current values of the Port A pins.
       */
      public int getPortAPins() {
        
        // Read byte from disk.
        
        // Goes to PLA parallel port. 
        
        return readByteFromDisk();
      }
      
      /**
       * Returns the current values of the Port B pins.
       *
       * @return the current values of the Port B pins.
       */
      public int getPortBPins() {
        // PB0: Step 1 (out)
        // PB1: Step 0 (out)
        // PB2: Motor (out)
        // PB3: Activity LED (out)
        // PB4: Write Protect (in)
        // PB5: DS0 (out)
        // PB6: DS1 (out)
        // PB7: Sync (in)
        
        return 0;
      }
      
      // TODO: Byte Ready is connected to CA1, and to 6502 Set Overflow pin, which Cpu6502 doesn't currently emulate.
      
      // TODO: CA2 is SOE
      
      /**
       * Notifies the 6502 of the change in the state of the IRQ pin. In this case 
       * it is the 6502's IRQ pin that this 6522 IRQ is connected to.
       * 
       * @param The current state of this VIA chip's IRQ pin (1 or 0).
       */
      protected void updateIrqPin(int pinState) {
        if (pinState == 1) {
          cpu.setInterrupt(Cpu6502.S_IRQ);
        } else {
          cpu.clearInterrupt(Cpu6502.S_IRQ);
        }
      }
    };
  }
  
  /**
   * 
   * 
   * 
   */
  public class GcrDiskImage {
    
    //    Track   Sectors/track   # Sectors   Storage in Bytes
    //    -----   -------------   ---------   ----------------
    //     1-17        21            357           7820
    //    18-24        19            133           7170
    //    25-30        18            108           6300
    //    31-40(*)     17             85           6020
    //                               ---
    //                               683 (for a 35 track image)
    //
    //Track #Sect #SectorsIn D64 Offset   Track #Sect #SectorsIn D64 Offset
    //----- ----- ---------- ----------   ----- ----- ---------- ----------
    //1     21       0       $00000      21     19     414       $19E00
    //2     21      21       $01500      22     19     433       $1B100
    //3     21      42       $02A00      23     19     452       $1C400
    //4     21      63       $03F00      24     19     471       $1D700
    //5     21      84       $05400      25     18     490       $1EA00
    //6     21     105       $06900      26     18     508       $1FC00
    //7     21     126       $07E00      27     18     526       $20E00
    //8     21     147       $09300      28     18     544       $22000
    //9     21     168       $0A800      29     18     562       $23200
    //10     21     189       $0BD00      30     18     580       $24400
    //11     21     210       $0D200      31     17     598       $25600
    //12     21     231       $0E700      32     17     615       $26700
    //13     21     252       $0FC00      33     17     632       $27800
    //14     21     273       $11100      34     17     649       $28900
    //15     21     294       $12600      35     17     666       $29A00
    //16     21     315       $13B00      36(*)  17     683       $2AB00
    //17     21     336       $15000      37(*)  17     700       $2BC00
    //18     19     357       $16500      38(*)  17     717       $2CD00
    //19     19     376       $17800      39(*)  17     734       $2DE00
    //20     19     395       $18B00      40(*)  17     751       $2EF00
    //
    //(*)Tracks 36-40 apply to 40-track images only
    
    // Unlike MFM disks, the custom CBM CGR format has a variable number of sectors per track.
    private final int[][] TRACK_OFFSETS = new int[][] {
      { 21,   0,  0x00000 },
      { 21,  21,  0x01500 }, 
      { 21,  42,  0x02A00 },
      { 21,  63,  0x03F00 },
      { 21,  84,  0x05400 },
      { 21, 105,  0x06900 },
      { 21, 126,  0x07E00 },
      { 21, 147,  0x09300 },
      { 21, 168,  0x0A800 },
      { 21, 189,  0x0BD00 },
      { 21, 210,  0x0D200 },
      { 21, 231,  0x0E700 },
      { 21, 252,  0x0FC00 },
      { 21, 273,  0x11100 },
      { 21, 294,  0x12600 },
      { 21, 315,  0x13B00 },
      { 21, 336,  0x15000 },
      { 19, 357,  0x16500 },
      { 19, 376,  0x17800 },
      { 19, 395,  0x18B00 },
      { 19, 414,  0x19E00 },
      { 19, 433,  0x1B100 },
      { 19, 452,  0x1C400 },
      { 19, 471,  0x1D700 },
      { 18, 490,  0x1EA00 },
      { 18, 508,  0x1FC00 },
      { 18, 526,  0x20E00 },
      { 18, 544,  0x22000 },
      { 18, 562,  0x23200 },
      { 18, 580,  0x24400 },
      { 17, 598,  0x25600 },
      { 17, 615,  0x26700 },
      { 17, 632,  0x27800 },
      { 17, 649,  0x28900 },
      { 17, 666,  0x29A00 },
      { 17, 683,  0x2AB00 },
      { 17, 700,  0x2BC00 },
      { 17, 717,  0x2CD00 },
      { 17, 734,  0x2DE00 },
      { 17, 751,  0x2EF00 }
    };
    
    // GCR_SECTOR_SIZE => 354
    private final int GCR_SECTOR_SIZE = 1 + 10 + 9 + 1 + 325 + 8;
    
    // GCR conversion table - used for converting ordinary byte to 10-bits
    // (or 4 bits to 5)
    private final int[] GCR = new int[] {
      0x0a, 0x0b, 0x12, 0x13,
      0x0e, 0x0f, 0x16, 0x17,
      0x09, 0x19, 0x1a, 0x1b,
      0x0d, 0x1d, 0x1e, 0x15
    };

    // 5 bits > 4 bits (0xff => invalid)
    private final int[] GCR_REV = new int[] {
      0xff, 0xff, 0xff, 0xff, // 0 - 3invalid...
      0xff, 0xff, 0xff, 0xff, // 4 - 7 invalid...
      0xff, 0x08, 0x00, 0x01, // 8 invalid... 9 = 8, a = 0, b = 1
      0xff, 0x0c, 0x04, 0x05, // c invalid... d = c, e = 4, f = 5

      0xff, 0xff, 0x02, 0x03, // 10-11 invalid...
      0xff, 0x0f, 0x06, 0x07, // 14 invalid...
      0xff, 0x09, 0x0a, 0x0b, // 18 invalid...
      0xff, 0x0d, 0x0e, 0xff, // 1c, 1f invalid...
    };
    
    private int numOfTracks;           // Number of tracks per side
    private int[] rawImage;            // The raw disk image file loaded into memory
    private String diskImageName;
    private Sector[][] allTracks;
    
    /**
     * 
     * @param diskImageName
     * @param rawImage
     */
    public GcrDiskImage(String diskImageName, byte[] rawImage) {
      // Read in the full disk image data if it wasn't provided.
      if (rawImage == null) {
        rawImage = Gdx.files.internal("disks/" + diskImageName).readBytes();
      }
    
      this.rawImage = convertByteArrayToIntArray(rawImage);
      
      this.numOfTracks = 35;   // TODO: 40 track images.
      this.diskImageName = diskImageName;
      
      // Load all tracks.
      allTracks = new Sector[numOfTracks][];
      for (int track=0; track < numOfTracks; track++) {
        allTracks[track] = loadTrack(track); 
      }
    }
    
    /**
     * Loads a full track of sectors from the identified track number.
     * 
     * @param track The track number of the track to load.
     * 
     * @return Array of Sectors for the track that was loaded.
     */
    private Sector[] loadTrack(int track) {
      int numOfSectors = TRACK_OFFSETS[track][0];
      Sector[] sectors = new Sector[numOfSectors + 1];
      int trackStart = TRACK_OFFSETS[track][2];

      for (int sectorNum=0; sectorNum<numOfSectors; sectorNum++) {
        Sector sector = new Sector();
        sector.trackNum = track;
        sector.sectorNum = sectorNum;
        sector.sectorSize = 256;
        sector.dataOffset = trackStart + (sectorNum * 256);
        sectors[sector.sectorNum] = sector; 
      }
      
      return sectors;
    }
    
    /**
     * Converts a byte array into an int array.
     * 
     * @param data The byte array to convert.
     * 
     * @return The int array.
     */
    private int[] convertByteArrayToIntArray(byte[] data) {
      int[] convertedData = new int[data.length];
      for (int i=0; i<data.length; i++) {
        convertedData[i] = ((int)data[i]) & 0xFF;
      }
      return convertedData;
    }
    
    private long getGCR(int b) {
      return (GCR[b >> 4] << 5) | GCR[b & 15];
    }
    
    public int makeGCR(int[] gcrBuf, int pos, int b1, int b2, int b3, int b4) {
      int cSum = b1 ^ b2 ^ b3 ^ b4;
      long gcr = (getGCR(b1) << 30) | (getGCR(b2) << 20) | (getGCR(b3) << 10) | getGCR(b4);
      long bits = 32;
      for (int i = 0, n = 5; i < n; i++) {
        gcrBuf[pos++] = (int) ((gcr >> bits) & 0xff);
        bits = bits - 8;
      }
      return cSum;
    }
    
    /**
     * This class represents a Sector within the GCR disk image. It stores details such as the
     * sector ID, offset of the data for the sector, and the track and side of the disk where 
     * the sector resides. It also provides methods for reading and writing to/from a specified
     * sector position.
     */
    public class Sector {
      int idOffset;      // Absolute sector number. Not really required, but interesting for debug.
      int trackNum;      // This is the track that the sector is on.
      int sectorNum;     // This is the sector number within the track.
      int sectorSize;    // Should be the same for every sector on the disk.
      int dataOffset;    // Offset to the start of the sector within the raw image data.
      
      public int read(int sectorPos) {
        int value = rawImage[dataOffset + sectorPos];
        return value;
      }
      
      public void write(int sectorPos, int data) {
        // TODO: This is just updating an array in memory. Need to add writing back to disk at some point.
        rawImage[dataOffset + sectorPos] = (byte)data;
      }
      
      public String toString() {
        return String.format("Sector - track#: %d, sector#: %d, size: %d, idOffset: %d, dataOffset: %d", trackNum, sectorNum, sectorSize, idOffset, dataOffset);
      }
    }
  }
}
