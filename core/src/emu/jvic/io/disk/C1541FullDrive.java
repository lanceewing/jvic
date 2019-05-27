package emu.jvic.io.disk;

import com.badlogic.gdx.Gdx;

import emu.jvic.cpu.Cpu6502;
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
}
