package emu.jvic.gwt;

import com.badlogic.gdx.audio.AudioDevice;
import com.badlogic.gdx.utils.TimeUtils;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.typedarrays.shared.Float32Array;
import com.google.gwt.typedarrays.shared.TypedArrays;

import emu.jvic.MachineType;
import emu.jvic.sound.SoundGenerator;

/**
 * GWT/HTML5/Web implementation of the VIC chip sound. Uses the Web Audio API, 
 * specifically an AudioWorklet.
 */
public class GwtSoundGenerator extends SoundGenerator {
    
    private static final int SAMPLE_RATE = 22050;

    // Number of samples to queue before being output to the audio hardware.
    public static final int SAMPLE_LATENCY = 3072;
    
    private static final int VIC_REG_10 = 0x900A;
    private static final int VIC_REG_14 = 0x900E;
    
    private int cyclesPerSample;
    private AudioDevice audioDevice;
    private boolean soundPaused;
    private int soundClockDividerCounter;
    private int[] voiceClockDividerTriggers;
    private int[] voiceCounters;
    private int[] voiceShiftRegisters;
    private int noiseLFSR = 0xFFFF;
    private int lastNoiseLFSR0 = 0x1;
    
    private Float32Array sampleBuffer;
    private int sampleBufferOffset = 0;
    private double cyclesToNextSample;
    private SharedQueue sampleSharedQueue;
    
    // TODO: Remove these after debugging timing issue.
    private long cycleCount;
    private long startTime;
    private long sampleCount;
    
    private boolean writeSamplesEnabled;
    
    private PSGAudioWorklet audioWorklet;

    /**
     * Constructor for GwtSoundGenerator (invoked by the UI thread).
     * 
     * @param gwtJVicRunner 
     */
    public GwtSoundGenerator(GwtJVicRunner gwtJVicRunner) {
        this((JavaScriptObject)null);
        initialiseAudioWorklet(gwtJVicRunner);
    }

    /**
     * Constructor for GwtSoundGenerator (invoked by the web worker).
     * 
     * @param audioBufferSAB SharedArrayBuffer for the audio ring buffer.
     */
    public GwtSoundGenerator(JavaScriptObject audioBufferSAB) {
        this.startTime = TimeUtils.millis();
        
        if (audioBufferSAB == null) {
            audioBufferSAB = SharedQueue.getStorageForCapacity(22050);
        }
        this.sampleSharedQueue = new SharedQueue(audioBufferSAB);

        // 1024 is about 46ms of sample data, and is 8 frames of data for the
        // audio worklet processor.
        this.sampleBuffer = TypedArrays.createFloat32Array(512);
        this.sampleBufferOffset = 0;
    }
    
    /**
     * Initialise the SoundGenerator.
     * 
     * @param machineType 
     */
    public void initSound(MachineType machineType) {
        cyclesPerSample = (machineType.getCyclesPerSecond() / SAMPLE_RATE);
        cyclesToNextSample = cyclesPerSample;

        voiceCounters = new int[4];
        voiceShiftRegisters = new int[4];
        voiceClockDividerTriggers = new int[] { 0xF, 0x7, 0x3, 0x1 };
    }

    /**
     * Turns on sample writing to the sample buffer.
     */
    public void enableWriteSamples() {
        logToJSConsole("Enabling writing of samples...");
        writeSamplesEnabled = true;
    }
    
    /**
     * Returns whether the sample writing is currently enabled.
     * 
     * @return true if the sample writing is currently enabled, otherwise false.
     */
    public boolean isWriteSamplesEnabled() {
        return writeSamplesEnabled;
    }
    
    /**
     * Turn off sample writing to the sample buffer.
     */
    public void disableWriteSamples() {
        writeSamplesEnabled = false;
    }
    
    /**
     * Emulates a single cycle of activity for the VIC chip sound.
     */
    public void emulateCycle() {
        cycleCount++;
        
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
            
            cyclesToNextSample += cyclesPerSample;
            
            // No point writing samples until we know that the AudioWorklet is ready.
            if (writeSamplesEnabled) {
                writeSample();
            }
        }
    }

    /**
     * Pauses the sound output. Invoked when the Machine is paused.
     */
    public void pauseSound() {
        if (audioWorklet != null) {
            audioWorklet.suspend();
        }
    }

    /**
     * Resumes the sound output. Invoked when the Machine is (re)created or unpaused.
     */
    public void resumeSound() {
        if (sampleSharedQueue != null) {
            if (!sampleSharedQueue.isEmpty()) {
                // Clear out the old data from when it was last playing.
                logToJSConsole("Clearing sample queue...");
                int totalCleared = 0;
                int itemsRead = 0;
                Float32Array data = TypedArrays.createFloat32Array(1024);
                do {
                    itemsRead = sampleSharedQueue.pop(data);
                    totalCleared += itemsRead;
                } while (itemsRead == 1024);
                logToJSConsole("Cleared " + totalCleared + " old samples.");
                
                // Now fill with silence, so that we do not slow down emulation rate.
                int silentSampleCount = GwtSoundGenerator.SAMPLE_LATENCY - (GwtSoundGenerator.SAMPLE_RATE / 60);
                sampleSharedQueue.push(TypedArrays.createFloat32Array(silentSampleCount));
            }
        }
        if (audioWorklet != null) {
            logToJSConsole("Resuming PSGAudioWorker...");
            audioWorklet.resume();
            if (audioWorklet.isReady()) {
                audioWorklet.notifyAudioReady();
            }
        }
    }
    
    /**
     * Returns true if sound is currently being produce; otherwise false.
     * 
     * @return
     */
    @Override
    public boolean isSoundOn() {
        logToJSConsole("Audio worklet running? : " + audioWorklet.isRunning());
        return audioWorklet.isRunning();
    }

    /**
     * Stops and closes the audio line.
     */
    public void dispose() {
        // TODO: Replace with web worker audio generating equivalent.
        // if (audioLine != null) {
        // audioLine.stop();
        // audioLine.close();
        // }
    }
    
    /**
     * Writes a single sample to the sample buffer. If the buffer is full after
     * writing the sample, then the whole buffer is written out to the
     * SourceDataLine.
     */
    public void writeSample() {
        int sample = 0;

        for (int i = 0; i < 4; i++) {
            if ((mem[VIC_REG_10 + i] & 0x80) > 0) {
                // Voice enabled. First bit of SR goes out.
                sample += ((voiceShiftRegisters[i] & 0x01) << 11);
            }
        }

        // Apply master volume.
        sample = (((sample >> 2) * (mem[VIC_REG_14] & 0x0F)) & 0x7FFF);
        
        // Conversion to -1.0 to 1.0, which is what the AudioWorkletProcessor needs.
        sampleBuffer.set(sampleBufferOffset, ((sample - 16384.0f) / 16384.0f));
        
        // Increment total sample count, so that we can keep in sync with cycle count.
        sampleCount++;
        
        // If the sample buffer is full, write it out to the shared queue.
        if ((sampleBufferOffset++) == sampleBuffer.length()) {
            sampleSharedQueue.push(sampleBuffer);
            sampleBufferOffset = 0;
            
            //float elapsedTimeInSecs = (TimeUtils.millis() - this.startTime) / 1000.0f;
            //float cyclesPerSecond = cycleCount / elapsedTimeInSecs;
            
            //this.frameCount += sampleBuffer.length();
            //logToJSConsole("GwtAYPSG - Sample rate = " + (frameCount / elapsedTimeInSecs) + 
            //        ", Cycle rate = " + cyclesPerSecond);
            
            //logToJSConsole("GwtAYPSG - elapsedTimeInSecs = " + elapsedTimeInSecs + 
            //        ", cycle rate = " + cyclesPerSecond + 
            //        ", audio time = " + sampleSharedQueue.getCurrentTime());
            
            //logToJSConsole("GwtAYPSG - volumeA: " + volumeA + 
            //        ", volumeB: " + volumeB + ", volumeC: " + volumeC + 
            //        ", cntA: " + cnt[A] + ", cntB: " + cnt[B] + 
            //        ", cntC: " + cnt[C] + ", sample: " + sample);
        }
    }

    public int getCyclesPerSample() {
        return cyclesPerSample;
    }
    
    public SharedQueue getSampleSharedQueue() {
        return sampleSharedQueue;
    }
    
    JavaScriptObject getSharedArrayBuffer() {
        return sampleSharedQueue.getSharedArrayBuffer();
    }

    private void initialiseAudioWorklet(GwtJVicRunner gwtJVicRunner) {
        this.audioWorklet = new PSGAudioWorklet(sampleSharedQueue, gwtJVicRunner);
    }
    
    private final native void logToJSConsole(String message)/*-{
        console.log(message);
    }-*/;
}
