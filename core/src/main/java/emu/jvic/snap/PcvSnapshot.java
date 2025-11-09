package emu.jvic.snap;

import emu.jvic.memory.Memory;
import emu.jvic.memory.Vic20Memory;

/**
 * This class represents a PCVIC snapshot file. PCVIC was one of the early VIC 20
 * emulators for DOS.
 * 
 * @author Lance Ewing
 */
public class PcvSnapshot extends Snapshot {

  /**
   * Constructor for PcvSnapshot.
   */
  public PcvSnapshot(byte[] pcvData) {
    decode(pcvData);
  }

  /**
   * Decodes the PCVIC snapshot file.
   * 
   * @param pcvData The raw data of the PCVIC snapshot file.
   */
  private void decode(byte[] pcvData) {
    // $0000-0015 - PCVIC signature string "PCVIC system snapshot", with a zero terminator
    char[] signatureChars = new char[] {
        (char)pcvData[0], (char)pcvData[1], (char)pcvData[2], (char)pcvData[3], (char)pcvData[4],
        (char)pcvData[5], (char)pcvData[6], (char)pcvData[7], (char)pcvData[8], (char)pcvData[9],
        (char)pcvData[10], (char)pcvData[11], (char)pcvData[12], (char)pcvData[13], (char)pcvData[14],
        (char)pcvData[15], (char)pcvData[16], (char)pcvData[17], (char)pcvData[18], (char)pcvData[19],
        (char)pcvData[20]
    };
    String signature = new String(signatureChars);
    
    // Check that this is a PCVIC snapshot.
    if (signature.equals("PCVIC system snapshot")) {
      // 0016-0017 - Version#, minor (0-99)/major (0-255).
      int versionMinor = ((int)pcvData[0x16] & 0xFF);
      int versionMajor = ((int)pcvData[0x17] & 0xFF);
      
      if ((versionMajor == 1) && (versionMinor == 0)) {
        // 001A - Register State Block (should be 35 bytes)
        int pcvDataIndex = decodeRegisterStateBlock(pcvData);
        
        // Compressed memory area $0000..$7FFF
        pcvDataIndex = uncompressMemory(pcvData, pcvDataIndex, 0x0000, 0x7FFF);
  
        // Compressed memory area $9000..$BFFF
        pcvDataIndex = uncompressMemory(pcvData, pcvDataIndex, 0x9000, 0xBFFF);
        
        // Clear the top 4 bits of the nibble RAM.
        for (int i=0x9400; i<0x9800; i++) {
          mem[i] = (mem[i] & 0x0F);
        }
      }
    }
  }
  
  /**
   * Uncompresses the given area of memory. 
   * 
   * @param pcvData The raw data of the PCVIC snapshot file.
   * @param pcvDataIndex The index of the start of the compressed memory area.
   * @param startAddress The start address of the memory area in the VIC 20's memory map.
   * @param endAddress The end address of the memory area in the VIC 20's memory map.
   * 
   * @return The index within the raw data for the byte immediately after the compressed memory area.
   */
  private int uncompressMemory(byte[] pcvData, int pcvDataIndex, int startAddress, int endAddress) {
    int leaderByte, value, memoryIndex = startAddress, expectedBytes = ((endAddress - startAddress) + 1), n, runningTotal = 0;
  
    while (true) {
      // The leader byte tells us what type of "run" of bytes it is.
      leaderByte = ((int)pcvData[pcvDataIndex++] & 0xFF);
      
      // 0..127: Copy n bytes.
      if (leaderByte < 128) {
        for (n=0; n<=leaderByte; n++) {
          value = ((int)pcvData[pcvDataIndex++] & 0xFF);
          mem[memoryIndex++] = value;
          runningTotal++;
        }
      }
  
      // 129..255: Repeat a byte n times.
      if (leaderByte > 128) {
        value = ((int)pcvData[pcvDataIndex++] & 0xFF);
        
        for (n=0; n<257-leaderByte; n++) {
          mem[memoryIndex++] = value;
          runningTotal++;
        }
      }
      
      // 128: Ignore byte.
      
      // Check on each iteration if the expected number of bytes has been uncompressed.
      if ((memoryIndex > endAddress) && (runningTotal == expectedBytes)) {
        return pcvDataIndex;
      }
    }
  }
  
  /**
   * Decodes the PCVIC snapshot's Register State Block (RSB). This is the block that contains
   * the CPU registers, VIC registers and VIA chip registers that aren't memory mapped. 
   * 
   * @param pcvData The raw data of the PCVIC snapshot file.
   * 
   * @return The index within the raw data for the byte immediately after the Register State Block (RSB).
   */
  private int decodeRegisterStateBlock(byte[] pcvData) {
    int pcvDataIndex = 0x1A;
    
    // X register
    indexRegisterX = ((int)pcvData[pcvDataIndex++] & 0xFF) + (((int)pcvData[pcvDataIndex++] & 0xFF) << 8);

    // Y register
    indexRegisterY = ((int)pcvData[pcvDataIndex++] & 0xFF) + (((int)pcvData[pcvDataIndex++] & 0xFF) << 8);
    
    // S register
    stackPointer = ((int)pcvData[pcvDataIndex++] & 0xFF) + (((int)pcvData[pcvDataIndex++] & 0xFF) << 8);
    
    // JVic assumes a high byte value of 0x01 and therefore doesn't store it in the stackPointer value.
    stackPointer = (stackPointer & 0xFF);

    // Old scan count (scrap register; don't care)
    pcvDataIndex++;
    
    // Aux flags (Bit2..Bit5 are Bit2..Bit5 (1,B,D,I) of P register. Others should be 0)
    int auxFlags = ((int)pcvData[pcvDataIndex++] & 0xFF);

    // Scanline (0..263(NTSC) or 0..311(PAL) - Values up to 32767 are possible!)
    scanLine = ((int)pcvData[pcvDataIndex++] & 0xFF) + (((int)pcvData[pcvDataIndex++] & 0xFF) << 8);

    // VIA1 IFR
    via1InterruptFlagRegister = ((int)pcvData[pcvDataIndex++] & 0xFF);
    
    // VIA1 IER
    via1InterruptEnableRegister = ((int)pcvData[pcvDataIndex++] & 0xFF);

    // VIA2 IFR
    via2InterruptFlagRegister = ((int)pcvData[pcvDataIndex++] & 0xFF);
    
    // VIA2 IER
    via2InterruptEnableRegister = ((int)pcvData[pcvDataIndex++] & 0xFF);

    // PERIP1B  VIA1   IRB
    via1InputRegisterB = ((int)pcvData[pcvDataIndex++] & 0xFF);
    
    // LATCH1B  VIA1   ORB
    via1OutputRegisterB = ((int)pcvData[pcvDataIndex++] & 0xFF);
    
    // PERIP1A  VIA1   IRA
    via1InputRegisterA = ((int)pcvData[pcvDataIndex++] & 0xFF);
    
    // LATCH1A  VIA1   ORA
    via1OutputRegisterA = ((int)pcvData[pcvDataIndex++] & 0xFF);

    // PERIP2B  VIA2   IRB
    via2InputRegisterB = ((int)pcvData[pcvDataIndex++] & 0xFF);
    
    // LATCH2B  VIA2   ORB
    via2OutputRegisterB = ((int)pcvData[pcvDataIndex++] & 0xFF);
    
    // PERIP2A  VIA2   IRA
    via2InputRegisterA = ((int)pcvData[pcvDataIndex++] & 0xFF);
    
    // LATCH2A  VIA2   ORA
    via2OutputRegisterA = ((int)pcvData[pcvDataIndex++] & 0xFF);
    
    // Timer Status. Lower 4 bits correspond to status of VIA timers:
    //  bit0  1 = VIA1 Timer1 running
    //  bit1  1 = VIA1 Timer2 running
    //  bit2  1 = VIA2 Timer1 running
    //  bit3  1 = VIA2 Timer2 running
    int timerStatus = ((int)pcvData[pcvDataIndex++] & 0xFF);
    via1TimerStatus = (timerStatus & 0x03);
    via2TimerStatus = ((timerStatus & 0x0C) >> 2);

    // V1T2LATCHL
    via1Timer2LatchLow = ((int)pcvData[pcvDataIndex++] & 0xFF);
    
    // V2T2LATCHL
    via2Timer2LatchLow = ((int)pcvData[pcvDataIndex++] & 0xFF);
    
    // V1T2TIMERL
    via1Timer2CounterLow = ((int)pcvData[pcvDataIndex++] & 0xFF);
    
    // V2T2TIMERL
    via2Timer2CounterLow = ((int)pcvData[pcvDataIndex++] & 0xFF);

    // NMI Flank
    // 0 = NMI was low since last time the NMI pin was sampled.
    // Other = NMI high since last time sampled.
    nmiFlank = ((int)pcvData[pcvDataIndex++] & 0xFF);

    // Memory Config
    //
    // Each bit corresponds to the presence of an 8k block.
    //
    // Bit=1 => area is RAM
    // Bit=0 => area is write-protected
    //   (for ROM areas or unused addressing space)
    //
    // Bit:             7    6    5    4    3    2    1    0
    // 8K-Block start: xxxx xxxx A000 xxxx 6000 4000 2000 0000
    //
    // xxxx means: Unavailable. (the system ROMs and I/O space are situated
    // here) Always set these bits to 0.
    //
    // Note that the areas 0000..03ff and 1000..2000 are considered to be
    // always occupied by RAM. For an unexpanded Vic, all blocks are set to
    // 0. Setting only the low block (bit0) it to 1 results in 3K RAM
    // expansion.
    int memoryConfig = ((int)pcvData[pcvDataIndex++] & 0xFF);
    ramExpansion = 0;
    if ((memoryConfig & 0x01) > 0) ramExpansion |= (Vic20Memory.RAM_1 | Vic20Memory.RAM_2 | Vic20Memory.RAM_3);
    if ((memoryConfig & 0x02) > 0) ramExpansion |= Vic20Memory.BLK_1;
    if ((memoryConfig & 0x04) > 0) ramExpansion |= Vic20Memory.BLK_2;
    if ((memoryConfig & 0x08) > 0) ramExpansion |= Vic20Memory.BLK_3;
    if ((memoryConfig & 0x20) > 0) ramExpansion |= Vic20Memory.BLK_5;
    
    // PC register
    programCounter = ((int)pcvData[pcvDataIndex++] & 0xFF) + (((int)pcvData[pcvDataIndex++] & 0xFF) << 8);

    // Main flags (much-used 6502 flags)
    //
    // Note that: '.' means: 'don't care'. '0' means: always set to 0.
    //        Bit: 7654 3210
    // Low byte  = SZ.. ....
    // High byte = 0V00 000C
    int mainFlags = ((int)pcvData[pcvDataIndex++] & 0xFF) + (((int)pcvData[pcvDataIndex++] & 0xFF) << 8);

    // Now that we have the main flags and aux flags, we can combine into the
    // full status register.
    // Aux flags (Bit2..Bit5 are Bit2..Bit5 (1,B,D,I) of P register. Others should be 0)
    processorStatusRegister =
        ((mainFlags & 0x0080) > 0 ? 0x80 : 0) |
        ((mainFlags & 0x4000) > 0 ? 0x40 : 0) |
         // TODO: Investigate why the B flag is set in some snapshots. Currently we're clearing it to avoid immediate RESET. 
         (auxFlags & 0x2C) |
        ((mainFlags & 0x0040) > 0 ? 0x02 : 0) |
        ((mainFlags & 0x0100) > 0 ? 0x01 : 0);
    
    // A register
    accumulator = ((int)pcvData[pcvDataIndex++] & 0xFF);

    // Scan count (CPU cycle count within scan line, 0 = End of scan line, 129(NTSC) or 131(PAL) = Start of scan line).
    scanCount = ((int)pcvData[pcvDataIndex++] & 0xFF);
    
    return pcvDataIndex;
  }
}
