package emu.jvic.memory;

import emu.jvic.MachineType;
import emu.jvic.config.AppConfigItem;
import emu.jvic.cpu.Cpu6502;
import emu.jvic.io.Joystick;
import emu.jvic.io.Keyboard;
import emu.jvic.io.SerialBus;
import emu.jvic.io.Via1;
import emu.jvic.io.Via2;
import emu.jvic.io.tape.C1530Datasette;
import emu.jvic.testutil.RecordingPixelData;
import emu.jvic.testutil.TestKeyboardMatrix;
import emu.jvic.video.Vic6561;
import org.junit.jupiter.api.Test;

import java.util.Queue;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests {@link Vic20Memory#loadCart} and {@link Vic20Memory#loadBasicProgram},
 * previously-untested pure logic for cartridge splitting and BASIC-pointer
 * patching on a PRG load.
 */
public class Vic20MemoryCartAndProgramLoadTest {

    private static Vic20Memory buildMemory() {
        Cpu6502 cpu = new Cpu6502(null);
        TestKeyboardMatrix matrix = new TestKeyboardMatrix();
        Keyboard keyboard = new Keyboard(matrix);
        Joystick joystick = new Joystick(matrix);
        SerialBus serialBus = new SerialBus();
        C1530Datasette datasette = new C1530Datasette();
        RecordingPixelData pixelData = new RecordingPixelData();
        pixelData.init(MachineType.PAL.getTotalScreenWidth() * 2, MachineType.PAL.getTotalScreenHeight() * 2);

        Vic6561 vic = new Vic6561(pixelData, MachineType.PAL, null);
        Via1 via1 = new Via1(cpu, keyboard, joystick, serialBus, datasette, null);
        Via2 via2 = new Via2(cpu, keyboard, joystick, serialBus, datasette, null);

        return new Vic20Memory(cpu, vic, via1, via2, RamType.RAM_35K.getRamPlacement(), MachineType.PAL,
                new byte[8192], new byte[8192], new byte[4096], null);
    }

    private static byte[] fill(int length, int value) {
        byte[] data = new byte[length];
        for (int i = 0; i < length; i++) {
            data[i] = (byte) ((value + i) & 0xFF);
        }
        return data;
    }

    // ------------------------------------------------------------ loadCart ----

    @Test
    void a16kCartSplitsIntoTwo8kHalvesAtTheDefaultAddresses() {
        Vic20Memory memory = buildMemory();
        byte[] cartData = fill(16384, 0);

        memory.loadCart(cartData, new AppConfigItem());

        int[] mem = memory.getMemoryArray();
        assertEquals(cartData[0] & 0xFF, mem[0x6000]);
        assertEquals(cartData[8191] & 0xFF, mem[0x7FFF]);
        assertEquals(cartData[8192] & 0xFF, mem[0xA000]);
        assertEquals(cartData[16383] & 0xFF, mem[0xBFFF]);
    }

    @Test
    void a16kCartHonoursACustomLoadAddressOverride() {
        Vic20Memory memory = buildMemory();
        byte[] cartData = fill(16384, 0);
        AppConfigItem appConfigItem = new AppConfigItem();
        appConfigItem.setLoadAddress("2000|A000");

        memory.loadCart(cartData, appConfigItem);

        int[] mem = memory.getMemoryArray();
        assertEquals(cartData[0] & 0xFF, mem[0x2000]);
        assertEquals(cartData[8191] & 0xFF, mem[0x3FFF]);
        assertEquals(cartData[8192] & 0xFF, mem[0xA000]);
        assertEquals(cartData[16383] & 0xFF, mem[0xBFFF]);
    }

    @Test
    void aSmallCartLoadsAsASingleBlockAt0xA000() {
        Vic20Memory memory = buildMemory();
        byte[] cartData = fill(4096, 0);

        memory.loadCart(cartData, new AppConfigItem());

        int[] mem = memory.getMemoryArray();
        assertEquals(cartData[0] & 0xFF, mem[0xA000]);
        assertEquals(cartData[4095] & 0xFF, mem[0xA000 + 4095]);
    }

    // ------------------------------------------------------------ loadBasicProgram ----

    @Test
    void loadingWithoutWaitingForBasicWritesDataAndPointersImmediately() {
        Vic20Memory memory = buildMemory();
        int startAddress = 0x1001;
        byte[] payload = { 0x11, 0x22, 0x33 };
        byte[] programData = new byte[2 + payload.length];
        programData[0] = (byte) (startAddress & 0xFF);
        programData[1] = (byte) ((startAddress >> 8) & 0xFF);
        System.arraycopy(payload, 0, programData, 2, payload.length);

        Callable<Queue<char[]>> result = memory.loadBasicProgram(programData, false);

        assertNull(result, "waitForBasic=false should load immediately and return null");
        int[] mem = memory.getMemoryArray();
        assertEquals(0x11, mem[startAddress]);
        assertEquals(0x22, mem[startAddress + 1]);
        assertEquals(0x33, mem[startAddress + 2]);

        int endAddress = startAddress + programData.length;
        assertEquals(startAddress & 0xFF, mem[0x2b]);
        assertEquals(startAddress & 0xFF, mem[0xac]);
        assertEquals((startAddress >> 8) & 0xFF, mem[0x2c]);
        assertEquals((startAddress >> 8) & 0xFF, mem[0xad]);
        assertEquals(endAddress & 0xFF, mem[0x2d]);
        assertEquals(endAddress & 0xFF, mem[0x2f]);
        assertEquals(endAddress & 0xFF, mem[0x31]);
        assertEquals(endAddress & 0xFF, mem[0xae]);
        assertEquals((endAddress >> 8) & 0xFF, mem[0x2e]);
        assertEquals((endAddress >> 8) & 0xFF, mem[0x30]);
        assertEquals((endAddress >> 8) & 0xFF, mem[0x32]);
        assertEquals((endAddress >> 8) & 0xFF, mem[0xaf]);
    }

    @Test
    void loadingWhileWaitingForBasicReturnsACallableThatLoadsWhenRunAndQueuesRun() throws Exception {
        Vic20Memory memory = buildMemory();
        int startAddress = 0x1001;
        byte[] programData = { (byte) (startAddress & 0xFF), (byte) ((startAddress >> 8) & 0xFF), 0x42 };

        Callable<Queue<char[]>> result = memory.loadBasicProgram(programData, true);

        assertEquals(0, memory.getMemoryArray()[startAddress], "should not have loaded yet before the callable runs");

        Queue<char[]> autoRunCmds = result.call();

        assertEquals(0x42, memory.getMemoryArray()[startAddress]);
        assertEquals(1, autoRunCmds.size());
        assertEquals("RUN", new String(autoRunCmds.peek()));
    }
}
