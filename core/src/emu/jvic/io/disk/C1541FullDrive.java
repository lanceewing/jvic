package emu.jvic.io.disk;

import com.badlogic.gdx.Gdx;

import emu.jvic.cpu.Cpu6502;
import emu.jvic.io.SerialBus;
import emu.jvic.io.Via6522;
import emu.jvic.io.disk.GcrDiskImage.Sector;
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
  
  private GcrDiskImage disk;
  
  // This is mainly for debugging purposes. No strict need to emulate an LED.
  private boolean ledOn;
  
  private boolean motorOn;
  
  private int currentTrack = 1;        // Currently selected track
  private Sector currentSector;        // Pointers to the current sector in the disk image being used by an active read or write operation
  private int currentSectorOffset;     // Current offset into the above sector
  private int currentTrackSize = 21;
  
  /** 
   * The drive's stepper motor can stop at 84 different locations (tracks) on a disk. However, the 
   * read/write head on the drive is too wide to use each one separately, so every other track is 
   * skipped for a total of 42 theoretical tracks. The common terminology for the step in between 
   * each track is a "half-track" and a specific track would be referred to as (for example) "35.5" 
   * instead of the actual track (which would be 71). Commodore limited use to only the first 35 
   * tracks in their standard DOS, but commercial software isn't limited by this.
   */
  private int currentHalfTrack = 2;

  private int bytesWritten = 0;
  private int currentByte = 0;

  // CPU Switches to write mode by ANDing the contents of the 6522's peripheral control
  // register (PCR) with $1F, ORing the result with $C0, and storing the final
  // result back in the PCR (i.e. CB2 Manual output LOW mode)
  //
  //  Once all the data bytes have been written, CPU switches to read mode by ORing
  // the contents of the 6522's peripheral control register (PCR) with $E0 and
  // storing the result back in the PCR. (i.e. CB2 Manual output HIGH mode)
  private boolean diskModeWrite = false;

  private boolean lastSync = false;
  
  private boolean byteReady = false;
  
  /**
   * The cycle count at which we will move the head forward one byte.
   */
  private long nextMoveForward;
  
  /**
   * Number of cycles since this 1541 Drive started emulating.
   */
  private long totalElapsedCycles;
  
  /**
   * The SerialBus that the 1541 disk drive is connected to.
   */
  private SerialBus serialBus;
  
  /**
   * Constructor for C1541FullDrive.
   * 
   * @param serialBus The SerialBus that the 1541 disk drive is connected to.
   */
  public C1541FullDrive(SerialBus serialBus) {
    cpu = new Cpu6502(null);
    via1 = createVia1();
    via2 = createVia2();
    createMemory(cpu, via1, via2);
    this.serialBus = serialBus;
  }
  
  /**
   * Acts as if a new disk has been inserted, using the given diskData byte array
   * for the raw .d64 disk image.
   * 
   * @param diskData The draw .d64 disk image data to insert.
   */
  public void insertDisk(byte[] diskData) {
    disk = new GcrDiskImage(diskData);
	  
    currentTrack = 1;
    currentHalfTrack = 2;
    currentSectorOffset = -1;
    currentTrackSize = disk.getSectorCount(currentTrack);
    currentSector = disk.getSector(currentTrack, 0);
  }
  
  /**
   * Emulates a single cycle of the 1541 disk drive.
   */
  public void emulateCycle() {
    // CB2 of VIA#2 is used in Manual output mode and determines disk R/W mode (0 = W, 1 = R)
    diskModeWrite = (via2.getCb2() == 0);
    if (!diskModeWrite) currentByte = -1;
    
    // "Set Overflow" patch. Always fake a 'byte ready' for fast read.
    if (byteReady && (via2.getCa2() == 1)) {  // VIA2 CA2 is Set Overflow Enable (SEO)
      cpu.setOverflowFlag(true);
      byteReady = false;
    }
    
    cpu.emulateCycle();
    
    // Check if head should move forward one byte.
    checkForMoveForward();
    
    via1.emulateCycle();
    via2.emulateCycle();
    
    // Increment total cycle count.
    totalElapsedCycles++;
  }
  
  /**
   * Creates the memory of the 1541 disk drive.
   * 
   * @param cpu The 6502 CPU that runs the 1541 disk drive unit.
   * @param via1 The first 6522 VIA that handles the serial communication with the serial port.
   * @param via2 The second 6522 VIA that handles the CPU R/W and motor control logic.
   * 
   * @return The created Memory.
   */
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
      
      // TODO: ATNA connect to data line pin 5 is not emulated yet.
      
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

  /**
   * Flushes out any written data, if any. When bytes are written, only the GCR data is
   * updated. It is only when we change sector that these updates are decoded back to 
   * the raw data and updated in the .d64 disk image.
   */
  private void flushWrites() {
    if (bytesWritten > 0)  {  
      // TODO: Flush out written bytes, i.e. convert sector to raw unencoded data and set into disk image.
    }
    bytesWritten = 0;
  }
  
  /**
   * Manages the movement of the head to the next byte of data, by using the 
   * total number of elapsed cycles and a set "next" cycle count at which we will 
   * move forward again.
   */
  private void checkForMoveForward() {
    if (motorOn) {
      if (nextMoveForward < totalElapsedCycles) {
        if (nextMoveForward == 0) {
          nextMoveForward = totalElapsedCycles + 32;
        } else {
          // 30 seems to work (3 x 8 is fastest, 6x8 slowest)
          nextMoveForward += 30;
          moveForwardOneByte();
        }
      }
    } else {
      nextMoveForward = totalElapsedCycles + 10000;
    }
  }
  
  /**
   * Returns true if the current sector position is at a sync mark and we're reading.
   * 
   * @return true if the current sector position is at a sync mark and we're reading.
   */
  private boolean atSyncMark() {
    // Sync only applicable in read mode. Mode is included in the SYNC NAND gate inputs.
    return (!diskModeWrite && (currentSectorOffset >= 0) && (currentSector.read(currentSectorOffset) == 0xff));
  }
  
  /**
   * Rotate head forward to read next byte.
   */
  private void moveForwardOneByte() {
    // If we're in write mode, and a byte has been written, then update GCR sector buffer.
    if (diskModeWrite && (via2.getDataDirectionRegisterA() == 0xff) &&
        (currentByte != -1) && (currentSectorOffset >= 0)) {
      bytesWritten++;
      // Should this be written a byte "backwards"??
      currentSector.write(currentSectorOffset, currentByte);
    }
    
    // Move forward one byte.
    currentSectorOffset++;
    
    // Check if reached end of current sector.
    if (currentSectorOffset == GcrDiskImage.GCR_SECTOR_SIZE) {
      // If we wrote bytes out in this sector, then flush them.
      flushWrites();
      // Read in the next sector.
      currentSectorOffset = -1;
      currentSector = disk.getSector(currentTrack, (currentSector.sectorNum + 1) % currentTrackSize);
      // Some extra cycles when switching sector.
      nextMoveForward += 1000;
    }

    // If not sync now or last time...
    if (diskModeWrite || !atSyncMark() || !lastSync) {
      // Only trigger byte ready when not at sync bytes.
      byteReady = true;
    }
    
    // TODO: Note sure why we need to check the last byte. Check this carefully.
    lastSync = atSyncMark();
  }

  /**
   * Moves the head out one half track.
   */
  private void moveHeadOut() {
    if (currentHalfTrack > 2) currentHalfTrack--;
    updateCurrentTrack();
  }

  /**
   * Moves the head in one half track.
   */
  private void moveHeadIn() {
    // TODO: This only handles 35 track disks.
    if (currentHalfTrack < 70) currentHalfTrack++;
    updateCurrentTrack();
  }
  
  /**
   * Updates the current track based on half track value.
   */
  private void updateCurrentTrack() {
    if (currentTrack != (currentHalfTrack >> 1)) {
      // Takes some time before ready for next...
      nextMoveForward = totalElapsedCycles + 100000;

      flushWrites();
      
      currentTrack = (currentHalfTrack >> 1);
      currentSectorOffset = -1;
      currentTrackSize = disk.getSectorCount(currentTrack);
      currentSector = disk.getSector(currentTrack, 0);
    }
  }
  
  /**
   * Reads a single byte from the current disk position.
   * 
   * @return The byte read from disk.
   */
  private int readByteFromDisk() {
    if (currentSectorOffset == -1) return 0;
    return currentSector.read(currentSectorOffset);
  }
  
  /**
   * Creates the Via6522 that handles the Serial Communication.
   * 
   * @return
   */
  public Via6522 createVia1() {
    return new Via6522(false) {
      
      /**
       * Returns the current values of the Port B pins.
       *
       * @return the current values of the Port B pins.
       */
      public int getPortBPins() {
        // PB0: Data IN
        // PB1: Data OUT
        // PB2: Clk IN
        // PB3: Clk OUT
        // PB4: Atn ACK (out)
        // PB5: Switch to GND (device address) (in)
        // PB6: Switch to GND (in)
        // PB7: Atn IN
        
        // Work out current read state of the serial bus.
        int value = ((super.getPortBPins() & 0x1A) |
             (serialBus.getAtn()? 0x80 : 0x00) | 
             (serialBus.getData()? 0x01 : 0x00) | 
             (serialBus.getClock()? 0x04 : 0x00));
        
        return value;
      }
      
      /**
       * Invoked whenever the ORB or DDRB register of the VIA is written to.
       */
      protected void updatePortBPins() {
        // Refresh the state of Port B pins based on ORB, DDRB, and current port B pin state.
        super.updatePortBPins();
        
        // Now used refreshed portBPins to update serial bus.
        if ((portBPins & 0x08) == 0x08) {
          serialBus.pullDownClock(this);
        } else {
          serialBus.releaseClock(this);
        }
        if ((portBPins & 0x02) == 0x02) {
          serialBus.pullDownData(this);
        } else {
          serialBus.releaseData(this);
        }
        // Note: 1541 shouldn't touch ATN, so nothing to do for ATN.
      }
      
      /**
       * CA1 of this VIA is connected to the ATN line of the serial bus.
       */
      public int getCa1() {
        return (serialBus.getAtn()? 1 : 0);
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
        // Read byte from disk. In the real machine, goes to PLA parallel port .
        return readByteFromDisk();
      }
      
      /**
       * Returns the current values of the Port B pins.
       *
       * @return the current values of the Port B pins.
       */
      public int getPortBPins() {
        // Only Sync and Write Protect are inputs.
        // PB4: Write Protect (in)
        // PB7: Sync (in) - LOW means it is at sync mark.
        
        // NOTE: The Sync mark is a special identifying mark that is used to know where to begin
        // reading or writing bits. It is a string of at least 10 1s in a row. The GCR code is 
        // designed such that the actual data will produce no more than 8 1s in a row, making the
        // sync mark distinctive. The DOS actually writes 5 0xFF bytes for a sync mark, and 
        // they're aligned
        
        // TODO: Write protection logic. Seems low on the priority list at the moment.
        
        int value = ((super.getPortBPins() & 0x6F) | (atSyncMark()? 0x00 : 0x80));
    	  
        return value;
      }
      
      /**
       * Invoked whenever the ORA or DDRA register of the VIA is written to.
       */
      protected void updatePortAPins() {
        super.updatePortAPins();
        // Set disk write byte to current Port A pins state.
        currentByte = portAPins;
      }
      
      /**
       * Invoked whenever the ORB or DDRB register of the VIA is written to.
       */
      protected void updatePortBPins() {
        // Six output pins. Only 4 are of interest to us.
        // PB0: Step 1 (out)
        // PB1: Step 0 (out)
        // PB2: Motor (out)
        // PB3: Activity LED (out)
        // PB5: DS0 (out) - Not emulated.
        // PB6: DS1 (out) - Not emulated.

        int oldPortBPins = portBPins;
        
        // Refresh the state of Port B pins based on ORB, DDRB, and current port B pin state.
        super.updatePortBPins();
        
        ledOn = ((portBPins & 0x08) != 0);
        motorOn = ((portBPins & 0x04) != 0);

        // If step motor value changed...
        if (((oldPortBPins ^ portBPins) & 0x3) != 0) {
          // ...check if it is out or in.
          if ((oldPortBPins & 0x3) == ((portBPins + 1) & 0x3)) {
            // Moving out one half track.
            moveHeadOut();
          } else if ((oldPortBPins & 0x3) == ((portBPins - 1) & 0x3)) {
            // Moving in one half track.
            moveHeadIn();
          }
        }
      }
      
      /**
       * CA1 of this VIA is connected to the Byte Ready. Apparently 1541 doesn't make use of 
       * this though. Emulate it anyway.
       */
      public int getCa1() {
        // Byte Ready is active LOW. CA2 (SEO) enables Byte Ready.
        return ((byteReady && (getCa2() == 1))? 0 : 1);
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
}
