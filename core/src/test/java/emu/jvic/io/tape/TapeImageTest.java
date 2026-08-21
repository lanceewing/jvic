package emu.jvic.io.tape;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link TapeImage}'s pure, dependency-free .TAP file parsing: header
 * decode, and the version 0 vs version 1 pulse-timing encodings used by
 * {@code getNumOfCyclesToNextPulse()}.
 */
public class TapeImageTest {

    private static byte[] buildTapFile(int version, byte[] payload) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("C64-TAPE-RAW".getBytes("ASCII")); // 12 bytes.
        out.write(version);
        out.write(1); // Machine: VIC-20.
        out.write(0); // Video standard: PAL.
        out.write(0); // Reserved.
        int size = payload.length;
        out.write(size & 0xFF);
        out.write((size >> 8) & 0xFF);
        out.write((size >> 16) & 0xFF);
        out.write((size >> 24) & 0xFF);
        out.write(payload);
        return out.toByteArray();
    }

    @Test
    void aNonZeroPulseByteIsMultipliedByEightRegardlessOfVersion() throws IOException {
        TapeImage tape = new TapeImage(buildTapFile(0, new byte[] { 0x10 }));

        assertEquals(0x10 << 3, tape.getNumOfCyclesToNextPulse());
    }

    @Test
    void versionZeroTreatsAZeroPulseByteAsAFixedLongDelay() throws IOException {
        TapeImage tape = new TapeImage(buildTapFile(0, new byte[] { 0x00 }));

        assertEquals(2048, tape.getNumOfCyclesToNextPulse());
    }

    @Test
    void versionOneTreatsAZeroPulseByteAsAThreeByteLittleEndianDelay() throws IOException {
        // 3-byte value 0x030201 following the zero marker byte.
        TapeImage tape = new TapeImage(buildTapFile(1, new byte[] { 0x00, 0x01, 0x02, 0x03 }));

        assertEquals(0x030201, tape.getNumOfCyclesToNextPulse());
    }

    @Test
    void versionOneTreatsAZeroThreeByteDelayAsOneToAvoidAZeroLengthPulse() throws IOException {
        TapeImage tape = new TapeImage(buildTapFile(1, new byte[] { 0x00, 0x00, 0x00, 0x00 }));

        assertEquals(1, tape.getNumOfCyclesToNextPulse());
    }

    @Test
    void isEndOfInputIsFalseWhileDataRemainsAndTrueOnceExhausted() throws IOException {
        TapeImage tape = new TapeImage(buildTapFile(0, new byte[] { 0x10, 0x20 }));

        assertFalse(tape.isEndOfInput());
        tape.getNumOfCyclesToNextPulse(); // Consumes the first byte (index 0 of 2).
        assertFalse(tape.isEndOfInput(), "one byte still remains");
        tape.getNumOfCyclesToNextPulse(); // Consumes the last byte (index 1 of 2).
        assertTrue(tape.isEndOfInput(), "consuming the last byte should mark end of input");
    }

    @Test
    void resetReturnsToTheStartOfTheData() throws IOException {
        TapeImage tape = new TapeImage(buildTapFile(0, new byte[] { 0x10, 0x20 }));
        tape.getNumOfCyclesToNextPulse();
        tape.getNumOfCyclesToNextPulse();
        assertTrue(tape.isEndOfInput());

        tape.reset();

        assertFalse(tape.isEndOfInput());
        assertEquals(0x10 << 3, tape.getNumOfCyclesToNextPulse());
    }

    @Test
    void emptyPayloadIsImmediatelyAtEndOfInput() throws IOException {
        TapeImage tape = new TapeImage(buildTapFile(0, new byte[0]));

        assertTrue(tape.isEndOfInput());
    }
}
