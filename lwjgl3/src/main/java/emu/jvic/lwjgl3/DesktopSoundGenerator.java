package emu.jvic.lwjgl3;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

import emu.jvic.MachineType;
import emu.jvic.sound.SoundGenerator;

/**
 * An emulation of the VIC 20 sound that is tailored for the Desktop platform.
 */
public class DesktopSoundGenerator extends SoundGenerator {

    private static final int SAMPLE_RATE = 22050;
    
    private static final int VIC_REG_10 = 0x900A;
    private static final int VIC_REG_14 = 0x900E;
    
    private int cyclesPerSample;
    private byte[] sampleBuffer;
    private int sampleBufferOffset = 0;
    private int cyclesToNextSample;
    private SourceDataLine audioLine;
    private int soundClockDividerCounter;
    private int[] voiceClockDividerTriggers;
    private int[] voiceCounters;
    private int[] voiceShiftRegisters;
    private int noiseLFSR = 0xFFFF;
    private int lastNoiseLFSR0 = 0x1;
    
    @Override
    public void initSound(MachineType machineType) {
        cyclesPerSample = (machineType.getCyclesPerSecond() / SAMPLE_RATE);
        
        try {
            // PCM SIGNED, 16 bit, mono, 2 bytes/frame, little-endian, 50ms buffer size (i.e. delay)
            int audioBufferSize = ((((SAMPLE_RATE/ 20) * 2) / 10) * 10);
            AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format, audioBufferSize);
            audioLine = (SourceDataLine)AudioSystem.getLine(info);
            audioLine.open();
            audioLine.start();
            
            sampleBuffer = new byte[audioBufferSize / 10];
            sampleBufferOffset = 0;
            
        } catch (LineUnavailableException lue) {
            audioLine = null;
        }
        
        voiceCounters = new int[4];
        voiceShiftRegisters = new int[4];
        voiceClockDividerTriggers = new int[] { 0xF, 0x7, 0x3, 0x1 };
    }

    @Override
    public void emulateCycle() {
        // 5-bit counter in the 6561, but only bottom 4 bits are used. Other bit might have been used for 6562/3.
        soundClockDividerCounter = ((soundClockDividerCounter + 1) & 0xF);

        for (int i = 0; i < 4; i++) {
            if ((voiceClockDividerTriggers[i] & soundClockDividerCounter) == 0) {
                voiceCounters[i] = (voiceCounters[i] + 1) & 0x7F;
                if (voiceCounters[i] == 0) {
                    // Reload the voice counter from the control register.
                    voiceCounters[i] = (mem[VIC_REG_10 + i] & 0x7F);

                    if (i == 3) {
                        // For Noise voice, we perform a shift of the LFSR whenever the counter is
                        // reloaded, and only shift the main voice shift register when LFSR bit 0 changes 
                        // from LOW to HIGH, i.e. on the positive edge.
                        if ((lastNoiseLFSR0 == 0) && (noiseLFSR & 0x0001) > 0) {
                            voiceShiftRegisters[i] = (((voiceShiftRegisters[i] & 0x7F) << 1)
                                    | ((mem[VIC_REG_10 + i] & 0x80) > 0 ? (((voiceShiftRegisters[i] & 0x80) >> 7) ^ 1)
                                            : 0));
                        }

                        // The LFSR taps are bits 3, 12, 14 and 15.
                        int bit3 = (noiseLFSR >> 3) & 1;
                        int bit12 = (noiseLFSR >> 12) & 1;
                        int bit14 = (noiseLFSR >> 14) & 1;
                        int bit15 = (noiseLFSR >> 15) & 1;
                        int feedback = (((bit3 ^ bit12) ^ (bit14 ^ bit15)) ^ 1);
                        lastNoiseLFSR0 = (noiseLFSR & 0x1);
                        noiseLFSR = (((noiseLFSR << 1) | (((feedback & ((mem[VIC_REG_10 + i] & 0x80) >> 7)) ^ 1) & 0x1))
                                & 0xFFFF);

                    } else {
                        // For the three other voices, we shift the voice shift register whenever the
                        // counter is reloaded.
                        voiceShiftRegisters[i] = (((voiceShiftRegisters[i] & 0x7F) << 1)
                                | ((mem[VIC_REG_10 + i] & 0x80) > 0 ? (((voiceShiftRegisters[i] & 0x80) >> 7) ^ 1)
                                        : 0));
                    }
                }
            }
        }

        // If enough cycles have elapsed since the last sample, then output another.
        if (--cyclesToNextSample <= 0) {
            writeSample();
            cyclesToNextSample += cyclesPerSample;
        }
    }

    /**
     * Writes a single sample to the sample buffer. If the buffer is full after
     * writing the sample, then the whole buffer is written out.
     */
    public void writeSample() {
        int sample = 0;
        int masterVolume = (mem[VIC_REG_14] & 0x0F);

        for (int i = 0; i < 4; i++) {
            if ((mem[VIC_REG_10 + i] & 0x80) > 0) {
                // Voice enabled. First bit of SR goes out.
                sample += ((voiceShiftRegisters[i] & 0x01) << 11);
            }
        }

        sample = ((sample >> 2) * masterVolume) & 0x7FFF;

        sampleBuffer[sampleBufferOffset + 0] = (byte)(sample & 0x00FF);
        sampleBuffer[sampleBufferOffset + 1] = (byte)((sample & 0xFF00) >> 8);
        
        // If the sample buffer is full, write it out to the audio line.
        if ((sampleBufferOffset += 2) == sampleBuffer.length) {
            audioLine.write(sampleBuffer, 0, sampleBuffer.length);
            sampleBufferOffset = 0;
        }
    }
    
    @Override
    public void pauseSound() {
        if (audioLine != null) {
            audioLine.stop();
        }
    }

    @Override
    public void resumeSound() {
        if (audioLine != null) {
            audioLine.start();
        }
    }

    @Override
    public boolean isSoundOn() {
        if (audioLine != null) {
            return audioLine.isRunning();
        } else {
            return false;
        }
    }

    @Override
    public void dispose() {
        if (audioLine != null) {
            audioLine.stop();
            audioLine.close();
        }
    }
}
