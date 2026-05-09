package emu.jvic.teavm;

import emu.jvic.MachineType;
import emu.jvic.sound.SoundGenerator;
import org.teavm.jso.typedarrays.Float32Array;
import org.teavm.jso.typedarrays.SharedArrayBuffer;

public class TeaVMSoundGenerator extends SoundGenerator {

    public static final int SAMPLE_RATE = 22050;
    public static final int SAMPLE_LATENCY = 3072;

    private int vicReg10 = 0x900A;
    private int vicReg14 = 0x900E;

    private boolean soundOn;
    private boolean writeSamplesEnabled;
    private int cyclesPerSample;
    private int soundClockDividerCounter;
    private int[] voiceClockDividerTriggers;
    private int[] voiceCounters;
    private int[] voiceShiftRegisters;
    private int noiseLfsr = 0xFFFF;
    private int lastNoiseLfsr0 = 0x1;

    private Float32Array sampleBuffer;
    private int sampleBufferOffset;
    private double cyclesToNextSample;
    private TeaVMSharedQueue sampleSharedQueue;
    private TeaVMPSGAudioWorklet audioWorklet;
    private TeaVMJVicRunner jvicRunner;

    public TeaVMSoundGenerator() {
        this(null);
    }

    public TeaVMSoundGenerator(SharedArrayBuffer audioBufferSharedArrayBuffer) {
        if (audioBufferSharedArrayBuffer == null) {
            audioBufferSharedArrayBuffer = TeaVMSharedQueue.getStorageForCapacity(SAMPLE_RATE);
        }
        sampleSharedQueue = new TeaVMSharedQueue(audioBufferSharedArrayBuffer);
        sampleBuffer = Float32Array.create(512);
        sampleBufferOffset = 0;
    }

    @Override
    public void initSound(MachineType machineType) {
        soundOn = false;
        writeSamplesEnabled = false;
        cyclesPerSample = (machineType.getCyclesPerSecond() / SAMPLE_RATE);
        cyclesToNextSample = cyclesPerSample;

        voiceCounters = new int[4];
        voiceShiftRegisters = new int[4];
        voiceClockDividerTriggers = new int[] { 0xF, 0x7, 0x3, 0x1 };
        noiseLfsr = 0xFFFF;
        lastNoiseLfsr0 = 0x1;
        soundClockDividerCounter = 0;
        sampleBufferOffset = 0;

        vicReg10 = 0x900A;
        vicReg14 = 0x900E;
        if (machineType.isVIC44K()) {
            vicReg10 = 0xBC0A;
            vicReg14 = 0xBC0E;
        }
    }

    @Override
    public void emulateCycle() {
        soundClockDividerCounter = (soundClockDividerCounter + 1) & 0xF;

        for (int index = 0; index < 4; index++) {
            if ((voiceClockDividerTriggers[index] & soundClockDividerCounter) == 0) {
                voiceCounters[index] = (voiceCounters[index] + 1) & 0x7F;
                if (voiceCounters[index] == 0) {
                    voiceCounters[index] = mem[vicReg10 + index] & 0x7F;

                    if (index == 3) {
                        if ((lastNoiseLfsr0 == 0) && ((noiseLfsr & 0x0001) > 0)) {
                            voiceShiftRegisters[index] = ((voiceShiftRegisters[index] & 0x7F) << 1)
                                    | ((mem[vicReg10 + index] & 0x80) > 0
                                            ? (((voiceShiftRegisters[index] & 0x80) >> 7) ^ 1)
                                            : 0);
                        }

                        int bit3 = (noiseLfsr >> 3) & 1;
                        int bit12 = (noiseLfsr >> 12) & 1;
                        int bit14 = (noiseLfsr >> 14) & 1;
                        int bit15 = (noiseLfsr >> 15) & 1;
                        int feedback = ((bit3 ^ bit12) ^ (bit14 ^ bit15)) ^ 1;
                        lastNoiseLfsr0 = noiseLfsr & 0x1;
                        noiseLfsr = ((noiseLfsr << 1)
                                | ((((feedback & ((mem[vicReg10 + index] & 0x80) >> 7)) ^ 1) & 0x1))) & 0xFFFF;
                    } else {
                        voiceShiftRegisters[index] = ((voiceShiftRegisters[index] & 0x7F) << 1)
                                | ((mem[vicReg10 + index] & 0x80) > 0
                                        ? (((voiceShiftRegisters[index] & 0x80) >> 7) ^ 1)
                                        : 0);
                    }
                }
            }
        }

        if (--cyclesToNextSample <= 0) {
            cyclesToNextSample += cyclesPerSample;
            if (writeSamplesEnabled) {
                writeSample();
            }
        }
    }

    @Override
    public void pauseSound() {
        soundOn = false;
        if (audioWorklet != null) {
            audioWorklet.suspend();
        }
    }

    @Override
    public void resumeSound() {
        soundOn = true;
        if (sampleSharedQueue != null) {
            clearSampleQueue();
        }
        if (audioWorklet != null) {
            audioWorklet.resetStats();
            audioWorklet.resume();
            if (audioWorklet.isReady() && (jvicRunner != null)) {
                jvicRunner.notifyAudioWorkletReady();
            }
        }
    }

    @Override
    public boolean isSoundOn() {
        return (audioWorklet != null) ? audioWorklet.isRunning() : soundOn;
    }

    @Override
    public void dispose() {
        soundOn = false;
        writeSamplesEnabled = false;
        sampleBufferOffset = 0;
        if (audioWorklet != null) {
            audioWorklet.suspend();
        }
    }

    void attachAudioWorklet(TeaVMJVicRunner jvicRunner) {
        this.jvicRunner = jvicRunner;
        if (audioWorklet == null) {
            audioWorklet = new TeaVMPSGAudioWorklet(sampleSharedQueue, jvicRunner);
        }
    }

    public void enableWriteSamples() {
        writeSamplesEnabled = true;
    }

    public void disableWriteSamples() {
        writeSamplesEnabled = false;
    }

    public boolean isWriteSamplesEnabled() {
        return writeSamplesEnabled;
    }

    public TeaVMSharedQueue getSampleSharedQueue() {
        return sampleSharedQueue;
    }

    SharedArrayBuffer getSharedArrayBuffer() {
        return sampleSharedQueue.getSharedArrayBuffer();
    }

    public int getCyclesPerSample() {
        return cyclesPerSample;
    }

    private void writeSample() {
        int sample = 0;

        for (int index = 0; index < 4; index++) {
            if ((mem[vicReg10 + index] & 0x80) > 0) {
                sample += (voiceShiftRegisters[index] & 0x01) << 11;
            }
        }

        sample = (((sample >> 2) * (mem[vicReg14] & 0x0F)) & 0x7FFF);
        sampleBuffer.set(sampleBufferOffset, (float)((sample - 16384.0f) / 16384.0f));
        sampleBufferOffset++;

        if (sampleBufferOffset == sampleBuffer.getLength()) {
            sampleSharedQueue.push(sampleBuffer);
            sampleBufferOffset = 0;
        }
    }

    private void clearSampleQueue() {
        if (sampleSharedQueue.isEmpty()) {
            return;
        }

        Float32Array data = Float32Array.create(1024);
        int itemsRead;
        do {
            itemsRead = sampleSharedQueue.pop(data);
        } while (itemsRead == 1024);

        int silentSampleCount = SAMPLE_LATENCY - (SAMPLE_RATE / 60);
        sampleSharedQueue.push(Float32Array.create(silentSampleCount));
    }
}