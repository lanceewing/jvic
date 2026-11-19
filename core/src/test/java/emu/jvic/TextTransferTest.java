package emu.jvic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import emu.jvic.memory.RamType;
import emu.jvic.testutil.MachineTestHarness;

/**
 * Tests for {@link TextTransfer}, behind the Alt+Delete (copy screen text) and
 * Alt+Insert (paste) hot keys. The machine tests boot the real ROMs to the READY
 * prompt and drive the KERNAL's keyboard buffer directly; no program files are used.
 */
public class TextTransferTest {

    private static final int KEYBOARD_BUFFER_COUNT = 0xC6;

    private static String codes(int... values) {
        StringBuilder text = new StringBuilder();
        for (int value : values) {
            text.append((char) value);
        }
        return text.toString();
    }

    @Test
    void inTheUpperCaseSetLettersOfBothCasesTypeAsPlainLetters() {
        assertEquals(codes(0x50, 0x52, 0x49, 0x4E, 0x54), TextTransfer.toPetscii("PrInt", false));
    }

    @Test
    void inTheLowerCaseSetCapitalsTypeShifted() {
        assertEquals(codes(0xD0, 0x52, 0xC9, 0x4E, 0x54), TextTransfer.toPetscii("PrInt", true));
    }

    @Test
    void lineBreaksOfEveryKindBecomeOneReturn() {
        assertEquals(codes('1', 0x0D, '2', 0x0D, '3', 0x0D, '4'), TextTransfer.toPetscii("1\r\n2\n3\r4", false));
    }

    @Test
    void punctuationMapsToPetsciiAndUnknownCharactersAreDropped() {
        assertEquals(codes(' ', '"', '$', '@', '[', ']', 0x5C, 0x5C, 0x5E, 0x5E, 0x5F, 0x5F, ' '),
                TextTransfer.toPetscii(" \"$@[]\\£^↑_←\t{}~é", false));
    }

    @Test
    void feedFillsTheKeyboardBufferAsItEmpties() {
        TextTransfer transfer = new TextTransfer();
        transfer.queue("ABCDEFGHIJKLMNO");
        int[] mem = new int[0x10000];

        transfer.feed(mem);
        assertEquals(10, mem[KEYBOARD_BUFFER_COUNT]);
        assertEquals('A', mem[0x277]);
        assertEquals('J', mem[0x277 + 9]);

        // The KERNAL has read 7 keys, leaving 3.
        mem[KEYBOARD_BUFFER_COUNT] = 3;
        transfer.feed(mem);
        assertEquals(8, mem[KEYBOARD_BUFFER_COUNT]);
        assertEquals('K', mem[0x277 + 3]);
        assertEquals('O', mem[0x277 + 7]);
        assertTrue(transfer.isEmpty());
    }

    @Test
    void clearDropsTextNotYetTyped() {
        TextTransfer transfer = new TextTransfer();
        transfer.queue("ABC");
        transfer.clear();
        int[] mem = new int[0x10000];
        transfer.feed(mem);
        assertEquals(0, mem[KEYBOARD_BUFFER_COUNT]);
    }

    @Test
    void copiesTheBootScreen() {
        MachineTestHarness h = MachineTestHarness.withRealRoms(MachineType.PAL, RamType.RAM_UNEXPANDED);
        h.frames(50);

        assertFalse(TextTransfer.isLowerCaseMode(h.machine));
        assertEquals("**** CBM BASIC V2 ****\n\n3583 BYTES FREE\n\nREADY.", firstLines(TextTransfer.readScreen(h.machine), 5));
    }

    @Test
    void copyKeepsCaseInTheLowerCaseSet() {
        MachineTestHarness h = MachineTestHarness.withRealRoms(MachineType.PAL, RamType.RAM_UNEXPANDED);
        h.frames(50);
        // As SHIFT + Commodore does: the character set moves from $8000 to $8800.
        int register5 = h.getMemory().getMemoryMap()[0x9005].peekMemory(0x9005);
        h.getMemory().getMemoryMap()[0x9005].writeMemory(0x9005, register5 | 0x02);

        assertTrue(TextTransfer.isLowerCaseMode(h.machine));
        assertEquals("**** cbm basic v2 ****\n\n3583 bytes free\n\nready.", firstLines(TextTransfer.readScreen(h.machine), 5));
    }

    @Test
    void pastedTextIsTypedIntoBasic() {
        MachineTestHarness h = MachineTestHarness.withRealRoms(MachineType.PAL, RamType.RAM_UNEXPANDED);
        h.frames(50);
        TextTransfer transfer = new TextTransfer();
        // Longer than the keyboard buffer, so it has to be fed over several frames.
        transfer.queue(TextTransfer.toPetscii("print \"pasted\";6*7\n", TextTransfer.isLowerCaseMode(h.machine)));

        int[] mem = h.machine.getMemory().getMemoryArray();
        for (int frame = 0; frame < 50; frame++) {
            transfer.feed(mem);
            h.frames(1);
        }

        assertTrue(transfer.isEmpty());
        String screen = TextTransfer.readScreen(h.machine);
        assertTrue(screen.contains("PRINT \"PASTED\";6*7\nPASTED 42\n\nREADY."), screen);
    }

    private static String firstLines(String text, int count) {
        String[] lines = text.split("\n", -1);
        StringBuilder result = new StringBuilder();
        for (int i = 0; (i < count) && (i < lines.length); i++) {
            if (i > 0) {
                result.append('\n');
            }
            result.append(lines[i]);
        }
        return result.toString();
    }
}
