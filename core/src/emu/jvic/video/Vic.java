package emu.jvic.video;

import emu.jvic.MachineType;
import emu.jvic.memory.MemoryMappedChip;
import emu.jvic.snap.Snapshot;

/**
 * This class emulates the VIC chip. The emulation is cycle based.
 * 
 * @author Lance Ewing
 */
public class Vic extends MemoryMappedChip {
  
  /**
   * This is the memory location that the VIC chip reads from when outside the
   * video matrix (technically speaking it reads from 0x3814, but for speed
   * reasons, the VIC chip memory mapping is not emulated exactly).
   */
  private static final int DEFAULT_FETCH_ADDRESS = 0x1814;

  /**
   * Constant for fetch toggle to indicate that screen code should be fetched.
   */
  private static final int FETCH_SCREEN_CODE = 0;

  /**
   * Constant for fetch toggle to indicate that character data should be fetched.
   */
  private static final int FETCH_CHAR_DATA = 1;

  // VIC chip memory mapped registers.
  private static final int VIC_REG_0 = 0x9000;
  private static final int VIC_REG_1 = 0x9001;
  private static final int VIC_REG_2 = 0x9002;
  private static final int VIC_REG_3 = 0x9003;
  private static final int VIC_REG_4 = 0x9004;
  private static final int VIC_REG_5 = 0x9005;
  private static final int VIC_REG_6 = 0x9006;
  private static final int VIC_REG_7 = 0x9007;
  private static final int VIC_REG_8 = 0x9008;
  private static final int VIC_REG_9 = 0x9009;
  private static final int VIC_REG_10 = 0x900A;
  private static final int VIC_REG_11 = 0x900B;
  private static final int VIC_REG_12 = 0x900C;
  private static final int VIC_REG_13 = 0x900D;
  private static final int VIC_REG_14 = 0x900E;
  private static final int VIC_REG_15 = 0x900F;

  /**
   * RGB values for the VIC's 16 colours.
   */
  private final static int vicColours[] = {
    0xFF000000,     // BLACK
    0xFFFFFFFF,     // WHITE
    0xFF1529AE,     // RED
    0xFFF3E467,     // CYAN
    0xFFEA2EAA,     // PURPLE
    0xFF1BDC66,     // GREEN
    0xFFF52C45,     // BLUE
    0xFF2FF7E2,     // YELLOW
    0xFF0067BF,     // ORANGE
    0xFF29CDEE,     // LIGHT ORANGE
    0xFF8091FF,     // PINK
    0xFFFFFF9D,     // LIGHT CYAN
    0xFFFF8BFD,     // LIGHT PURPLE
    0xFF64FFA8,     // LIGHT GREEN
    0xFF9E88FF,     // LIGHT BLUE
    0xFF69FFFF      // LIGHT YELLOW
  };
  
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
  
  /**
   * Last byte fetched by the VIC chip. This could be the cell index or cell bitmap.
   * 
   * TODO: It should have a separate variable for the char bitmap, since that is how it is done at the silicon level.
   */
  private int cellData;

  /**
   * Last fetched cell colour.
   */
  private int cellColour;

  /**
   * Index of the cell colour into the colours array.
   */
  private int cellColourIndex;

  /**
   * Current start of video memory.
   */
  private int videoMemoryStart;

  /**
   * Current start of colour memory.
   */
  private int colourMemoryStart;

  /**
   * Current start of character memory.
   */
  private int charMemoryStart;

  /**
   * Video matrix counter value.
   */
  private int videoMatrixCounter;

  /**
   * The video matrix location of the start of the current row.
   */
  private int rowStart;

  /**
   * Horizontal counter (measured in pixels).
   */
  private int horizontalCounter;

  /**
   * Vertical counter (measured in pixels). Aka. raster line
   */
  private int verticalCounter;

  /**
   * Pixel counter. Current offset into TV frame array.
   */
  private int pixelCounter;

  /**
   * Horizontal cell counter.
   */
  private int horizontalCellCounter;

  /**
   * Vertical cell counter.
   */
  private int verticalCellCounter;

  /**
   * Cell depth counter. This will be the current offset into the character
   * table and will be a value between 0 and the character size (8 or 16).
   */
  private int cellDepthCounter;

  /**
   * The actual start position inside character table after adding the current
   * cell depth offset.
   */
  private int charMemoryCellDepthStart;

  /**
   * The number of rows in the video matrix.
   */
  private int numOfRows;

  /**
   * The number of columns in the video matrix.
   */
  private int numOfColumns;

  /**
   * The current character size.
   */
  private int characterSize;

  /**
   * The number of bits to shift a cell index left by to get the true index into
   * the character table.
   */
  private int characterSizeShift;

  /**
   * The current horizontal screen origin.
   */
  private int horizontalScreenOrigin;

  /**
   * The current vertical screen origin.
   */
  private int verticalScreenOrigin;

  /**
   * Toggle used to keep track of whether to fetch a screen code or character
   * data (0 = fetch screen code, 1 = fetch character data)
   */
  private int fetchToggle;

  /**
   * The current background colour.
   */
  private int backgroundColour;

  /**
   * The index of the current background colour into the colours array.
   */
  private int backgroundColourIndex;

  /**
   * The current border colour.
   */
  private int borderColour;

  /**
   * The current auxiliary colour;
   */
  private int auxiliaryColour;

  /**
   * Whether the characters are reversed at present or not.
   */
  private int reverse;

  /**
   * Holds the current colour values of each of the multi-colour colours.
   */
  private int multiColourTable[] = new int[4];

  /**
   * The left hand side of th text screen.
   */
  private int textScreenLeft;

  /**
   * The right hand side of the text screen.
   */
  private int textScreenRight;

  /**
   * The top line of the text screen.
   */
  private int textScreenTop;

  /**
   * The bottom line of the text screen.
   */
  private int textScreenBottom;

  /**
   * The width in pixels of the text screen.
   */
  private int textScreenWidth;

  /**
   * The height in pixels of the text screen.
   */
  private int textScreenHeight;

  /**
   * Master volume for all of the VIC chip voices.
   */
  private int masterVolume;
  
  /**
   * Holds the pixel data for the TV frame screen.
   */
  private int framePixels[];
  
  /**
   * Constructor for VIC.
   * 
   * @param machineType The type of machine, PAL or NTSC.
   * @param snapshot Optional snapshot of the machine state to start with.
   */
  public Vic(MachineType machineType, Snapshot snapshot) {
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
    int mem[] = snapshot.getMemoryArray();
    
    int value = mem[VIC_REG_0];
    horizontalScreenOrigin = (value & 0x7F);
    textScreenLeft = (horizontalScreenOrigin << 2);
    textScreenRight = textScreenLeft + textScreenWidth;
    
    value = mem[VIC_REG_1];
    verticalScreenOrigin = value;
    textScreenTop = (value << 1);
    textScreenBottom = textScreenTop + textScreenHeight;
    
    value = mem[VIC_REG_2];
    numOfColumns = (value & 0x7f);
    textScreenWidth = (numOfColumns << 3);
    textScreenRight = textScreenLeft + textScreenWidth;
    colourMemoryStart = ((value > 0x80) ? 0x9600 : 0x9400);
    videoMemoryStart = videoMemoryTable[((mem[VIC_REG_5] & 0xF0) >> 3) | ((value & 0x80) >> 7)];
    
    value = mem[VIC_REG_3];
    switch (value & 0x01) {
    case 0:
      characterSize = 8;
      characterSizeShift = 3;
      break;

    case 1:
      characterSize = 16;
      characterSizeShift = 4;
      break;
    }
    numOfRows = (value & 0x7e) >> 1;
    textScreenHeight = characterSize * numOfRows;
    textScreenBottom = textScreenTop + textScreenHeight;
    
    value = mem[VIC_REG_5];
    videoMemoryStart = videoMemoryTable[((value & 0xF0) >> 3) | ((mem[VIC_REG_2] & 0x80) >> 7)];
    charMemoryStart = charMemoryTable[value & 0x0F];
    charMemoryCellDepthStart = charMemoryStart + cellDepthCounter;
    
    value = mem[VIC_REG_14];
    auxiliaryColour = vicColours[(value & 0xF0) >> 4];
    multiColourTable[3] = auxiliaryColour;
    masterVolume = (15 - (value & 0x0F));
    
    value = mem[VIC_REG_15];
    borderColour = vicColours[value & 0x07];
    backgroundColourIndex = (value & 0xF0) >> 4;
    backgroundColour = vicColours[backgroundColourIndex];
    multiColourTable[0] = backgroundColour;
    multiColourTable[1] = borderColour;
    reverse = ((value & 0x08) == 0x08 ? 0 : 1);
    
    // TODO: Vertical counter, cell depth counter and horizontal counter.
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
    rowStart = 0;
    cellData = 0;
    cellColour = 0;
    fetchToggle = FETCH_SCREEN_CODE;
    charMemoryCellDepthStart = charMemoryStart;
    // TODO: Have a queue of ready framePixels arrays. Skip a frame if this grows to three in size??
    framePixels = new int[(machineType.getTotalScreenWidth() * machineType.getTotalScreenHeight())];
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
        value = cellData & 0xFF;
    }

    return value;
  }

  /**
   * Writes a value to VIC memory.
   * 
   * @param address The address to write the value to.
   * @param value The value to write into the address.
   */
  public void writeMemory(int address, int value) {
    // This is how the VIC chip is mapped, i.e. each register to multiple addresses.
    address = address & 0xFF0F;

    switch (address) {
      case VIC_REG_0: // $9000 Left margin, or horizontal origin (4 pixel granularity)
        mem[address] = value;
        horizontalScreenOrigin = (value & 0x7F);
        textScreenLeft = (horizontalScreenOrigin << 2); // + 32;// - 32 + RIGHT_ADD;
        textScreenRight = textScreenLeft + textScreenWidth;
        break;
  
      case VIC_REG_1: // $9001 Top margin, or vertical origin (2 pixel granularity)
        mem[address] = value;
        verticalScreenOrigin = value;
        textScreenTop = (value << 1);// - 36 + TOP_ADD;
        textScreenBottom = textScreenTop + textScreenHeight;
        break;
  
      case VIC_REG_2: // $9002 Video Matrix Columns, Video and colour memory
        mem[VIC_REG_2] = value;
        numOfColumns = (value & 0x7f);
        textScreenWidth = (numOfColumns << 3);
        textScreenRight = textScreenLeft + textScreenWidth;
        colourMemoryStart = ((value > 0x80) ? 0x9600 : 0x9400);
        videoMemoryStart = videoMemoryTable[((mem[VIC_REG_5] & 0xF0) >> 3) | ((value & 0x80) >> 7)];
        break;
  
      case VIC_REG_3: // $9003 Video Matrix Rows, Character size
        mem[address] = value;
        switch (value & 0x01) {
        case 0:
          characterSize = 8;
          characterSizeShift = 3;
          break;
  
        case 1:
          characterSize = 16;
          characterSizeShift = 4;
          break;
        }
        numOfRows = (value & 0x7e) >> 1;
        textScreenHeight = characterSize * numOfRows;
        textScreenBottom = textScreenTop + textScreenHeight;
        break;
  
      case VIC_REG_4: // $9004 Raster line counter (READ ONLY)
        break;
  
      case VIC_REG_5: // $9005 Video matrix and char generator base address control
        mem[address] = value;
        videoMemoryStart = videoMemoryTable[((value & 0xF0) >> 3) | ((mem[VIC_REG_2] & 0x80) >> 7)];
        charMemoryStart = charMemoryTable[value & 0x0F];
        charMemoryCellDepthStart = charMemoryStart + cellDepthCounter;
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
        auxiliaryColour = vicColours[(value & 0xF0) >> 4];
        multiColourTable[3] = auxiliaryColour;
        masterVolume = (15 - (value & 0x0F));
        break;
  
      case VIC_REG_15: // $900F Screen and Border Colours, Reverse Video
        mem[address] = value;
        borderColour = vicColours[value & 0x07];
        backgroundColourIndex = (value & 0xF0) >> 4;
        backgroundColour = vicColours[backgroundColourIndex];
        multiColourTable[0] = backgroundColour;
        multiColourTable[1] = borderColour;
        reverse = ((value & 0x08) == 0x08 ? 0 : 1);
        break;
    }
  }
  
  /**
   * Emulates a single machine cycle. The VIC chip alternates its function
   * between fetching the screen code for a character from the video matrix and
   * fetching the bitmap of the character line from character memory on alternate
   * cycles. Four pixels are output every cycle. Note that the VIC starts fetching
   * the data for character it needs to render during the 2 cycles prior to the dots
   * being sent to the TV. So the border column immediately preceding the left 
   * edge of the video matrix area is when it is fetching the data required for 
   * the first column of the video matrix area.
   * 
   * @return true If a screen repaint is required due to the frame render having completed. 
   */
  public boolean emulateCycle() {
    //boolean frameRenderComplete = false;
    int charDataOffset = 0;
    int tempColour = 0;
    
    // TODO: This needs to change so that it renders 4 pixels every cycle rather than 8 every 2 cycles.
    
    // Some points to consider:
    //
    // (1)
    //
    // "Changes to $900f and $900e colors appear 1 hires pixel late with respect to char 
    //  (or half char) boundaries and changes to the reverse mode bit appear 3 hires pixels 
    //  late. This "anomaly" appears on both my 6561-101 and 6561E VICs."
    //
    // (2)
    //
    // BUS DATA                     DISPLAY 
    // 
    // VIC fetch (screen data *)    ... 
    // CPU cycle                    ... 
    // VIC fetch (character data)   ... 
    // CPU cycle                    pixel-columns 1/2 of character 
    // ...                          pixel-columns 3/4 of character 
    // ...                          pixel-columns 5/6 of character 
    // ...                          pixel-columns 7/8 of character 
    // 
    // *) includes colour RAM data
    //
    // Y=1   start of vblank
    // Y=10    end of vblank
    // Y=4   start of vsync
    // Y=7     end of vsync
    // Y=311  last line of (even?) frame
    // Y=312 and INT and not INT  last line of (odd?) frame
    //
    // so interlace mode does nothing in effect: 311 is enabled on
    // every frame, 312 is never enabled.
    

    // TODO: Verify that this is correct, for both PAL and NTSC. It almost certainly isn't.
    if (verticalCounter > 9) {
      
    // Check that we are inside the text screen.
    if ((verticalCounter >= textScreenTop) && (verticalCounter < textScreenBottom) && (horizontalCounter >= textScreenLeft) && (horizontalCounter < textScreenRight)) {

      // Determine whether we are fetching screen code or char data.
      if (fetchToggle == FETCH_SCREEN_CODE) {
        
        // Calculate address within video memory and fetch cell index.
        cellData = mem[videoMemoryStart + videoMatrixCounter];

        // Due to the way the colour memory is wired up, the above fetch of the cell index
        // also happens to automatically fetch the foreground colour from the Colour Matrix
        // via the top 4 lines of the data bus (DB8-DB11), which are wired directly from 
        // colour RAM in to the VIC chip.
        cellColourIndex = mem[colourMemoryStart + videoMatrixCounter];
        cellColour = vicColours[cellColourIndex];

        // Increment the video matrix counter.
        videoMatrixCounter++;

        // Toggle fetch toggle.
        fetchToggle = FETCH_CHAR_DATA;
        
        return frameRenderComplete;
        
      } else {
        // Calculate offset of data.
        charDataOffset = charMemoryCellDepthStart + (cellData << characterSizeShift);

        // Adjust offset for memory wrap around.
        if ((charMemoryStart < 8192) && (charDataOffset >= 8192)) {
          charDataOffset += 24576;
        }

        // Fetch cell data.
        cellData = mem[charDataOffset];

        // Plot pixels.
        if ((cellColourIndex & 0x08) == 0) {
          if (reverse == 0) {
            // Normal graphics.
            framePixels[pixelCounter++] = ((cellData & 0x80) == 0 ? backgroundColour : cellColour);
            framePixels[pixelCounter++] = ((cellData & 0x40) == 0 ? backgroundColour : cellColour);
            framePixels[pixelCounter++] = ((cellData & 0x20) == 0 ? backgroundColour : cellColour);
            framePixels[pixelCounter++] = ((cellData & 0x10) == 0 ? backgroundColour : cellColour);

            horizontalCounter = horizontalCounter + 4;

            if (horizontalCounter < machineType.getTotalScreenWidth()) {
              framePixels[pixelCounter++] = ((cellData & 0x08) == 0 ? backgroundColour : cellColour);
              framePixels[pixelCounter++] = ((cellData & 0x04) == 0 ? backgroundColour : cellColour);
              framePixels[pixelCounter++] = ((cellData & 0x02) == 0 ? backgroundColour : cellColour);
              framePixels[pixelCounter++] = ((cellData & 0x01) == 0 ? backgroundColour : cellColour);
            }
          } else {
            // Reverse graphics.
            framePixels[pixelCounter++] = ((cellData & 0x80) == 0 ? cellColour : backgroundColour);
            framePixels[pixelCounter++] = ((cellData & 0x40) == 0 ? cellColour : backgroundColour);
            framePixels[pixelCounter++] = ((cellData & 0x20) == 0 ? cellColour : backgroundColour);
            framePixels[pixelCounter++] = ((cellData & 0x10) == 0 ? cellColour : backgroundColour);

            horizontalCounter = horizontalCounter + 4;

            if (horizontalCounter < machineType.getTotalScreenWidth()) {
              framePixels[pixelCounter++] = ((cellData & 0x08) == 0 ? cellColour : backgroundColour);
              framePixels[pixelCounter++] = ((cellData & 0x04) == 0 ? cellColour : backgroundColour);
              framePixels[pixelCounter++] = ((cellData & 0x02) == 0 ? cellColour : backgroundColour);
              framePixels[pixelCounter++] = ((cellData & 0x01) == 0 ? cellColour : backgroundColour);
            }
          }
        } else {
          // Multicolour graphics.
          multiColourTable[2] = cellColour;
          tempColour = multiColourTable[(cellData >> 6) & 0x03];
          framePixels[pixelCounter++] = tempColour;
          framePixels[pixelCounter++] = tempColour;
          tempColour = multiColourTable[(cellData >> 4) & 0x03];
          framePixels[pixelCounter++] = tempColour;
          framePixels[pixelCounter++] = tempColour;

          horizontalCounter = horizontalCounter + 4;

          if (horizontalCounter < machineType.getTotalScreenWidth()) {
            tempColour = multiColourTable[(cellData >> 2) & 0x03];
            framePixels[pixelCounter++] = tempColour;
            framePixels[pixelCounter++] = tempColour;
            tempColour = multiColourTable[cellData & 0x03];
            framePixels[pixelCounter++] = tempColour;
            framePixels[pixelCounter++] = tempColour;
          }
        }

        // Toggle fetch toggle.
        fetchToggle = FETCH_SCREEN_CODE;
      }
    } else {
      cellData = mem[DEFAULT_FETCH_ADDRESS];

      // Output four border pixels.
      framePixels[pixelCounter++] = borderColour;
      framePixels[pixelCounter++] = borderColour;
      framePixels[pixelCounter++] = borderColour;
      framePixels[pixelCounter++] = borderColour;
    }
    
    } else {
      // Vertical blanking is in progress. Not pixels are output during this time.
    }

    // Increment the horizontal counter.
    horizontalCounter = horizontalCounter + 4;

    // If end of line is reached, reset horiz counter and increment vert
    // counter.
    if (horizontalCounter >= machineType.getTotalScreenWidth()) {
      horizontalCounter = 0;
      verticalCounter++;

      // If last line has been reached, reset all counters.
      if (verticalCounter >= machineType.getTotalScreenHeight()) {
        verticalCounter = 0;
        pixelCounter = 0;
        videoMatrixCounter = 0;
        rowStart = 0;
        cellDepthCounter = 0;
        charMemoryCellDepthStart = charMemoryStart;
        
        // Signal to the caller that the frame render has completed.
        frameRenderComplete = true;
        
      } else {
        if (videoMatrixCounter > 0) {
          cellDepthCounter++;

          if (cellDepthCounter == characterSize) {
            // Advance to the next row of characters in text window.
            cellDepthCounter = 0;
            videoMatrixCounter = rowStart + numOfColumns;
            rowStart = videoMatrixCounter;
            charMemoryCellDepthStart = charMemoryStart;
          } else {
            // Reset the video matrix to beginning of current row.
            videoMatrixCounter = rowStart;
            charMemoryCellDepthStart = charMemoryStart + cellDepthCounter;
          }
        }
      }

      // Update raster line in VIC registers.
      mem[VIC_REG_4] = (verticalCounter >> 1);
      if ((verticalCounter & 0x01) == 0) {
        mem[VIC_REG_3] &= 0x7F;
      } else {
        mem[VIC_REG_3] |= 0x80;
      }
    }
    
    return frameRenderComplete;
  }
  
  private boolean frameRenderComplete;

  public boolean isFrameReady() {
    return frameRenderComplete;
  }
  
  public int[] getFramePixels() {
    frameRenderComplete = false;
    return framePixels;
  }
}
