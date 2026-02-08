package emu.jvic.video;

import emu.jvic.MachineType;
import emu.jvic.PixelData;
import emu.jvic.snap.Snapshot;

public class Vic44k extends Vic44 {
    
    private static final int VIC44K_COLOUR_RAM_BASE_ADDRESS = 0xB800;
    private static final int VIC44K_REGISTER_BASE_ADDRESS = 0xBC00;
    
    /**
     * Constructor for Vic4k.
     * 
     * @param pixelData Interface to the platform specific mechanism for writing pixels.
     * @param machineType The type of machine, PAL or NTSC.
     * @param snapshot    Optional snapshot of the machine state to start with.
     */
    public Vic44k(PixelData pixelData, MachineType machineType, Snapshot snapshot) {
        super(pixelData, machineType, snapshot, VIC44K_COLOUR_RAM_BASE_ADDRESS);
        
        // VIC chip addresses     VIC 20 addresses and their normal usage
        //
        // $0000                  $A000  1K RAM [Char ROM during VIC cycle]
        // $0400                  $A400  1K RAM [Char ROM during VIC cycle]
        // $0800                  $A800  1K RAM [Char ROM during VIC cycle]
        // $0C00                  $AC00  1K RAM [Char ROM during VIC cycle]
        // $1000                  $B000  1K RAM
        // $1400                  $B400  1K RAM Default Screen memory
        // $1800                  $B800  Colour RAM (fixed location)
        // $1C00                  $BC00  VIC and VIA chips
        //
        // $2000                  $8000  1K RAM
        // $2400                  $8400  1K RAM
        // $2800                  $8800  1K RAM
        // $2C00                  $8C00  1K RAM
        // $3000                  $9000  1K RAM
        // $3400                  $9400  1K RAM
        // $3800                  $9800  1K RAM
        // $3C00                  $9C00  1K RAM
        
        for (int i=0; i<0x2000; i++) {
            VIC_MEM_TABLE[i] = 0xA000 + i;
        }
        for (int i=0x2000; i<0x4000; i++) {
            VIC_MEM_TABLE[i] = 0x8000 + i;
        }
    }
    
    protected void initRegNumbers() {
        initRegNumbers(VIC44K_REGISTER_BASE_ADDRESS);
    }
}
