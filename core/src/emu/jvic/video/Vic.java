package emu.jvic.video;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.AudioDevice;
import com.badlogic.gdx.utils.GdxRuntimeException;

import emu.jvic.MachineType;
import emu.jvic.memory.MemoryMappedChip;
import emu.jvic.snap.Snapshot;

/**
 * This class emulates the VIC chip. The emulation is cycle based.
 * 
 * @author Lance Ewing
 */
public class Vic extends MemoryMappedChip {
  
  private static final int SAMPLE_RATE = 22050;

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

  private final static int palRGBA8888Colours[] = {
    0xFF000000,            // BLACK
    0xFFFFFFFF,            // WHITE
    0xFF211FB6,            // RED
    0xFFFFF04D,            // CYAN
    0xFFFF3FB4,            // PURPLE
    0xFF37E244,            // GREEN
    0xFFFF341A,            // BLUE
    0xFF1BD7DC,            // YELLOW
    0xFF0054CA,            // ORANGE
    0xFF72B0E9,            // LIGHT ORANGE
    0xFF9392E7,            // PINK
    0xFFFDF79A,            // LIGHT CYAN
    0xFFE09FFF,            // LIGHT PURPLE
    0xFF93E48F,            // LIGHT GREEN
    0xFFFF9082,            // LIGHT BLUE
    0xFF85DEE5             // LIGHT YELLOW
  };
  
  private final static short palRGB565Colours[] = {
    (short)0x0000,         // BLACK
    (short)0xFFFF,         // WHITE
    (short)0xB0E4,         // RED
    (short)0x4F9F,         // CYAN
    (short)0xB1FF,         // PURPLE
    (short)0x4706,         // GREEN
    (short)0x19BF,         // BLUE
    (short)0xDEA3,         // YELLOW
    (short)0xCAA0,         // ORANGE
    (short)0xED8E,         // LIGHT ORANGE
    (short)0xE492,         // PINK
    (short)0x9FBF,         // LIGHT CYAN
    (short)0xE4FF,         // LIGHT PURPLE
    (short)0x8F32,         // LIGHT GREEN
    (short)0x849F,         // LIGHT BLUE
    (short)0xE6F0          // LIGHT YELLOW
  };
  
  private final static short vicColours[] = palRGB565Colours;
  
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
   * Last character data fetched by the VIC chip.
   */
  private int charData;

  /**
   * Last cell index fetched by the VIC chip.
   */
  private int cellIndex;
  
  /**
   * Last fetched cell colour.
   */
  private short cellColour;

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
  private int characterSize = 8;

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
  private short backgroundColour;

  /**
   * The index of the current background colour into the colours array.
   */
  private int backgroundColourIndex;

  /**
   * The current border colour.
   */
  private short borderColour;

  /**
   * The current auxiliary colour;
   */
  private short auxiliaryColour;

  /**
   * Whether the characters are reversed at present or not.
   */
  private int reverse;

  /**
   * Holds the current colour values of each of the multi-colour colours.
   */
  private short multiColourTable[] = new short[4];

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
   * An array of two Frames, one being the one that the VIC is currently writing to,
   * the other being the last one that was completed and ready to blit.
   */
  private Frame[] frames;
  
  /**
   * The index of the active frame within the frames. This will toggle between 0 and 1.
   */
  private int activeFrame;
  
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

  /**
   * Constructor for VIC.
   * 
   * @param machineType The type of machine, PAL or NTSC.
   * @param snapshot Optional snapshot of the machine state to start with.
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
    
    int audioBufferSize = ((((SAMPLE_RATE/ 20) * 2) / 10) * 10);
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
    voiceClockDividerTriggers = new int[] { 0xF, 0x7, 0x3, 0x1 } ;
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
    masterVolume = value & 0x0F;
    
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
    charData = 0;
    cellColour = 0;
    fetchToggle = FETCH_SCREEN_CODE;
    charMemoryCellDepthStart = charMemoryStart;
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
   * @param value The value to write into the address.
   */
  public void writeMemory(int address, int value) {
    // This is how the VIC chip is mapped, i.e. each register to multiple addresses.
    address = address & 0xFF0F;

    switch (address) {
      case VIC_REG_0: // $9000 Left margin, or horizontal origin (4 pixel granularity)
        mem[address] = value;
        horizontalScreenOrigin = (value & 0x7F);
        textScreenLeft = (horizontalScreenOrigin << 2) + 24;   // TODO: 6 cycles between matching left edge and start of text window rendering.
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
        masterVolume = value & 0x0F;
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
   * Emulates a cycle where rendering is skipped. This is intended to be used by every cycle
   * in a frame whose rendering is being skipped. All this method does is make sure that the
   * vertical counter register is updated. Everything else is hidden from the CPU, so doesn't
   * need to be updated for a skip frame.
   * 
   * @return true if the frame was completed by the cycle that was emulated.
   */
  public boolean emulateSkipCycle() {
    boolean frameComplete = false;
    
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

  private boolean matrixLine;
  private boolean inMatrix;
  private boolean vblank;
  private boolean hblank;
  private boolean hccLast;  
  private boolean vccLast;
  private boolean sxCompare;
  private boolean syCompare;
  private boolean inMatrixY;
  private boolean busAvailable;
  private boolean vmcAndHccEnabled;
  private boolean addressOutEnabled;
  
  private int borderOffDelay;
  
  private boolean hiresMode;
  
  private boolean debug = false;
  
  /**
   * This method is an attempt to translate my 6561 die shot reversing schematics into Java code.
   * 
   * These are the hard wired parameters:
   * 
   * - PAL horizontal counter counts from 0 to 70 (i.e. 71 cycles). NTSC from 0 to 64 (i.e. 65 cycles).
   * - PAL vertical counter counts from 0 to 311 (i.e. 312 lines). NTSC from 0 to 260 (i.e. 261 lines).
   * - Line 0 is last visible line, at bottom of CRT. Line 1 is first blanking line. Line 9 is last blanking line.
   * - Line 10 is first visible line, at the top of CRT.
   */
  public boolean emulateCycleNew(boolean doSound) {
    short[] framePixels = frames[activeFrame].framePixels;
    boolean frameRenderComplete = false;
    int charDataOffset = 0;
    short tempColour = 0;
    
    boolean newLine = false;
    boolean firstLine = false;
    
    //if (inMatrix) debug = true;

    if (!vblank && !hblank) {
      
      if ((borderOffDelay & 1) == 1) {
        
        // Border is currently off, so render video matrix pixels. 
        if (hiresMode) {
          if (reverse == 0) {
            // Normal hi-res graphics.
            framePixels[pixelCounter++] = ((charData & 0x80) == 0 ? backgroundColour : cellColour);
            framePixels[pixelCounter++] = ((charData & 0x40) == 0 ? backgroundColour : cellColour);
            framePixels[pixelCounter++] = ((charData & 0x20) == 0 ? backgroundColour : cellColour);
            framePixels[pixelCounter++] = ((charData & 0x10) == 0 ? backgroundColour : cellColour);
          } else {
            // Reverse hi-res graphics.
            framePixels[pixelCounter++] = ((charData & 0x80) == 0 ? cellColour : backgroundColour);
            framePixels[pixelCounter++] = ((charData & 0x40) == 0 ? cellColour : backgroundColour);
            framePixels[pixelCounter++] = ((charData & 0x20) == 0 ? cellColour : backgroundColour);
            framePixels[pixelCounter++] = ((charData & 0x10) == 0 ? cellColour : backgroundColour);
          }
        } else {
          // Multicolour graphics.
          multiColourTable[2] = cellColour;
          tempColour = multiColourTable[(charData >> 6) & 0x03];
          framePixels[pixelCounter++] = tempColour;
          framePixels[pixelCounter++] = tempColour;
          tempColour = multiColourTable[(charData >> 4) & 0x03];
          framePixels[pixelCounter++] = tempColour;
          framePixels[pixelCounter++] = tempColour;
        }

        // Shift char data to account for the 4 pixels written out.
        charData = (charData << 4) & 0xF0;
        
        //if (!addressOutEnabled) {
        //  framePixels[pixelCounter - 4] = vicColours[2];
        //}
        
      } else {
        // Output four border pixels.
        framePixels[pixelCounter++] = borderColour;  //(addressOutEnabled? 0 : borderColour);
        framePixels[pixelCounter++] = borderColour;
        framePixels[pixelCounter++] = borderColour;
        framePixels[pixelCounter++] = borderColour;
      }

    } else {
      // No pixels rendered during blanking.
      pixelCounter += 4;   // TODO: Adjust the size of the pixel map / visual screen so we don't have to do this.
    }       
      
    if (addressOutEnabled) {
      
      // Determine whether we are fetching screen code or char data.
      if (fetchToggle == FETCH_SCREEN_CODE) {
        
        // Calculate address within video memory and fetch cell index.
        cellIndex = mem[videoMemoryStart + ((videoMatrixCounter - 0) >> 1)];

        // Due to the way the colour memory is wired up, the above fetch of the cell index
        // also happens to automatically fetch the foreground colour from the Colour Matrix
        // via the top 4 lines of the data bus (DB8-DB11), which are wired directly from 
        // colour RAM in to the VIC chip.
        cellColourIndex = mem[colourMemoryStart + ((videoMatrixCounter - 0) >> 1)];
        
        // TODO: Shouldn't need to have this, but for some reason HCC stops incrementing while addressOutEnabled is still true.
        // TODO: I have a suspicion that the interpretation of HCC0 is reversed, even on my die shot diagram. Might be HCC0 that comes out rather than HCC0'
        fetchToggle = FETCH_CHAR_DATA;
        
      } else {
        // Calculate offset of character data.
        //charDataOffset = charMemoryCellDepthStart + (cellIndex << characterSizeShift);
        charDataOffset = charMemoryStart + cellDepthCounter + (cellIndex << characterSizeShift);

        // Adjust offset for memory wrap around.
        if ((charMemoryStart < 8192) && (charDataOffset >= 8192)) {
          charDataOffset += 24576;
        }

        //System.out.println(
        //    String.format("charDataOffset: %d, charMemoryStart: %d, cellDepthCounter: %d, cellIndex: %d, characterSizeShift: %d", 
        //    charDataOffset, charMemoryStart, cellDepthCounter, cellIndex, characterSizeShift));
        
        // Fetch character data.
        charData = mem[charDataOffset];
        
        // Decode colour.
        cellColour = vicColours[cellColourIndex & 0x0F];   // TODO: Not sure why we have to mask this.
        
        // Decode mode (hires vs multi-colour).
        hiresMode = ((cellColourIndex & 0x08) == 0);
      }
    }
  
    
//    if (inMatrix) {
//      //framePixels[pixelCounter - 1] = vicColours[7];
//    } else if (vmcAndHccEnabled) {
//      framePixels[pixelCounter - 4] = vicColours[8];
//    }
//    
//    if (hccLast) {
//      framePixels[pixelCounter - 4] = vicColours[11];
//    }
//    
//    
//    if (fetchToggle == 0  && (verticalCounter % 4 == 0)) {
//      framePixels[pixelCounter - 4] = vicColours[13];
//    }
//    
//    if (fetchToggle == 1  && (verticalCounter % 4 == 0)) {
//      framePixels[pixelCounter - 4] = vicColours[0];
//    }
    
    
    // Notes from silicon die:
    // 1. HCC increments on F2.
    // 2. So hcc_last is true on F2 and will have immediate effect on in_matrix state.
    // 3. VMC and HCC increment disabled on F1 following hcc_last.
    // 4. HC increments on F2.
    // 5. VC increments on F2.
    
    // Increment horizontal counter.
    horizontalCounter++; 
    
    if (horizontalCounter == machineType.getCyclesPerLine()) {
      // Reset horizontal counter.
      horizontalCounter = 0;
      newLine = true;
      
      // Horizontal blanking starts at the beginning of a new line.
      hblank = true;
      
      // Increment vertical counter at start of new line.
      verticalCounter ++;
      if (verticalCounter == machineType.getTotalScreenHeight()) {
        verticalCounter = 0;
        firstLine = true;
        
      } else if (verticalCounter == 1) {
        vblank = true;
        pixelCounter = 0;
        
        synchronized(frames) {
          // Mark the current frame as complete.
          frames[activeFrame].ready = true;
          
          // Toggle the active frame.
          activeFrame = ((activeFrame + 1) % 2);
          frames[activeFrame].ready = false;
        }
        
        frameRenderComplete = true;
        
      } else if (verticalCounter == 10) {
        vblank = false;
        
      } else if (verticalCounter == 4) {
        // VSYNC sets video matrix latch all to ones (at the silicon level, all to ones, but it stores
        // the inverse value, so is effectively all to ones). Next increment puts VMC to zero.
        rowStart = 0xFFF;
        
        videoMatrixCounter = 0xFFF;  // TODO: Shouldn't need to do this here.
      }
      
      // Update raster line in VIC registers.
      mem[VIC_REG_4] = (verticalCounter >> 1);
      if ((verticalCounter & 0x01) == 0) {
        mem[VIC_REG_3] &= 0x7F;
      } else {
        mem[VIC_REG_3] |= 0x80;
      }
      
    } else if (horizontalCounter == 12) {
      // Horizontal blanking ends after first 12 cycles of a new line.
      hblank = false;
    }
    
    // Address Out Enabled state changes one cycle after VMC and HCC are enabled/disabled.
    addressOutEnabled = vmcAndHccEnabled;    
    
    // VMC And HCC are enabled if we're still in the text window (video matrix) and last 
    // cycle bus wasn't available. This means that as soon as inMatrix is false, VMC and HCC
    // are disabled.
    vmcAndHccEnabled = !busAvailable && inMatrix;
    
    // There is a 3 cycle delay between VMC and HCC being enabled/disabled and border being affected.
    borderOffDelay = ((borderOffDelay >> 1) | (vmcAndHccEnabled? 8 : 0)); 
    
    // Bus available state is inverse of whatever inMatrix was in the previous cycle.
    busAvailable = !inMatrix;
    
    // Determine whether we are in the text window (video matrix) area.
    if (syCompare) {
      inMatrixY = true;
    }
    if (firstLine || vccLast) {
      inMatrixY = false;
      matrixLine = false;
      vccLast = false;
    }
    if (inMatrixY && sxCompare) {
      inMatrix = true;
      matrixLine = true;
    }
    if (newLine || hccLast) {
      inMatrix = false;
      hccLast = false;
    }
    
    // Screen origin (text window top left corner) to horiz/vert counter comparators. This
    // is deliberately calculated after the inMatrix calculation due to a one cycle delay.
    sxCompare = (horizontalScreenOrigin == horizontalCounter);
    
    // SY comparison ignores vertical counter bit 0. Only from bit 1 and above.
    syCompare = (verticalScreenOrigin == (verticalCounter >> 1));
    
    if (newLine) {
      // New line reloads horizontal cell counter from control register.
      horizontalCellCounter = (((numOfColumns ^ 0x7F) << 1) & 0xFE) + 2;  // TODO: This seems wrong. Why add 2?

      // First line reloads the vertical cell counter from control register.
      if (firstLine) {
        verticalCellCounter = ((numOfRows ^ 0x3F) & 0x3F);
      }
      
      // New line increments cell depth counter (CDC) if this is a matrix line.
      if (matrixLine) {
        cellDepthCounter++;
        
        // If we've already reached the character size, then reset CDC.
        if (cellDepthCounter == characterSize) {
          cellDepthCounter = 0;
          
          // CDC reset triggers Vertical Cell Counter increment if not first line.
          if (!firstLine) {
            // Increment VCC (down counter implemented as an up counter).
            verticalCellCounter++;
            vccLast = (verticalCellCounter == 0x3F);
          }
        }
      }

      if (cellDepthCounter == 0) {
        // If CDC was reset, store the current video matrix counter value.
        rowStart = videoMatrixCounter;
      } else {
        // Otherwise load the previously stored video matrix counter value.
        videoMatrixCounter = rowStart;
      }
      
    } else  {
      if (vmcAndHccEnabled) {
        // Increment HCC (down counter implemented as an up counter).
        horizontalCellCounter = ((horizontalCellCounter + 1) & 0xFF);
        hccLast = (horizontalCellCounter == 0xFF);

        // When HCC0 is LOW, it is fetching from the video matrix memory, and when HCC0 is HIGH, it is fetching from the character memory.
        // TODO: Suspect that fetchToggle is currently being reversed in logic. Check die shot.
        fetchToggle = (horizontalCellCounter & 0x01);
        
        // Increment video matrix counter. 12-bit counter.
        videoMatrixCounter = ((videoMatrixCounter + 1) & 0xFFF);
      }
    }
    
    if (debug) {
      System.out.println(
          String.format(
              "pxc: %d, syc: %s, sxc: %s, ncols: %d, ml: %s, im: %s, imy: %s, vc: %d, hc: %d, hcc: %d, vcc: %d, vmc: %d, " + 
              "charDataOffset: %d, charMemoryStart: %d, cdc: %d, cellIndex: %d, characterSizeShift: %d, " + 
              "colourMemoryStart: %d",
          pixelCounter,
          (syCompare? "y" : "n"), (sxCompare? "y" : "n"), numOfColumns,
          (matrixLine? "y" : "n"), (inMatrix? "y" : "n"), (inMatrixY? "y" : "n"),
          verticalCounter, horizontalCounter, horizontalCellCounter, verticalCellCounter, videoMatrixCounter, charDataOffset, charMemoryStart, cellDepthCounter, 
          cellIndex, characterSizeShift, colourMemoryStart));
    }
    
    // 5-bit counter in the 6561, but only bottom 4 bits are used. Other bit might have been used for 6562/3.
    soundClockDividerCounter = ((soundClockDividerCounter + 1) & 0xF);
    
    for (int i=0; i<4; i++) {
      if ((voiceClockDividerTriggers[i] & soundClockDividerCounter) == 0) {
        voiceCounters[i] = (voiceCounters[i] + 1) & 0x7F;
        if (voiceCounters[i] == 0) {
          // Reload the voice counter from the control register.
          voiceCounters[i] = (mem[VIC_REG_10 + i] & 0x7F);
          
          if (i == 3) {
            // For Noise voice, we perform a shift of the LFSR whenever the counter is reloaded, and
            // only shift the main voice shift register if LFSR bit 0 is 1.
            if ((noiseLFSR & 0x0001) > 0) {
              voiceShiftRegisters[i] = (
                  ((voiceShiftRegisters[i] & 0x7F) << 1) | 
                  ((mem[VIC_REG_10 + i] & 0x80) > 0? (((voiceShiftRegisters[i] & 0x80) >> 7) ^ 1) : 0));
            }
            
            // The LFSR taps are bits 3, 12, 14 and 15.
            int bit3  = (noiseLFSR >> 3) & 1;
            int bit12 = (noiseLFSR >> 12) & 1;
            int bit14 = (noiseLFSR >> 14) & 1;
            int bit15 = (noiseLFSR >> 15) & 1;
            int feedback = (((bit3 ^ bit12) ^ (bit14 ^ bit15)) ^ 1);
            noiseLFSR = ((noiseLFSR << 1) | ((feedback & ((mem[VIC_REG_10 + i] & 0x80) >> 7)) ^ 1) & 0xFFFF);

          } else {
            // For the three other voices, we shift the voice shift register whenever the counter is reloaded.
            voiceShiftRegisters[i] = (
                ((voiceShiftRegisters[i] & 0x7F) << 1) | 
                ((mem[VIC_REG_10 + i] & 0x80) > 0? (((voiceShiftRegisters[i] & 0x80) >> 7) ^ 1) : 0));
          }
        }
      }
    }
    
    // If enough cycles have elapsed since the last sample, then output another.
    if (--cyclesToNextSample <= 0) {
      if (doSound) writeSample();
      cyclesToNextSample += cyclesPerSample;
    }
    
    return frameRenderComplete;
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
  public boolean emulateCycle(boolean doSound) {
    boolean frameRenderComplete = false;
    int charDataOffset = 0;
    short tempColour = 0;
    
    // Get a local reference to the current Frame's pixel array.
    short[] framePixels = frames[activeFrame].framePixels;

    if (!vblank && !hblank) {
      
      // Check that we are inside the text screen.
      if ((verticalCounter >= textScreenTop) && (verticalCounter < textScreenBottom) && (horizontalCounter >= textScreenLeft) && (horizontalCounter < textScreenRight)) {
  
        // Determine whether we are fetching screen code or char data.
        if (fetchToggle == FETCH_SCREEN_CODE) {
          
          // Calculate address within video memory and fetch cell index.
          cellIndex = mem[videoMemoryStart + videoMatrixCounter];
  
          // Due to the way the colour memory is wired up, the above fetch of the cell index
          // also happens to automatically fetch the foreground colour from the Colour Matrix
          // via the top 4 lines of the data bus (DB8-DB11), which are wired directly from 
          // colour RAM in to the VIC chip.
          cellColourIndex = mem[colourMemoryStart + videoMatrixCounter];
  
          // Increment the video matrix counter.
          videoMatrixCounter++;
  
          // Toggle fetch toggle.
          fetchToggle = FETCH_CHAR_DATA;
          
          return frameRenderComplete;
          
        } else {
          // Calculate offset of data.
          charDataOffset = charMemoryCellDepthStart + (cellIndex << characterSizeShift);
  
          // Adjust offset for memory wrap around.
          if ((charMemoryStart < 8192) && (charDataOffset >= 8192)) {
            charDataOffset += 24576;
          }
  
          // Fetch cell data.
          charData = mem[charDataOffset];
          
          // Decode colour.
          cellColour = vicColours[cellColourIndex & 0x0F];   // TODO: Not sure why we have to mask this.
          
          // Plot pixels.
          if ((cellColourIndex & 0x08) == 0) {
            if (reverse == 0) {
              // Normal graphics.
              framePixels[pixelCounter++] = ((charData & 0x80) == 0 ? backgroundColour : cellColour);
              framePixels[pixelCounter++] = ((charData & 0x40) == 0 ? backgroundColour : cellColour);
              framePixels[pixelCounter++] = ((charData & 0x20) == 0 ? backgroundColour : cellColour);
              framePixels[pixelCounter++] = ((charData & 0x10) == 0 ? backgroundColour : cellColour);
  
              horizontalCounter = horizontalCounter + 4;
  
              if (horizontalCounter < machineType.getTotalScreenWidth()) {
                framePixels[pixelCounter++] = ((charData & 0x08) == 0 ? backgroundColour : cellColour);
                framePixels[pixelCounter++] = ((charData & 0x04) == 0 ? backgroundColour : cellColour);
                framePixels[pixelCounter++] = ((charData & 0x02) == 0 ? backgroundColour : cellColour);
                framePixels[pixelCounter++] = ((charData & 0x01) == 0 ? backgroundColour : cellColour);
              }
            } else {
              // Reverse graphics.
              framePixels[pixelCounter++] = ((charData & 0x80) == 0 ? cellColour : backgroundColour);
              framePixels[pixelCounter++] = ((charData & 0x40) == 0 ? cellColour : backgroundColour);
              framePixels[pixelCounter++] = ((charData & 0x20) == 0 ? cellColour : backgroundColour);
              framePixels[pixelCounter++] = ((charData & 0x10) == 0 ? cellColour : backgroundColour);
  
              horizontalCounter = horizontalCounter + 4;
  
              if (horizontalCounter < machineType.getTotalScreenWidth()) {
                framePixels[pixelCounter++] = ((charData & 0x08) == 0 ? cellColour : backgroundColour);
                framePixels[pixelCounter++] = ((charData & 0x04) == 0 ? cellColour : backgroundColour);
                framePixels[pixelCounter++] = ((charData & 0x02) == 0 ? cellColour : backgroundColour);
                framePixels[pixelCounter++] = ((charData & 0x01) == 0 ? cellColour : backgroundColour);
              }
            }
          } else {
            // Multicolour graphics.
            multiColourTable[2] = cellColour;
            tempColour = multiColourTable[(charData >> 6) & 0x03];
            framePixels[pixelCounter++] = tempColour;
            framePixels[pixelCounter++] = tempColour;
            tempColour = multiColourTable[(charData >> 4) & 0x03];
            framePixels[pixelCounter++] = tempColour;
            framePixels[pixelCounter++] = tempColour;
  
            horizontalCounter = horizontalCounter + 4;
  
            if (horizontalCounter < machineType.getTotalScreenWidth()) {
              tempColour = multiColourTable[(charData >> 2) & 0x03];
              framePixels[pixelCounter++] = tempColour;
              framePixels[pixelCounter++] = tempColour;
              tempColour = multiColourTable[charData & 0x03];
              framePixels[pixelCounter++] = tempColour;
              framePixels[pixelCounter++] = tempColour;
            }
          }
  
          // Toggle fetch toggle.
          fetchToggle = FETCH_SCREEN_CODE;
        }
      } else {
        // Output four border pixels.
        framePixels[pixelCounter++] = borderColour;
        framePixels[pixelCounter++] = borderColour;
        framePixels[pixelCounter++] = borderColour;
        framePixels[pixelCounter++] = borderColour;
      }
    
    } else {
      // No pixels rendered during blanking.
      pixelCounter += 4;
    }

    // Increment the horizontal counter.
    horizontalCounter = horizontalCounter + 4;
    
    // If end of line is reached, reset horiz counter and increment vert
    // counter.
    if (horizontalCounter >= machineType.getTotalScreenWidth()) {
      horizontalCounter = 0;
      verticalCounter++;
      hblank = true;

      // If last line has been reached, reset all counters.
      if (verticalCounter >= machineType.getTotalScreenHeight()) {
        verticalCounter = 0;
        videoMatrixCounter = 0;
        rowStart = 0;
        cellDepthCounter = 0;
        charMemoryCellDepthStart = charMemoryStart;
        
      } else {
        if (verticalCounter == 1) {
          // Vertical blank starts on Line 1, not Line 0.
          vblank = true;
          pixelCounter = 0;
          
          synchronized(frames) {
            // Mark the current frame as complete.
            frames[activeFrame].ready = true;
            
            // Toggle the active frame.
            activeFrame = ((activeFrame + 1) % 2);
            frames[activeFrame].ready = false;
          }
          
          frameRenderComplete = true;
          
        } else if (verticalCounter == 10) {
          vblank = false;
        }
        
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
      
    } else if (horizontalCounter >= 48) {  // 12 cycles of horizontal blanking
      hblank = false;
    }
    
    // 5-bit counter in the 6561, but only bottom 4 bits are used. Other bit might have been used for 6562/3.
    soundClockDividerCounter = ((soundClockDividerCounter + 1) & 0xF);
    
    for (int i=0; i<4; i++) {
      if ((voiceClockDividerTriggers[i] & soundClockDividerCounter) == 0) {
        voiceCounters[i] = (voiceCounters[i] + 1) & 0x7F;
        if (voiceCounters[i] == 0) {
          // Reload the voice counter from the control register.
          voiceCounters[i] = (mem[VIC_REG_10 + i] & 0x7F);
          
          if (i == 3) {
            // For Noise voice, we perform a shift of the LFSR whenever the counter is reloaded, and
            // only shift the main voice shift register if LFSR bit 0 is 1.
            if ((noiseLFSR & 0x0001) > 0) {
              voiceShiftRegisters[i] = (
                  ((voiceShiftRegisters[i] & 0x7F) << 1) | 
                  ((mem[VIC_REG_10 + i] & 0x80) > 0? (((voiceShiftRegisters[i] & 0x80) >> 7) ^ 1) : 0));
            }
            
            // The LFSR taps are bits 3, 12, 14 and 15.
            int bit3  = (noiseLFSR >> 3) & 1;
            int bit12 = (noiseLFSR >> 12) & 1;
            int bit14 = (noiseLFSR >> 14) & 1;
            int bit15 = (noiseLFSR >> 15) & 1;
            int feedback = (((bit3 ^ bit12) ^ (bit14 ^ bit15)) ^ 1);
            noiseLFSR = ((noiseLFSR << 1) | ((feedback & ((mem[VIC_REG_10 + i] & 0x80) >> 7)) ^ 1) & 0xFFFF);

          } else {
            // For the three other voices, we shift the voice shift register whenever the counter is reloaded.
            voiceShiftRegisters[i] = (
                ((voiceShiftRegisters[i] & 0x7F) << 1) | 
                ((mem[VIC_REG_10 + i] & 0x80) > 0? (((voiceShiftRegisters[i] & 0x80) >> 7) ^ 1) : 0));
          }
        }
      }
    }
    
    // If enough cycles have elapsed since the last sample, then output another.
    if (--cyclesToNextSample <= 0) {
      if (doSound) writeSample();
      cyclesToNextSample += cyclesPerSample;
    }
    
    return frameRenderComplete;
  }
  
  /**
   * Writes a single sample to the sample buffer. If the buffer is full after writing the
   * sample, then the whole buffer is written out.
   */
  public void writeSample() {
    short sample = 0;
    
    for (int i=0; i<4; i++) {
      if ((mem[VIC_REG_10 + i] & 0x80) > 0) {
        // Voice enabled. First bit of SR goes out.
        //sample += ((voiceShiftRegisters[i] & 0x01) * 2500);  // TODO: Try shifting to multiply by 2048
        sample += ((voiceShiftRegisters[i] & 0x01) << 11);
      }      
    }
    
    sampleBuffer[sampleBufferOffset + 0] = (short)(((sample >> 2) * masterVolume) & 0x7FFF);   // TODO: Try shifting.

    // If the sample buffer is full, write it out to the audio line.
    if ((sampleBufferOffset += 1) == sampleBuffer.length) {
      try {
        if (!soundPaused) audioDevice.writeSamples(sampleBuffer, 0, sampleBuffer.length);
      } catch (Throwable e) {
        // An Exception or Error can occur here if the app is closing, so we catch and ignore.
      }
      sampleBufferOffset = 0;
    }
  }
  
  /**
   * Gets the pixels for the current frame from the VIC chip.
   * 
   * @return The pixels for the current frame. Returns null if there isn't one that is ready.
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
}
