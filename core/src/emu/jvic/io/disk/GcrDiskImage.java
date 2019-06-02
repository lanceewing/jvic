package emu.jvic.io.disk;

/**
 * This class represents a GCE encoded 1541 disk image. A 1541 disk stores the data 
 * using the Commodore GCR encoding scheme, but the .d64 disk image format contains
 * the data already unencoded, i.e. not GCR encoded. So to emulate a 1541 disk such 
 * that the 1541 ROM will be able to read it (which expects it to be GCR encoded), 
 * we need to re-encode the .d64 image data and return the encoded bytes when the 
 * sector data is being read.
 * 
 * Some interesting notes about GCR:
 * 
 * When data is being read from a floppy diskette, the data is "clocked in" at a fixed 
 * rate. A magnetic transition is interpreted as a "1" bit. The lack of a signal when 
 * data is expected is interpreted as a "0" bit. Since the speed of the drive is not 
 * absolutely constant, we can run into problems if there are too many "0" (no signal) 
 * bits in a row. Commodore's GCR code is designed so that no GCR byte, or combination 
 * of GCR bytes, ever contains more than two consecutive "0" bits. As a further 
 * precaution, the clock is zeroed (cleared) every time a "1" bit is read. This 
 * re-synchronizes the clock to the bit stream and prevents small fluctuations in the 
 * speed of the drive from causing read errors. 
 * 
 * Note that the data recorded onto a diskette is not divided into bytes. There is just 
 * one continuous stream of bits. In order to know where to begin to read or write bits,
 * we need some special identifying mark. This is the function of the SYNC mark, a 
 * string of 10 or more 1s in a row. The GCR code is designed so that no combination of
 * bytes can produce more than eight "1" bits in a row. This guarantees the uniqueness 
 * of the sync mark. 
 * 
 * To differentiate a sync mark from a normal data byte, the 1541 writes to diskette in
 * two modes, a sync mode and a normal write mode. To appreciate the uniqueness of a 
 * sync mark, we must first look at how a normal data byte is recorded. During normal 
 * write mode each 8-bit byte is encoded into 10 bits before it is written to disk. 
 * Commodore calls this encoding scheme binary to GCR (Group Code Recording) conversion. 
 * 
 * A sync mark is 10 or more on bits (ls) recorded in succession. Only one normal data 
 * byte, an $FF (%11111111), can even begin to fill the shoes of a sync mark. During 
 * normal write mode, however, an $FF would take the following GCR form, 1010110101. 
 * Enter sync mode. When the 1541 writes an $FF in sync mode no binary to GCR conversion
 * is done. A single $FF is only eight consecutive on bits and falls short of the ten 
 * consecutive on bits needed to create a sync character. To remedy this, Commodore 
 * writes five consecutive 8-bit $FFs to disk. This records 40 on bits (ls) in succession.
 * The overkill is intentional on the DOS's part. Commodore is trying to guarantee that 
 * the 1541 will never have any trouble finding a sync mark during subsequent 
 * reads/writes to a diskette. 
 * 
 * @author Lance Ewing
 */
public class GcrDiskImage { 
  
  /**
   * Constant for the size of a GCR encoded sector. We have 5 unencoded 0xFF sync mark bytes,
   * then 10 encoded Header block bytes, then 9 unencoded 0x55 gap bytes, then another 5 
   * unencoded 0xFF sync mark bytes, then 325 encoded Data Block bytes (256 * 5/4 = 320 is
   * the data itself, and the other 5 are for the Data Block identifier, checksum, and two 
   * off bytes), then comes another 8 unencoded 0x55 gap bytes (although note that the 
   * length of the tail gap is variable and doesn't matter as long as it is at least 4 bytes).
   */
  public final static int GCR_SECTOR_SIZE = 5 + 10 + 9 + 5 + 325 + 8;
  
  /**
   * An array containing the number of sectors in each track, and the byte offset and 
   * absolute sector offset of each track. Unlike MFM disks, the custom CBM CGR format
   * has a variable number of sectors per track.
   */
  private static final int[][] TRACK_OFFSETS = new int[][] {
    // There is no track 0, so this first row is a dummy entry.
	  { 0,    0,  0x00000 },
    { 21,   0,  0x00000 },
    { 21,  21,  0x01500 }, 
    { 21,  42,  0x02A00 },
    { 21,  63,  0x03F00 },
    { 21,  84,  0x05400 },
    { 21, 105,  0x06900 },
    { 21, 126,  0x07E00 },
    { 21, 147,  0x09300 },
    { 21, 168,  0x0A800 },
    { 21, 189,  0x0BD00 },
    { 21, 210,  0x0D200 },
    { 21, 231,  0x0E700 },
    { 21, 252,  0x0FC00 },
    { 21, 273,  0x11100 },
    { 21, 294,  0x12600 },
    { 21, 315,  0x13B00 },
    { 21, 336,  0x15000 },
    { 19, 357,  0x16500 },
    { 19, 376,  0x17800 },
    { 19, 395,  0x18B00 },
    { 19, 414,  0x19E00 },
    { 19, 433,  0x1B100 },
    { 19, 452,  0x1C400 },
    { 19, 471,  0x1D700 },
    { 18, 490,  0x1EA00 },
    { 18, 508,  0x1FC00 },
    { 18, 526,  0x20E00 },
    { 18, 544,  0x22000 },
    { 18, 562,  0x23200 },
    { 18, 580,  0x24400 },
    { 17, 598,  0x25600 },
    { 17, 615,  0x26700 },
    { 17, 632,  0x27800 },
    { 17, 649,  0x28900 },
    { 17, 666,  0x29A00 },
    { 17, 683,  0x2AB00 },
    { 17, 700,  0x2BC00 },
    { 17, 717,  0x2CD00 },
    { 17, 734,  0x2DE00 },
    { 17, 751,  0x2EF00 }
  };
  
  /**
   * Commodore GCR conversion table that is sed to convert a normal 8-bit byte to a 10-bit GCR 
   * code by replacing each 4-bit value with the 5-bit value from this table.
   */
  private static final int[] GCR = new int[] {
    0x0a, 0x0b, 0x12, 0x13, 0x0e, 0x0f, 0x16, 0x17, 0x09, 0x19, 0x1a, 0x1b, 0x0d, 0x1d, 0x1e, 0x15
  };
  
  /**
   * Reverse lookup table for converting GCR encoded data to the raw unencoded for, i.e. for 
   * taking a 5-bit GCR code and looking up the 4-bit value that it represents. This table is
   * used when flushing disk writes back to the raw .d64 disk image.
   */
  private static final int[] GCR_REV = new int[] {
    0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0x08, 0x00, 0x01, 0xff, 0x0c, 0x04, 0x05, 
    0xff, 0xff, 0x02, 0x03, 0xff, 0x0f, 0x06, 0x07, 0xff, 0x09, 0x0a, 0x0b, 0xff, 0x0d, 0x0e, 0xff, 
  };
  
  /**
   * The number of tracks on this disk. Normally 35, but there are 40 track disks as well.
   */
  private int numOfTracks;
  
  /**
   * The original raw data from the .d64 disk image.
   */
  private int[] rawImage;
  
  /**
   * Byte 1 of the disk ID, read from the BAM sector.
   */
  private int diskID1;
  
  /**
   * Byte 2 of the disk ID, read from the BAM sector.
   */
  private int diskID2;
  
  /**
   * Multi-dimensional array where first index is the track number (with track 1 as the
   * first in use track; there is no 0 track), and the second index being the sector
   * number. A Sector contains all the information about that Sector, including both the
   * raw unencoded sector data (256 bytes) and the GCR encoded data.
   */
  private Sector[][] allTracks;
  
  /**
   * Constructor for GcrDiskImage.
   * 
   * @param rawImage The raw unencoded .d64 disk image data to use.
   */
  public GcrDiskImage(byte[] rawImage) {
    this.rawImage = convertByteArrayToIntArray(rawImage);
    this.numOfTracks = 35;   // TODO: 40 track images.
    
    // Load all tracks. This is the raw data that is not GCR encoded. A d64
    // disk image has already been decoded.
    allTracks = new Sector[numOfTracks + 1][];
    for (int track=1; track <= numOfTracks; track++) {
      allTracks[track] = loadTrack(track);
    }
    
    // Read disk ID bytes from BAM sector. Needed for GCR encoding.
    Sector bam = allTracks[18][0];
    this.diskID1 = bam.rawData[162];
    this.diskID2 = bam.rawData[163];
    
    // Encode all tracks (uses disk ID bytes). We need to GCR encoded it so that 
    // the DOS gets the encoded data when it reads the sectors.
    for (int track=1; track <= numOfTracks; track++) {
      encodeTrack(allTracks[track]);
    }
  }

  /**
   * Loads a full track of sectors from the identified track number.
   * 
   * @param track The track number of the track to load.
   * 
   * @return Array of Sectors for the track that was loaded.
   */
  private Sector[] loadTrack(int track) {
    int numOfSectors = TRACK_OFFSETS[track][0];
    int absoluteSector = TRACK_OFFSETS[track][1];
    int trackStart = TRACK_OFFSETS[track][2];
    Sector[] sectors = new Sector[numOfSectors];
  
    for (int sectorNum=0; sectorNum<numOfSectors; sectorNum++) {
      Sector sector = new Sector();
      sector.sectorId = absoluteSector++;
      sector.trackNum = track;
      sector.sectorNum = sectorNum;
      sector.sectorSize = 256;
      sector.rawDataOffset = trackStart + (sectorNum * 256);
      sector.rawData = new int[256];
      System.arraycopy(rawImage, sector.rawDataOffset, sector.rawData, 0, 256);
      sectors[sector.sectorNum] = sector;
    }
    
    return sectors;
  }
  
  /**
   * GCR encodes the raw data of the given Sectors.
   * 
   * @param sectors The array of Sectors to GCR encode.
   */
  private void encodeTrack(Sector[] sectors) {
    for (int sectorNum=0; sectorNum<sectors.length; sectorNum++) {
      Sector sector = sectors[sectorNum];
      sector.gcrData = buildGCRSectorData(sector.trackNum, sectorNum, sector.rawData);
    }
  }
  
  /**
   * Converts a byte array into an int array.
   * 
   * @param data The byte array to convert.
   * 
   * @return The int array.
   */
  private int[] convertByteArrayToIntArray(byte[] data) {
    int[] convertedData = new int[data.length];
    for (int i=0; i<data.length; i++) {
      convertedData[i] = ((int)data[i]) & 0xFF;
    }
    return convertedData;
  }
  
  /**
   * Builds the Commodore GCR encoded data for a sector. We need to do this because the .d64
   * disk image is already decoded, but the DOS ROM code expects the data to be GCR encoded.
   * 
   * @param track The track number for the sector.
   * @param sector The sector number of the sector within that track.
   * @param rawData The raw unencoded sector data.
   * 
   * @return The GCR encoded sector data.
   */
  private int[] buildGCRSectorData(int track, int sector, int[] rawData) {
    int[] gcrData = new int[GCR_SECTOR_SIZE];
    int pos = 0;
    int checkSum = 0;

    // Any given sector is divided into two contiguous parts: a header block and a data block.
    
    // ----------------------------------------- Header Block --------------------------------------
    
    //       5           Sync Character (DOS writes out 5 0xFF chars, but only 2 is strictly needed)
    for (int i = 0; i < 5; i++) {
      gcrData[pos++] = 0xff;
    }
    //       1           Header Block Identifier ($08)
    //       1           Header Block Checksum: The checksum is the XOR of C, S, T and ID
    //       1           Sector Number: This is the sector number (0-16/17/18/20, depending on the track). 
    //       1           Track Number: This is the track number (1-35). It is the same for all sectors on the same track.
    gcrEncode4Bytes(gcrData, pos, 0x08, sector ^ track ^ diskID1 ^ diskID2, sector, track);
    pos += 5;
    //       1           ID LO : The two-byte ID should be unique per disk and is used to detect disk 
    //       1           ID HI : changes. It is the same for all sectors on the same disk.
    //       2           Off Bytes ($0F)
    gcrEncode4Bytes(gcrData, pos, diskID2, diskID1, 0x0f, 0x0f);
    pos += 5;
    //       9           Header Gap ($55). Separates header from sector data.
    for (int i = 0; i < 9; i++) {
      gcrData[pos++] = 0x55;   // Note: This is not GCR encoded.
    }

    // ----------------------------------------- Data Block --------------------------------------
    
    //       -           Sync Character (not GCR encoded)
    for (int i = 0; i < 5; i++) {
      gcrData[pos++] = 0xff;
    }
    
    //       1           Data Block Identifier ($07)
    //       3           First 3 Data Bytes

    // Cancel out first 7 by setting cSum to 7.
    checkSum = 0x07;
    checkSum ^= gcrEncode4Bytes(gcrData, pos, 0x07, rawData[0] & 0xff, rawData[1] & 0xff, rawData[2] & 0xff);
    pos += 5;

    //       252         Next 252 Data Bytes
    for (int i = 3, n = 255; i < n; i += 4) {
      checkSum ^= gcrEncode4Bytes(
          gcrData, pos, 
          rawData[i + 0] & 0xff, 
          rawData[i + 1] & 0xff,
          rawData[i + 2] & 0xff,
          rawData[i + 3] & 0xff);
      pos += 5;
    }

    checkSum ^= rawData[255] & 0xff;
    
    //       1           Last Data Byte
    //       1           Data Block Checksum
    //       2           Off Bytes ($00)
    gcrEncode4Bytes(gcrData, pos, rawData[255] & 0xff, checkSum & 0xff, 0, 0);
    pos += 5;

    //       Variable    Tail Gap ($55). Not GCR encoded. Length normally varies, but we use 8.
    for (int i = 0; i < 8; i++) {
      gcrData[pos++] = 0x55;
    }

    return gcrData;
  }
  
  /**
   * Returns the 10-bit GCR code for the given 8-bit byte.
   * 
   * @param b The 8-bit byte to encode.
   * 
   * @return The 10-bit GCR code for the given byte.
   */
  private long gcrEncode(int b) {
    return (GCR[(b >> 4) & 0x0F] << 5) | GCR[b & 0x0F];
  }
  
  /**
   * Builds the 5-byte GCR code for the given 4 bytes, stores them at the given position
   * in the given GCR buffer, and then returns the checksum.
   * 
   * @param gcrEncodedData The GCR buffer to store the 5-bit GCR code in.
   * @param pos The position within the GCR buffer to start storing the 
   * @param b1 The first byte to GCR encode.
   * @param b2 The second byte to GCR encode.
   * @param b3 The third byte to GCR encode.
   * @param b4 The fourth byte to GCR encode.
   * 
   * @return The checksum value for the 4 bytes.
   */
  private int gcrEncode4Bytes(int[] gcrEncodedData, int pos, int b1, int b2, int b3, int b4) {
    long gcrCodes = (gcrEncode(b1) << 30) | (gcrEncode(b2) << 20) | (gcrEncode(b3) << 10) | gcrEncode(b4);
    for (int i = 0, bits = 32; i < 5; i++, bits -= 8) {
      gcrEncodedData[pos++] = (int)((gcrCodes >> bits) & 0xff);
    }
    return (b1 ^ b2 ^ b3 ^ b4);
  }
  
  /**
   * Gets the Sector for the given track and sector.
   * 
   * @param track The track that the Sector is on.
   * @param sector The number of the Sector on that track.
   * 
   * @return The Sector for the given track and sector.
   */
  public Sector getSector(int track, int sector) {
	  return this.allTracks[track][sector];
  }
  
  /**
   * Gets the number of sectors in the given track.
   * 
   * @param track The track to get the number of sectors in.
   * 
   * @return The number of sectors in the given track.
   */
  public int getSectorCount(int track) {
    return TRACK_OFFSETS[track][0];
  }
  
  /**
   * This class represents a Sector within the GCR disk image. It stores details such as the
   * absolute sector num, offset of the data for the sector, and the track where  the sector 
   * resides. It also provides methods for reading and writing to/from a specified sector 
   * position.
   */
  public class Sector {
    
    int sectorId;       // Absolute sector number. Not really required, but interesting for debug.
    int trackNum;       // This is the track that the sector is on.
    int sectorNum;      // This is the sector number within the track.
    int sectorSize;     // Should be the same for every sector on the disk. Raw unencoded sector size.
    int[] rawData;      // The unencoded raw data from the .d64 disk image.
    int rawDataOffset;  // Offset to the start of the sector within the raw image data.
    int[] gcrData;      // GCR encoded data for the sector.
    
    /**
     * Reads a byte from this Sector from the given position. The position is an index into
     * the GCR encoded sector data.
     * 
     * @param sectorPos The position (i.e. offset) into the Sector to get the byte from.
     * 
     * @return The byte from the given sector position in this Sector.
     */
    public int read(int sectorPos) {
      int value = gcrData[sectorPos];
      return value;
    }
    
    /**
     * Writes a byte to the given position of this Sector. The position is an index into 
     * the GCR encoded sector data.
     * 
     * @param sectorPos The position (i.e. offset) into the Sector to write the byte to.
     * @param data The byte to write into the given sector position.
     */
    public void write(int sectorPos, int data) {
      // TODO: This is just updating an array in memory. Need to add writing back to disk at some point.
      gcrData[sectorPos] = data;
    }
  }
}