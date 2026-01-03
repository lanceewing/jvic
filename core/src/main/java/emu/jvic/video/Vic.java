package emu.jvic.video;

import emu.jvic.MachineType;
import emu.jvic.PixelData;
import emu.jvic.memory.MemoryMappedChip;
import emu.jvic.snap.Snapshot;

/**
 * This class emulates the VIC chip. The emulation is very similar to the PIVIC firmware code,
 * which I wrote most of the initial release of, in fact I quite often used this JVic emulator
 * as a place to test out bits of the code, so the PIVIC and JVic emulation of the VIC chip 
 * evolved together.
 * 
 * @author Lance Ewing
 */
public abstract class Vic extends MemoryMappedChip {
    
    // NOTE: VIC chip vs VIC 20 memory map is different. This is why we have
    // the control registers appearing at $1000. The Chip Select for reading
    // and writing to the VIC chip registers is when A13=A11=A10=A9=A8=0 and 
    // A12=1, i.e. $10XX. Bottom 4 bits select one of the 16 registers.
    //
    // VIC chip addresses     VIC 20 addresses and their normal usage
    //
    // $0000                  $8000  Unreversed Character ROM
    // $0400                  $8400  Reversed Character ROM
    // $0800                  $8800  Unreversed upper/lower case ROM
    // $0C00                  $8C00  Reversed upper/lower case ROM
    // $1000                  $9000  VIC and VIA chips
    // $1400                  $9400  Colour memory (at either $9400 or $9600)
    // $1800                  $9800  Reserved for expansion (I/O #2)
    // $1C00                  $9C00  Reserved for expansion (I/O #3)
    // $2000                  $0000  System memory work area
    // $2400                  $0400  Reserved for 1st 1K of 3K expansion
    // $2800                  $0800  Reserved for 2nd 1K of 3K expansion
    // $2C00                  $0C00  Reserved for 3rd 1K of 3K expansion
    // $3000                  $1000  BASIC program area / Screen when using 8K+ exp
    // $3400                  $1400  BASIC program area
    // $3800                  $1800  BASIC program area
    // $3C00                  $1C00  BASIC program area / $1E00 screen mem for unexp VIC
    
    /**
     * A lookup table to map between the VIC chip's memory addresses and VIC 20 memory map.
     */
    protected final static int[] VIC_MEM_TABLE = new int[0x4000];
    {
        for (int i=0; i<0x2000; i++) {
            VIC_MEM_TABLE[i] = 0x8000 + i;
        }
        for (int i=0x2000; i<0x4000; i++) {
            VIC_MEM_TABLE[i] = i - 0x2000;
        }
    }
            
    // VIC chip memory mapped registers.
    protected static final int VIC_REG_0 = 0x9000;   // ABBBBBBB A=Interlace B=Screen Origin X (4 pixels granularity)
    protected static final int VIC_REG_1 = 0x9001;   // CCCCCCCC C=Screen Origin Y (2 pixel granularity)
    protected static final int VIC_REG_2 = 0x9002;   // HDDDDDDD D=Number of Columns
    protected static final int VIC_REG_3 = 0x9003;   // GEEEEEEF E=Number of Rows F=Double Size Chars
    protected static final int VIC_REG_4 = 0x9004;   // GGGGGGGG G=Raster Line
    protected static final int VIC_REG_5 = 0x9005;   // HHHHIIII H=Screen Mem Addr I=Char Mem Addr
    protected static final int VIC_REG_6 = 0x9006;   // JJJJJJJJ Light pen X
    protected static final int VIC_REG_7 = 0x9007;   // KKKKKKKK Light pen Y
    protected static final int VIC_REG_8 = 0x9008;   // LLLLLLLL Paddle X
    protected static final int VIC_REG_9 = 0x9009;   // MMMMMMMM Paddle Y
    protected static final int VIC_REG_10 = 0x900A;  // NRRRRRRR Sound voice 1
    protected static final int VIC_REG_11 = 0x900B;  // OSSSSSSS Sound voice 2
    protected static final int VIC_REG_12 = 0x900C;  // PTTTTTTT Sound voice 3
    protected static final int VIC_REG_13 = 0x900D;  // QUUUUUUU Noise voice
    protected static final int VIC_REG_14 = 0x900E;  // WWWWVVVV W=Auxiliary colour V=Volume control
    protected static final int VIC_REG_15 = 0x900F;  // XXXXYZZZ X=Background colour Y=Reverse Z=Border colour

    // Constants for the fetch state of the vic_core1_loop.
    protected static final int FETCH_OUTSIDE_MATRIX = 0;
    protected static final int FETCH_IN_MATRIX_Y = 1;
    protected static final int FETCH_IN_MATRIX_X = 2;
    protected static final int FETCH_MATRIX_LINE = 3;
    protected static final int FETCH_MATRIX_DLY_1 = 4;
    protected static final int FETCH_MATRIX_DLY_2 = 5;
    protected static final int FETCH_MATRIX_DLY_3 = 6;
    protected static final int FETCH_SCREEN_CODE = 7;
    protected static final int FETCH_CHAR_DATA = 8;
    protected static final int FETCH_MATRIX_END = 9;
    
    // These are to make it easier to copy the PIVIC code in an out.
    protected static final int CVBS_PIO = 0;
    protected static final int CVBS_SM = 0;

    /**
     * Interface to the platform specific mechanism for writing pixels.
     */
    protected PixelData pixelData;
    
    /**
     * Pixel counter. Current offset into TV frame array.
     */
    protected int pixelCounter;
    
    /**
     * The type of machine that this Vic chip is in, i.e. either PAL or NTSC.
     */
    protected MachineType machineType;

    
    //
    // START OF VIC CHIP STATE
    //
    
    // Counters.
    protected int videoMatrixCounter;     // 12-bit video matrix counter (VMC)
    protected int videoMatrixLatch;       // 12-bit latch that VMC is stored to and loaded from
    protected int verticalCounter;        // 9-bit vertical counter (i.e. raster lines)
    protected int horizontalCounter;      // 8-bit horizontal counter (although top bit isn't used)
    protected int prevHorizontalCounter;  // 8-bit previous value of horizontal counter.
    protected int horizontalCellCounter;  // 8-bit horizontal cell counter (down counter)
    protected int verticalCellCounter;    // 6-bit vertical cell counter (down counter)
    protected int cellDepthCounter;       // 4-bit cell depth counter (counts either from 0-7, or 0-15)
    protected int halfLineCounter;        // 1-bit half-line counter
    
    // Values normally fetched externally, from screen mem, colour RAM and char mem.
    protected int cellIndex;              // 8 bits fetched from screen memory.
    protected int charData;               // 8 bits of bitmap data fetched from character memory.
    protected int charDataLatch;          // 8 bits of bitmap data fetched from character memory (latched)
    protected int colourData;             // 4 bits fetched from colour memory (top bit multi/hires mode)
    protected boolean hiresMode;

    // Palette index for the current border colour.
    protected int borderColour;
    
    // Holds the palette index for each of the current multi colour colours.
    protected int multiColourTable[] = new int[4];
    
    // Every cpu cycle, we output four pixels. The values are temporarily stored in these vars.
    protected int pixel1;
    protected int pixel2;
    protected int pixel3;
    protected int pixel4;
    protected int pixel5;
    protected int pixel6;
    protected int pixel7;
    protected int pixel8;
    
    // Optimisation to represent "in matrix", "address output enabled", and "pixel output enabled" 
    // all with one simple state variable. It might not be 100% accurate but should work for most 
    // cases.
    protected int fetchState = FETCH_OUTSIDE_MATRIX;
    
    //
    // END OF VIC CHIP STATE
    //
    
    
    // Temporary variables, not a core part of the state.
    protected int charDataOffset = 0;
    
    /**
     * Constructor for VIC.
     * 
     * @param pixelData Interface to the platform specific mechanism for writing pixels.
     * @param machineType The type of machine, PAL or NTSC.
     * @param snapshot    Optional snapshot of the machine state to start with.
     */
    public Vic(PixelData pixelData, MachineType machineType, Snapshot snapshot) {
        this.pixelData = pixelData;
        this.machineType = machineType;

        reset();

        if (snapshot != null) {
            loadSnapshot(snapshot);
        }
    }

    /**
     * Initialises the VIC chip with the state stored in the Snapshot.
     * 
     * @param snapshot The Snapshot to load the initial state of the VIC chip from.
     */
    private void loadSnapshot(Snapshot snapshot) {
        // TODO: Does this need to do anything now that we're checking memory on every cycle?
    }

    /**
     * Resets the VIC chip to an initial state.
     * 
     * @param machineType The type of VIC 20 machine that is being emulated.
     */
    public void reset() {
        prevHorizontalCounter = horizontalCounter = 0;
        verticalCounter = 0;
        pixelCounter = 0;
        horizontalCellCounter = 0;
        verticalCellCounter = 0;
        cellDepthCounter = 0;
        videoMatrixCounter = 0;
        videoMatrixLatch = 0;
        halfLineCounter = 0;
        cellIndex = 0;
        borderColour = 0;
        fetchState = FETCH_OUTSIDE_MATRIX;
        charDataOffset = 0;
        pixel1 = pixel2 = pixel3 = pixel4 = pixel5 = pixel6 = pixel7 = pixel8 = 1;
        hiresMode = false;
        colourData = 0x08;
        charData = charDataLatch = 0x55;
        multiColourTable[0] = 0;
        multiColourTable[1] = 0;
        multiColourTable[2] = 0;
        multiColourTable[3] = 0;
    }

    /**
     * Reads a value from VIC memory.
     * 
     * @param address The address to read from.
     * 
     * @return The byte at the specified address.
     */
    public int readMemory(int address) {
        int value = 0;

        // Handle all VIC chip memory address ranges, including undocumented ones.
        address = (address & 0xFF0F);

        switch (address) {
            case VIC_REG_0:
                value = mem[address];
                break;
    
            case VIC_REG_1:
                value = mem[address];
                break;
    
            case VIC_REG_2:
                value = mem[address];
                break;
    
            case VIC_REG_3:
                value = mem[address];
                break;
    
            case VIC_REG_4:
                value = mem[address];
                break;
    
            case VIC_REG_5:
                value = mem[address];
                break;
    
            case VIC_REG_6:
                value = mem[address];
                break;
    
            case VIC_REG_7:
                value = mem[address];
                break;
    
            case VIC_REG_8:
                value = mem[address];
                break;
    
            case VIC_REG_9:
                value = mem[address];
                break;
    
            case VIC_REG_10:
                value = mem[address];
                break;
    
            case VIC_REG_11:
                value = mem[address];
                break;
    
            case VIC_REG_12:
                value = mem[address];
                break;
    
            case VIC_REG_13:
                value = mem[address];
                break;
    
            case VIC_REG_14:
                value = mem[address];
                break;
    
            case VIC_REG_15:
                value = mem[address];
                break;
    
            default:
                value = charData & 0xFF;
        }

        memory.setLastBusData(value);
        
        return value;
    }

    /**
     * Writes a value to VIC memory.
     * 
     * @param address The address to write the value to.
     * @param value   The value to write into the address.
     */
    public void writeMemory(int address, int value) {
        memory.setLastBusData(value);
        
        // This is how the VIC chip is mapped, i.e. each register to multiple addresses.
        address = address & 0xFF0F;

        switch (address) {
            case VIC_REG_0: // $9000 Left margin, or horizontal origin (4 pixel granularity)
                mem[address] = value;
                break;
    
            case VIC_REG_1: // $9001 Top margin, or vertical origin (2 pixel granularity)
                mem[address] = value;
                break;
    
            case VIC_REG_2: // $9002 Video Matrix Columns, Video and colour memory
                mem[address] = value;
                break;
    
            case VIC_REG_3: // $9003 Video Matrix Rows, Character size
                mem[address] = value;
                break;
    
            case VIC_REG_4: // $9004 Raster line counter (READ ONLY)
                break;
    
            case VIC_REG_5: // $9005 Video matrix and char generator base address control
                mem[address] = value;
                break;
    
            case VIC_REG_6: // $9006 Light pen X (READ ONLY)
                break;
    
            case VIC_REG_7: // $9007 Light pen Y (READ ONLY)
                break;
    
            case VIC_REG_8: // $9008 Paddle X (READ ONLY)
                break;
    
            case VIC_REG_9: // $9009 Paddle Y (READ ONLY)
                break;
    
            case VIC_REG_10: // $900A Bass sound switch and frequency
                mem[address] = value;
                break;
    
            case VIC_REG_11: // $900B Alto sound switch and frequency
                mem[address] = value;
                break;
    
            case VIC_REG_12: // $900C Soprano sound switch and frequency
                mem[address] = value;
                break;
    
            case VIC_REG_13: // $900D Noise sound switch and frequency
                mem[address] = value;
                break;
    
            case VIC_REG_14: // $900E Auxiliary Colour, Master Volume
                mem[address] = value;
                break;
    
            case VIC_REG_15: // $900F Screen and Border Colours, Reverse Video
                mem[address] = value;
                break;
        }
    }

    protected void pio_sm_put(int pio, int sm, int pixel) {
        // TODO: Fix this check. Is it needed?
        if ((pixelCounter < 88608)) {
            if ((pixel & 0xFF) == 0xFF) {
                // If alpha channel is set to 0xFF, then its a normal colour.
                pixelData.putPixel(pixelCounter++, pixel);
            } else {
                // Otherwise it is command that happens during blanking, e.g. hsync, col burst,
                // front porch, back porch, etc., so we simply output transparent black.
                for (int i = 0; i < pixel; i++) {
                    pixelData.putPixel(pixelCounter++, 0);
                }
            }
        }
    }
    
    /**
     * Emulates a single cycle of the VIC chip.
     * 
     * @return true if a frame has just been completed.
     */
    public abstract boolean emulateCycle();
    
}
