package emu.jvic.cpu;

import emu.jvic.testutil.CpuTestHarness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * JMP (absolute + indirect), JSR, RTS, RTI.
 *
 * <p>JMP indirect deliberately reproduces the famous NMOS 6502 page-boundary bug:
 * if the pointer's low byte is $FF, the high byte of the target is fetched from
 * the START of the same page instead of the next page (e.g. JMP ($02FF) reads the
 * target's high byte from $0200, not $0300). This is a well-known hardware quirk
 * that real VIC-20 software may rely on (or avoid), so the test locks in that the
 * emulator continues to reproduce it rather than "fixing" it.
 *
 * <p>NOTE on expected PC values: {@link CpuTestHarness#runInstruction()} runs until
 * the CPU has fetched the opcode of the FOLLOWING instruction (fused into the last
 * cycle of the current one), so PC is always one further byte past wherever
 * control actually landed by the time it returns. Expected PCs below are therefore
 * "destination + 1".
 */
public class JumpAndSubroutineTest {

    private CpuTestHarness h;

    @BeforeEach
    void setUp() {
        h = new CpuTestHarness();
    }

    @Test
    void jmpAbsolute() {
        long cycles = h.execute(0x0300, 0x4C, 0x00, 0x04); // JMP $0400
        assertEquals(0x0401, h.cpu.getProgramCounter());
        assertEquals(3, cycles);
    }

    @Test
    void jmpIndirect() {
        h.writeWord(0x0250, 0x0400);
        long cycles = h.execute(0x0300, 0x6C, 0x50, 0x02); // JMP ($0250)
        assertEquals(0x0401, h.cpu.getProgramCounter());
        assertEquals(5, cycles);
    }

    @Test
    void jmpIndirectPageBoundaryBug() {
        h.writeByte(0x02FF, 0x00); // target low byte
        h.writeByte(0x0300, 0x99); // what the HIGH byte would be if fetched correctly
        h.writeByte(0x0200, 0x03); // what real NMOS hardware actually fetches instead (wraps within page)
        long cycles = h.execute(0x0400, 0x6C, 0xFF, 0x02); // JMP ($02FF)
        assertEquals(0x0301, h.cpu.getProgramCounter()); // 0x00 | (0x03 << 8) = 0x0300, NOT 0x9900
        assertEquals(5, cycles);
    }

    @Test
    void jsrPushesAddressOfLastByteOfJsrInstructionMinusOne() {
        h.prepareInstruction(0x0300, 0x20, 0x10, 0x03); // JSR $0310
        h.cpu.setStackPointer(0xFF);
        long cycles = h.runInstruction();
        assertEquals(0x0311, h.cpu.getProgramCounter());
        assertEquals(0xFD, h.cpu.getStackPointer());
        assertEquals(0x03, h.readByte(0x01FF)); // high byte of (0x0300+2) pushed first
        assertEquals(0x02, h.readByte(0x01FE)); // low byte of (0x0300+2) pushed second
        assertEquals(6, cycles);
    }

    @Test
    void rtsPullsReturnAddressAndIncrementsByOne() {
        h.writeByte(0x01FF, 0x03); // pushed PCH
        h.writeByte(0x01FE, 0x02); // pushed PCL (i.e. $0302, one less than the real return address)
        h.prepareInstruction(0x0300, 0x60); // RTS
        h.cpu.setStackPointer(0xFD);
        long cycles = h.runInstruction();
        assertEquals(0x0304, h.cpu.getProgramCounter());
        assertEquals(0xFF, h.cpu.getStackPointer());
        assertEquals(6, cycles);
    }

    @Test
    void jsrThenRtsRoundTripsToInstructionAfterJsr() {
        h.loadBytes(0x0300, 0x20, 0x10, 0x03); // JSR $0310
        h.loadBytes(0x0310, 0x60);             // RTS
        h.cpu.reset();
        h.cpu.setProgramCounter(0x0300);
        h.cpu.setStackPointer(0xFF);

        h.cpu.step(); // prime: fetch JSR opcode
        h.cpu.step(); // run JSR, fetch RTS opcode at the target (0x0310)
        assertEquals(0x0311, h.cpu.getProgramCounter());

        h.cpu.step(); // run RTS
        assertEquals(0x0304, h.cpu.getProgramCounter());
        assertEquals(0xFF, h.cpu.getStackPointer());
    }

    @Test
    void rtiPullsStatusAndProgramCounterWithoutIncrementing() {
        h.writeByte(0x01FF, 0x03); // pushed PCH
        h.writeByte(0x01FE, 0x00); // pushed PCL -> $0300, used as-is (no +1, unlike RTS)
        h.writeByte(0x01FD, 0x81); // pushed status: N + C set
        h.prepareInstruction(0x0400, 0x40); // RTI
        h.cpu.setStackPointer(0xFC);
        long cycles = h.runInstruction();
        assertEquals(0x0301, h.cpu.getProgramCounter());
        assertEquals(0xFF, h.cpu.getStackPointer());
        assertEquals(6, cycles);
        assertEquals(true, h.cpu.getNegativeResultFlag());
        assertEquals(true, h.cpu.getCarryFlag());
    }
}
