package emu.jvic.video;

import emu.jvic.MachineType;
import emu.jvic.PixelData;
import emu.jvic.snap.Snapshot;

/**
 * This class emulates a special PIVIC44 mode where the pixels are output at twice the speed.
 */
public class Vic44 extends Vic {
    
    // Constants related to video timing for PAL.
    private static final int PAL_HBLANK_END = 12;
    private static final int PAL_HBLANK_START = 70;
    private static final int PAL_VBLANK_START = 1;
    private static final int PAL_VSYNC_START = 4;
    private static final int PAL_VSYNC_END = 6;
    private static final int PAL_VBLANK_END = 9;
    private static final int PAL_LAST_LINE = 311;
    
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
    
    private static final int PAL_FRONTPORCH_1 = (8 << 0);      // From 0 to 10 
    private static final int PAL_FRONTPORCH_2 = (6 << 0);      // From 1 to 1.75
    private static final int PAL_HSYNC = (40 << 0);            // From 1.75 to 6.75
    private static final int PAL_BREEZEWAY = (6 << 0);         // From 6.75 to 7.5
    private static final int PAL_COLBURST_O = (34 << 0);       // From 7.5 to 11.75
    private static final int PAL_COLBURST_E = (34 << 0);       // From 7.5 to 11.75
    private static final int PAL_BACKPORCH = (8 << 0);         // From 11.75 to 12.75
    
    // Vertical blanking and sync.
    private static final int PAL_LONG_SYNC_L = (266 << 0);
    private static final int PAL_LONG_SYNC_H = (18 << 0);
    private static final int PAL_SHORT_SYNC_L = (18 << 0);
    private static final int PAL_SHORT_SYNC_H = (266 << 0);

    private final static int palOddRGBA8888Colours[] = { 
            0x000000FF, // BLACK
            0xFFFFFFFF, // WHITE
            0x942741FF, // RED
            0x66EAB5FF, // CYAN
            0xA13ED2FF, // PURPLE
            0x69DC30FF, // GREEN
            0x2C3BC1FF, // BLUE
            0xE1E631FF, // YELLOW
            0xBF6531FF, // ORANGE
            0xD5C382FF, // LIGHT ORANGE
            0xD2AB8EFF, // PINK
            0xC4EDFFFF, // LIGHT CYAN
            0xE3A8D3FF, // LIGHT PURPLE
            0xADF2C0FF, // LIGHT GREEN
            0xAE99E9FF, // LIGHT BLUE
            0xE7FFBEFF  // LIGHT YELLOW
    };
    
    private final static int palEvenRGBA8888Colours[] = { 
            0x000000FF, // BLACK
            0xFFFFFFFF, // WHITE
            0x982D18FF, // RED
            0x73D5FFFF, // CYAN
            0xB63BA7FF, // PURPLE
            0x54D87DFF, // GREEN
            0x502DA8FF, // BLUE
            0xD0E65CFF, // YELLOW
            0xB47017FF, // ORANGE
            0xE8B0B1FF, // LIGHT ORANGE
            0xDC9CC1FF, // PINK
            0xB3FCDDFF, // LIGHT CYAN
            0xD6A7F9FF, // LIGHT PURPLE
            0xBAF2A1FF, // LIGHT GREEN
            0xA0A4D8FF, // LIGHT BLUE
            0xFFF2C2FF  // LIGHT YELLOW
    };

    private int[] pal_palette_o = palOddRGBA8888Colours;
    private int[] pal_palette_e = palEvenRGBA8888Colours;
    
    // Reference that alternates on each line between even and odd PAL palettes.
    private int[] pal_palette = pal_palette_e;
    private int[] pal_trunc_palette = pal_palette;
    
    private static final int VIC44_COLOUR_RAM_BASE_ADDRESS = 0x9400;
    private int colourRamBaseAddress;
    
    /**
     * Constructor for Vic44.
     * 
     * @param pixelData Interface to the platform specific mechanism for writing pixels.
     * @param machineType The type of machine, PAL or NTSC.
     * @param snapshot    Optional snapshot of the machine state to start with.
     */
    public Vic44(PixelData pixelData, MachineType machineType, Snapshot snapshot) {
        this(pixelData, machineType, snapshot, VIC44_COLOUR_RAM_BASE_ADDRESS);
    }
    
    /**
     * Constructor for Vic44.
     * 
     * @param pixelData Interface to the platform specific mechanism for writing pixels.
     * @param machineType The type of machine, PAL or NTSC.
     * @param snapshot    Optional snapshot of the machine state to start with.
     * @param colourRamBaseAddress The base address for colour RAM.
     */
    public Vic44(PixelData pixelData, MachineType machineType, Snapshot snapshot, int colourRamBaseAddress) {
        super(pixelData, machineType, snapshot);
        this.colourRamBaseAddress = colourRamBaseAddress;
    }
    
    /**
     * Emulates single PAL VIC cycle.
     * 
     * @return true if a frame render has just finished; otherwise false.
     */
    public boolean emulateCycle() {
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
        int screen_mem_start = (((mem[VIC_REG_5] & 0xF0) << 6) | ((mem[VIC_REG_2] & 0x80) << 2));
        int char_mem_start = ((mem[VIC_REG_5] & 0x0F) << 10);

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
                            
                            // Screen origin X can match in the same cycle as Y.
                            if (prevHorizontalCounter == screen_origin_x) {
                                fetchState = FETCH_MATRIX_DLY_1;
                            }
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
                        fetchState = ((horizontalCellCounter-- > 0) ? FETCH_CHAR_DATA : FETCH_MATRIX_END);
                        break;
                    case FETCH_CHAR_DATA:
                        videoMatrixCounter++;
                        fetchState = FETCH_SCREEN_CODE;
                        break;
                    case FETCH_MATRIX_END:
                        fetchState = FETCH_MATRIX_LINE;
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
                        
                        // Screen origin X can match in the same cycle as Y.
                        if (prevHorizontalCounter == screen_origin_x) {
                            fetchState = FETCH_MATRIX_DLY_1;
                        }
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
                            
                            // Screen origin X can match in the same cycle as Y.
                            if (prevHorizontalCounter == screen_origin_x) {
                                fetchState = FETCH_MATRIX_DLY_1;
                            }
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
                        fetchState = ((horizontalCellCounter-- > 0) ? FETCH_CHAR_DATA : FETCH_MATRIX_END);
                        break;
                    case FETCH_CHAR_DATA:
                        videoMatrixCounter++;
                        fetchState = FETCH_SCREEN_CODE;
                        break;
                    case FETCH_MATRIX_END:
                        fetchState = FETCH_MATRIX_LINE;
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
                            
                            // Screen origin X can match in the same cycle as Y.
                            if (prevHorizontalCounter == screen_origin_x) {
                                fetchState = FETCH_MATRIX_DLY_1;
                            }
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
                        fetchState = ((horizontalCellCounter-- > 0) ? FETCH_CHAR_DATA : FETCH_MATRIX_END);
                        break;
                    case FETCH_CHAR_DATA:
                        videoMatrixCounter++;
                        fetchState = FETCH_SCREEN_CODE;
                        break;
                    case FETCH_MATRIX_END:
                        fetchState = FETCH_MATRIX_LINE;
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
                                    
                                    // Screen origin X can match in the same cycle as Y.
                                    if (prevHorizontalCounter == screen_origin_x) {
                                        fetchState = FETCH_MATRIX_DLY_1;
                                    }
                                }
                                borderColour = border_colour_index;
                                pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[borderColour]);
                                pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[borderColour]);
                                pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[borderColour]);
                                pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[borderColour]);
                                pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[borderColour]);
                                pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[borderColour]);
                                pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[borderColour]);
                                pio_sm_put(CVBS_PIO, CVBS_SM, pal_trunc_palette[borderColour]);
                                break;
                            
                            case FETCH_MATRIX_LINE:
                                // Look up latest background, border and auxiliary colours.
                                multiColourTable[0] = background_colour_index;
                                multiColourTable[1] = borderColour = border_colour_index;
                                multiColourTable[3] = auxiliary_colour_index;
                                
                                pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[multiColourTable[pixel2]]);
                                pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[multiColourTable[pixel3]]);
                                pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[multiColourTable[pixel4]]);
                                pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[multiColourTable[pixel5]]);
                                pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[borderColour]);
                                pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[borderColour]);
                                pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[borderColour]);
                                pio_sm_put(CVBS_PIO, CVBS_SM, pal_trunc_palette[borderColour]);
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
                                pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[borderColour]);
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
                                
                                fetchState = ((horizontalCellCounter-- > 0) ? FETCH_CHAR_DATA : FETCH_MATRIX_END);
        
                            case FETCH_CHAR_DATA:
                            case FETCH_MATRIX_END:
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
                                
                                if (fetchState == FETCH_MATRIX_END) {
                                    // Leaving the matrix
                                    fetchState = FETCH_MATRIX_LINE;
                                } else {
                                    // Increment the video matrix counter to next cell.
                                    videoMatrixCounter++;
                                    
                                    // Toggle fetch state. For efficiency, HCC deliberately not checked here.
                                    fetchState = FETCH_SCREEN_CODE;
                                }
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
                                    
                                    // Screen origin X can match in the same cycle as Y.
                                    if (prevHorizontalCounter == screen_origin_x) {
                                        fetchState = FETCH_MATRIX_DLY_1;
                                    }
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
                                        pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[borderColour]);
                                        pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[borderColour]);
                                        pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[borderColour]);
                                    }
                                    pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[borderColour]);
                                    pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[borderColour]);
                                }
                                break;
        
                            case FETCH_IN_MATRIX_Y:
                            case FETCH_MATRIX_LINE:
                                if (horizontalCounter >= PAL_HBLANK_END) {
                                    
                                    // Look up very latest background, border and auxiliary colour values.
                                    multiColourTable[0] = background_colour_index;
                                    multiColourTable[1] = border_colour_index;
                                    multiColourTable[3] = auxiliary_colour_index;
                    
                                    if (horizontalCounter > PAL_HBLANK_END) {
                                        pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[multiColourTable[pixel6]]);
                                        pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[multiColourTable[pixel7]]);
                                    }
                    
                                    // Handle the last pixel of the last char of the current matrix row.
                                    if (hiresMode) {
                                        if (non_reverse_mode != 0) {
                                            pixel8 = ((charData & 0x01) > 0? 2 : 0);
                                        } else {
                                            pixel8 = ((charData & 0x01) > 0? 0 : 2);
                                        }
                                    } else {
                                        pixel8 = (charData & 0x03);
                                    }
                                    
                                    hiresMode = false;
                                    colourData = 0x08;
                                    charData = charDataLatch = 0x55;
                                    pixel1 = ((charData >> 6) & 0x03);
                                    
                                    if (horizontalCounter > PAL_HBLANK_END) {
                                        pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[multiColourTable[pixel8]]);
                                        pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[multiColourTable[pixel1]]);
                                    }
                                    
                                    pixel6 = pixel2 = pixel1;
                                    pixel7 = pixel3 = ((charData >> 4) & 0x03);
                                    pixel8 = pixel1 = pixel2 = pixel3 = pixel4 = pixel5 = 1;
                                    
                                    if (horizontalCounter > PAL_HBLANK_END) {
                                        pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[multiColourTable[pixel2]]);
                                        pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[multiColourTable[pixel3]]);
                                    }
                                    pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[multiColourTable[pixel4]]);
                                    pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[multiColourTable[pixel5]]);
        
                                    if (prevHorizontalCounter == screen_origin_x) {
                                        fetchState = FETCH_MATRIX_DLY_1;
                                    }
                                } else if (prevHorizontalCounter == screen_origin_x) {
                                    // Still in horizontal blanking, but we still need to prepare for the case
                                    // where the next cycle isn't in horiz blanking, i.e. when HC=11 this cycle.
                                    fetchState = FETCH_MATRIX_DLY_1;
                                }
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
                                        pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[borderColour]);
                                        pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[borderColour]);
                                        pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[borderColour]);
                                    }
                                    pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[borderColour]);
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
                                int screenAddress = screen_mem_start + videoMatrixCounter;
                                
                                switch ((screenAddress >> 10) & 0xF) {
                                    case 4:
                                    case 5:
                                    case 6:
                                    case 7:
                                    case 9:
                                    case 10:
                                    case 11:
                                        // Unconnected memory, so VIC chip sees what CPU put on bus.
                                        cellIndex = memory.getLastBusData();
                                        break;
                                        
                                    default:
                                        cellIndex = mem[VIC_MEM_TABLE[screenAddress & 0x3FFF]];
                                        memory.setLastBusData(cellIndex);
                                        break;
                                }
                                
                                // Due to the way the colour memory is wired up, the above fetch of the cell
                                // index also happens to automatically fetch the foreground colour from the 
                                // Colour Matrix via the top 4 lines of the data bus (DB8-DB11), which are 
                                // wired directly from colour RAM in to the VIC chip.
                                colourData = mem[colourRamBaseAddress + (screenAddress & 0x3ff)];
        
                                // Output the 1st pixel of next character. Note that this is not the character
                                // that relates to the cell index and colour data fetched above.
                                if (horizontalCounter > PAL_HBLANK_END) {
                                    pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[multiColourTable[pixel1]]);
                                }
        
                                // Toggle fetch state. Close matrix if HCC hits zero.
                                fetchState = ((horizontalCellCounter-- > 0) ? FETCH_CHAR_DATA : FETCH_MATRIX_END);
                                
        
                            case FETCH_CHAR_DATA:
                            case FETCH_MATRIX_END:
                                
                                // Look up very latest background, border and auxiliary colour values.
                                multiColourTable[0] = background_colour_index;
                                multiColourTable[1] = border_colour_index;
                                multiColourTable[3] = auxiliary_colour_index;
        
                                // Output only one visible pixel for HC=12, as first three "pixels"
                                // are part of the horizontal blanking. Note that the third one is due
                                // to the switch delay in hblank turning off. This is why we skip these
                                // pixels for HC=12.
                                if (horizontalCounter > PAL_HBLANK_END) {
                                    pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[multiColourTable[pixel2]]);
                                    pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[multiColourTable[pixel3]]);
                                }
        
                                // Calculate offset of data.
                                charDataOffset = char_mem_start + (cellIndex << char_size_shift) + cellDepthCounter;
        
                                switch ((charDataOffset >> 10) & 0xF) {
                                    case 4:
                                    case 5:
                                    case 6:
                                    case 7:
                                    case 9:
                                    case 10:
                                    case 11:
                                        // Unconnected memory, so VIC chip sees what CPU put on bus.
                                        charDataLatch = memory.getLastBusData();
                                        break;
                                    default:
                                        // Fetch cell data, initially latched to the side until it is needed.
                                        charDataLatch = mem[VIC_MEM_TABLE[(charDataOffset & 0x3FFF)]];
                                        memory.setLastBusData(charDataLatch);
                                        break;
                                }
        
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
                                    pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[multiColourTable[pixel4]]);
                                    pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[multiColourTable[pixel5]]);
                                }
                                
                                if (fetchState == FETCH_MATRIX_END) {
                                    // Leaving the matrix
                                    fetchState = FETCH_MATRIX_LINE;
                                } else {
                                    // Increment the video matrix counter to next cell.
                                    videoMatrixCounter++;
                                    
                                    // Toggle fetch state. For efficiency, HCC deliberately not checked here.
                                    fetchState = FETCH_SCREEN_CODE;
                                }
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
                                
                                // Screen origin X can match in the same cycle as Y.
                                if (prevHorizontalCounter == screen_origin_x) {
                                    fetchState = FETCH_MATRIX_DLY_1;
                                }
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
                            fetchState = ((horizontalCellCounter-- > 0) ? FETCH_CHAR_DATA : FETCH_MATRIX_END);
                            break;
                        case FETCH_CHAR_DATA:
                            videoMatrixCounter++;
                            fetchState = FETCH_SCREEN_CODE;
                            break;
                        case FETCH_MATRIX_END:
                            fetchState = FETCH_MATRIX_LINE;
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
        
        return frameRenderComplete;
    }
}
