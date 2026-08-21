package emu.jvic;

import emu.jvic.cpu.Cpu6502;
import emu.jvic.io.Joystick;
import emu.jvic.io.Keyboard;
import emu.jvic.io.SerialBus;
import emu.jvic.io.Via1;
import emu.jvic.io.Via2;
import emu.jvic.io.tape.C1530Datasette;
import emu.jvic.memory.Memory;
import emu.jvic.testutil.TestKeyboardMatrix;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the real, documented VIC-20 datasette wiring - confirmed this session
 * from {@code Via1.java}/{@code Via2.java}'s own field comments and
 * {@code C1530Datasette.java}'s class-level documentation: cassette switch
 * sense on VIA1 PA6 (input), motor control via VIA1 CA2 (output, active low),
 * pulse read via VIA2 CA1 (input). Wires a real {@link Via1}, {@link Via2},
 * and {@link C1530Datasette} together the same way {@code Machine.init} does,
 * confirming the cross-object wiring (not just {@link C1530Datasette}'s
 * isolated logic, already covered by {@code C1530DatasetteTest}).
 */
public class TapeIntegrationTest {

    private static final int ORA = 1;
    private static final int PCR = 12;

    private static final int CA2_MOTOR_ON = 0x0C; // VIA1 CA2 manual output low -> motor commanded on.
    private static final int CA2_MOTOR_OFF = 0x0E; // VIA1 CA2 manual output high -> motor commanded off.

    private C1530Datasette datasette;
    private Via1 via1;
    private Via2 via2;

    @BeforeEach
    void setUp() throws IOException {
        Cpu6502 cpu = new Cpu6502(null);
        Memory memory = new Memory(cpu, null, true);
        TestKeyboardMatrix matrix = new TestKeyboardMatrix();
        Keyboard keyboard = new Keyboard(matrix);
        Joystick joystick = new Joystick(matrix);
        SerialBus serialBus = new SerialBus();
        datasette = new C1530Datasette();

        via1 = new Via1(cpu, keyboard, joystick, serialBus, datasette, null);
        via1.setMemory(memory);
        via2 = new Via2(cpu, keyboard, joystick, serialBus, datasette, null);
        via2.setMemory(memory);

        // Start with CA2 driven high (motor commanded off), matching a fresh reset.
        via1.writeMemory(PCR, CA2_MOTOR_OFF);

        datasette.insertTape(buildTapFile(new byte[] { 0x10, 0x10, 0x10, 0x10 }));
    }

    private static byte[] buildTapFile(byte[] payload) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("C64-TAPE-RAW".getBytes("ASCII"));
        out.write(0);
        out.write(1);
        out.write(0);
        out.write(0);
        int size = payload.length;
        out.write(size & 0xFF);
        out.write((size >> 8) & 0xFF);
        out.write((size >> 16) & 0xFF);
        out.write((size >> 24) & 0xFF);
        out.write(payload);
        return out.toByteArray();
    }

    @Test
    void via1Pa6ReflectsTheRealCassetteSwitchSense() {
        assertEquals(0x40, via1.readMemory(ORA) & 0x40, "no button pressed should read high on PA6");

        datasette.play();

        assertEquals(0x00, via1.readMemory(ORA) & 0x40, "PLAY pressed should read low (active low) on PA6");
    }

    @Test
    void via1Ca2CommandsTheRealMotorOnAndOff() {
        datasette.play(); // Button pressed, but motor still needs CA2 to command it on.
        assertFalse(datasette.isMotorOn());

        via1.writeMemory(PCR, CA2_MOTOR_ON);

        assertTrue(datasette.isMotorOn(), "driving VIA1's CA2 low should command the real motor on");

        via1.writeMemory(PCR, CA2_MOTOR_OFF);

        // Per C1530Datasette's own documented behaviour (also pinned down in
        // C1530DatasetteTest), the motor doesn't stop instantly - it's still spinning
        // immediately after CA2 goes high again.
        assertTrue(datasette.isMotorOn(), "motor should still be spinning immediately after CA2 goes high");
    }

    @Test
    void via2Ca1ReflectsRealTapePulsesOnceTheMotorIsRunning() {
        // CA1_BIT (0x02) in IFR - set when VIA2's CA1 sees its configured active edge.
        // getCa1() (protected, package emu.jvic.io) drives itself off
        // datasette.isMotorOn()/getPulse() every emulateCycle() - so a real pulse from the
        // real inserted tape should surface as a real CA1 interrupt flag over enough cycles.
        final int CA1_BIT = 0x02;
        final int IFR = 13;

        datasette.play();
        via1.writeMemory(PCR, CA2_MOTOR_ON);
        assertTrue(datasette.isMotorOn());

        boolean sawCa1Flag = false;
        for (int i = 0; i < 2000 && !sawCa1Flag; i++) {
            via1.emulateCycle();
            via2.emulateCycle();
            if ((via2.readMemory(IFR) & CA1_BIT) != 0) {
                sawCa1Flag = true;
            }
        }
        assertTrue(sawCa1Flag, "VIA2's CA1 should register a real pulse from the tape once the motor is running");
    }
}
