package emu.jvic.gwt;

import com.badlogic.gdx.utils.TimeUtils;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.typedarrays.shared.Float32Array;
import com.google.gwt.typedarrays.shared.TypedArrays;

import emu.jvic.sound.SoundGenerator;

/**
 * GWT/HTML5/Web implementation of the VIC chip sound. Uses the Web Audio API, 
 * specifically an AudioWorklet.
 */
public class GwtSoundGenerator extends SoundGenerator {

    // The Oric runs at 1 MHz.
    private static final int CLOCK_1MHZ = 1000000;
    private static final int SAMPLE_RATE = 22050;
    private static final int CYCLES_PER_SECOND = 1000000;
    
    // The number of cycles it takes to generate a single sample.
    public static final double CYCLES_PER_SAMPLE = ((float) CYCLES_PER_SECOND / (float) SAMPLE_RATE);

    // Number of samples to queue before being output to the audio hardware.
    public static final int SAMPLE_LATENCY = 3072;
    
    // Not entirely sure what these volume levels should be. With LEVEL_DIVISOR set
    // to 4, and volumes A, B, and C all at 15, then max sample is at 32760, which 
    // is just under the limit.
    private final static int LEVEL_DIVISOR = 4;
    private final static int[] VOLUME_LEVELS = { 
            0x0000 / LEVEL_DIVISOR, 0x0055 / LEVEL_DIVISOR, 0x0079 / LEVEL_DIVISOR,
            0x00AB / LEVEL_DIVISOR, 0x00F1 / LEVEL_DIVISOR, 0x0155 / LEVEL_DIVISOR, 0x01E3 / LEVEL_DIVISOR,
            0x02AA / LEVEL_DIVISOR, 0x03C5 / LEVEL_DIVISOR, 0x0555 / LEVEL_DIVISOR, 0x078B / LEVEL_DIVISOR,
            0x0AAB / LEVEL_DIVISOR, 0x0F16 / LEVEL_DIVISOR, 0x1555 / LEVEL_DIVISOR, 0x1E2B / LEVEL_DIVISOR,
            0x2AAA / LEVEL_DIVISOR };

    // Constants for index values into output, count, and period arrays.
    private static final int A = 0;
    private static final int B = 1;
    private static final int C = 2;
    private static final int NOISE = 3;
    private static final int ENVELOPE = 4;

    private int[] output; // A, B, C and Noise
    private int[] count; // A, B, C, Noise and Envelope counters
    private int[] period; // A, B, C, Noise and Envelope periods

    // Channel volumes. Envelope volume takes effect depending on volume mode bit.
    private int volumeA;
    private int volumeB;
    private int volumeC;
    private int volumeEnvelope;

    // Current mixer disable/enable settings.
    private int enable;
    private boolean disableToneA;
    private boolean disableToneB;
    private boolean disableToneC;
    private boolean disableAllNoise;

    private int outNoise;
    private int random = 1;

    private int countEnv;
    private int hold;
    private int alternate;
    private int attack;
    private int holding;

    private int updateStep;
    private final int step = 0x8000;

    private int busControl1 = 0;
    private int busDirection = 0;
    private int addressLatch = 0;
    private int[] registers;

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
     * The AY-3-8912 in the Oric gets its data from the 6522 VIA chip.
     */
    private Via via;

    /**
     * Constructor for GwtAYPSG (invoked by the UI thread).
     * 
     * @param gwtJOricRunner 
     */
    public GwtSoundGenerator(GwtJVicRunner gwtJOricRunner) {
        this((JavaScriptObject)null);
        initialiseAudioWorklet(gwtJOricRunner);
    }

    /**
     * Constructor for GwtAYPSG (invoked by the web worker).
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
     * Initialise the AY38912 PSG.
     * 
     * @param via      The 6522 VIA chip that the register data comes from.
     * @param keyboard The Keyboard that the AY-3-8912 Port A is connected to.
     * @param snapshot
     */
    public void init(Via via, Keyboard keyboard, Snapshot snapshot) {
        // Via and Keyboard are used with PORT A for scanning keyboard.
        this.via = via;
        keyboard.setPsg(this);

        updateStep = (int) (((long) step * 8L * (long) SAMPLE_RATE) / (long) CLOCK_1MHZ);
        output = new int[] { 0, 0, 0, 0xFF };
        count = new int[] { updateStep, updateStep, updateStep, 0x7fff, updateStep };
        period = new int[] { updateStep, updateStep, updateStep, updateStep, 0 };
        registers = new int[16];

        volumeA = volumeB = volumeC = volumeEnvelope = 0;
        disableToneA = disableToneB = disableToneC = disableAllNoise = false;
        countEnv = hold = alternate = attack = holding = 0;
        enable = 0;
        outNoise = 0;
        random = 1;
        busControl1 = 0;
        busDirection = 0;
        addressLatch = 0;

        cyclesToNextSample = CYCLES_PER_SAMPLE;
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
     * Emulates a single cycle of activity for the AY-3-8912. This involves
     * responding to the CA2 and CB2 lines coming in from the 6522 VIA chip
     */
    public void emulateCycle() {
        cycleCount++;
        
        // Bus Control 1 is connected to the VIA CA2 line in the Oric.
        busControl1 = via.getCa2();

        // Bus Direction is connected to the VIA CB2 line in the Oric.
        busDirection = via.getCb2();

        if (busDirection == 1) {
            if (busControl1 == 1) {
                // Address latch write is occurring.
                addressLatch = (via.getPortAPins() & 0x0f);

            } else {
                // Write register. Probably needs a "has it changed" check.
                writeRegister(addressLatch, via.getPortAPins());
            }
        } else {
            if (busControl1 == 1) {
                // Read occurred. Not sure how often this happens in the Oric. Log for now so
                // we'll find out.
                System.out.println("AY-3-8912: Read from " + via.getPortAPins());
            }
        }

        // If enough cycles have elapsed since the last sample, then output another.
        if (--cyclesToNextSample <= 0) {
            
            cyclesToNextSample += CYCLES_PER_SAMPLE;
            
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
     * Gets the current value stored in PORT A. The Keyboard instance will call this
     * method to get the selected columns when testing if key(s) are pressed.
     * 
     * @return The current value stored in PORT A (the AY-3-8912 only has one port).
     */
    public int getIOPortA() {
        return registers[14];
    }

    /**
     * Reads the value stored in an AY-3-8912 register.
     * 
     * @param address The address of the register to read from.
     * 
     * @return The value stored in the register.
     */
    public int readRegister(int address) {
        return registers[address];
    }

    /**
     * Writes a value to an AY-3-8912 register.
     * 
     * @param address The address of the register to write to.
     * @param value   The value to write to the register.
     */
    public void writeRegister(int address, int value) {
        registers[address] = value;

        switch (address) {

        case 0x00: // Fine tune A
        case 0x01: // Coarse tune A
        case 0x02: // Fine tune B
        case 0x03: // Coarse tune B
        case 0x04: // Fine tune C
        case 0x05: { // Coarse tune C
            address >>= 1;
            int val = (((registers[(address << 1) + 1] & 0x0f) << 8) | registers[address << 1]) * updateStep;
            int last = period[address];
            period[address] = val = ((val < 0x8000) ? 0x8000 : val);
            int newCount = count[address] - (val - last);
            count[address] = newCount < 1 ? 1 : newCount;
            break;
        }

        // Noise period.
        case 0x06: {
            int val = (value & 0x1f) * updateStep;
            val *= 2;
            int last = period[NOISE];
            period[NOISE] = val = val == 0 ? updateStep : val;
            int newCount = count[NOISE] - (val - last);
            count[NOISE] = newCount < 1 ? 1 : newCount;
            break;
        }

        // Voice enable (i.e Mixer)
        case 0x07:
            enable = value;
            disableToneA = (enable & 0x01) != 0;
            disableToneB = (enable & 0x02) != 0;
            disableToneC = (enable & 0x04) != 0;
            disableAllNoise = (enable & 0x38) == 0x38;
            break;

        // Channel A volume
        case 0x08:
            volumeA = (((value & 0x10) == 0) ? value & 0x0f : volumeEnvelope);
            break;

        // Channel B volume
        case 0x09:
            volumeB = (((value & 0x10) == 0) ? value & 0x0f : volumeEnvelope);
            break;

        // Channel C volume
        case 0x0A:
            volumeC = (((value & 0x10) == 0) ? value & 0x0f : volumeEnvelope);
            break;

        // Envelope Fine & Coarse tune
        case 0x0B:
        case 0x0C: {
            int val = (((registers[0x0C] << 8) | registers[0x0B]) * updateStep) << 1;
            int last = period[ENVELOPE];
            period[ENVELOPE] = val;
            int newCount = count[ENVELOPE] - (val - last);
            count[ENVELOPE] = newCount < 1 ? 1 : newCount;
            break;
        }

        // Envelope shape
        case 0x0D: {
            attack = (value & 0x04) == 0 ? 0 : 0x0f;
            if ((value & 0x08) == 0) {
                hold = 1;
                alternate = attack;
            } else {
                hold = value & 0x01;
                alternate = value & 0x02;
                if (hold != 0) {
                    attack = alternate;
                }
            }
            count[ENVELOPE] = period[ENVELOPE];
            countEnv = 0x0f;
            holding = 0;
            int vol = volumeEnvelope = attack ^ 0x0f;
            if ((registers[0x08] & 0x10) != 0) {
                volumeA = vol;
            }
            if ((registers[0x09] & 0x10) != 0) {
                volumeB = vol;
            }
            if ((registers[0x0A] & 0x10) != 0) {
                volumeC = vol;
            }
            break;
        }

        default:
            break;
        }
    }

    /**
     * Writes a single sample to the sample buffer. If the buffer is full after
     * writing the sample, then the whole buffer is written out to the
     * SourceDataLine.
     */
    public void writeSample() {
        if (disableToneA) {
            if (count[A] <= step) {
                count[A] += step;
            }
            output[A] = 1;
        }
        if (disableToneB) {
            if (count[B] <= step) {
                count[B] += step;
            }
            output[B] = 1;
        }
        if (disableToneC) {
            if (count[C] <= step) {
                count[C] += step;
            }
            output[C] = 1;
        }
        outNoise = output[NOISE] | enable;
        if (disableAllNoise) {
            if (count[NOISE] <= step) {
                count[NOISE] += step;
            }
        }

        int[] cnt = new int[3];
        int left = step;
        do {
            int add = count[NOISE] < left ? count[NOISE] : left;
            for (int channel = A; channel <= C; channel++) {
                int channelCount = count[channel];
                if ((outNoise & (0x08 << channel)) != 0) {
                    int val = output[channel] == 0 ? cnt[channel] : cnt[channel] + channelCount;
                    if ((channelCount -= add) <= 0) {
                        int channelPeriod = period[channel];
                        while (true) {
                            if ((channelCount += channelPeriod) > 0) {
                                if ((output[channel] ^= 0x01) != 0) {
                                    val += channelPeriod - channelCount;
                                }
                                break;
                            }
                            val += channelPeriod;
                            if ((channelCount += channelPeriod) > 0) {
                                if (output[channel] == 0) {
                                    val -= channelCount;
                                }
                                break;
                            }
                        }
                    } else if (output[channel] != 0) {
                        val -= channelCount;
                    }
                    cnt[channel] = val;
                } else {
                    if ((channelCount -= add) <= 0) {
                        int channelPeriod = period[channel];
                        while (true) {
                            if ((channelCount += channelPeriod) > 0) {
                                output[channel] ^= 0x01;
                                break;
                            }
                            if ((channelCount += channelPeriod) > 0) {
                                break;
                            }
                        }
                    }
                }
                count[channel] = channelCount;
            }

            if ((count[NOISE] -= add) <= 0) {
                int val = random + 1;
                if ((val & 0x02) != 0) {
                    outNoise = (output[NOISE] ^= 0xff) | enable;
                }
                random = (random & 0x01) == 0 ? random >> 1 : (random ^ 0x28000) >> 1;
                count[NOISE] += period[NOISE];
            }

            left -= add;
        } while (left > 0);

        if (holding == 0 && period[ENVELOPE] != 0) {
            if ((count[ENVELOPE] -= step) <= 0) {
                int ce = countEnv;
                int envelopePeriod = period[ENVELOPE];
                do {
                    ce--;
                } while ((count[ENVELOPE] += envelopePeriod) <= 0);

                if (ce < 0) {
                    if (hold != 0) {
                        if (alternate != 0) {
                            attack ^= 0x0f;
                        }
                        holding = 1;
                        ce = 0;
                    } else {
                        if (alternate != 0 && (ce & 0x10) != 0) {
                            attack ^= 0x0f;
                        }
                        ce &= 0x0f;
                    }
                }
                countEnv = ce;
                int vol = volumeEnvelope = ce ^ attack;
                if ((registers[0x08] & 0x10) != 0) {
                    volumeA = vol;
                }
                if ((registers[0x09] & 0x10) != 0) {
                    volumeB = vol;
                }
                if ((registers[0x0A] & 0x10) != 0) {
                    volumeC = vol;
                }
            }
        }

        int sample = (((((VOLUME_LEVELS[volumeA] * cnt[A]) >> 13) + 
                        ((VOLUME_LEVELS[volumeB] * cnt[B]) >> 13) + 
                        ((VOLUME_LEVELS[volumeC] * cnt[C]) >> 13)) & 0x7FFF));

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

    public SharedQueue getSampleSharedQueue() {
        return sampleSharedQueue;
    }
    
    JavaScriptObject getSharedArrayBuffer() {
        return sampleSharedQueue.getSharedArrayBuffer();
    }

    private void initialiseAudioWorklet(GwtJVicRunner gwtJOricRunner) {
        this.audioWorklet = new PSGAudioWorklet(sampleSharedQueue, gwtJOricRunner);
    }
    
    private final native void logToJSConsole(String message)/*-{
        console.log(message);
    }-*/;
}
