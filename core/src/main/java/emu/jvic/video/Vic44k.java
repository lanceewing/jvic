package emu.jvic.video;

import emu.jvic.MachineType;
import emu.jvic.PixelData;
import emu.jvic.snap.Snapshot;

public class Vic44k extends Vic44 {
    
    private static final int VIC44_COLOUR_RAM_BASE_ADDRESS = 0xB800;
    
    /**
     * Constructor for Vic4k.
     * 
     * @param pixelData Interface to the platform specific mechanism for writing pixels.
     * @param machineType The type of machine, PAL or NTSC.
     * @param snapshot    Optional snapshot of the machine state to start with.
     */
    public Vic44k(PixelData pixelData, MachineType machineType, Snapshot snapshot) {
        super(pixelData, machineType, snapshot, VIC44_COLOUR_RAM_BASE_ADDRESS);
        
        // NOTE: VIC chip vs VIC 20 memory map is different. This is why we have
        // the control registers appearing at $1000. The Chip Select for reading
        // and writing to the VIC chip registers is when A13=A11=A10=A9=A8=0 and 
        // A12=1, i.e. $10XX. Bottom 4 bits select one of the 16 registers.
        //
        // VIC chip addresses     VIC 20 addresses and their normal usage
        //
        // $0000                  $8000  1K RAM
        // $0400                  $8400  1K RAM
        // $0800                  $8800  1K RAM
        // $0C00                  $8C00  1K RAM
        // $1000                  $9000  1K RAM
        // $1400                  $9400  1K RAM
        // $1800                  $9800  1K RAM
        // $1C00                  $9C00  1K RAM
        //
        // $2000                  $A000  1K RAM
        // $2400                  $A400  1K RAM
        // $2800                  $A800  1K RAM
        // $2C00                  $AC00  1K RAM
        // $3000                  $B000  1K RAM
        // $3400                  $B400  Default Screen memory
        // $3800                  $B800  Colour RAM (fixed location)
        // $3C00                  $BC00  VIC and VIA chips
        
        for (int i=0; i<0x4000; i++) {
            VIC_MEM_TABLE[i] = 0x8000 + i;
        }
    }
}
