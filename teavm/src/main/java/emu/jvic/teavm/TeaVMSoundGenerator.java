package emu.jvic.teavm;

import emu.jvic.MachineType;
import emu.jvic.sound.SoundGenerator;
import org.teavm.jso.typedarrays.Float32Array;
import org.teavm.jso.typedarrays.SharedArrayBuffer;

public class TeaVMSoundGenerator extends SoundGenerator {

    public static final int SAMPLE_RATE = 22050;
    public static final int SAMPLE_LATENCY = 3072;

    // Small baseline level that remains when voices are effectively silent.
    // Rapid volume writes can modulate this into audible 4-bit digi output.
    private static final int VOLUME_DAC_BIAS = 192;
    private static final float HIGH_PASS_CUTOFF_HZ = 120.0f;

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
    private double accumulatedSample;
    private int accumulatedCycles;
    private float highPassAlpha;
    private float highPassLastInput;
    private float highPassLastOutput;
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

        float dt = 1.0f / SAMPLE_RATE;
        float rc = (float)(1.0 / (2.0 * Math.PI * HIGH_PASS_CUTOFF_HZ));
        highPassAlpha = rc / (rc + dt);
        resetSampleAccumulator();
        highPassLastInput = 0.0f;
        highPassLastOutput = 0.0f;

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

        accumulatedSample += getCurrentMixedOutput();
        accumulatedCycles++;

        if (--cyclesToNextSample <= 0) {
            cyclesToNextSample += cyclesPerSample;
            if (writeSamplesEnabled) {
                writeSample();
            } else {
                resetSampleAccumulator();
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
        float sample = 0.0f;

        if (accumulatedCycles > 0) {
            sample = (float)(accumulatedSample / accumulatedCycles);
        }

        resetSampleAccumulator();

        // Model the output coupling capacitor with a one-pole high-pass filter,
        // so fast volume-register changes become audible digi output.
        float filtered = applyHighPass(sample);
        float normalized = filtered / 16384.0f;
        if (normalized > 1.0f) {
            normalized = 1.0f;
        } else if (normalized < -1.0f) {
            normalized = -1.0f;
        }

        sampleBuffer.set(sampleBufferOffset, normalized);
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

    private float getCurrentMixedOutput() {
        int mixedVoices = 0;

        for (int index = 0; index < 4; index++) {
            if ((mem[vicReg10 + index] & 0x80) > 0) {
                // Voice enabled. First bit of SR goes out.
                mixedVoices += (voiceShiftRegisters[index] & 0x01) << 11;
            }
        }

        int masterVolume = (mem[vicReg14] & 0x0F);
        int sample = ((mixedVoices >> 2) + VOLUME_DAC_BIAS) * masterVolume;
        return Math.min(sample, 0x7FFF);
    }

    private float applyHighPass(float input) {
        float output = highPassAlpha * (highPassLastOutput + input - highPassLastInput);
        highPassLastInput = input;
        highPassLastOutput = output;
        return output;
    }

    private void resetSampleAccumulator() {
        accumulatedSample = 0.0;
        accumulatedCycles = 0;
    }
}