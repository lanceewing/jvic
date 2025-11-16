package emu.jvic.video;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.AudioDevice;
import com.badlogic.gdx.utils.GdxRuntimeException;

import emu.jvic.MachineType;
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
public class Vic extends MemoryMappedChip {
    
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

    private static final int SAMPLE_RATE = 22050;
            
    // VIC chip memory mapped registers.
    private static final int VIC_REG_0 = 0x9000;   // ABBBBBBB A=Interlace B=Screen Origin X (4 pixels granularity)
    private static final int VIC_REG_1 = 0x9001;   // CCCCCCCC C=Screen Origin Y (2 pixel granularity)
    private static final int VIC_REG_2 = 0x9002;   // HDDDDDDD D=Number of Columns
    private static final int VIC_REG_3 = 0x9003;   // GEEEEEEF E=Number of Rows F=Double Size Chars
    private static final int VIC_REG_4 = 0x9004;   // GGGGGGGG G=Raster Line
    private static final int VIC_REG_5 = 0x9005;   // HHHHIIII H=Screen Mem Addr I=Char Mem Addr
    private static final int VIC_REG_6 = 0x9006;   // JJJJJJJJ Light pen X
    private static final int VIC_REG_7 = 0x9007;   // KKKKKKKK Light pen Y
    private static final int VIC_REG_8 = 0x9008;   // LLLLLLLL Paddle X
    private static final int VIC_REG_9 = 0x9009;   // MMMMMMMM Paddle Y
    private static final int VIC_REG_10 = 0x900A;  // NRRRRRRR Sound voice 1
    private static final int VIC_REG_11 = 0x900B;  // OSSSSSSS Sound voice 2
    private static final int VIC_REG_12 = 0x900C;  // PTTTTTTT Sound voice 3
    private static final int VIC_REG_13 = 0x900D;  // QUUUUUUU Noise voice
    private static final int VIC_REG_14 = 0x900E;  // WWWWVVVV W=Auxiliary colour V=Volume control
    private static final int VIC_REG_15 = 0x900F;  // XXXXYZZZ X=Background colour Y=Reverse Z=Border colour

    // Constants for the fetch state of the vic_core1_loop.
    private static final int FETCH_OUTSIDE_MATRIX = 0;
    private static final int FETCH_IN_MATRIX_Y = 1;
    private static final int FETCH_IN_MATRIX_X = 2;
    private static final int FETCH_MATRIX_LINE = 3;
    private static final int FETCH_MATRIX_DLY_1 = 4;
    private static final int FETCH_MATRIX_DLY_2 = 5;
    private static final int FETCH_MATRIX_DLY_3 = 6;
    private static final int FETCH_SCREEN_CODE = 7;
    private static final int FETCH_CHAR_DATA = 8;
    
    // Constants related to video timing for PAL.
    private static final int PAL_HBLANK_END = 12;
    private static final int PAL_HBLANK_START = 70;
    private static final int PAL_VBLANK_START = 1;
    private static final int PAL_VSYNC_START = 4;
    private static final int PAL_VSYNC_END = 6;
    private static final int PAL_VBLANK_END = 9;
    private static final int PAL_LAST_LINE = 311;

    // Constants related to video timing for NTSC.
    private static final int NTSC_HBLANK_END = 9;
    private static final int NTSC_HBLANK_START = 59;
    private static final int NTSC_LINE_END = 64;
    private static final int NTSC_VBLANK_START = 1;
    private static final int NTSC_VSYNC_START = 4;
    private static final int NTSC_VSYNC_END = 6;
    private static final int NTSC_VBLANK_END = 9;
    private static final int NTSC_NORM_LAST_LINE = 261;
    private static final int NTSC_INTL_LAST_LINE = 262;

    // PAL TIMING:
    //
    // Horiz Blanking - From: 70.915 To: 12.75 Len: 12.835 cycles (51.33 pixels)
    //
    // Made up of the following:
    //
    // Front Porch - From: 70.915 To: 1.75 Len: 1.835 cycles (7.33 pixels)
    // Horiz Sync - From: 1.75 To: 6.75 Len: 5 cycles (20 pixels)
    // Breezeway - From: 6.75 To: 7.5 Len: 0.75 cycles (3 pixels)
    // Colour Burst - From: 7.5 To: 11.75 Len: 4.25 cycles (17 pixels)
    // Back Porch - From: 11.75 To: 12.75 Len: 1 cycle (4 pixels)
    //
    // Visible pixels - From 12.75 to 70.915 Len: 58.165 cycles (232.66 pixels, i.e. approx. 233)
    
    private static final int PAL_FRONTPORCH_1 = (4 << 16);      // From 0 to 10 
    private static final int PAL_FRONTPORCH_2 = (3 << 16);      // From 1 to 1.75
    private static final int PAL_HSYNC = (20 << 16);            // From 1.75 to 6.75
    private static final int PAL_BREEZEWAY = (3 << 16);         // From 6.75 to 7.5
    private static final int PAL_COLBURST_O = (17 << 16);       // From 7.5 to 11.75
    private static final int PAL_COLBURST_E = (17 << 16);       // From 7.5 to 11.75
    private static final int PAL_BACKPORCH = (4 << 16);         // From 11.75 to 12.75
    
    // Vertical blanking and sync.
    private static final int PAL_LONG_SYNC_L = (133 << 16);
    private static final int PAL_LONG_SYNC_H = (9 << 16);
    private static final int PAL_SHORT_SYNC_L = (9 << 16);
    private static final int PAL_SHORT_SYNC_H = (133 << 16);

    // Front porch split into two, to allow for change to vertical blanking after
    // first part, if required.
    private static final int NTSC_FRONTPORCH_1 = (12 << 16);
    private static final int NTSC_FRONTPORCH_2 = (4 << 16);
    private static final int NTSC_HSYNC = (20 << 16);
    private static final int NTSC_BREEZEWAY = (2 << 16);
    private static final int NTSC_BACKPORCH = (2 << 16);
    private static final int NTSC_LONG_SYNC_L = (110 << 16);
    private static final int NTSC_LONG_SYNC_H = (20 << 16);
    private static final int NTSC_SHORT_SYNC_L = (20 << 16);
    private static final int NTSC_SHORT_SYNC_H = (110 << 16);
    private static final int NTSC_BLANKING = (200 << 16);

    // Two NTSC burst methods available CMD_BURST or PIXEL_RUN CMD_BURST is more 
    // experimental since it runs carrier cycles instead of dot clock cycles
    // 17 carrier cycles => 19.5 dot clock cycles, syncs up as 20 dot clock cycles
    private static final int NTSC_BURST_E = (20 << 16);
    private static final int NTSC_BURST_O = (20 << 16);

    private static final int CVBS_PIO = 0;
    private static final int CVBS_SM = 0;

    private final static int palRGBA8888Colours[] = { 
            0xFF000000, // BLACK
            0xFFFFFFFF, // WHITE
            0xFF211FB6, // RED
            0xFFFFF04D, // CYAN
            0xFFFF3FB4, // PURPLE
            0xFF37E244, // GREEN
            0xFFFF341A, // BLUE
            0xFF1BD7DC, // YELLOW
            0xFF0054CA, // ORANGE
            0xFF72B0E9, // LIGHT ORANGE
            0xFF9392E7, // PINK
            0xFFFDF79A, // LIGHT CYAN
            0xFFE09FFF, // LIGHT PURPLE
            0xFF93E48F, // LIGHT GREEN
            0xFFFF9082, // LIGHT BLUE
            0xFF85DEE5  // LIGHT YELLOW
    };

    private final static short palRGB565Colours[] = { 
            (short) 0x0000, // BLACK
            (short) 0xFFFF, // WHITE
            (short) 0xB0E4, // RED
            (short) 0x4F9F, // CYAN
            (short) 0xB1FF, // PURPLE
            (short) 0x4706, // GREEN
            (short) 0x19BF, // BLUE
            (short) 0xDEA3, // YELLOW
            (short) 0xCAA0, // ORANGE
            (short) 0xED8E, // LIGHT ORANGE
            (short) 0xE492, // PINK
            (short) 0x9FBF, // LIGHT CYAN
            (short) 0xE4FF, // LIGHT PURPLE
            (short) 0x8F32, // LIGHT GREEN
            (short) 0x849F, // LIGHT BLUE
            (short) 0xE6F0  // LIGHT YELLOW
    };

    private final static short palEvenRGB565Colours[] = { 
            (short) 0x0000, // BLACK
            (short) 0xFFFF, // WHITE
            (short) 0x9963, // RED
            (short) 0x76BF, // CYAN
            (short) 0xB1D4, // PURPLE
            (short) 0x56CF, // GREEN
            (short) 0x5175, // BLUE
            (short) 0xD72B, // YELLOW
            (short) 0xB382, // ORANGE
            (short) 0xED96, // LIGHT ORANGE
            (short) 0xDCF8, // PINK
            (short) 0xB7FB, // LIGHT CYAN
            (short) 0xD53F, // LIGHT PURPLE
            (short) 0xBF94, // LIGHT GREEN
            (short) 0xA53B, // LIGHT BLUE
            (short) 0xFF98  // LIGHT YELLOW
    };

    private final static short palOddRGB565Colours[] = { 
            (short) 0x0000, // BLACK
            (short) 0xFFFF, // WHITE
            (short) 0x9128, // RED
            (short) 0x6756, // CYAN
            (short) 0xA1FA, // PURPLE
            (short) 0x6EE6, // GREEN
            (short) 0x29D8, // BLUE
            (short) 0xE726, // YELLOW
            (short) 0xBB26, // ORANGE
            (short) 0xD610, // LIGHT ORANGE
            (short) 0xD551, // PINK
            (short) 0xC77F, // LIGHT CYAN
            (short) 0xE55A, // LIGHT PURPLE
            (short) 0xAF98, // LIGHT GREEN
            (short) 0xACDD, // LIGHT BLUE
            (short) 0xE7F7  // LIGHT YELLOW
    };

    private short[] pal_palette_o = palOddRGB565Colours;
    private short[] pal_palette_e = palEvenRGB565Colours;

    // NTSC Palette data.
    private int pIndex;
    // TODO: Change to NTSC palette.
    private short[][] palette = { 
        pal_palette_e, pal_palette_e, pal_palette_e, pal_palette_e, 
        pal_palette_e, pal_palette_e, pal_palette_e, pal_palette_e, 
        pal_palette_e, pal_palette_e, pal_palette_e, pal_palette_e,
        pal_palette_e, pal_palette_e, pal_palette_e, pal_palette_e
    };
    
    /**
     * Pixel counter. Current offset into TV frame array.
     */
    private int pixelCounter;

    /**
     * Represents the data for one VIC frame.
     */
    class Frame {

        /**
         * Holds the pixel data for the TV frame screen.
         */
        short framePixels[];

        /**
         * Says whether this frame is ready to be blitted to the GPU.
         */
        boolean ready;
    }

    /**
     * An array of two Frames, one being the one that the VIC is currently writing
     * to, the other being the last one that was completed and ready to blit.
     */
    private Frame[] frames;

    /**
     * The index of the active frame within the frames. This will toggle between 0 and 1.
     */
    private int activeFrame;

    /**
     * A lookup table for determining the start of video memory.
     */
    private final static int videoMemoryTable[] = {
        0x8000, 0x8200, 0x8400, 0x8600, 0x8800, 0x8A00, 0x8C00, 0x8E00,
        0x9000, 0x9200, 0x9400, 0x9600, 0x9800, 0x9A00, 0x9C00, 0x9E00, 
        0x0000, 0x0200, 0x0400, 0x0600, 0x0800, 0x0A00, 0x0C00, 0x0E00, 
        0x1000, 0x1200, 0x1400, 0x1600, 0x1800, 0x1A00, 0x1C00, 0x1E00
    };

    /**
     * A lookup table for determining the start of character memory.
     */
    private final static int charMemoryTable[] = {
        0x8000, 0x8400, 0x8800, 0x8C00, 0x9000, 0x9400, 0x9800, 0x9C00,
        0x0000, 0x0400, 0x0800, 0x0C00, 0x1000, 0x1400, 0x1800, 0x1C00
    };

    /**
     * The type of machine that this Vic chip is in, i.e. either PAL or NTSC.
     */
    private MachineType machineType;

    
    //
    // START OF VIC CHIP STATE
    //
    
    // Counters.
    private int videoMatrixCounter;     // 12-bit video matrix counter (VMC)
    private int videoMatrixLatch;       // 12-bit latch that VMC is stored to and loaded from
    private int verticalCounter;        // 9-bit vertical counter (i.e. raster lines)
    private int horizontalCounter;      // 8-bit horizontal counter (although top bit isn't used)
    private int prevHorizontalCounter;  // 8-bit previous value of horizontal counter.
    private int horizontalCellCounter;  // 8-bit horizontal cell counter (down counter)
    private int verticalCellCounter;    // 6-bit vertical cell counter (down counter)
    private int cellDepthCounter;       // 4-bit cell depth counter (counts either from 0-7, or 0-15)
    private int halfLineCounter;        // 1-bit half-line counter
    
    // Values normally fetched externally, from screen mem, colour RAM and char mem.
    private int cellIndex;              // 8 bits fetched from screen memory.
    private int charData;               // 8 bits of bitmap data fetched from character memory.
    private int charDataLatch;          // 8 bits of bitmap data fetched from character memory (latched)
    private int colourData;             // 4 bits fetched from colour memory (top bit multi/hires mode)
    private boolean hiresMode;

    // Palette index for the current border colour.
    private int borderColour;
    
    // Holds the palette index for each of the current multi colour colours.
    private int multiColourTable[] = new int[4];
    
    // Every cpu cycle, we output four pixels. The values are temporarily stored in these vars.
    private int pixel1;
    private int pixel2;
    private int pixel3;
    private int pixel4;
    private int pixel5;
    private int pixel6;
    private int pixel7;
    private int pixel8;
    
    // Used by NTSC to keep track of whether it is an odd or even line.
    boolean oddLine = true;
    
    // Reference that alternates on each line between even and odd PAL palettes.
    private short[] pal_palette = pal_palette_e;
    private short[] pal_trunc_palette = pal_palette;
    
    // Optimisation to represent "in matrix", "address output enabled", and "pixel output enabled" 
    // all with one simple state variable. It might not be 100% accurate but should work for most 
    // cases.
    private int fetchState = FETCH_OUTSIDE_MATRIX;
    
    // Due to the complexity of how the NTSC vertical blanking shifts depending on state, we keep 
    // track of the vblanking state in a separate boolean, so that we know when we should be writing 
    // out visible line content. It is complex to deduce it otherwise.
    boolean vblanking = false;
    
    //
    // END OF VIC CHIP STATE
    //
    
    
    // Temporary variables, not a core part of the state.
    private int charDataOffset = 0;
    
    // Flag variable to delay vblank command push to late in cycle
    private static final int DO_VBLANK_LONG  = 1;
    private static final int DO_VBLANK_SHORT = 2;
    private int do_vblank = 0;

    // Index of the current border colour (used temporarily when we don't want to use the define multiple times in a cycle)
    private int borderColourIndex;
    
    
    private int cyclesPerSample;
    private short[] sampleBuffer;
    private int sampleBufferOffset = 0;
    private int cyclesToNextSample;
    private AudioDevice audioDevice;
    private boolean soundPaused;
    private int soundClockDividerCounter;
    private int[] voiceClockDividerTriggers;
    private int[] voiceCounters;
    private int[] voiceShiftRegisters;
    private int noiseLFSR = 0xFFFF;
    private int lastNoiseLFSR0 = 0x1;

    
    /**
     * Constructor for VIC.
     * 
     * @param machineType The type of machine, PAL or NTSC.
     * @param snapshot    Optional snapshot of the machine state to start with.
     */
    public Vic(MachineType machineType, Snapshot snapshot) {
        this.machineType = machineType;

        this.cyclesPerSample = (machineType.getCyclesPerSecond() / SAMPLE_RATE);

        frames = new Frame[2];
        frames[0] = new Frame();
        frames[0].framePixels = new short[(machineType.getTotalScreenWidth() * machineType.getTotalScreenHeight())];
        frames[0].ready = false;
        frames[1] = new Frame();
        frames[1].framePixels = new short[(machineType.getTotalScreenWidth() * machineType.getTotalScreenHeight())];
        frames[1].ready = false;

        reset();

        if (snapshot != null) {
            loadSnapshot(snapshot);
        }

        int audioBufferSize = ((((SAMPLE_RATE / 20) * 2) / 10) * 10);
        sampleBuffer = new short[audioBufferSize / 10];
        sampleBufferOffset = 0;

        try {
            audioDevice = Gdx.audio.newAudioDevice(SAMPLE_RATE, true);
        } catch (GdxRuntimeException e) {
            audioDevice = null;
        }

        cyclesToNextSample = cyclesPerSample;

        voiceCounters = new int[4];
        voiceShiftRegisters = new int[4];
        voiceClockDividerTriggers = new int[] { 0xF, 0x7, 0x3, 0x1 };
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
        horizontalCounter = 0;
        verticalCounter = 0;
        pixelCounter = 0;
        horizontalCellCounter = 0;
        verticalCellCounter = 0;
        cellDepthCounter = 0;
        videoMatrixCounter = 0;
        charData = 0;
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

        return value;
    }

    /**
     * Writes a value to VIC memory.
     * 
     * @param address The address to write the value to.
     * @param value   The value to write into the address.
     */
    public void writeMemory(int address, int value) {
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

    /**
     * Emulates a cycle where rendering is skipped. This is intended to be used by
     * every cycle in a frame whose rendering is being skipped. All this method does
     * is make sure that the vertical counter register is updated. Everything else
     * is hidden from the CPU, so doesn't need to be updated for a skip frame.
     * 
     * @return true if the frame was completed by the cycle that was emulated.
     */
    public boolean emulateSkipCycle() {
        boolean frameComplete = false;

        // TODO: Update this to account for new PIVIC logic.
        
        // Increment the horizontal counter.
        horizontalCounter = horizontalCounter + 4;

        // If end of line is reached, reset horiz counter and increment vert counter.
        if (horizontalCounter >= machineType.getTotalScreenWidth()) {
            horizontalCounter = 0;
            verticalCounter++;

            // If last line has been reached, reset all counters.
            if (verticalCounter >= machineType.getTotalScreenHeight()) {
                verticalCounter = 0;
                frameComplete = true;
            }

            // Update raster line in VIC registers.
            mem[VIC_REG_4] = (verticalCounter >> 1);
            if ((verticalCounter & 0x01) == 0) {
                mem[VIC_REG_3] &= 0x7F;
            } else {
                mem[VIC_REG_3] |= 0x80;
            }
        }

        return frameComplete;
    }

    private void pio_sm_put(int pio, int sm, int pixel) {
        if ((pixelCounter < 88608)) {
            if (((int) pixel) <= 0xFFFF) {
                // TODO: 67860 out of bounds error for Apple Panic. NTSC.
                // TODO: Also Atlantis is NTSC.
                frames[activeFrame].framePixels[pixelCounter++] = (short) pixel;
            } else {
                int count = (pixel >> 16);
                for (int i = 0; i < count; i++) {
                    frames[activeFrame].framePixels[pixelCounter++] = 0;
                }
            }
        }
    }

    /**
     * TODO:
     * 
     * @return
     */
    public boolean emulateCyclePal(boolean doSound) {
        boolean frameRenderComplete = false;

        // Expressions to access different parts of control registers.
        int border_colour_index = (mem[VIC_REG_15] & 0x07);
        int background_colour_index = (mem[VIC_REG_15] >> 4);
        int auxiliary_colour_index = (mem[VIC_REG_14] >> 4);
        int non_reverse_mode = (mem[VIC_REG_15] & 0x08);
        int screen_origin_x = ((mem[VIC_REG_0] & 0x7F));
        int screen_origin_y = (mem[VIC_REG_1]);
        int num_of_columns = (mem[VIC_REG_2] & 0x7F);
        int num_of_rows = ((mem[VIC_REG_3] & 0x7E) >> 1);
        int double_height_mode = (mem[VIC_REG_3] & 0x01);
        int last_line_of_cell = (7 | (double_height_mode << 3));
        int char_size_shift = (3 + double_height_mode);
        int screen_mem_start = videoMemoryTable[((mem[VIC_REG_5] & 0xF0) >> 3) | ((mem[VIC_REG_2] & 0x80) >> 7)];
        int char_mem_start = charMemoryTable[mem[VIC_REG_5] & 0x0F];
        int colour_mem_start = (0x9400 | ((mem[VIC_REG_2] & 0x80) << 2));

        // VERTICAL TIMINGS:
        // Lines 1-9: Vertical blanking
        // Lines 4-6: Vertical sync
        // Lines 10-311: Normal visible lines.
        // Line 0: Last visible line of a frame (yes, this is actually true)

        // To correctly implement the screen origin X matching, we need to compare the
        // the screen_origin_x that the CPU may have just updated in F2 of the cycle
        // before with the HC value from the previous cycle. This is why there are uses
        // of the prevHorizontalCounter variable in the logic.

        switch (horizontalCounter) {

            // HC = 0 is handled in a single block for ALL lines.
            case 0:
    
                // Reset pixel output buffer to be all border colour at start of line.
                pixel1 = pixel2 = pixel3 = pixel4 = pixel5 = pixel6 = pixel7 = pixel8 = 1;
                hiresMode = false;
                colourData = 0x08;
                charData = charDataLatch = 0x55;
    
                // Simplified state updates for HC=0. Counters and states still need to
                // change as appropriate, regardless of it being during blanking.
                switch (fetchState) {
                    case FETCH_OUTSIDE_MATRIX:
                        // In HC=0, it is not possible to match screen origin x, if the last
                        // line was not a matrix line. This is as per the real chip. It is
                        // possible to match screen origin y though.
                        if ((verticalCounter >> 1) == screen_origin_y) {
                            // This is the line the video matrix starts on. As in the real chip, we use
                            // a different state for the first part of the first video matrix line.
                            fetchState = FETCH_IN_MATRIX_Y;
                        }
                        break;
                    case FETCH_IN_MATRIX_Y:
                        // Since we are now looking at prev HC, this behaves same as FETCH_MATRIX_LINE.
                    case FETCH_MATRIX_LINE:
                        // NOTE: Due to comparison being prev HC, this is matching HC=70.
                        if (prevHorizontalCounter == screen_origin_x) {
                            fetchState = FETCH_MATRIX_DLY_1;
                        }
                        break;
                    case FETCH_MATRIX_DLY_1:
                    case FETCH_MATRIX_DLY_2:
                    case FETCH_MATRIX_DLY_3:
                        fetchState++;
                        break;
                    case FETCH_SCREEN_CODE:
                        fetchState = ((horizontalCellCounter-- > 0) ? FETCH_CHAR_DATA : FETCH_MATRIX_LINE);
                        break;
                    case FETCH_CHAR_DATA:
                        videoMatrixCounter++;
                        fetchState = FETCH_SCREEN_CODE;
                        break;
                }
    
                prevHorizontalCounter = horizontalCounter++;
                break;
    
            // HC = 1 is another special case, handled in a single block for ALL lines. This
            // is when the "new line" signal is seen by most components. It is also the cycle 
            // during which we queue the horiz blanking, horiz sync, colour burst, vertical
            // blanking and vsync, all up front for efficiency reasons.
            case 1:
    
                // This needs to be checked before the vertical counter is updated.
                if (fetchState == FETCH_OUTSIDE_MATRIX) {
                    if ((verticalCounter >> 1) == screen_origin_y) {
                        // This is the line the video matrix starts on. As in the real chip, we use
                        // a different state for the first part of the first video matrix line.
                        fetchState = FETCH_IN_MATRIX_Y;
                    }
                }
    
                // The Vertical Counter is incremented during HC=1, due to a deliberate 1 cycle
                // delay between the HC reset and the VC increment.
                if (verticalCounter == PAL_LAST_LINE) {
                    // Previous cycle was end of last line, so reset VC.
                    verticalCounter = 0;
                    fetchState = FETCH_OUTSIDE_MATRIX;
                    cellDepthCounter = 0;
                } else {
                    // Otherwise increment line counter.
                    verticalCounter++;
                }
    
                // Update the raster line value stored in the VIC registers.
                mem[VIC_REG_4] = (verticalCounter >> 1);
                if ((verticalCounter & 0x01) == 0) {
                    mem[VIC_REG_3] &= 0x7F;
                } else {
                    mem[VIC_REG_3] |= 0x80;
                }
    
                if ((verticalCounter == 0) || (verticalCounter > PAL_VBLANK_END)) {
                    // In HC=1 for visible lines, we start with outputting the full sequence of CVBS
                    // commands for horizontal blanking, including the hsync and colour burst.
                    pio_sm_put(CVBS_PIO, CVBS_SM, PAL_FRONTPORCH_2);
                    pio_sm_put(CVBS_PIO, CVBS_SM, PAL_HSYNC);
                    pio_sm_put(CVBS_PIO, CVBS_SM, PAL_BREEZEWAY);
                    if ((verticalCounter & 1) == 1) {
                        // Odd line. Switch colour palettes.
                        pal_trunc_palette = pal_palette = pal_palette_o;
                        pio_sm_put(CVBS_PIO, CVBS_SM, PAL_COLBURST_O);
                    } else {
                        // Even line. Switch colour palettes.
                        pal_trunc_palette = pal_palette = pal_palette_e;
                        pio_sm_put(CVBS_PIO, CVBS_SM, PAL_COLBURST_E);
                    }
                    pio_sm_put(CVBS_PIO, CVBS_SM, PAL_BACKPORCH);
                } else {
                    // Last line was 0, which is the end of the visible lines.
                    if (verticalCounter == 1) {
                        pixelCounter = 0;
    
                        synchronized (frames) {
                            // Mark the current frame as complete.
                            frames[activeFrame].ready = true;
    
                            // Toggle the active frame.
                            activeFrame = ((activeFrame + 1) % 2);
                            frames[activeFrame].ready = false;
                        }
    
                        frameRenderComplete = true;
                    }
    
                    // Vertical blanking and sync - Lines 1-9.
                    if (verticalCounter < PAL_VSYNC_START) {
                        // Lines 1, 2, 3.
                        pio_sm_put(CVBS_PIO, CVBS_SM, PAL_SHORT_SYNC_L);
                        pio_sm_put(CVBS_PIO, CVBS_SM, PAL_SHORT_SYNC_H);
                        pio_sm_put(CVBS_PIO, CVBS_SM, PAL_SHORT_SYNC_L);
                        pio_sm_put(CVBS_PIO, CVBS_SM, PAL_SHORT_SYNC_H);
                    } else if (verticalCounter <= PAL_VSYNC_END) {
                        // Vertical sync, lines 4, 5, 6.
                        pio_sm_put(CVBS_PIO, CVBS_SM, PAL_LONG_SYNC_L);
                        pio_sm_put(CVBS_PIO, CVBS_SM, PAL_LONG_SYNC_H);
                        pio_sm_put(CVBS_PIO, CVBS_SM, PAL_LONG_SYNC_L);
                        pio_sm_put(CVBS_PIO, CVBS_SM, PAL_LONG_SYNC_H);
    
                        // Vertical sync is what resets the video matrix latch.
                        videoMatrixLatch = videoMatrixCounter = 0;
                    } else {
                        // Lines 7, 8, 9.
                        pio_sm_put(CVBS_PIO, CVBS_SM, PAL_SHORT_SYNC_L);
                        pio_sm_put(CVBS_PIO, CVBS_SM, PAL_SHORT_SYNC_H);
                        pio_sm_put(CVBS_PIO, CVBS_SM, PAL_SHORT_SYNC_L);
                        pio_sm_put(CVBS_PIO, CVBS_SM, PAL_SHORT_SYNC_H);
                    }
                }
    
                // Due to the "new line" signal being generated by the Horizontal Counter Reset
                // logic, and the pass transistors used within it delaying the propagation of
                // that signal, this signal doesn't get seen by components such as the Cell
                // Depth Counter Reset logic, the "In Matrix" status logic, and Video Matrix Latch
                // until HC = 1.
    
                if (fetchState >= FETCH_MATRIX_DLY_1) {
                    // The real chip appears to have another increment in this cycle, if the
                    // state is FETCH_CHAR_DATA. Not 100% clear though, since distortion ensues
                    // when setting the registers such that the matrix closes here.
                    if (fetchState == FETCH_CHAR_DATA) {
                        videoMatrixCounter++;
                    }
                    fetchState = FETCH_MATRIX_LINE;
                }
    
                // Check for Cell Depth Counter reset.
                if ((cellDepthCounter == last_line_of_cell) || (cellDepthCounter == 0xF)) {
    
                    // Reset CDC.
                    cellDepthCounter = 0;
    
                    // If last line was the last line of the character cell, then we latch
                    // the current VMC value ready for the next character row.
                    videoMatrixLatch = videoMatrixCounter;
    
                    // Vertical Cell Counter decrements when CDC resets, unless its the first line,
                    // since it was loaded instead (see VC reset logic in HC=2).
                    if (verticalCounter > 0) {
                        verticalCellCounter--;
    
                        if ((verticalCellCounter == 0) && (screen_origin_x > 0)) {
                            // If all text rows rendered, then we're outside the matrix again.
                            fetchState = FETCH_OUTSIDE_MATRIX;
                        } else {
                            // NOTE: Due to comparison being prev HC, this is match HC=0.
                            if (prevHorizontalCounter == screen_origin_x) {
                                // Last line was in the matrix, so start the in matrix delay.
                                fetchState = FETCH_MATRIX_DLY_1;
                            }
                        }
                    }
                } else if (fetchState >= FETCH_MATRIX_LINE) {
                    // If the line that just ended was a video matrix line, then increment CDC,
                    // unless the VCC is 0, in which case close the matrix.
                    if (verticalCellCounter > 0) {
                        cellDepthCounter++;
    
                        // NOTE: Due to comparison being prev HC, this is match HC=0.
                        if (prevHorizontalCounter == screen_origin_x) {
                            // Last line was in the matrix, so start the in matrix delay.
                            fetchState = FETCH_MATRIX_DLY_1;
                        }
                    } else {
                        fetchState = FETCH_OUTSIDE_MATRIX;
                    }
                } else if (fetchState == FETCH_IN_MATRIX_Y) {
                    // If fetchState is FETCH_IN_MATRIX_Y at this point, it means that the
                    // last line matched the screen origin Y but not X. This results in the
                    // matrix being rendered one line lower if X now matches, as per real chip.
                    if (prevHorizontalCounter == screen_origin_x) {
                        fetchState = FETCH_IN_MATRIX_X;
                    }
                }
    
                prevHorizontalCounter = horizontalCounter++;
                break;
    
            // HC = 2 is yet another special case, handled in a single block for ALL
            // lines. This is when the horizontal cell counter is loaded.
            case 2:
    
                // Simplified state changes. We're in hblank, so its just the bare minimum.
                switch (fetchState) {
                    case FETCH_OUTSIDE_MATRIX:
                        if ((verticalCounter >> 1) == screen_origin_y) {
                            // This is the line the video matrix starts on. As in the real chip, we use
                            // a different state for the first part of the first video matrix line.
                            fetchState = FETCH_IN_MATRIX_Y;
                        }
                        break;
                    case FETCH_IN_MATRIX_X:
                        // If screen origin x matched during HC=1, which can only mean that the screen
                        // origin y matched on the previous line, then we move to second matrix delay
                        // state, since the match happened in the previous cycle.
                        fetchState = FETCH_MATRIX_DLY_2;
                        break;
                    case FETCH_IN_MATRIX_Y:
                    case FETCH_MATRIX_LINE:
                        if (prevHorizontalCounter == screen_origin_x) {
                            fetchState = FETCH_MATRIX_DLY_1;
                        }
                        break;
                    case FETCH_MATRIX_DLY_1:
                    case FETCH_MATRIX_DLY_2:
                    case FETCH_MATRIX_DLY_3:
                        fetchState++;
                        break;
                    // In theory, the following states should not be possible at this point.
                    case FETCH_SCREEN_CODE:
                        fetchState = ((horizontalCellCounter-- > 0) ? FETCH_CHAR_DATA : FETCH_MATRIX_LINE);
                        break;
                    case FETCH_CHAR_DATA:
                        videoMatrixCounter++;
                        fetchState = FETCH_SCREEN_CODE;
                        break;
                }
    
                // Video Matrix Counter (VMC) is reloaded from latch on "new line" signal.
                videoMatrixCounter = videoMatrixLatch;
    
                // Horizontal Cell Counter (HCC) is reloaded on "new line" signal.
                horizontalCellCounter = num_of_columns;
                prevHorizontalCounter = horizontalCounter++;
                break;
    
            // HC = 3 is yet another special case, handled in a single block for ALL
            // lines. This is when the vertical cell counter is loaded
            case 3:
    
                // Simplified state changes. We're in hblank, so its just the bare minimum.
                switch (fetchState) {
                    case FETCH_OUTSIDE_MATRIX:
                        if ((verticalCounter >> 1) == screen_origin_y) {
                            // This is the line the video matrix starts on. As in the real chip, we use
                            // a different state for the first part of the first video matrix line.
                            fetchState = FETCH_IN_MATRIX_Y;
                        }
                        break;
                    case FETCH_IN_MATRIX_Y:
                    case FETCH_MATRIX_LINE:
                        if (prevHorizontalCounter == screen_origin_x) {
                            fetchState = FETCH_MATRIX_DLY_1;
                        }
                        break;
                    case FETCH_MATRIX_DLY_1:
                    case FETCH_MATRIX_DLY_2:
                    case FETCH_MATRIX_DLY_3:
                        fetchState++;
                        break;
                    // In theory, the following states should not be possible at this point.
                    case FETCH_SCREEN_CODE:
                        fetchState = ((horizontalCellCounter-- > 0) ? FETCH_CHAR_DATA : FETCH_MATRIX_LINE);
                        break;
                    case FETCH_CHAR_DATA:
                        videoMatrixCounter++;
                        fetchState = FETCH_SCREEN_CODE;
                        break;
                }
    
                // Vertical Cell Counter is loaded 2 cycles after the VC resets.
                if (verticalCounter == 0) {
                    verticalCellCounter = num_of_rows;
                }
    
                prevHorizontalCounter = horizontalCounter++;
                break;
    
            // Covers HC=4 and above, up to HC=HBLANKSTART (e.g. HC=70 for PAL)
            default:
    
                // Line 0, and Lines after 9, are "visible", i.e. not within the vertical blanking.
                if ((verticalCounter == 0) || (verticalCounter > PAL_VBLANK_END)) {
                    // Is the visible part of the line ending now and horizontal blanking starting?
                    if (horizontalCounter == PAL_HBLANK_START) {
                        // Horizontal blanking doesn't start until 3.66 pixels in. What exactly those
                        // 3.66 pixels are depends on the fetch state.
                        switch (fetchState) {
                            case FETCH_OUTSIDE_MATRIX:
                                if ((verticalCounter >> 1) == screen_origin_y) {
                                    // This is the line the video matrix starts on. As in the real chip, we use
                                    // a different state for the first part of the first video matrix line.
                                    fetchState = FETCH_IN_MATRIX_Y;
                                }
                                borderColour = border_colour_index;
                                pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[borderColour]);
                                pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[borderColour]);
                                pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[borderColour]);
                                pio_sm_put(CVBS_PIO, CVBS_SM, pal_trunc_palette[borderColour]);
                                break;
                            
                            case FETCH_MATRIX_LINE:
                                // Look up latest background, border and auxiliary colours.
                                multiColourTable[0] = background_colour_index;
                                multiColourTable[1] = border_colour_index;
                                multiColourTable[3] = auxiliary_colour_index;
                                
                                pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[multiColourTable[pixel2]]);
                                pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[multiColourTable[pixel3]]);
                                pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[multiColourTable[pixel4]]);
                                pio_sm_put(CVBS_PIO, CVBS_SM, pal_trunc_palette[multiColourTable[pixel5]]);
                                break;
        
                            case FETCH_MATRIX_DLY_1:
                            case FETCH_MATRIX_DLY_2:
                            case FETCH_MATRIX_DLY_3:
                                fetchState++;
                            case FETCH_IN_MATRIX_Y:
                                borderColour = border_colour_index;
                                pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[borderColour]);
                                pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[borderColour]);
                                pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[borderColour]);
                                pio_sm_put(CVBS_PIO, CVBS_SM, pal_trunc_palette[borderColour]);
                                break;
        
                            case FETCH_SCREEN_CODE:
                                // Look up latest background, border and auxiliary colours.
                                multiColourTable[0] = background_colour_index;
                                multiColourTable[1] = border_colour_index;
                                multiColourTable[3] = auxiliary_colour_index;
        
                                // First 3 whole pixels are from end of current character.
                                pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[multiColourTable[pixel6]]);
                                pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[multiColourTable[pixel7]]);
                                
                                // We only need to calculate 8th & 1st pixel in this scenario. Hblanking is about to start.
                                if (non_reverse_mode != 0) {
                                    // New non-reversed mode value kicks in a pixel before new character.
                                    if (hiresMode) {
                                        pixel8 = ((charData & 0x01) > 0? 2 : 0);
                                    } else {
                                        pixel8 = (charData & 0x03);
                                    }
                                    
                                    // Update the operating hires state and char data immediately prior to
                                    // shifting out new character pixel.
                                    hiresMode = ((colourData & 0x08) == 0);
                                    charData = charDataLatch;
                                    
                                    // Pixel 1 should be same non-reversed mode but pick up the new hires mode.
                                    if (hiresMode) {
                                        pixel1 = ((charData & 0x80) > 0? 2 : 0);
                                    } else {
                                        pixel1 = ((charData >> 6) & 0x03);
                                    }
                                } else {
                                    // New reversed mode value kicks in a pixel before new character.
                                    if (hiresMode) {
                                        pixel8 = ((charData & 0x01) > 0? 0 : 2);
                                    } else {
                                        pixel8 = (charData & 0x03);
                                    }
                                    
                                    // Update the operating hires state and char data immediately prior to
                                    // shifting out new character pixel.
                                    hiresMode = ((colourData & 0x08) == 0);
                                    charData = charDataLatch;
                                    
                                    // Pixel 1 should be same reversed mode but pick up the new hires mode.
                                    if (hiresMode) {
                                        pixel1 = ((charData & 0x80) > 0? 0 : 2);
                                    } else {
                                        pixel1 = ((charData >> 6) & 0x03);
                                    }
                                }
                                
                                // The 3rd pixel is from the previous character with new reverse mode applied (see above).
                                pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[multiColourTable[pixel8]]);
                                
                                // Look up foreground colour before outputting first pixel of new character.
                                multiColourTable[2] = (colourData & 0x07);
                                
                                // The 4th pixel is partial before horiz blanking kicks in.
                                pio_sm_put(CVBS_PIO, CVBS_SM, pal_trunc_palette[multiColourTable[pixel1]]);
                                
                                fetchState = ((horizontalCellCounter-- > 0) ? FETCH_CHAR_DATA : FETCH_MATRIX_LINE);
                                break;
        
                            case FETCH_CHAR_DATA:
                                // Look up latest background, border and auxiliary colours.
                                multiColourTable[0] = background_colour_index;
                                multiColourTable[1] = border_colour_index;
                                multiColourTable[3] = auxiliary_colour_index;
                                
                                // Output the three whole pixels.
                                pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[multiColourTable[pixel2]]);
                                pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[multiColourTable[pixel3]]);
                                pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[multiColourTable[pixel4]]);
                                
                                // The 4th pixel is a partial pixel before horizontal blanking kicks in.
                                pio_sm_put(CVBS_PIO, CVBS_SM, pal_trunc_palette[multiColourTable[pixel5]]);
                                
                                // If the matrix hasn't yet closed, then in the FETCH_CHAR_DATA
                                // state, we need to keep incrementing the video matrix counter
                                // until it is closed, which at the latest could be HC=1 on the
                                // next line.
                                videoMatrixCounter++;
        
                                fetchState = FETCH_SCREEN_CODE;
                                break;
                        }
    
                        // After the 3.66 visible pixels, we now output the start of horiz blanking.
                        pio_sm_put(CVBS_PIO, CVBS_SM, PAL_FRONTPORCH_1);
    
                        // Reset HC to start a new line.
                        prevHorizontalCounter = horizontalCounter;
                        horizontalCounter = 0;
                    } else {
                        // Covers visible line cycles from HC=4 to 1 cycle before HC=HBLANKSTART 
                        // (e.g. HC=70 for PAL)
                        switch (fetchState) {
                        
                            case FETCH_OUTSIDE_MATRIX:
                                if ((verticalCounter >> 1) == screen_origin_y) {
                                    // This is the line the video matrix starts on. As in the real chip, we use
                                    // a different state for the first part of the first video matrix line.
                                    fetchState = FETCH_IN_MATRIX_Y;
                                }
                                if (horizontalCounter >= PAL_HBLANK_END) {
                                    // Output four border pixels.
                                    borderColour = border_colour_index;
                                    
                                    // Output only one visible border pixel for HC=12, as first three "pixels"
                                    // are part of the horizontal blanking. Note that the third one is due
                                    // to the switch delay in hblank turning off.
                                    if (horizontalCounter > PAL_HBLANK_END) {
                                        pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[borderColour]);
                                        pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[borderColour]);
                                        pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[borderColour]);
                                    }
                                    pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[borderColour]);
                                }
                                break;
        
                            case FETCH_IN_MATRIX_Y:
                            case FETCH_MATRIX_LINE:
                                if (horizontalCounter >= PAL_HBLANK_END) {
                                    // Look up very latest background, border and auxiliary colour values. This
                                    // should not include an update to the foreground colour, as that will not
                                    // have changed.
                                    multiColourTable[0] = background_colour_index;
                                    multiColourTable[1] = border_colour_index;
                                    multiColourTable[3] = auxiliary_colour_index;
        
                                    if (horizontalCounter > PAL_HBLANK_END) {
                                        pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[multiColourTable[pixel2]]);
                                        pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[multiColourTable[pixel3]]);
                                    }
        
                                    // Pixels 4-7 calculations are less complex, since the hires mode,
                                    // reverse mode and char data stay the same four all four pixels.
                                    if (hiresMode) {
                                        if (non_reverse_mode != 0) {
                                            pixel4 = ((charData & 0x10) > 0? 2 : 0);
                                            pixel5 = ((charData & 0x08) > 0? 2 : 0);
                                            pixel6 = ((charData & 0x04) > 0? 2 : 0);
                                            pixel7 = ((charData & 0x02) > 0? 2 : 0);
                                        } else {
                                            pixel4 = ((charData & 0x10) > 0? 0 : 2);
                                            pixel5 = ((charData & 0x08) > 0? 0 : 2);
                                            pixel6 = ((charData & 0x04) > 0? 0 : 2);
                                            pixel7 = ((charData & 0x02) > 0? 0 : 2);
                                        }
                                    } else {
                                        // Multicolour graphics.
                                        pixel4 = ((charData >> 4) & 0x03);
                                        pixel5 = pixel6 = ((charData >> 2) & 0x03);
                                        pixel7 = (charData & 0x03);
                                    }
                                    
                                    // Pixels 4 & 5 have to be output after the pixel var calculations above, not before.
                                    if (horizontalCounter > PAL_HBLANK_END) {
                                        pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[multiColourTable[pixel4]]);
                                    }
                                    pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[multiColourTable[pixel5]]);

                                    // Rotate pixels so that the other 3 remaining char pixels are output
                                    // and then border colours takes over after that.
                                    pixel2 = pixel6;
                                    pixel3 = pixel7;
                                    pixel4 = pixel8;
                                    pixel5 = pixel6 = pixel7 = pixel8 = pixel1 = 1;
        
                                    if (prevHorizontalCounter == screen_origin_x) {
                                        // Last 4 pixels before first char renders are still border.
                                        fetchState = FETCH_MATRIX_DLY_1;
                                    }
                                } else if (prevHorizontalCounter == screen_origin_x) {
                                    // Still in horizontal blanking, but we still need to prepare for the case
                                    // where the next cycle isn't in horiz blanking, i.e. when HC=11 this cycle.
                                    fetchState = FETCH_MATRIX_DLY_1;
                                }
                                hiresMode = false;
                                colourData = 0x08;
                                charData = charDataLatch = 0x55;
                                break;
        
                            case FETCH_MATRIX_DLY_1:
                            case FETCH_MATRIX_DLY_2:
                            case FETCH_MATRIX_DLY_3:
                                if (horizontalCounter >= PAL_HBLANK_END) {
                                    // Output four border pixels.
                                    borderColour = border_colour_index;

                                    // Output only one visible border pixel for HC=12, as first three "pixels"
                                    // are part of the horizontal blanking. Note that the third one is due
                                    // to the switch delay in hblank turning off.
                                    if (horizontalCounter > PAL_HBLANK_END) {
                                        pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[borderColour]);
                                        pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[borderColour]);
                                        pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[borderColour]);
                                    }
                                    pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[borderColour]);
                                }
                                else {
                                    pixel2 = pixel3 = pixel4 = pixel5 = pixel6 = pixel7 = pixel8 = 1;
                                }
        
                                // Prime the pixel output queue with border pixels in multicolour
                                // mode. Not quite what the real chip does but is functionally equivalent.
                                hiresMode = false;
                                colourData = 0x08;
                                charDataLatch = 0x55;
        
                                fetchState++;
                                break;
        
                            case FETCH_SCREEN_CODE:
        
                                // Look up very latest background, border and auxiliary colour values.
                                multiColourTable[0] = background_colour_index;
                                multiColourTable[1] = border_colour_index;
                                multiColourTable[3] = auxiliary_colour_index;
        
                                // Output last 3 pixels of the last character. These had already left 
                                // the shift register but in the delay path to the colour lookup.
                                if (horizontalCounter > PAL_HBLANK_END) {
                                    // Note: These 3 pixels are not output for HC=12, as first three "pixels"
                                    // are part of the horizontal blanking. Note that the third one is due
                                    // to the switch delay in hblank turning off.
                                    pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[multiColourTable[pixel6]]);
                                    pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[multiColourTable[pixel7]]);
                                }
        
                                // Note that when we first enter this state, these variables are primed
                                // to initially output border pixels while the process of fetching the 
                                // first real character is taking place, which happens over the first two 
                                // cycles.

                                if (non_reverse_mode != 0) {
                                    // New non-reversed mode value kicks in a pixel before new character.
                                    if (hiresMode) {
                                        pixel8 = ((charData & 0x01) > 0? 2 : 0);
                                    } else {
                                        pixel8 = (charData & 0x03);
                                    }
                                    
                                    // Update the operating hires state and char data immediately prior to
                                    // shifting out new character pixel.
                                    hiresMode = ((colourData & 0x08) == 0);
                                    charData = charDataLatch;
                                    
                                    // Pixel 1 should be same non-reversed mode but pick up the new hires mode.
                                    if (hiresMode) {
                                        pixel1 = ((charData & 0x80) > 0? 2 : 0);
                                        pixel2 = ((charData & 0x40) > 0? 2 : 0);
                                        pixel3 = ((charData & 0x20) > 0? 2 : 0);
                                    } else {
                                        pixel1 = pixel2 = ((charData >> 6) & 0x03);
                                        pixel3 = ((charData >> 4) & 0x03);
                                    }
                                } else {
                                    // New reversed mode value kicks in a pixel before new character.
                                    if (hiresMode) {
                                        pixel8 = ((charData & 0x01) > 0? 0 : 2);
                                    } else {
                                        pixel8 = (charData & 0x03);
                                    }
                                    
                                    // Update the operating hires state and char data immediately prior to
                                    // shifting out new character pixel.
                                    hiresMode = ((colourData & 0x08) == 0);
                                    charData = charDataLatch;
                                    
                                    // Pixel 1 should be same reversed mode but pick up the new hires mode.
                                    if (hiresMode) {
                                        pixel1 = ((charData & 0x80) > 0? 0 : 2);
                                        pixel2 = ((charData & 0x40) > 0? 0 : 2);
                                        pixel3 = ((charData & 0x20) > 0? 0 : 2);
                                    } else {
                                        pixel1 = pixel2 = ((charData >> 6) & 0x03);
                                        pixel3 = ((charData >> 4) & 0x03);
                                    }
                                }
                                
                                if (horizontalCounter > PAL_HBLANK_END) {
                                    pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[multiColourTable[pixel8]]);
                                }
        
                                // Look up foreground colour before outputting first pixel.
                                multiColourTable[2] = (colourData & 0x07);
        
                                // Calculate address within video memory and fetch cell index.
                                // TODO: Implement unconnected memory check.
                                //int screenAddress = screen_mem_start + videoMatrixCounter;
                                // cellIndex = mem[screenAddress];
                                //switch ((screenAddress >> 10) & 0xF) {
                                //    case 4:
                                //    case 5:
                                //    case 6:
                                //    case 7:
                                //        // case 9:
                                //        // case 10:
                                //        // case 11:
                                //        cellIndex = memory.getLastBusData();
                                //        break;
                                //    default:
                                //        cellIndex = mem[screenAddress];
                                //        break;
                                //}
                                
                                // TODO: Replace with unconnected memory version above.
                                cellIndex = mem[screen_mem_start + videoMatrixCounter];
        
                                // Due to the way the colour memory is wired up, the above fetch of the cell
                                // index also happens to automatically fetch the foreground colour from the 
                                // Colour Matrix via the top 4 lines of the data bus (DB8-DB11), which are 
                                // wired directly from colour RAM in to the VIC chip.
                                colourData = mem[colour_mem_start + videoMatrixCounter];
        
                                // Output the 1st pixel of next character. Note that this is not the character
                                // that relates to the cell index and colour data fetched above.
                                if (horizontalCounter >= PAL_HBLANK_END) {
                                    pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[multiColourTable[pixel1]]);
                                }
        
                                // Toggle fetch state. Close matrix if HCC hits zero.
                                fetchState = ((horizontalCellCounter-- > 0) ? FETCH_CHAR_DATA : FETCH_MATRIX_LINE);
                                break;
        
                            case FETCH_CHAR_DATA:
                                // Look up very latest background, border and auxiliary colour values.
                                multiColourTable[0] = background_colour_index;
                                multiColourTable[1] = border_colour_index;
                                multiColourTable[3] = auxiliary_colour_index;
        
                                if (horizontalCounter >= PAL_HBLANK_END) {
                                    // Output only one visible pixel for HC=12, as first three "pixels"
                                    // are part of the horizontal blanking. Note that the third one is due
                                    // to the switch delay in hblank turning off. This is why we skip these
                                    // pixels for HC=12.
                                    if (horizontalCounter > PAL_HBLANK_END) {
                                        pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[multiColourTable[pixel2]]);
                                        pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[multiColourTable[pixel3]]);
                                    }
                                }
        
                                // Calculate offset of data.
                                charDataOffset = char_mem_start + (cellIndex << char_size_shift) + cellDepthCounter;
        
                                // Adjust offset for memory wrap around.
                                if ((char_mem_start < 8192) && (charDataOffset >= 8192)) {
                                    charDataOffset += 24576;
                                }
                                
                                // TODO: Add unconnected memory check here.
        
                                // Fetch cell data, initially latched to the side until it is needed.
                                charDataLatch = mem[charDataOffset];
        
                                // Determine next character pixels.
                                if (hiresMode) {
                                    if (non_reverse_mode != 0) {
                                        pixel4 = ((charData & 0x10) > 0? 2 : 0);
                                        pixel5 = ((charData & 0x08) > 0? 2 : 0);
                                        pixel6 = ((charData & 0x04) > 0? 2 : 0);
                                        pixel7 = ((charData & 0x02) > 0? 2 : 0);
                                    } else {
                                        pixel4 = ((charData & 0x10) > 0? 0 : 2);
                                        pixel5 = ((charData & 0x08) > 0? 0 : 2);
                                        pixel6 = ((charData & 0x04) > 0? 0 : 2);
                                        pixel7 = ((charData & 0x02) > 0? 0 : 2);
                                    }
                                } else {
                                    // Multicolour graphics.
                                    pixel4 = ((charData >> 4) & 0x03);
                                    pixel5 = pixel6 = ((charData >> 2) & 0x03);
                                    pixel7 = (charData & 0x03);
                                }
                                
                                if (horizontalCounter >= PAL_HBLANK_END) {
                                    if (horizontalCounter > PAL_HBLANK_END) {
                                        pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[multiColourTable[pixel4]]);
                                    }
                                    pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[multiColourTable[pixel5]]);
                                }
        
                                // Increment the video matrix counter to next cell.
                                videoMatrixCounter++;
        
                                // Toggle fetch state. For efficiency, HCC deliberately not checked here.
                                fetchState = FETCH_SCREEN_CODE;
                                break;
                        }
    
                        prevHorizontalCounter = horizontalCounter++;
                    }
                } else {
                    // Inside vertical blanking. The CVBS commands for each line were already sent
                    // during HC=0. In case the screen origin Y is set within the vertical blanking 
                    // lines, we still need to update the fetch state, video matrix counter, and the
                    // horizontal cell counter, even though we're not outputting character pixels. 
                    // So for the rest of the line, it is a simplified version of the standard line,
                    // except that we don't output any pixels.
                    switch (fetchState) {
                        case FETCH_OUTSIDE_MATRIX:
                            if ((verticalCounter >> 1) == screen_origin_y) {
                                // This is the line the video matrix starts on. As in the real chip, we use
                                // a different state for the first part of the first video matrix line.
                                fetchState = FETCH_IN_MATRIX_Y;
                            }
                            break;
                        case FETCH_IN_MATRIX_Y:
                        case FETCH_MATRIX_LINE:
                            if (prevHorizontalCounter == screen_origin_x) {
                                fetchState = FETCH_MATRIX_DLY_1;
                            }
                            break;
                        case FETCH_MATRIX_DLY_1:
                        case FETCH_MATRIX_DLY_2:
                        case FETCH_MATRIX_DLY_3:
                            fetchState++;
                            break;
                        case FETCH_SCREEN_CODE:
                            fetchState = ((horizontalCellCounter-- > 0) ? FETCH_CHAR_DATA : FETCH_MATRIX_LINE);
                            break;
                        case FETCH_CHAR_DATA:
                            videoMatrixCounter++;
                            fetchState = FETCH_SCREEN_CODE;
                            break;
                    }
    
                    prevHorizontalCounter = horizontalCounter;
                    if (horizontalCounter == PAL_HBLANK_START) {
                        horizontalCounter = 0;
                    } else {
                        horizontalCounter++;
                    }
                }
                break;
        }

        // Audio.
        // 5-bit counter in the 6561, but only bottom 4 bits are used. Other bit might have been used for 6562/3.
        soundClockDividerCounter = ((soundClockDividerCounter + 1) & 0xF);

        for (int i = 0; i < 4; i++) {
            if ((voiceClockDividerTriggers[i] & soundClockDividerCounter) == 0) {
                voiceCounters[i] = (voiceCounters[i] + 1) & 0x7F;
                if (voiceCounters[i] == 0) {
                    // Reload the voice counter from the control register.
                    voiceCounters[i] = (mem[VIC_REG_10 + i] & 0x7F);

                    if (i == 3) {
                        // For Noise voice, we perform a shift of the LFSR whenever the counter is
                        // reloaded, and only shift the main voice shift register when LFSR bit 0 changes 
                        // from LOW to HIGH, i.e. on the positive edge.
                        if ((lastNoiseLFSR0 == 0) && (noiseLFSR & 0x0001) > 0) {
                            voiceShiftRegisters[i] = (((voiceShiftRegisters[i] & 0x7F) << 1)
                                    | ((mem[VIC_REG_10 + i] & 0x80) > 0 ? (((voiceShiftRegisters[i] & 0x80) >> 7) ^ 1)
                                            : 0));
                        }

                        // The LFSR taps are bits 3, 12, 14 and 15.
                        int bit3 = (noiseLFSR >> 3) & 1;
                        int bit12 = (noiseLFSR >> 12) & 1;
                        int bit14 = (noiseLFSR >> 14) & 1;
                        int bit15 = (noiseLFSR >> 15) & 1;
                        int feedback = (((bit3 ^ bit12) ^ (bit14 ^ bit15)) ^ 1);
                        lastNoiseLFSR0 = (noiseLFSR & 0x1);
                        noiseLFSR = (((noiseLFSR << 1) | (((feedback & ((mem[VIC_REG_10 + i] & 0x80) >> 7)) ^ 1) & 0x1))
                                & 0xFFFF);

                    } else {
                        // For the three other voices, we shift the voice shift register whenever the
                        // counter is reloaded.
                        voiceShiftRegisters[i] = (((voiceShiftRegisters[i] & 0x7F) << 1)
                                | ((mem[VIC_REG_10 + i] & 0x80) > 0 ? (((voiceShiftRegisters[i] & 0x80) >> 7) ^ 1)
                                        : 0));
                    }
                }
            }
        }

        // If enough cycles have elapsed since the last sample, then output another.
        if (--cyclesToNextSample <= 0) {
            if (doSound) {
                writeSample();
            }
            cyclesToNextSample += cyclesPerSample;
        }
        
        return frameRenderComplete;
    }

    /**
     * Writes a single sample to the sample buffer. If the buffer is full after
     * writing the sample, then the whole buffer is written out.
     */
    public void writeSample() {
        short sample = 0;
        int masterVolume = (mem[VIC_REG_14] & 0x0F);

        for (int i = 0; i < 4; i++) {
            if ((mem[VIC_REG_10 + i] & 0x80) > 0) {
                // Voice enabled. First bit of SR goes out.
                // sample += ((voiceShiftRegisters[i] & 0x01) * 2500); // TODO: Try shifting to
                // multiply by 2048
                sample += ((voiceShiftRegisters[i] & 0x01) << 11);
            }
        }

        sampleBuffer[sampleBufferOffset + 0] = (short) (((sample >> 2) * masterVolume) & 0x7FFF); // TODO: Try shifting.

        // If the sample buffer is full, write it out to the audio line.
        if ((sampleBufferOffset += 1) == sampleBuffer.length) {
            try {
                if (!soundPaused) {
                    audioDevice.writeSamples(sampleBuffer, 0, sampleBuffer.length);
                }
            } catch (Throwable e) {
                // An Exception or Error can occur here if the app is closing, so we catch and
                // ignore.
            }
            sampleBufferOffset = 0;
        }
    }

    /**
     * Gets the pixels for the current frame from the VIC chip.
     * 
     * @return The pixels for the current frame. Returns null if there isn't one
     *         that is ready.
     */
    public short[] getFramePixels() {
        short[] framePixels = null;
        synchronized (frames) {
            Frame nonActiveFrame = frames[((activeFrame + 1) % 2)];
            if (nonActiveFrame.ready) {
                nonActiveFrame.ready = false;
                framePixels = nonActiveFrame.framePixels;
            }
        }
        return framePixels;
    }

    /**
     * Emulates PIVIC NTSC loop.
     * 
     * @param doSound
     * 
     * @return
     */
    public boolean emulateCycleNtsc(boolean doSound) {
        boolean frameRenderComplete = false;

        // Expressions to access different parts of control registers.
        int border_colour_index = (mem[VIC_REG_15] & 0x07);
        int background_colour_index = (mem[VIC_REG_15] >> 4);
        int auxiliary_colour_index = (mem[VIC_REG_14] >> 4);
        int non_reverse_mode = (mem[VIC_REG_15] & 0x08);
        int interlaced_mode = (mem[VIC_REG_0] & 0x80);
        int screen_origin_x = ((mem[VIC_REG_0] & 0x7F));
        int screen_origin_y = (mem[VIC_REG_1]);
        int num_of_columns = (mem[VIC_REG_2] & 0x7F);
        int num_of_rows = ((mem[VIC_REG_3] & 0x7E) >> 1);
        int double_height_mode = (mem[VIC_REG_3] & 0x01);
        int last_line_of_cell = (7 | (double_height_mode << 3));
        int char_size_shift = (3 + double_height_mode);
        int screen_mem_start = videoMemoryTable[((mem[VIC_REG_5] & 0xF0) >> 3) | ((mem[VIC_REG_2] & 0x80) >> 7)];
        int char_mem_start = charMemoryTable[mem[VIC_REG_5] & 0x0F];
        int colour_mem_start = (0x9400 | ((mem[VIC_REG_2] & 0x80) << 2));

        // VERTICAL TIMINGS:
        // The definition of a line is somewhat fuzzy in the NTSC 6560 chip.
        // The vertical counter (VC) increments partway through the visible part of the raster line (at HC=29)
        // and can reset at two different points along the raster line (HC=29 or HC=62) depending on the 
        // interlaced mode and half-line counter (HLC) states.
        // So, unlike the 6561 PAL chip, the VC and raster line are NOT equivalent in the 6560.
        // Also note that things like the vblank and vsync can start/end at two different HC values half a line 
        // apart (HC=29 or HC=62), once again depending on the interlaced mode and half-line counter states.
        // Given that, then documenting what lines are vblank, vsync and visible is a little complex, as they
        // shift depending on state, and can span multiple vertical counter values. 
        // The code is the source of truth in that regard.

        // HORIZONTAL TIMINGS:
        // The horizontal timings for the NTSC 6560 are also quite strange compared to the PAL 6561.
        // There are 65 cycles per "line" (see above for comments on the obscure nature of what a line is)
        // The horizontal counter (HC) continously counts from 0 to 64, then resets back to 0.
        // 15 cycles for horizontal blanking, between HC=59 and HC=9.
        // - 4 cycles of front porch [59 -> 63]
        // - 5 cycles of hsync [63 -> 3]
        // - 0.5 cycles of breezeway [3 -> 3.5]
        // - 5 cycles colour burst [3.5 -> 8.5]
        // - 0.5 back porch [8.5 -> 9]
        // 50 cycles for visible pixels, making 200 visible pixels total [9 -> 59]
        //
        // The following are some events of note that happen for certain HC (horizontal counter) values:
        // 1: New line logic. Same as PAL.
        // 2: Horizontal Cell Counter (HCC) reloaded.
        // 3: Vertical Cell Counter (VCC) reloaded if VC=0. Same as PAL.
        // 9: Start of visible pixels. Technically they start halfway into the cycle.
        // 29: Increments VC and HLC (half-line counter). Resets VC every second field for interlaced.
        // 59: Horiz blanking starts, for visible lines.
        // 62: Increments half-line counter. Resets VC if non-interlaced, or every second field for interlaced.
        // 64: Resets HC.

        switch (horizontalCounter) {

            // HC = 0. The main reason for this having its own special case block is due to
            // the special handling for screen origin X matching when HC=0.
            case 0:
    
                // Reset pixel output buffer to be all border colour.
                pixel1 = pixel2 = pixel3 = pixel4 = pixel5 = pixel6 = pixel7 = pixel8 = 1;
                hiresMode = false;
                colourData = 0x08;
                charData = charDataLatch = 0x55;
                
                // Simplified state updates for HC=0. Counters and states still need to
                // change as appropriate, regardless of it being during blanking.
                switch (fetchState) {
                    case FETCH_OUTSIDE_MATRIX:
                        // In HC=0, it is not possible to match screen origin x, if the last
                        // line was not a matrix line. This is as per the real chip. It is 
                        // possible to match screen origin y though.
                        if ((verticalCounter >> 1) == screen_origin_y) {
                            // This is the line the video matrix starts on. As in the real chip, we use
                            // a different state for the first part of the first video matrix line.
                            fetchState = FETCH_IN_MATRIX_Y;
                        }
                        break;
                    case FETCH_IN_MATRIX_Y:
                        // Since we are now looking at prev HC, this behaves same as FETCH_MATRIX_LINE.
                    case FETCH_MATRIX_LINE:
                        // NOTE: Due to comparison being prev HC, this is matching HC=64.
                        if (prevHorizontalCounter == screen_origin_x) {
                            fetchState = FETCH_MATRIX_DLY_1;
                        }
                        break;
                    case FETCH_MATRIX_DLY_1:
                    case FETCH_MATRIX_DLY_2:
                    case FETCH_MATRIX_DLY_3:
                        fetchState++;
                        break;
                    case FETCH_SCREEN_CODE:
                        fetchState = ((horizontalCellCounter-- > 0)? FETCH_CHAR_DATA : FETCH_MATRIX_LINE);
                        break;
                    case FETCH_CHAR_DATA:
                        videoMatrixCounter++;
                        fetchState = FETCH_SCREEN_CODE;
                        break;
                }
    
                if (verticalCounter == 1) {
                    pixelCounter = 0;
    
                    synchronized (frames) {
                        // Mark the current frame as complete.
                        frames[activeFrame].ready = true;
    
                        // Toggle the active frame.
                        activeFrame = ((activeFrame + 1) % 2);
                        frames[activeFrame].ready = false;
                    }
    
                    frameRenderComplete = true;
                }
    
                prevHorizontalCounter = horizontalCounter++;
                break;
    
            // HC = 1 is another special case, handled in a single block for ALL lines. This
            // is when the "new line" signal is seen by most components.
            case 1:
    
                // Due to the "new line" signal being generated by the Horizontal Counter Reset
                // logic, and the pass transistors used within it delaying the propagation of
                // that signal, this signal doesn't get seen by components such as the Cell Depth
                // Counter Reset logic, the "In Matrix" status logic, and Video Matrix Latch,
                // until HC = 1.
    
                // The "new line" signal closes the matrix, if it is still open.
                if (fetchState >= FETCH_MATRIX_DLY_1) {
                    // The real chip appears to have another increment in this cycle, if the 
                    // state is FETCH_CHAR_DATA. Not 100% clear though, since distortion ensues
                    // when setting the registers such that the matrix closes here.
                    if (fetchState == FETCH_CHAR_DATA) {
                        videoMatrixCounter++;
                    }
                    fetchState = FETCH_MATRIX_LINE;
                }
    
                // Check for Cell Depth Counter reset.
                if ((cellDepthCounter == last_line_of_cell) || (cellDepthCounter == 0xF)) {
                    // Reset CDC.
                    cellDepthCounter = 0;
                    
                    // If last line was the last line of the character cell, then we latch
                    // the current VMC value ready for the next character row.
                    videoMatrixLatch = videoMatrixCounter;
                    
                    // Vertical Cell Counter decrements when CDC resets, unless its the first line, 
                    // since it was loaded instead (see VC reset logic in HC=2).
                    if (verticalCounter > 0) {
                        verticalCellCounter--;
    
                        if ((verticalCellCounter == 0) && (screen_origin_x > 0)) {
                            // If all text rows rendered, then we're outside the matrix again.
                            fetchState = FETCH_OUTSIDE_MATRIX;
                        } else {
                            // NOTE: Due to comparison being prev HC, this is match HC=0.
                            if (prevHorizontalCounter == screen_origin_x) {
                                // Last line was in the matrix, so start the in matrix delay.
                                fetchState = FETCH_MATRIX_DLY_1;
                            }
                        }
                    }
                }
                else if (fetchState >= FETCH_MATRIX_LINE) {
                    // If the line that just ended was a video matrix line, then increment CDC,
                    // unless the VCC is 0, in which case close the matrix.
                    if (verticalCellCounter > 0) {
                        cellDepthCounter++;
                      
                        // NOTE: Due to comparison being prev HC, this is match HC=0.
                        if (prevHorizontalCounter == screen_origin_x) {
                            // Last line was in the matrix, so start the in matrix delay.
                            fetchState = FETCH_MATRIX_DLY_1;
                        }
                    } else {
                        fetchState = FETCH_OUTSIDE_MATRIX;
                    }
                }
                else if (fetchState == FETCH_IN_MATRIX_Y) {
                    // If fetchState is already FETCH_IN_MATRIX_Y at this point, it means that the
                    // last line matched the screen origin Y but not X. This results in the
                    // matrix being rendered one line lower if X now matches, as per real chip.
                    if (prevHorizontalCounter == screen_origin_x) {
                        fetchState = FETCH_IN_MATRIX_X;
                    }
                }
                else if (fetchState == FETCH_OUTSIDE_MATRIX) {
                    if ((verticalCounter >> 1) == screen_origin_y) {
                        // This is the line the video matrix starts on. As in the real chip, we use
                        // a different state for the first part of the first video matrix line.
                        fetchState = FETCH_IN_MATRIX_Y;
                    }
                }
              
                prevHorizontalCounter = horizontalCounter++;
                break;
    
            // HC = 2 is yet another special case, handled in a single block for ALL 
            // lines. This is when the horizontal cell counter is loaded.
            case 2:
    
                // Simplified state changes. We're in hblank, so its just the bare minimum.
                switch (fetchState) {
                    case FETCH_OUTSIDE_MATRIX:
                        if ((verticalCounter >> 1) == screen_origin_y) {
                            // This is the line the video matrix starts on. As in the real chip, we use
                            // a different state for the first part of the first video matrix line.
                            fetchState = FETCH_IN_MATRIX_Y;
                        }
                        break;
                    case FETCH_IN_MATRIX_X:
                        // If screen origin x matched during HC=1, which can only mean that the screen 
                        // origin y matched on the previous line, then we move to second matrix delay 
                        // state, since the match happened in the previous cycle.
                        fetchState = FETCH_MATRIX_DLY_2;
                        break;
                    case FETCH_IN_MATRIX_Y:
                    case FETCH_MATRIX_LINE:
                        if (prevHorizontalCounter == screen_origin_x) {
                            fetchState = FETCH_MATRIX_DLY_1;
                        }
                        break;
                    case FETCH_MATRIX_DLY_1:
                    case FETCH_MATRIX_DLY_2:
                    case FETCH_MATRIX_DLY_3:
                        fetchState++;
                        break;
                    // In theory, the following states should not be possible at this point.
                    case FETCH_SCREEN_CODE:
                        fetchState = ((horizontalCellCounter-- > 0)? FETCH_CHAR_DATA : FETCH_MATRIX_LINE);
                        break;
                    case FETCH_CHAR_DATA:
                        videoMatrixCounter++;
                        fetchState = FETCH_SCREEN_CODE;
                        break;
                }
              
                // Video Matrix Counter (VMC) is reloaded from latch on "new line" signal.
                videoMatrixCounter = videoMatrixLatch;
                
                // Horizontal Cell Counter (HCC) is reloaded on "new line" signal.
                horizontalCellCounter = num_of_columns;
                prevHorizontalCounter = horizontalCounter++;
                break;
                
             // HC = 3 is yet another special case, handled in a single block for ALL 
             // lines. This is when the vertical cell counter is loaded.
            case 3:

                // Simplified state changes. We're in hblank, so its just the bare minimum.
                switch (fetchState) {
                    case FETCH_OUTSIDE_MATRIX:
                        if ((verticalCounter >> 1) == screen_origin_y) {
                            // This is the line the video matrix starts on. As in the real chip, we use
                            // a different state for the first part of the first video matrix line.
                            fetchState = FETCH_IN_MATRIX_Y;
                        }
                        break;
                    case FETCH_IN_MATRIX_Y:
                    case FETCH_MATRIX_LINE:
                        if (prevHorizontalCounter == screen_origin_x) {
                            fetchState = FETCH_MATRIX_DLY_1;
                        }
                        break;
                    case FETCH_MATRIX_DLY_1:
                    case FETCH_MATRIX_DLY_2:
                    case FETCH_MATRIX_DLY_3:
                        fetchState++;
                        break;
                    // In theory, the following states should not be possible at this point.
                    case FETCH_SCREEN_CODE:
                        fetchState = ((horizontalCellCounter-- > 0)? FETCH_CHAR_DATA : FETCH_MATRIX_LINE);
                        break;
                    case FETCH_CHAR_DATA:
                        videoMatrixCounter++;
                        fetchState = FETCH_SCREEN_CODE;
                        break;
                }
                
                // Vertical Cell Counter is loaded 2 cycles after the VC resets.
                // TODO: This probably needs to move for the 6560, as VC doesn't reset in HC=1.
                if (verticalCounter == 0) {
                    verticalCellCounter = num_of_rows;
                }
                
                prevHorizontalCounter = horizontalCounter++;
                break;
    
            // HC = 62 is one of the two increment points for the 1/2 line counter. It is also when 
            // the vertical counter resets when in non-interlaced mode, and for interlaced mode where 
            // it resets every second field. It is also one of two points where vertical blanking
            // and vertical sync can start and end.
            case 62:
                if (interlaced_mode != 0) {
                    if ((verticalCounter >= NTSC_INTL_LAST_LINE) && (halfLineCounter == 0)) {
                        // For interlaced mode, the vertical counter resets here every second field, as
                        // controlled by the half-line counter.
                        verticalCounter = 0;
                        fetchState = FETCH_OUTSIDE_MATRIX;
                        cellDepthCounter = 0;
                        halfLineCounter = 0;
    
                        // Update raster line CR value to be 0.
                        mem[VIC_REG_4] = 0;
                        mem[VIC_REG_3] &= 0x7F;
                    } else {
                        // Half line counter simply toggles between 0 and 1.
                        halfLineCounter ^= 1;
                    }
                } else {
                    if (verticalCounter == NTSC_NORM_LAST_LINE) {
                        // For non-interlaced mode, the vertical counter always resets at this point.
                        verticalCounter = 0;
                        fetchState = FETCH_OUTSIDE_MATRIX;
                        cellDepthCounter = 0;
                        halfLineCounter = 0;
    
                        // Update raster line CR value to be 0.
                        mem[VIC_REG_4] = 0;
                        mem[VIC_REG_3] &= 0x7F;
                    } else {
                        // Half line counter simply toggles between 0 and 1.
                        halfLineCounter ^= 1;
                    }
                }
    
                // Output vertical blanking or vsync, if required. If the half-line counter is 1, then 
                // vblank and vsync get delayed by half a line, i.e. to HC=29.
                if (halfLineCounter == 0) {
                    if ((verticalCounter > 0) && (verticalCounter <= NTSC_VBLANK_END)) {
                        // Vertical blanking and sync - Lines 1-9.
                        vblanking = true;
    
                        if (verticalCounter < NTSC_VSYNC_START) {
                            // Lines 1, 2, 3.
                            pio_sm_put(CVBS_PIO, CVBS_SM, NTSC_SHORT_SYNC_L);
                            pio_sm_put(CVBS_PIO, CVBS_SM, NTSC_SHORT_SYNC_H);
                            pio_sm_put(CVBS_PIO, CVBS_SM, NTSC_SHORT_SYNC_L);
                            pio_sm_put(CVBS_PIO, CVBS_SM, NTSC_SHORT_SYNC_H);
                        } else if (verticalCounter <= NTSC_VSYNC_END) {
                            // Vertical sync, lines 4, 5, 6.
                            pio_sm_put(CVBS_PIO, CVBS_SM, NTSC_LONG_SYNC_L);
                            pio_sm_put(CVBS_PIO, CVBS_SM, NTSC_LONG_SYNC_H);
                            pio_sm_put(CVBS_PIO, CVBS_SM, NTSC_LONG_SYNC_L);
                            pio_sm_put(CVBS_PIO, CVBS_SM, NTSC_LONG_SYNC_H);
    
                            // Vertical sync is what resets the video matrix latch.
                            videoMatrixLatch = videoMatrixCounter = 0;
                        } else {
                            // Lines 7, 8, 9.
                            pio_sm_put(CVBS_PIO, CVBS_SM, NTSC_SHORT_SYNC_L);
                            pio_sm_put(CVBS_PIO, CVBS_SM, NTSC_SHORT_SYNC_H);
                            pio_sm_put(CVBS_PIO, CVBS_SM, NTSC_SHORT_SYNC_L);
                            pio_sm_put(CVBS_PIO, CVBS_SM, NTSC_SHORT_SYNC_H);
                        }
                    } else {
                        vblanking = false;
                    }
                }
    
                // If we're not in vertical blanking, i.e. we didn't output the CVBS commands above, 
                // then we continue horizontal blanking commands instead, including hsync and colour 
                // burst. It will end at HC=9
                if (!vblanking) {
                    pio_sm_put(CVBS_PIO, CVBS_SM, NTSC_FRONTPORCH_2);
                    pio_sm_put(CVBS_PIO, CVBS_SM, NTSC_HSYNC);
                    pio_sm_put(CVBS_PIO, CVBS_SM, NTSC_BREEZEWAY);
                    if (oddLine) {
                        // Odd line. Switch palette starting offset.
                        pIndex = 2;
                        pio_sm_put(CVBS_PIO, CVBS_SM, NTSC_BURST_O);
                    } else {
                        // Even line. Switch palette starting offset.
                        pIndex = 6;
                        pio_sm_put(CVBS_PIO, CVBS_SM, NTSC_BURST_E);
                    }
                    pio_sm_put(CVBS_PIO, CVBS_SM, NTSC_BACKPORCH);
                }
                oddLine = !oddLine;
    
                //
                // IMPORTANT: THE HC=62 CASE STATEMENT DELIBERATELY FALLS THROUGH TO NEXT BLOCK.
                //
    
            // These HC values are always in blanking and have no special behaviour other than
            // the standard state changes common to all cycles.
            case 60:
            case 61:
            case 63:
                // Simplified state changes. We're in hblank, so its just the bare minimum.
                switch (fetchState) {
                    case FETCH_OUTSIDE_MATRIX:
                        if ((verticalCounter >> 1) == screen_origin_y) {
                            // This is the line the video matrix starts on. As in the real chip, we use
                            // a different state for the first part of the first video matrix line.
                            fetchState = FETCH_IN_MATRIX_Y;
                        }
                        break;
                    case FETCH_IN_MATRIX_Y:
                    case FETCH_MATRIX_LINE:
                        if (prevHorizontalCounter == screen_origin_x) {
                            fetchState = FETCH_MATRIX_DLY_1;
                        }
                        break;
                    case FETCH_MATRIX_DLY_1:
                    case FETCH_MATRIX_DLY_2:
                    case FETCH_MATRIX_DLY_3:
                        fetchState++;
                        break;
                    case FETCH_SCREEN_CODE:
                        fetchState = ((horizontalCellCounter-- > 0)? FETCH_CHAR_DATA : FETCH_MATRIX_LINE);
                        break;
                    case FETCH_CHAR_DATA:
                        videoMatrixCounter++;
                        fetchState = FETCH_SCREEN_CODE;
                        break;
                }
                prevHorizontalCounter = horizontalCounter++;
                break;
    
            // HC = 64 is the last HC value, so triggers HC reset.
            case 64:
                // Simplified state changes. We're in hblank, so its just the bare minimum.
                switch (fetchState) {
                    case FETCH_OUTSIDE_MATRIX:
                        if ((verticalCounter >> 1) == screen_origin_y) {
                            // This is the line the video matrix starts on. As in the real chip, we use
                            // a different state for the first part of the first video matrix line.
                            fetchState = FETCH_IN_MATRIX_Y;
                        }
                        break;
                    case FETCH_IN_MATRIX_Y:
                    case FETCH_MATRIX_LINE:
                        if (prevHorizontalCounter == screen_origin_x) {
                            fetchState = FETCH_MATRIX_DLY_1;
                        }
                        break;
                    case FETCH_MATRIX_DLY_1:
                    case FETCH_MATRIX_DLY_2:
                    case FETCH_MATRIX_DLY_3:
                        fetchState++;
                        break;
                    case FETCH_SCREEN_CODE:
                        fetchState = ((horizontalCellCounter-- > 0)? FETCH_CHAR_DATA : FETCH_MATRIX_LINE);
                        break;
                    case FETCH_CHAR_DATA:
                        videoMatrixCounter++;
                        fetchState = FETCH_SCREEN_CODE;
                        break;
                } 

                // And then reset HC.
                prevHorizontalCounter = horizontalCounter;
                horizontalCounter = 0;
                break;
    
            // HC=29 is when the 6560 increments the vertical counter (VC). The 1/2 line counter also
            // toggles at this time. This is therefore the end of the raster line as reported by
            // the VIC registers, but is not the actual end of the raster, as that happens when the 
            // hsync occurs at HC=62.
            case 29:
                // NOTE: The VC always resets at HC=62 when in non-interlaced mode, but for interlaced,
                // it can reset in HC=29 as controlled by the half-line counter.
                if ((interlaced_mode != 0) && (verticalCounter == NTSC_INTL_LAST_LINE) && (halfLineCounter == 0)) {
                    // For interlaced mode, the vertical counter resets every second field at HC=29.
                    verticalCounter = 0;
                    fetchState = FETCH_OUTSIDE_MATRIX;
                    cellDepthCounter = 0;
                    halfLineCounter = 0;
                } else {
                    // Otherwise increment vertical counter.
                    verticalCounter++;
    
                    // Half line counter simply toggles between 0 and 1.
                    halfLineCounter ^= 1;
                }
    
                // Update the raster line value stored in the VIC registers. Note that this is
                // correct for NTSC, i.e. the VIC control registers for the raster value do
                // change at HC=29 (not at HC=1 like PAL does). It can also change at HC=62,
                // if the VC is reset to 0 during that cycle.
                mem[VIC_REG_4] = (verticalCounter >> 1);
                if ((verticalCounter & 0x01) == 0) {
                    mem[VIC_REG_3] &= 0x7F;
                } else {
                    mem[VIC_REG_3] |= 0x80;
                }
    
                // Output vertical blanking or vsync, if required. If the half-line counter is 1, then 
                // vblank and vsync get delayed by half a line, i.e. to HC=62. 
                // Actual FIFO submission moved to the end of the cycle (default case) to counter FIFO overruns. 
                if (halfLineCounter == 0) {
                    if ((verticalCounter > 0) && (verticalCounter <= NTSC_VBLANK_END)) {
                        // Vertical blanking and sync - Lines 1-9.
                        vblanking = true;
    
                        if (verticalCounter < NTSC_VSYNC_START) {
                            // Lines 1, 2, 3.
                            do_vblank = DO_VBLANK_SHORT;
                        }
                        else if (verticalCounter <= NTSC_VSYNC_END) {
                            // Vertical sync, lines 4, 5, 6.
                            do_vblank = DO_VBLANK_LONG;

                            // Vertical sync is what resets the video matrix latch.
                            videoMatrixLatch = videoMatrixCounter = 0;
                        }
                        else {
                            // Lines 7, 8, 9.
                            do_vblank = DO_VBLANK_SHORT;
                        }
                    } else {
                        vblanking = false;
                    }
                }
    
                //
                // IMPORTANT: THE HC=29 CASE STATEMENT DELIBERATELY FALLS THROUGH TO THE DEFAULT BLOCK.
                //
    
            // Covers from HC=4 to HC=59.
            default:
                // Line 0, and Lines after 9, are "visible", i.e. not within the vertical blanking.
                if (!vblanking) {
                    // Is the visible part of the line ending now and horizontal blanking starting?
                    if (horizontalCounter == NTSC_HBLANK_START) {
                        // Horizontal blanking starts here. Simplified state changes, so its just the bare minimum.
                        switch (fetchState) {
                            case FETCH_OUTSIDE_MATRIX:
                                if ((verticalCounter >> 1) == screen_origin_y) {
                                    // This is the line the video matrix starts on. As in the real chip, we use
                                    // a different state for the first part of the first video matrix line.
                                    fetchState = FETCH_IN_MATRIX_Y;
                                }
                                break;
                            case FETCH_IN_MATRIX_Y:
                            case FETCH_MATRIX_LINE:
                                if (prevHorizontalCounter == screen_origin_x) {
                                    fetchState = FETCH_MATRIX_DLY_1;
                                }
                                break;
                                
                            case FETCH_MATRIX_DLY_1:
                            case FETCH_MATRIX_DLY_2:
                            case FETCH_MATRIX_DLY_3:
                                fetchState++;
                                break;
                                
                            case FETCH_SCREEN_CODE:
                                fetchState = ((horizontalCellCounter-- > 0)? FETCH_CHAR_DATA : FETCH_MATRIX_LINE);
                                break;
                          
                            case FETCH_CHAR_DATA:
                                // If the matrix hasn't yet closed, then in the FETCH_CHAR_DATA 
                                // state, we need to keep incrementing the video matrix counter
                                // until it is closed, which at the latest could be HC=1 on the
                                // next line.
                                videoMatrixCounter++;
                                
                                fetchState = FETCH_SCREEN_CODE;
                                break;
                        }
    
                        // We output the start of horiz blanking here, enough of it to last to the start
                        // of HC=62, where a decision is then made as to whether it will be horizontal
                        // blanking or vertical blanking. This is why there is a part 1 and 2 of the front
                        // porch.
                        pio_sm_put(CVBS_PIO, CVBS_SM, NTSC_FRONTPORCH_1);
    
                        // Unlike PAL, for NTSC hblank starts 6 cycles before the HC reset, so we increment.
                        prevHorizontalCounter = horizontalCounter++;
                    } else {
                        // Covers visible line cycles from HC=4 to HC=58, i.e. 1 cycle before HBLANK start.
                        switch (fetchState) {
                            case FETCH_OUTSIDE_MATRIX:
                                if ((verticalCounter >> 1) == screen_origin_y) {
                                    // This is the line the video matrix starts on. As in the real chip, we use
                                    // a different state for the first part of the first video matrix line.
                                    fetchState = FETCH_IN_MATRIX_Y;
                                }
                                if (horizontalCounter >= NTSC_HBLANK_END) {
                                    borderColourIndex = border_colour_index;
                                    pio_sm_put(CVBS_PIO, CVBS_SM, palette[(pIndex++ & 0x7)][borderColourIndex]);
                                    pio_sm_put(CVBS_PIO, CVBS_SM, palette[(pIndex++ & 0x7)][borderColourIndex]);
                                    pio_sm_put(CVBS_PIO, CVBS_SM, palette[(pIndex++ & 0x7)][borderColourIndex]);
                                    pio_sm_put(CVBS_PIO, CVBS_SM, palette[(pIndex++ & 0x7)][borderColourIndex]);
                                }
                                // Nothing to do otherwise. Still in blanking if below 12.
                                break;
        
                            case FETCH_IN_MATRIX_Y:
                            case FETCH_MATRIX_LINE:
                                if (horizontalCounter >= NTSC_HBLANK_END) {
                                    // Look up very latest background, border and auxiliary colour values. This
                                    // should not include an update to the foreground colour, as that will not 
                                    // have changed.
                                    multiColourTable[0] = background_colour_index;
                                    multiColourTable[1] = border_colour_index;
                                    multiColourTable[3] = auxiliary_colour_index;
                                    
                                    pio_sm_put(CVBS_PIO, CVBS_SM, palette[(pIndex++ & 0x7)][multiColourTable[pixel2]]);
                                    pio_sm_put(CVBS_PIO, CVBS_SM, palette[(pIndex++ & 0x7)][multiColourTable[pixel3]]);
                                    
                                    // Pixels 4-7 calculations are less complex, since the hires mode,
                                    // reverse mode and char data stay the same four all four pixels.
                                    if (hiresMode) {
                                        if (non_reverse_mode != 0) {
                                            pixel4 = ((charData & 0x10) > 0 ? 2 : 0);
                                            pixel5 = ((charData & 0x08) > 0 ? 2 : 0);
                                            pixel6 = ((charData & 0x04) > 0 ? 2 : 0);
                                            pixel7 = ((charData & 0x02) > 0 ? 2 : 0);
                                        } else {
                                            pixel4 = ((charData & 0x10) > 0 ? 0 : 2);
                                            pixel5 = ((charData & 0x08) > 0 ? 0 : 2);
                                            pixel6 = ((charData & 0x04) > 0 ? 0 : 2);
                                            pixel7 = ((charData & 0x02) > 0 ? 0 : 2);
                                        }
                                    } else {
                                        // Multicolour graphics.
                                        pixel4 = ((charData >> 4) & 0x03);
                                        pixel5 = pixel6 = ((charData >> 2) & 0x03);
                                        pixel7 = (charData & 0x03);
                                    }

                                    // Pixels 4 & 5 have to be output after the pixel var calculations above.
                                    pio_sm_put(CVBS_PIO, CVBS_SM, palette[(pIndex++ & 0x7)][multiColourTable[pixel4]]);
                                    pio_sm_put(CVBS_PIO, CVBS_SM, palette[(pIndex++ & 0x7)][multiColourTable[pixel5]]);
                                  
                                    // Rotate pixels so that the other 3 remaining char pixels are output
                                    // and then border colours takes over after that.
                                    pixel2 = pixel6;
                                    pixel3 = pixel7;
                                    pixel4 = pixel8;
                                    pixel5 = pixel6 = pixel7 = pixel8 = pixel1 = 1;

                                    if (prevHorizontalCounter == screen_origin_x) {
                                        // Last 4 pixels before first char renders are still border.
                                        fetchState = FETCH_MATRIX_DLY_1;
                                    }
                                }
                                else if (prevHorizontalCounter == screen_origin_x) {
                                    // Still in horizontal blanking, but we still need to prepare for the case
                                    // where the next cycle isn't in horiz blanking, i.e. when HC=8 this cycle.
                                    fetchState = FETCH_MATRIX_DLY_1;
                                }
                                
                                hiresMode = false;
                                colourData = 0x08;
                                charData = charDataLatch = 0x55;
                                break;
        
                            case FETCH_MATRIX_DLY_1:
                            case FETCH_MATRIX_DLY_2:
                            case FETCH_MATRIX_DLY_3:
                                if (horizontalCounter >= NTSC_HBLANK_END) {
                                    // Output border pixels.
                                    borderColourIndex = border_colour_index;
                                    pio_sm_put(CVBS_PIO, CVBS_SM, palette[(pIndex++ & 0x7)][borderColourIndex]);
                                    pio_sm_put(CVBS_PIO, CVBS_SM, palette[(pIndex++ & 0x7)][borderColourIndex]);
                                    pio_sm_put(CVBS_PIO, CVBS_SM, palette[(pIndex++ & 0x7)][borderColourIndex]);
                                    pio_sm_put(CVBS_PIO, CVBS_SM, palette[(pIndex++ & 0x7)][borderColourIndex]);
                                }
                                else {
                                    pixel2 = pixel3 = pixel4 = pixel5 = pixel6 = pixel7 = pixel8 = 1;
                                }

                                // Prime the pixel output queue with border pixels in multicolour 
                                // mode. Not quite what the real chip does but is functionally equivalent.
                                hiresMode = false;
                                colourData = 0x08;
                                charDataLatch = 0x55;

                                fetchState++;
                                break;
        
                            case FETCH_SCREEN_CODE:
                                
                                // Look up very latest background, border and auxiliary colour values.
                                multiColourTable[0] = background_colour_index;
                                multiColourTable[1] = border_colour_index;
                                multiColourTable[3] = auxiliary_colour_index;
                            
                                // Output last 3 pixels of the last character. These had already left 
                                // the shift register but in the delay path to the colour lookup.
                                if (horizontalCounter >= NTSC_HBLANK_END) {
                                    pio_sm_put(CVBS_PIO, CVBS_SM, palette[(pIndex++ & 0x7)][multiColourTable[pixel6]]);
                                    pio_sm_put(CVBS_PIO, CVBS_SM, palette[(pIndex++ & 0x7)][multiColourTable[pixel7]]);
                                }
                                
                                if (non_reverse_mode != 0) {
                                    // New non-reversed mode value kicks in a pixel before new character.
                                    if (hiresMode) {
                                        pixel8 = ((charData & 0x01) > 0? 2 : 0);
                                    } else {
                                        pixel8 = (charData & 0x03);
                                    }
                                    
                                    // Update the operating hires state and char data immediately prior to
                                    // shifting out new character pixel.
                                    hiresMode = ((colourData & 0x08) == 0);
                                    charData = charDataLatch;
                                    
                                    // Pixel 1 should be same non-reversed mode but pick up the new hires mode.
                                    if (hiresMode) {
                                        pixel1 = ((charData & 0x80) > 0? 2 : 0);
                                        pixel2 = ((charData & 0x40) > 0? 2 : 0);
                                        pixel3 = ((charData & 0x20) > 0? 2 : 0);
                                    } else {
                                        pixel1 = pixel2 = ((charData >> 6) & 0x03);
                                        pixel3 = ((charData >> 4) & 0x03);
                                    }
                                } else {
                                    // New reversed mode value kicks in a pixel before new character.
                                    if (hiresMode) {
                                        pixel8 = ((charData & 0x01) > 0? 0 : 2);
                                    } else {
                                        pixel8 = (charData & 0x03);
                                    }
                                    
                                    // Update the operating hires state and char data immediately prior to
                                    // shifting out new character pixel.
                                    hiresMode = ((colourData & 0x08) == 0);
                                    charData = charDataLatch;
                                    
                                    // Pixel 1 should be same reversed mode but pick up the new hires mode.
                                    if (hiresMode) {
                                        pixel1 = ((charData & 0x80) > 0? 0 : 2);
                                        pixel2 = ((charData & 0x40) > 0? 0 : 2);
                                        pixel3 = ((charData & 0x20) > 0? 0 : 2);
                                    } else {
                                        pixel1 = pixel2 = ((charData >> 6) & 0x03);
                                        pixel3 = ((charData >> 4) & 0x03);
                                    }
                                }
                              
                                // The 3rd pixel is from the previous character with new reverse mode applied (see above).
                                if (horizontalCounter >= NTSC_HBLANK_END) {
                                    pio_sm_put(CVBS_PIO, CVBS_SM, palette[(pIndex++ & 0x7)][multiColourTable[pixel8]]);
                                }
                                
                                // Look up foreground colour before outputting first pixel.
                                multiColourTable[2] = (colourData & 0x07);

                                // TODO: Replace with unconnected memory version above.
                                cellIndex = mem[screen_mem_start + videoMatrixCounter];

                                // Due to the way the colour memory is wired up, the above fetch of the cell index
                                // also happens to automatically fetch the foreground colour from the Colour Matrix
                                // via the top 4 lines of the data bus (DB8-DB11), which are wired directly from 
                                // colour RAM in to the VIC chip.
                                colourData = mem[colour_mem_start + videoMatrixCounter];

                                // Output the 1st pixel of next character. Note that this is not the character
                                // that relates to the cell index and colour data fetched above.
                                if (horizontalCounter >= NTSC_HBLANK_END) {
                                    pio_sm_put(CVBS_PIO, CVBS_SM, palette[(pIndex++ & 0x7)][multiColourTable[pixel1]]);
                                }

                                // Toggle fetch state. Close matrix if HCC hits zero.
                                fetchState = ((horizontalCellCounter-- > 0)? FETCH_CHAR_DATA : FETCH_MATRIX_LINE);
                                break;
        
                            case FETCH_CHAR_DATA:
                                
                                // Look up very latest background, border and auxiliary colour values.
                                multiColourTable[0] = background_colour_index;
                                multiColourTable[1] = border_colour_index;
                                multiColourTable[3] = auxiliary_colour_index;
                                
                                if (horizontalCounter >= NTSC_HBLANK_END) {
                                    pio_sm_put(CVBS_PIO, CVBS_SM, palette[(pIndex++ & 0x7)][multiColourTable[pixel2]]);
                                    pio_sm_put(CVBS_PIO, CVBS_SM, palette[(pIndex++ & 0x7)][multiColourTable[pixel3]]);
                                    
                                }
                                
                                // Calculate offset of data.
                                charDataOffset = char_mem_start + (cellIndex << char_size_shift) + cellDepthCounter;
                                
                                // Adjust offset for memory wrap around.
                                if ((char_mem_start < 8192) && (charDataOffset >= 8192)) {
                                    charDataOffset += 24576;
                                }
                                
                                // TODO: Add unconnected memory check here.
                                // Fetch cell data, initially latched to the side until it is needed.
                                charDataLatch = mem[charDataOffset];
                                
                                // Pixels 4-7 calculations are less complex, since the hires mode,
                                // reverse mode and char data stay the same four all four pixels.
                                if (hiresMode) {
                                    if (non_reverse_mode != 0) {
                                        pixel4 = ((charData & 0x10) > 0 ? 2 : 0);
                                        pixel5 = ((charData & 0x08) > 0 ? 2 : 0);
                                        pixel6 = ((charData & 0x04) > 0 ? 2 : 0);
                                        pixel7 = ((charData & 0x02) > 0 ? 2 : 0);
                                    } else {
                                        pixel4 = ((charData & 0x10) > 0 ? 0 : 2);
                                        pixel5 = ((charData & 0x08) > 0 ? 0 : 2);
                                        pixel6 = ((charData & 0x04) > 0 ? 0 : 2);
                                        pixel7 = ((charData & 0x02) > 0 ? 0 : 2);
                                    }
                                } else {
                                    // Multicolour graphics.
                                    pixel4 = ((charData >> 4) & 0x03);
                                    pixel5 = pixel6 = ((charData >> 2) & 0x03);
                                    pixel7 = (charData & 0x03);
                                }
                                
                                if (horizontalCounter >= NTSC_HBLANK_END) {
                                    pio_sm_put(CVBS_PIO, CVBS_SM, palette[(pIndex++ & 0x7)][multiColourTable[pixel4]]);
                                    pio_sm_put(CVBS_PIO, CVBS_SM, palette[(pIndex++ & 0x7)][multiColourTable[pixel5]]);
                                }

                                // Increment the video matrix counter to next cell.
                                videoMatrixCounter++;
                                
                                // Toggle fetch state. For efficiency, HCC deliberately not checked here.
                                fetchState = FETCH_SCREEN_CODE;
                                break;
                        }
    
                        prevHorizontalCounter = horizontalCounter++;
                    }
                } else {
                    // Inside vertical blanking. The CVBS commands for each line were already sent during 
                    // HC=62 or decided in HC=29 and output here (to avoid FIFO overruns)
                    // In case the screen origin Y is set within the vertical blanking
                    // lines, we still need to update the fetch state, video matrix counter, and the horizontal
                    // cell counter, even though we're not outputting character pixels. So for the rest of the 
                    // line, it is a simplified version of the standard line, except that we don't output 
                    // any pixels.
                    switch (fetchState) {
                        case FETCH_OUTSIDE_MATRIX:
                            if ((verticalCounter >> 1) == screen_origin_y) {
                                // This is the line the video matrix starts on. As in the real chip, we use
                                // a different state for the first part of the first video matrix line.
                                fetchState = FETCH_IN_MATRIX_Y;
                            }
                            break;
                        case FETCH_IN_MATRIX_Y:
                        case FETCH_MATRIX_LINE:
                            if (prevHorizontalCounter == screen_origin_x) {
                                fetchState = FETCH_MATRIX_DLY_1;
                            }
                            break;
                        case FETCH_MATRIX_DLY_1:
                        case FETCH_MATRIX_DLY_2:
                        case FETCH_MATRIX_DLY_3:
                            fetchState++;
                            break;
                        case FETCH_SCREEN_CODE:
                            fetchState = ((horizontalCellCounter-- > 0)? FETCH_CHAR_DATA : FETCH_MATRIX_LINE);
                            break;
                        case FETCH_CHAR_DATA:
                            videoMatrixCounter++;
                            fetchState = FETCH_SCREEN_CODE;
                            break;
                    }

                    // Delayed FIFO put to avoid overrun
                    if (do_vblank == DO_VBLANK_LONG){
                        pio_sm_put(CVBS_PIO, CVBS_SM, NTSC_LONG_SYNC_L);
                        pio_sm_put(CVBS_PIO, CVBS_SM, NTSC_LONG_SYNC_H);
                        pio_sm_put(CVBS_PIO, CVBS_SM, NTSC_LONG_SYNC_L);
                        pio_sm_put(CVBS_PIO, CVBS_SM, NTSC_LONG_SYNC_H);
                        do_vblank = 0;
                    }
                    if (do_vblank == DO_VBLANK_SHORT){
                        pio_sm_put(CVBS_PIO, CVBS_SM, NTSC_SHORT_SYNC_L);
                        pio_sm_put(CVBS_PIO, CVBS_SM, NTSC_SHORT_SYNC_H);
                        pio_sm_put(CVBS_PIO, CVBS_SM, NTSC_SHORT_SYNC_L);
                        pio_sm_put(CVBS_PIO, CVBS_SM, NTSC_SHORT_SYNC_H);
                        do_vblank = 0;
                    }
    
                    // The horizontal counter reset always happens within the top level case 64 statement, so
                    // we only need to cater for HC increments here.
                    prevHorizontalCounter = horizontalCounter++;
                }
                break;
        }

        return frameRenderComplete;
    }
}
