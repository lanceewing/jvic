package emu.jvic.worker;

import java.util.Queue;
import java.util.concurrent.Callable;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.typedarrays.shared.ArrayBuffer;
import com.google.gwt.typedarrays.shared.TypedArrays;
import com.google.gwt.typedarrays.shared.Uint8Array;
import com.google.gwt.webworker.client.DedicatedWorkerEntryPoint;

import emu.jvic.Machine;
import emu.jvic.MachineType;
import emu.jvic.Program;
import emu.jvic.config.AppConfigItem;
import emu.jvic.cpu.Cpu6502;
import emu.jvic.gwt.GwtSoundGenerator;
import emu.jvic.gwt.GwtKeyboardMatrix;
import emu.jvic.gwt.GwtPixelData;
import emu.jvic.memory.RamType;

/**
 * Web worker that performs the actual emulation of the VIC 20 machine.
 */
public class JVicWebWorker extends DedicatedWorkerEntryPoint implements MessageHandler {

    private DedicatedWorkerGlobalScope scope;

    // The web worker has its own instance of each of these. It is not the same instance
    // as in the JVicRunner. Instead part of the data is either shared, or transferred
    // between the client and worker.
    private GwtKeyboardMatrix keyboardMatrix;
    private GwtPixelData pixelData;
    private GwtSoundGenerator soundGenerator;
    
    /**
     * The actual Machine that runs the program.
     */
    private Machine machine;
    
    /**
     * The number of nanoseconds per frame.
     */
    private int nanosPerFrame;
    
    /**
     * Whether or not the machine is paused.
     */
    private boolean paused;
    
    /**
     * Whether or not the machine is running in warp speed mode.
     */
    private boolean warpSpeed = false;
    
    /**
     * Callable<Queue<char[]>> that, if not null, should be run when BASIC is ready.
     */
    private Callable<Queue<char[]>> autoLoadProgram;
    
    /**
     * The current queue of auto run commands to execute.
     */
    private Queue<char[]> autoRunCmdQueue;
    
    // Used by the old implementations.
    private double lastTime = -1;
    private long deltaTime;
    
    // Used by the current implementation.
    private double startTime = 0;
    private long cycleCount;
    
    @Override
    public void onMessage(MessageEvent event) {
        JavaScriptObject eventObject = event.getDataAsObject();
        
        switch (getEventType(eventObject)) {
            case "Initialise":
                JavaScriptObject keyMatrixSAB = getNestedObject(eventObject, "keyMatrixSAB");
                JavaScriptObject pixelDataSAB = getNestedObject(eventObject, "pixelDataSAB");
                JavaScriptObject audioDataSAB = getNestedObject(eventObject, "audioDataSAB");
                keyboardMatrix = new GwtKeyboardMatrix(keyMatrixSAB);
                pixelData = new GwtPixelData(pixelDataSAB);
                soundGenerator = new GwtSoundGenerator(audioDataSAB);
                break;
                
            case "Start":
                AppConfigItem appConfigItem = buildAppConfigItemFromEventObject(eventObject); 
                ArrayBuffer programArrayBuffer = getArrayBuffer(eventObject);
                byte[] basicRom = extractBytesFromArrayBuffer(programArrayBuffer, 0, 8192);
                byte[] kernalRom = extractBytesFromArrayBuffer(programArrayBuffer, 8192, 8192);
                byte[] charRom = extractBytesFromArrayBuffer(programArrayBuffer, 16384, 4096);
                byte[] dos1541Rom = extractBytesFromArrayBuffer(programArrayBuffer, 20480, 16384);
                Program program = extractProgram(programArrayBuffer);
                if (program != null) {
                    program.setAppConfigItem(appConfigItem);
                }
                MachineType machineType = MachineType.valueOf(appConfigItem.getMachineType());
                RamType ramType = RamType.valueOf(appConfigItem.getRam());
                nanosPerFrame = (1000000000 / machineType.getFramesPerSecond());
                machine = new Machine(soundGenerator, keyboardMatrix, pixelData);
                autoLoadProgram = machine.init(
                        basicRom, kernalRom, charRom, dos1541Rom, 
                        program, machineType, ramType);
                // TODO: lastTime = TimeUtils.nanoTime() - nanosPerFrame;
                performAnimationFrame(0);
                break;
                
            case "AudioWorkletReady":
                logToJSConsole("Enabling PSG sample writing...");
                soundGenerator.enableWriteSamples();
                break;
                
            case "SoundOff":
                logToJSConsole("Disabling PSG sample writing...");
                soundGenerator.disableWriteSamples();
                break;
                
            case "Pause":
                paused = true;
                break;
                
            case "Unpause":
                paused = false;
                break;
                
            case "WarpSpeedOn":
                logToJSConsole("Warp speed ON");
                warpSpeed = true;
                break;
                
            case "WarpSpeedOff":
                logToJSConsole("Warp speed OFF");
                warpSpeed = false;
                break;
                
            case "SendNMI":
                if (machine != null) {
                    machine.getCpu().setInterrupt(Cpu6502.S_NMI);
                }
                break;
                
            default:
                // Unknown message. Ignore.
        }
    }
    
    private byte[] extractBytesFromArrayBuffer(ArrayBuffer programDataBuffer,
            int offset, int length) {
        Uint8Array array = TypedArrays.createUint8Array(programDataBuffer);
        byte[] data = new byte[length];
        for (int index=offset, i=0; i<length; index++, i++) {
            data[i] = (byte)(array.get(index) & 0xFF);
        }
        return data;
    }
    
    private Program extractProgram(ArrayBuffer programDataBuffer) {
        Program program = null;
        int programOffset = 8192 + 16384 + 4096 + 8192;   // Allow for ROMs (basic, dos, char, kernal)
        int totalDataLength = programDataBuffer.byteLength();
        if (totalDataLength > programOffset) {
            int programLength = (totalDataLength - programOffset);
            byte[] programData = extractBytesFromArrayBuffer(programDataBuffer,
                    programOffset, programLength);
            program = new Program();
            program.setProgramData(programData);
        }
        return program;
    }

    private AppConfigItem buildAppConfigItemFromEventObject(JavaScriptObject eventObject) {
        AppConfigItem appConfigItem = new AppConfigItem();
        appConfigItem.setName(getNestedString(eventObject, "name"));
        appConfigItem.setFilePath(getNestedString(eventObject, "filePath"));
        appConfigItem.setFileType(getNestedString(eventObject, "fileType"));
        appConfigItem.setMachineType(getNestedString(eventObject, "machineType"));
        appConfigItem.setRam(getNestedString(eventObject, "ramType"));
        appConfigItem.setAutoRunCommand(getNestedString(eventObject, "autoRunCommand"));
        appConfigItem.setLoadAddress(getNestedString(eventObject, "loadAddress"));
        return appConfigItem;
    }
    
    /**
     * This method is the main emulator loop that is run for each animation frame. The
     * web worker uses requestAnimationFrame to request that this method is called on 
     * each frame. As this is GWT, it does so via a native method below. This particular
     * implementation uses an approach where it only emulates as many cycles required to
     * fill the sample buffer up to a certain number of samples, e.g. 3072. This value
     * will be tweaked during testing on different browsers and devices to choose the 
     * most appropriate. It needs to balance protecting against delays in the web worker
     * generating samples, perhaps due to an animation frame being skipped, and not 
     * introducing too much delay in the sound that is heard. A value of 3072 would be 
     * a delay of 3072/22050*1000=139ms. That fraction of a second may not be noticeable
     * but going much higher would become a perceivable latency/lag. In an ideal world,
     * the web worker would write out 128 samples and the Web Audio thread would read
     * that and output it immediately, but in reality both sides do sometimes pause
     * slightly, and so we need a "buffer" of already prepared samples for the audio
     * thread, thus the 3072 sample figure.
     * 
     * @param timestamp
     */
    public void performAnimationFrame(double timestamp) {
        // Audio currentTime is in seconds, so multiply by 1000 to get ms. This is 
        // not used but is instead for debugging, if required.
        //double currentAudioTime = psg.getSampleSharedQueue().getCurrentTime() * 1000;
        
        long expectedCycleCount = 0;
        
        if (paused) {
            // While the machine is paused, we keep reseting the startTime and cycleCount.
            cycleCount = 0;
            startTime = timestamp;
            
        } else {
            if (soundGenerator.isWriteSamplesEnabled()) {
                cycleCount = 0;
                
                // If the AudioWorklet is running, then adjust expected cycle count
                // to a value that would leave the available samples in the queue 
                // at a roughly fixed number. This is to avoid under or over generating
                // samples, being always a given number of samples ahead in the buffer.
                int currentBufferSize = soundGenerator.getSampleSharedQueue().availableRead();
                int samplesToGenerate = (currentBufferSize >= GwtSoundGenerator.SAMPLE_LATENCY? 0 : GwtSoundGenerator.SAMPLE_LATENCY - currentBufferSize);
                expectedCycleCount = (int)(samplesToGenerate * soundGenerator.getCyclesPerSample());
                
                // While the emulation cycle rate is throttling by the audio thread 
                // output rate, we keep resetting the startTime, in case sound is turned
                // off for the next frame.
                startTime = timestamp;
                
            } else if (!warpSpeed) {
                // If we are not writing samples, i.e. sound is turned off, then rate
                // of emulating cycles is controlled by the animation frame timestamp.
                double elapsedTime = (timestamp - startTime);
                expectedCycleCount = Math.round(elapsedTime * 1000);
            } else {
                // Warp speed, so we run it for a lot longer.
                expectedCycleCount = 1000000;
                cycleCount = 0;
                startTime = timestamp;
            }
            
            // Check for BASIC program auto-load
            if (autoLoadProgram != null) {
                int[] mem = machine.getMemory().getMemoryArray();
                
                // We need to wait for BASIC to boot up before loading the program.
                // The simplest way to wait for BASIC to be ready is to check for
                // the starting cursor position.
                
                if (mem[0xD1] == 110) {
                    // Now that the BASIC cursor is in the start position, let's load the
                    // program data in to memory.
                    try {
                        autoRunCmdQueue = autoLoadProgram.call();
                    } catch (Exception e) {}
                    
                    // If there is an auto run command, then run it.
                    runNextBasicCommand(autoRunCmdQueue, mem);
                    
                    if (autoRunCmdQueue.isEmpty()) {
                        autoLoadProgram = null;
                    }
                }
                
                // If it is a DISK, then we run two commands, the second being the RUN.
                if (mem[0xD1] == 220) {
                    runNextBasicCommand(autoRunCmdQueue, mem);
                    autoLoadProgram = null;
                }
            }
            
            // Emulate the required number of cycles.
            do {
                machine.emulateCycle();
                cycleCount++;
            } while (cycleCount <= expectedCycleCount);
        }

        requestNextAnimationFrame();
    }
    
    private void runNextBasicCommand(Queue<char[]> cmdQueue, int[] mem) {
        if ((cmdQueue != null) && (!cmdQueue.isEmpty())) {
            // Keyboard buffer, 10 bytes (631 - 640)
            char[] cmdChars = cmdQueue.remove();
            int cmdCharPos = 0;
            for (; cmdCharPos < cmdChars.length; cmdCharPos++) {
                mem[631 + cmdCharPos] = cmdChars[cmdCharPos];
            }
            mem[631 + cmdCharPos] = 0x0D;
            
            // Num of chars in keyboard buffer.
            mem[198] = cmdCharPos + 1;
        }
    }
    
    public native void exportPerformAnimationFrame() /*-{
        var that = this;
        $self.performAnimationFrame = $entry(function(timestamp) {
            that.@emu.jvic.worker.JVicWebWorker::performAnimationFrame(D)(timestamp);
        });
    }-*/;

    private native void requestNextAnimationFrame()/*-{
        $self.requestAnimationFrame($self.performAnimationFrame);
    }-*/;

    private native double getPerformanceNowTimestamp()/*-{
        return performance.now();
    }-*/;
    
    private native String getEventType(JavaScriptObject obj)/*-{
        return obj.name;
    }-*/;

    private native JavaScriptObject getNestedObject(JavaScriptObject obj, String fieldName)/*-{
        return obj.object[fieldName];
    }-*/;

    private native String getNestedString(JavaScriptObject obj, String fieldName)/*-{
        return obj.object[fieldName];
    }-*/;

    private native ArrayBuffer getArrayBuffer(JavaScriptObject obj)/*-{
        return obj.buffer;
    }-*/;

    protected final void postObject(String name, JavaScriptObject object) {
        getGlobalScope().postObject(name, object);
    }

    protected final void postTransferableObject(String name, JavaScriptObject object) {
        getGlobalScope().postTransferableObject(name, object);
    }

    @Override
    protected DedicatedWorkerGlobalScope getGlobalScope() {
        return scope;
    }

    protected final void setOnMessage(MessageHandler messageHandler) {
        getGlobalScope().setOnMessage(messageHandler);
    }

    @Override
    public void onWorkerLoad() {
        exportPerformAnimationFrame();
    
        this.scope = DedicatedWorkerGlobalScope.get();            
        this.setOnMessage(this);
    }

    private final native void logToJSConsole(String message)/*-{
        console.log(message);
    }-*/;
}
