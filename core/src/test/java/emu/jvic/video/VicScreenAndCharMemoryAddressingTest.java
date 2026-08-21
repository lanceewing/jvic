package emu.jvic.video;

import emu.jvic.MachineType;
import emu.jvic.testutil.VicTestHarness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Screen memory, character memory and colour memory start-address formulas (see
 * the {@code screen_mem_start}/{@code char_mem_start}/{@code colour_mem_start}
 * local variables computed at the top of {@code Vic6560.emulateCycle()}):
 * <pre>
 *   screen_mem_start = ((reg5 &amp; 0xF0) &lt;&lt; 6) | ((reg2 &amp; 0x80) &lt;&lt; 2)   [VIC address space]
 *   char_mem_start   = (reg5 &amp; 0x0F) &lt;&lt; 10                          [VIC address space]
 *   colour_mem_start = 0x9400 | ((reg2 &amp; 0x80) &lt;&lt; 2)                [real CPU address]
 * </pre>
 * Screen/char addresses are in the VIC's own 14-bit address space and go through
 * {@code Vic.VIC_MEM_TABLE} to reach a real CPU address (VIC $0000-$1FFF -> CPU
 * $8000-$9FFF; VIC $2000-$3FFF -> CPU $0000-$1FFF); colour memory is always a
 * direct, real CPU address. Verified indirectly by rendering: a marker byte is
 * placed at the CPU address the formula predicts, and the render is checked for
 * it; a decoy byte at a plausible-but-wrong nearby address confirms the VIC
 * really did read from the predicted address and not some neighbour of it.
 */
public class VicScreenAndCharMemoryAddressingTest {

    private static final int CYAN = 0x73D5FFFF;
    private static final int BLACK = 0x000000FF;

    private VicTestHarness h;

    @BeforeEach
    void setUp() {
        h = new VicTestHarness(MachineType.NTSC);
        h.writeRegister(2, 1);    // 1 column (bit 7 set per-test as needed)
        h.writeRegister(3, 0x02); // 1 row, single height
        h.writeRegister(0, 4);    // screen origin x (see VicTextModeRenderingTest)
        h.writeRegister(1, 0);    // screen origin y
        h.writeRegister(15, 0x09); // background=black, non-reverse, border=white
        h.writeRegister(14, 0x00);
    }

    private int firstCharacterPixelColour() {
        h.runToEndOfFrame();
        h.runToEndOfFrame();
        return h.pixelData.pixels[11]; // see VicTextModeRenderingTest: column 11 is the first character pixel
    }

    @Test
    void unexpandedVic20DefaultAddresses() {
        // The well-known real VIC-20 unexpanded defaults: screen at $1E00, char
        // ROM at $8000, colour RAM at $9600 (not $9400 - that's the expanded-memory
        // default), reached via reg5=$F0 and reg2 bit 7 set.
        h.writeRegister(5, 0xF0);
        h.writeRegister(2, 0x80 | 1); // bit7 set (contributes to both screen and colour addresses) + 1 column

        h.poke(0x1E00, 0x00); // screen memory: cell index 0
        h.poke(0x9600, 0x03); // colour memory: foreground=cyan, hires
        h.poke(0x8000, 0xF0); // char memory: bitmap

        assertEquals(CYAN, firstCharacterPixelColour());
    }

    @Test
    void colourMemoryMovesFrom9400To9600WhenScreenBit8IsSet() {
        h.writeRegister(5, 0xF0);
        h.writeRegister(2, 1); // bit7 clear -> colour mem stays at $9400
        h.poke(0x1C00, 0x00);
        h.poke(0x8000, 0xF0);

        h.poke(0x9400, 0x03); // correct location
        h.poke(0x9600, 0x05); // decoy at the "other" possible location - must NOT be used

        assertEquals(CYAN, firstCharacterPixelColour());
    }

    @Test
    void charMemoryNibbleSelectsAmongTheDocumentedVicMemoryMapBlocks() {
        // reg5 low nibble = 3 -> char mem VIC address $0C00, which the class-level
        // VIC_MEM_TABLE comment documents as CPU $8C00 ("Reversed upper/lower case
        // ROM" in a real VIC-20's memory map).
        h.writeRegister(5, 0xF0 | 0x03);
        h.writeRegister(2, 1);
        h.poke(0x1C00, 0x00);
        h.poke(0x9400, 0x03);

        h.poke(0x8000, 0x00); // decoy at the char-nibble-0 location - must NOT be used
        h.poke(0x8C00, 0xF0); // correct location for char-mem nibble 3

        assertEquals(CYAN, firstCharacterPixelColour());
    }

    @Test
    void screenMemoryCanPointIntoTheUpperVicAddressSpace() {
        // VIC address >= $2000 maps to real CPU address (vicAddress - $2000), i.e.
        // the low 8KB of VIC-20 system RAM, not the $8000-$9FFF ROM/RAM area.
        // reg5 high nibble = 0x0 with reg2 bit7 set -> screen mem VIC $0200 (CPU $8200)...
        // to land below $2000 instead, use reg2 bit7 clear with reg5 high nibble small
        // enough: reg5=0x00 -> screen VIC address 0 -> CPU $8000. That's still the
        // "lower" (ROM-mirrored) space; to reach the "upper" space (CPU $0000-$1FFF)
        // we need screen_mem_start >= $2000, e.g. reg5 high nibble = 0x8 (VIC $2000 -> CPU $0000).
        h.writeRegister(5, 0x80); // screen mem VIC $2000 -> CPU $0000; char mem VIC $0000 -> CPU $8000
        h.writeRegister(2, 1);
        h.poke(0x0000, 0x00); // screen memory now lives in real system RAM at $0000
        h.poke(0x9400, 0x03);
        h.poke(0x8000, 0xF0);

        assertEquals(CYAN, firstCharacterPixelColour());
    }
}
