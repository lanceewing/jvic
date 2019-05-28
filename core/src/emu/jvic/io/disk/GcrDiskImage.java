package emu.jvic.io.disk;

/**
 * This class represents a GCE encoded 1541 disk image.
 * 
 * @author Lance Ewing
 */
public class GcrDiskImage {
	
  // Unlike MFM disks, the custom CBM CGR format has a variable number of sectors per track.
  private final int[][] TRACK_OFFSETS = new int[][] {
	{ 0,    0,  0x00000 },  // There is no track 0, so this is a dummy entry.
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
	
  // GCR_SECTOR_SIZE => 354
  public final static int GCR_SECTOR_SIZE = 1 + 10 + 9 + 1 + 325 + 8;
	
  // GCR conversion table - used for converting ordinary byte to 10-bits (or 4 bits to 5)
  private final int[] GCR = new int[] {
    0x0a, 0x0b, 0x12, 0x13,
    0x0e, 0x0f, 0x16, 0x17,
    0x09, 0x19, 0x1a, 0x1b,
    0x0d, 0x1d, 0x1e, 0x15
  };
	
  // 5 bits > 4 bits (0xff => invalid)
  private final int[] GCR_REV = new int[] {
    0xff, 0xff, 0xff, 0xff, // 0 - 3invalid...
    0xff, 0xff, 0xff, 0xff, // 4 - 7 invalid...
    0xff, 0x08, 0x00, 0x01, // 8 invalid... 9 = 8, a = 0, b = 1
    0xff, 0x0c, 0x04, 0x05, // c invalid... d = c, e = 4, f = 5
	
    0xff, 0xff, 0x02, 0x03, // 10-11 invalid...
    0xff, 0x0f, 0x06, 0x07, // 14 invalid...
    0xff, 0x09, 0x0a, 0x0b, // 18 invalid...
    0xff, 0x0d, 0x0e, 0xff, // 1c, 1f invalid...
  };
	
  private int numOfTracks;           // Number of tracks on the disk.
  private int[] rawImage;            // The raw disk image file loaded into memory
  private int diskID1;
  private int diskID2;
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
    
    // Read disk ID bytes from BAM sector.
    Sector bam = allTracks[18][0];
    this.diskID1 = bam.read(162);
    this.diskID2 = bam.read(163);
    
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
    Sector[] sectors = new Sector[numOfSectors + 1];
  
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
   * Builds the GCR encoded data for a sector.
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
	int cSum = 0;

	// First data is sync!
	gcrData[pos++] = 0xff;

	makeGCR(gcrData, pos, 0x08, sector ^ track ^ diskID1 ^ diskID2, sector, track);
	pos += 5;
	makeGCR(gcrData, pos, diskID2, diskID1, 0x0f, 0x0f);
	pos += 5;

	// pos = 11
	for (int i = 0; i < 9; i++) {
	  gcrData[pos++] = 0x55;
	}

	// Another sync - at position 20 (?)
	gcrData[pos++] = 0xff;
	// pos = 21
	// cancel out first 7 by setting cSum to 7
	cSum = 0x07;
	cSum ^= makeGCR(gcrData, pos, 0x07, rawData[0] & 0xff, rawData[1] & 0xff, rawData[2] & 0xff);
	pos += 5;

	// pos = 26
	for (int i = 3, n = 255; i < n; i += 4) {
	  cSum ^= makeGCR(
          gcrData, pos, 
          rawData[i + 0] & 0xff, 
          rawData[i + 1] & 0xff,
          rawData[i + 2] & 0xff,
          rawData[i + 3] & 0xff);
	  pos += 5;
	}

	cSum ^= rawData[255] & 0xff;

	makeGCR(gcrData, pos, rawData[255] & 0xff, cSum & 0xff, 0, 0);
	pos += 5;

	for (int i = 0, n = 8; i < n; i++) {
	  gcrData[pos++] = 0x55;
	}

	return gcrData;
  }
  
  /**
   * Gets the 10-bit GCR code for the given 8-bit byte.
   * 
   * @param b The 8-bit byte to encode.
   * 
   * @return The 10-bit GCR code for the given byte.
   */
  private long getGCR(int b) {
    return (GCR[b >> 4] << 5) | GCR[b & 0x0F];
  }
  
  /**
   * Makes the 5-byte GCR code for the given 4 bytes, stores them at the given position
   * in the given GCR buffer, and then returns the checksum.
   * 
   * @param gcrBuf The GCR buffer to store the 5-bit GCR code in.
   * @param pos The position within the GCR buffer to start storing the 
   * @param b1 The first byte to GCR encode.
   * @param b2 The second byte to GCR encode.
   * @param b3 The third byte to GCR encode.
   * @param b4 The fourth byte to GCR encode.
   * 
   * @return The checksum value for the 4 bytes.
   */
  public int makeGCR(int[] gcrBuf, int pos, int b1, int b2, int b3, int b4) {
    int cSum = b1 ^ b2 ^ b3 ^ b4;
    long gcr = (getGCR(b1) << 30) | (getGCR(b2) << 20) | (getGCR(b3) << 10) | getGCR(b4);
    long bits = 32;
    for (int i = 0, n = 5; i < n; i++) {
      gcrBuf[pos++] = (int) ((gcr >> bits) & 0xff);
      bits = bits - 8;
    }
    return cSum;
  }
  
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
   * sector ID, offset of the data for the sector, and the track and side of the disk where 
   * the sector resides. It also provides methods for reading and writing to/from a specified
   * sector position.
   */
  public class Sector {
    int sectorId;       // Absolute sector number. Not really required, but interesting for debug.
    int trackNum;       // This is the track that the sector is on.
    int sectorNum;      // This is the sector number within the track.
    int sectorSize;     // Should be the same for every sector on the disk.
    int[] rawData;      // The unencoded raw data from the .d64 disk image.
    int rawDataOffset;  // Offset to the start of the sector within the raw image data.
    int[] gcrData;      // GCR encoded data for the sector.
    
    public int read(int sectorPos) {
      int value = gcrData[sectorPos];
      return value;
    }
    
    public void write(int sectorPos, int data) {
      // TODO: This is just updating an array in memory. Need to add writing back to disk at some point.
      gcrData[sectorPos] = data;
    }
    
    public String toString() {
      return String.format("Sector - track#: %d, sector#: %d, size: %d, idOffset: %d, rawDataOffset: %d", 
    		  trackNum, sectorNum, sectorSize, sectorId, rawDataOffset);
    }
  }
}