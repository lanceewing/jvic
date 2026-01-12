package emu.jvic.io.tape;

/**
 * This class holds the data for a .TAP image file.
 */
public class TapeImage {

    /**
     * The .TAP file version (0 or 1)
     */
    private int version;
    
    /**
     * Computer Platform (0 = C64, 1 = VIC-20, 2 = C16, Plus/4, 3 = PET, 4 = C5x0, 5 = C6x0, C7x0)
     */
    private int machine;
    
    /**
     * Video Standard (0 = PAL, 1 = NTSC, 2 = OLD NTSC, 3 = PALN). Only 1st two apply to VIC 20.
     */
    private int videoStandard;
    
    /**
     * The data payload of the .TAP file.
     */
    private byte[] dataBuffer;
    
    /**
     * The current position within the .TAP file data.
     */
    private int position;
    
    /**
     * Whether the end of the data input has been reached or not.
     */
    private boolean endOfInput;

    /**
     * Constructor for TapeImage.
     * 
     * @param rawImage
     */
    public TapeImage(byte[] rawImage) {
        dataBuffer = rawImage;
        reset();
        
        // Read .TAP file header.
        getString(12);                   // "C64-TAPE-RAW"
        version = getByte();             // Version (0 or 1)
        machine = getByte();             // Machine
        videoStandard = getByte();       // Video standard
        getByte();                       // Reserved
        
        int size = getDoubleWord();      // Size
        if (size < 0) {
            return;
        }
        
        // Read .tap file payload data.
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
            data[i] = (byte)getByte();
        }
        
        // Replace original buffer with the payload, i.e. without the header.
        dataBuffer = data;
        
        reset();
    }
    
    /**
     * Resets tape back to the start of the data.
     */
    public void reset() {
        endOfInput = (dataBuffer.length == 0);
        position = 0;
    }
    
    /**
     * Returns whether the end of the tape data has been reached.
     * 
     * @return true if the end of the tape data has been reached; otherwise false.
     */
    public boolean isEndOfInput() {
        return endOfInput;
    }

    /**
     * Get the number of CPU clock cycles to the next pulse. This is the time period between
     * the current falling edge and the next falling edge. It is this transition from high to
     * low that is the event that the KERNAL acts on via interrupts caused by the VIA #2 CA1
     * pulses.
     * 
     * @return The number of CPU clock cycles to the next pulse.
     */
    public int getNumOfCyclesToNextPulse() {
        // Non-zero values are the number of CPU clock cycles since the last transition divided by 8.
        int tapePulseDelay = (getByte() << 3);
        if (tapePulseDelay == 0) {
            // The value zero has a different meaning depending on file version.
            if (version == 1) {
                // In a version 1 file the period is encoded in the following three bytes, least 
                // significant byte first.
                tapePulseDelay = (getByte() | (getByte() << 8) | (getByte() << 16));
                if (tapePulseDelay == 0) {
                    tapePulseDelay = 1;
                }
            } else {
                // In a version 0 file they represent a period in excess of 2040 CPU clock cycles.
                tapePulseDelay = 2048;
            }
        }
        return tapePulseDelay;
    }

    /**
     * Gets a byte of data from the raw .TAP file data.
     * 
     * @return a byte of data from the raw .TAP file data.
     */
    private int getByte() {
        if (endOfInput) {
            return 0;
        }
        endOfInput = (position >= (dataBuffer.length - 1));
        return (dataBuffer[position++] & 0xFF);
    }

    /**
     * Gets a word of data (16 bits) from the raw .TAP file data.
     * 
     * @return a word of data from the raw .TAP file data.
     */
    private int getWord() {
        return (getByte() | (getByte() << 8));
    }

    /**
     * Gets a double word of data (32 bits) from the raw .TAP file data.
     * 
     * @return a double word of data from the raw .TAP file data.
     */
    private int getDoubleWord() {
        return (getWord() | (getWord() << 16));
    }

    /**
     * Gets a String of the given length from the .TAP file data.
     * 
     * @param length The length of the String to return.
     * 
     * @return A String of the given length from the .TAP file data.
     */
    private String getString(int length) {
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < length; i++) {
            int c = getByte();
            str.append((char)c);
        }
        return str.toString();
    }
}
