package emu.jvic.testutil;

import emu.jvic.cpu.Cpu6502;
import emu.jvic.memory.Memory;

/**
 * Test harness for driving a {@link TestVia6522} in isolation. Register numbers
 * (0-15) are passed directly as the "address" to {@link #write}/{@link #read},
 * since {@code Via6522} decodes the register purely from the bottom 4 bits.
 *
 * <p>Register map (from the MOS 6522 datasheet / Via6522's own field comments):
 * 0=ORB/IRB, 1=ORA/IRA (with handshake), 2=DDRB, 3=DDRA, 4=T1C-L, 5=T1C-H,
 * 6=T1L-L, 7=T1L-H, 8=T2C-L, 9=T2C-H, 10=SR, 11=ACR, 12=PCR, 13=IFR, 14=IER,
 * 15=ORA/IRA (no handshake).
 */
public class ViaTestHarness {

    public static final int ORB = 0;
    public static final int ORA = 1;
    public static final int DDRB = 2;
    public static final int DDRA = 3;
    public static final int T1C_L = 4;
    public static final int T1C_H = 5;
    public static final int T1L_L = 6;
    public static final int T1L_H = 7;
    public static final int T2C_L = 8;
    public static final int T2C_H = 9;
    public static final int SR = 10;
    public static final int ACR = 11;
    public static final int PCR = 12;
    public static final int IFR = 13;
    public static final int IER = 14;
    public static final int ORA_NO_HANDSHAKE = 15;

    // IFR/IER bit masks.
    public static final int IRQ_BIT = 0x80;
    public static final int TIMER1_BIT = 0x40;
    public static final int TIMER2_BIT = 0x20;
    public static final int CB1_BIT = 0x10;
    public static final int CB2_BIT = 0x08;
    public static final int SHIFT_BIT = 0x04;
    public static final int CA1_BIT = 0x02;
    public static final int CA2_BIT = 0x01;

    public final TestVia6522 via;

    public ViaTestHarness() {
        this(true);
    }

    public ViaTestHarness(boolean autoResetIrq) {
        Cpu6502 cpu = new Cpu6502(null);
        Memory memory = new Memory(cpu, null, true);
        via = new TestVia6522(autoResetIrq);
        via.setMemory(memory);
    }

    public void write(int register, int value) {
        via.writeMemory(register, value);
    }

    public int read(int register) {
        return via.readMemory(register);
    }

    public void cycle() {
        via.emulateCycle();
    }

    public void cycles(int count) {
        for (int i = 0; i < count; i++) {
            cycle();
        }
    }

    /** Writes a 16-bit value as low byte then high byte to the given register pair. */
    public void writeWord(int lowRegister, int highRegister, int value) {
        write(lowRegister, value & 0xFF);
        write(highRegister, (value >> 8) & 0xFF);
    }
}
