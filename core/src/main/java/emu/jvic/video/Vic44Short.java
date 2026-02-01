package emu.jvic.video;

import emu.jvic.MachineType;
import emu.jvic.PixelData;
import emu.jvic.snap.Snapshot;

/**
 * This class emulates the PAL VIC chip (6561).
 */
public class Vic44Short extends Vic {
    
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
    
    private static final int PAL_FRONTPORCH_1 = (4 << 0);      // From 0 to 10 
    private static final int PAL_FRONTPORCH_2 = (3 << 0);      // From 1 to 1.75
    private static final int PAL_HSYNC = (20 << 0);            // From 1.75 to 6.75
    private static final int PAL_BREEZEWAY = (3 << 0);         // From 6.75 to 7.5
    private static final int PAL_COLBURST_O = (17 << 0);       // From 7.5 to 11.75
    private static final int PAL_COLBURST_E = (17 << 0);       // From 7.5 to 11.75
    private static final int PAL_BACKPORCH = (4 << 0);         // From 11.75 to 12.75
    
    // Vertical blanking and sync.
    private static final int PAL_LONG_SYNC_L = (133 << 0);
    private static final int PAL_LONG_SYNC_H = (9 << 0);
    private static final int PAL_SHORT_SYNC_L = (9 << 0);
    private static final int PAL_SHORT_SYNC_H = (133 << 0);

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
    
    protected boolean vblanking = false;
    
    /**
     * Constructor for Vic44.
     * 
     * @param pixelData Interface to the platform specific mechanism for writing pixels.
     * @param machineType The type of machine, PAL or NTSC.
     * @param snapshot    Optional snapshot of the machine state to start with.
     */
    public Vic44Short(PixelData pixelData, MachineType machineType, Snapshot snapshot) {
        super(pixelData, machineType, snapshot);
    }
    
    /**
     * Puts a pixel into the pixel data.
     * 
     * @param pio 
     * @param sm 
     * @param pixel 
     */
    protected void pio_sm_put(int pio, int sm, int pixel) {
        // TODO: Fix this check. Is it needed?
        if ((pixelCounter < 177216)) {
            if ((pixel & 0xFF) == 0xFF) {
                // If alpha channel is set to 0xFF, then its a normal colour.
                pixelData.putPixel(pixelCounter++, pixel);
            } else {
                // Otherwise it is command that happens during blanking, e.g. hsync, col burst,
                // front porch, back porch, etc., so we simply output transparent black.
                int length = (pixel << 1);
                for (int i = 0; i < length; i++) {
                    pixelData.putPixel(pixelCounter++, 0);
                }
            }
        }
    }
    
    /**
     * This one appears to work best at the moment.
     * 
     * @return
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
        
        if (fetchState == FETCH_OUTSIDE_MATRIX) {
            // We haven't yet matched screen origin Y, so let's check it...
            if ((verticalCounter >> 1) == screen_origin_y) {
                // Text area doesn't open if number of rows is 0.
                // TODO: Check die shot to confirm this is true.
                if (num_of_rows > 0) {
                    // This is the line the video matrix starts on. As in the real chip, we use
                    // a different state for the first part of the first video matrix line.
                    fetchState = FETCH_IN_MATRIX_Y;
                }
            }
        }

        // Increment HC for next cycle.
        horizontalCounter++;
        
        // Reset horizontal counter if end of line reached.
        if (horizontalCounter > PAL_HBLANK_START) {
            horizontalCounter = 0;

            if (fetchState >= FETCH_MATRIX_LINE) {
                // If the line that just ended was a video matrix line, then increment CDC,
                // unless the VCC is 0, in which case close the matrix (see below).
                if (verticalCellCounter > 0) {
                    cellDepthCounter++;
                }
                
                fetchState = FETCH_MATRIX_LINE;
            }
            
            // Next line.
            verticalCounter++;
            
            if (verticalCounter > PAL_LAST_LINE) {
                verticalCounter = 0;
                fetchState = FETCH_OUTSIDE_MATRIX;
                // TODO: This actually happens in HC=1.
                cellDepthCounter = 0;
            }
            
            // Update the raster line value stored in the VIC registers.
            mem[VIC_REG_4] = (verticalCounter >> 1);
            if ((verticalCounter & 0x01) == 0) {
                mem[VIC_REG_3] &= 0x7F;
            } else {
                mem[VIC_REG_3] |= 0x80;
            }
            
            if (!vblanking) {
                pio_sm_put(CVBS_PIO, CVBS_SM, PAL_FRONTPORCH_1);
            }
        }

        if ((fetchState == FETCH_IN_MATRIX_Y) || (fetchState == FETCH_MATRIX_LINE)) {
            // Check if the horizontal counter (HC) has matched screen origin X.
            if (horizontalCounter == screen_origin_x) {
                fetchState = FETCH_MATRIX_DLY_0;
            }

            // Handle cell depth counter (CDC) and video matrix latch updates.
            if ((fetchState == FETCH_MATRIX_LINE) && (horizontalCounter == 0)) {
                // Check if last line was the last for the current character.
                if ((cellDepthCounter == (last_line_of_cell + 1) ||
                    (cellDepthCounter == (2 * (last_line_of_cell + 1))))) {
                    
                    // Reset cell depth counter, increment to next vertical cell.
                    cellDepthCounter = 0;
                    verticalCellCounter--;
                    
                    // If the VCC has counted down the number of rows, then we leave the matrix for this frame.
                    if (verticalCellCounter == 0) {
                        fetchState = FETCH_OUTSIDE_MATRIX;
                    }
                }
                else {
                    // If it was not the last line for the character, then reset VMC from the latch.
                    videoMatrixCounter = videoMatrixLatch;
                }
            }
        }

        if (verticalCounter == 0) {
            // Latch number of character rows.
            if (horizontalCounter == 2) {
                verticalCellCounter = num_of_rows;
            }
        }

        // Latch number of character columns.
        if (horizontalCounter == 1) {
            horizontalCellCounter = num_of_columns;
            
            switch (verticalCounter) {
                // Vertical blanking and sync - Lines 1-9.
                case 1:
                    vblanking = true;
                    pixelCounter = 0;
                    frameRenderComplete = true;
                case 2:
                case 3:
                    pio_sm_put(CVBS_PIO, CVBS_SM, PAL_SHORT_SYNC_L);
                    pio_sm_put(CVBS_PIO, CVBS_SM, PAL_SHORT_SYNC_H);
                    pio_sm_put(CVBS_PIO, CVBS_SM, PAL_SHORT_SYNC_L);
                    pio_sm_put(CVBS_PIO, CVBS_SM, PAL_SHORT_SYNC_H);
                    return frameRenderComplete;
                case 4:
                case 5:
                case 6:
                    // Vertical sync is what resets the video matrix latch.
                    videoMatrixLatch = videoMatrixCounter = 0;
                    
                    pio_sm_put(CVBS_PIO, CVBS_SM, PAL_LONG_SYNC_L);
                    pio_sm_put(CVBS_PIO, CVBS_SM, PAL_LONG_SYNC_H);
                    pio_sm_put(CVBS_PIO, CVBS_SM, PAL_LONG_SYNC_L);
                    pio_sm_put(CVBS_PIO, CVBS_SM, PAL_LONG_SYNC_H);
                    return frameRenderComplete;
                case 7:
                case 8:
                case 9:
                    pio_sm_put(CVBS_PIO, CVBS_SM, PAL_SHORT_SYNC_L);
                    pio_sm_put(CVBS_PIO, CVBS_SM, PAL_SHORT_SYNC_H);
                    pio_sm_put(CVBS_PIO, CVBS_SM, PAL_SHORT_SYNC_L);
                    pio_sm_put(CVBS_PIO, CVBS_SM, PAL_SHORT_SYNC_H);
                    return frameRenderComplete;
                
                // Other visible lines, so continue horizontal blanking.
                case 10:
                    vblanking = false;
                case 0:
                default:
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
                    break;
            }
        }
        
        ////////////////////////////////////////////////////////////////////////////////////
        
        if (!vblanking) {
            // Perform the fetch and rendering.
            switch (fetchState) {
            
                case FETCH_OUTSIDE_MATRIX:
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
                    }
                    break;
                    
                case FETCH_MATRIX_DLY_0:
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
                    colourData = mem[0x9400 + (screenAddress & 0x3ff)];
        
                    // Output the 1st pixel of next character. Note that this is not the character
                    // that relates to the cell index and colour data fetched above.
                    if (horizontalCounter > PAL_HBLANK_END) {
                        pio_sm_put(CVBS_PIO, CVBS_SM, pal_palette[multiColourTable[pixel1]]);
                    }
        
                    // Toggle fetch state. Close matrix if HCC hits zero.
                    fetchState = ((horizontalCellCounter-- > 0) ? FETCH_CHAR_DATA : FETCH_MATRIX_END);
                    
                    // Latch the video matrix counter every increment of the last line.
                    if (cellDepthCounter == last_line_of_cell) {
                        videoMatrixLatch = videoMatrixCounter;
                    }
        
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
                    
                    // Latch the video matrix counter every increment of the last line.
                    if (cellDepthCounter == last_line_of_cell) {
                        videoMatrixLatch = videoMatrixCounter;
                    }
                    break;
            }
        }
        
        return frameRenderComplete;
    }
}
