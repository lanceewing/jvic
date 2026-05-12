package emu.jvic.teavm;

import java.util.Queue;
import java.util.concurrent.Callable;

import org.teavm.jso.JSObject;
import org.teavm.jso.typedarrays.ArrayBuffer;
import org.teavm.jso.typedarrays.SharedArrayBuffer;
import org.teavm.jso.typedarrays.Uint8Array;

import emu.jvic.Machine;
import emu.jvic.MachineType;
import emu.jvic.Program;
import emu.jvic.config.AppConfigItem;
import emu.jvic.cpu.Cpu6502;
import emu.jvic.memory.RamType;

public final class TeaVMJVicWebWorker {

    private static final double PERFORMANCE_STATS_INTERVAL_MS = 500.0;

    private TeaVMKeyboardMatrix keyboardMatrix;
    private TeaVMPixelData pixelData;
    private TeaVMSoundGenerator soundGenerator;
    private TeaVMFrameCounter frameCounter;
    private Machine machine;
    private boolean paused;
    private boolean warpSpeed;
    private Callable<Queue<char[]>> autoLoadProgram;
    private Queue<char[]> autoRunCmdQueue;
    private double startTime;
    private long cycleCount;
    private double performanceWindowStartTime = -1;
    private long performanceWindowCycles;
    private double performanceWindowWorkMillis;
    private double performanceWindowEmulatedMillis;
    private long performanceWindowBatchCount;
    private int lastAutoLoadCursorState = -1;
    private boolean autoLoadStateLogged;

    public static void main(String[] args) {
        new TeaVMJVicWebWorker().onWorkerLoad();
    }

    private void onWorkerLoad() {
        TeaVMWorkerGlobalScope.setOnMessage(this::onMessage);
    }

    private void onMessage(JSObject eventObject) {
        switch (TeaVMWorkerInterop.getEventType(eventObject)) {
            case "Initialise":
                SharedArrayBuffer keyMatrixSAB = (SharedArrayBuffer)TeaVMWorkerInterop.getNestedObject(eventObject, "keyMatrixSAB");
                SharedArrayBuffer pixelDataSAB = (SharedArrayBuffer)TeaVMWorkerInterop.getNestedObject(eventObject, "pixelDataSAB");
                SharedArrayBuffer audioDataSAB = (SharedArrayBuffer)TeaVMWorkerInterop.getNestedObject(eventObject, "audioDataSAB");
                SharedArrayBuffer frameCounterSAB = (SharedArrayBuffer)TeaVMWorkerInterop.getNestedObject(eventObject, "frameCounterSAB");
                keyboardMatrix = new TeaVMKeyboardMatrix(keyMatrixSAB);
                pixelData = new TeaVMPixelData(pixelDataSAB);
                soundGenerator = new TeaVMSoundGenerator(audioDataSAB);
                frameCounter = new TeaVMFrameCounter(frameCounterSAB);
                break;

            case "Start":
                startMachine(eventObject);
                break;

            case "AudioWorkletReady":
                TeaVMWorkerGlobalScope.logToJSConsole("Enabling PSG sample writing...");
                soundGenerator.enableWriteSamples();
                break;

            case "SoundOff":
                TeaVMWorkerGlobalScope.logToJSConsole("Disabling PSG sample writing...");
                soundGenerator.disableWriteSamples();
                break;

            case "Pause":
                paused = true;
                break;

            case "Unpause":
                paused = false;
                break;

            case "WarpSpeedOn":
                warpSpeed = true;
                break;

            case "WarpSpeedOff":
                warpSpeed = false;
                break;

            case "SendNMI":
                if (machine != null) {
                    machine.getCpu().setInterrupt(Cpu6502.S_NMI);
                }
                break;

            default:
                break;
        }
    }

    private void startMachine(JSObject eventObject) {
        AppConfigItem appConfigItem = buildAppConfigItemFromEventObject(eventObject);
        ArrayBuffer programArrayBuffer = TeaVMWorkerInterop.getArrayBuffer(eventObject);
        byte[] basicRom = extractBytesFromArrayBuffer(programArrayBuffer, 0, 8192);
        byte[] kernalRom = extractBytesFromArrayBuffer(programArrayBuffer, 8192, 8192);
        byte[] charRom = extractBytesFromArrayBuffer(programArrayBuffer, 16384, 4096);
        byte[] dos1541Rom = extractBytesFromArrayBuffer(programArrayBuffer, 20480, 16384);
        Program program = extractProgram(programArrayBuffer);
        if (program != null) {
            program.setAppConfigItem(appConfigItem);
        }
        logProgramStart(appConfigItem, program);
        MachineType machineType = MachineType.valueOf(appConfigItem.getMachineType());
        RamType ramType = RamType.valueOf(appConfigItem.getRam());
        machine = new Machine(soundGenerator, keyboardMatrix, pixelData);
        autoLoadProgram = machine.init(basicRom, kernalRom, charRom, dos1541Rom, program, machineType, ramType, appConfigItem.getPalette());
        TeaVMWorkerGlobalScope.logToJSConsole("TeaVM worker: machine.init complete, autoLoadProgram="
                + (autoLoadProgram != null ? "present" : "null"));
        paused = false;
        cycleCount = 0;
        startTime = 0;
        if (frameCounter != null) {
            frameCounter.reset();
        }
        autoRunCmdQueue = null;
        lastAutoLoadCursorState = -1;
        autoLoadStateLogged = false;
        resetPerformanceStatsWindow();
        TeaVMWorkerGlobalScope.requestAnimationFrame(this::performAnimationFrame);
    }

    private byte[] extractBytesFromArrayBuffer(ArrayBuffer programDataBuffer, int offset, int length) {
        Uint8Array array = Uint8Array.create(programDataBuffer);
        byte[] data = new byte[length];
        for (int index = offset, dataIndex = 0; dataIndex < length; index++, dataIndex++) {
            data[dataIndex] = (byte)(array.get(index) & 0xFF);
        }
        return data;
    }

    private Program extractProgram(ArrayBuffer programDataBuffer) {
        int programOffset = 8192 + 16384 + 4096 + 8192;
        int totalDataLength = programDataBuffer.getByteLength();
        if (totalDataLength <= programOffset) {
            return null;
        }

        Program program = new Program();
        program.setProgramData(extractBytesFromArrayBuffer(programDataBuffer, programOffset,
                totalDataLength - programOffset));
        return program;
    }

    private AppConfigItem buildAppConfigItemFromEventObject(JSObject eventObject) {
        AppConfigItem appConfigItem = new AppConfigItem();
        appConfigItem.setName(TeaVMWorkerInterop.getNestedString(eventObject, "name"));
        appConfigItem.setFilePath(TeaVMWorkerInterop.getNestedString(eventObject, "filePath"));
        appConfigItem.setFileType(TeaVMWorkerInterop.getNestedString(eventObject, "fileType"));
        appConfigItem.setMachineType(TeaVMWorkerInterop.getNestedString(eventObject, "machineType"));
        appConfigItem.setRam(TeaVMWorkerInterop.getNestedString(eventObject, "ramType"));
        appConfigItem.setPalette(TeaVMWorkerInterop.getNestedString(eventObject, "palette"));
        appConfigItem.setAutoRunCommand(normalizeBlankToNull(
                TeaVMWorkerInterop.getNestedString(eventObject, "autoRunCommand")));
        appConfigItem.setLoadAddress(normalizeBlankToNull(
                TeaVMWorkerInterop.getNestedString(eventObject, "loadAddress")));
        return appConfigItem;
    }

    private String normalizeBlankToNull(String value) {
        return (value == null || value.trim().isEmpty()) ? null : value;
    }

    private void performAnimationFrame(double timestamp) {
        long expectedCycleCount = 0;

        if (paused) {
            cycleCount = 0;
            startTime = timestamp;
        } else {
            if (soundGenerator.isWriteSamplesEnabled()) {
                cycleCount = 0;
                int currentBufferSize = soundGenerator.getSampleSharedQueue().availableRead();
                int samplesToGenerate = (currentBufferSize >= TeaVMSoundGenerator.SAMPLE_LATENCY)
                        ? 0
                        : TeaVMSoundGenerator.SAMPLE_LATENCY - currentBufferSize;
                expectedCycleCount = (long)samplesToGenerate * soundGenerator.getCyclesPerSample();
                startTime = timestamp;
            } else if (!warpSpeed) {
                double elapsedTime = timestamp - startTime;
                expectedCycleCount = Math.round(elapsedTime * 1000);
            } else {
                expectedCycleCount = 1_000_000;
                cycleCount = 0;
                startTime = timestamp;
            }

            handleAutoLoad();

            long batchStartCycleCount = cycleCount;
            double batchStartTime = TeaVMWorkerGlobalScope.getPerformanceNowTimestamp();
            do {
                if (machine.emulateCycle() && (frameCounter != null)) {
                    frameCounter.increment();
                }
                cycleCount++;
            } while (cycleCount <= expectedCycleCount);

            double batchEndTime = TeaVMWorkerGlobalScope.getPerformanceNowTimestamp();
            int audioQueueSamples = soundGenerator.isWriteSamplesEnabled()
                    ? soundGenerator.getSampleSharedQueue().availableRead()
                    : -1;
            recordPerformanceStats(timestamp, cycleCount - batchStartCycleCount,
                    batchEndTime - batchStartTime, audioQueueSamples);
        }

        TeaVMWorkerGlobalScope.requestAnimationFrame(this::performAnimationFrame);
    }

    private void handleAutoLoad() {
        if ((machine == null)
                || ((autoLoadProgram == null) && ((autoRunCmdQueue == null) || autoRunCmdQueue.isEmpty()))) {
            return;
        }

        int[] mem = machine.getMemory().getMemoryArray();
        int cursorState = mem[0xD1];
        int previousCursorState = lastAutoLoadCursorState;
        if ((cursorState != previousCursorState)
                && ((cursorState == 110) || (cursorState == 220) || !autoLoadStateLogged)) {
            TeaVMWorkerGlobalScope.logToJSConsole("TeaVM worker: auto-load cursor state=" + cursorState
                    + ", queue=" + describeCommandQueue(autoRunCmdQueue)
                    + ", autoLoadProgram=" + (autoLoadProgram != null ? "present" : "null"));
            autoLoadStateLogged = true;
        }
        lastAutoLoadCursorState = cursorState;

        if ((cursorState == 110) && (previousCursorState != 110)) {
            try {
                if (autoLoadProgram != null) {
                    autoRunCmdQueue = autoLoadProgram.call();
                    autoLoadProgram = null;
                    TeaVMWorkerGlobalScope.logToJSConsole("TeaVM worker: auto-load queue created: "
                            + describeCommandQueue(autoRunCmdQueue));
                }
            } catch (Exception e) {
                autoRunCmdQueue = null;
                autoLoadProgram = null;
                TeaVMWorkerGlobalScope.logToJSConsole("TeaVM worker: autoLoadProgram.call() failed: " + e.getMessage());
            }
            runNextBasicCommand(autoRunCmdQueue, mem);
        }

        if ((cursorState == 220) && (previousCursorState != 220)
                && (autoRunCmdQueue != null) && !autoRunCmdQueue.isEmpty()) {
            TeaVMWorkerGlobalScope.logToJSConsole("TeaVM worker: issuing follow-up BASIC command: "
                    + describeCommandQueue(autoRunCmdQueue));
            runNextBasicCommand(autoRunCmdQueue, mem);
        }
    }

    private void recordPerformanceStats(double timestamp, long cyclesThisBatch,
            double workMillis, int audioQueueSamples) {
        if ((machine == null) || (cyclesThisBatch <= 0)) {
            return;
        }

        if (performanceWindowStartTime < 0) {
            performanceWindowStartTime = timestamp;
        }

        double emulatedMillis = (cyclesThisBatch * 1000.0) / machine.getMachineType().getCyclesPerSecond();

        performanceWindowCycles += cyclesThisBatch;
        performanceWindowWorkMillis += workMillis;
        performanceWindowEmulatedMillis += emulatedMillis;
        performanceWindowBatchCount++;

        if ((timestamp - performanceWindowStartTime) >= PERFORMANCE_STATS_INTERVAL_MS) {
            double avgUnusedNanosPerCycle = ((performanceWindowEmulatedMillis - performanceWindowWorkMillis)
                    * 1000000.0) / performanceWindowCycles;
            double headroomFactor = (performanceWindowWorkMillis > 0)
                    ? performanceWindowEmulatedMillis / performanceWindowWorkMillis
                    : 0;
            double busyPercent = (performanceWindowEmulatedMillis > 0)
                    ? (performanceWindowWorkMillis * 100.0) / performanceWindowEmulatedMillis
                    : 0;
            double avgBatchWorkMillis = performanceWindowWorkMillis / performanceWindowBatchCount;
            double avgBatchCycles = ((double)performanceWindowCycles) / performanceWindowBatchCount;
            double audioQueueMillis = (audioQueueSamples >= 0)
                    ? (audioQueueSamples * 1000.0) / TeaVMSoundGenerator.SAMPLE_RATE
                    : -1;

            TeaVMWorkerGlobalScope.postObject("PerformanceStats",
                    TeaVMWorkerInterop.createPerformanceStatsObject(avgUnusedNanosPerCycle,
                            headroomFactor, busyPercent, avgBatchWorkMillis,
                            avgBatchCycles, audioQueueSamples, audioQueueMillis));

            resetPerformanceStatsWindow();
            performanceWindowStartTime = timestamp;
        }
    }

    private void resetPerformanceStatsWindow() {
        performanceWindowStartTime = -1;
        performanceWindowCycles = 0;
        performanceWindowWorkMillis = 0;
        performanceWindowEmulatedMillis = 0;
        performanceWindowBatchCount = 0;
    }

    private void runNextBasicCommand(Queue<char[]> cmdQueue, int[] mem) {
        if ((cmdQueue == null) || cmdQueue.isEmpty()) {
            TeaVMWorkerGlobalScope.logToJSConsole("TeaVM worker: runNextBasicCommand skipped, queue empty");
            return;
        }

        char[] cmdChars = cmdQueue.remove();
        TeaVMWorkerGlobalScope.logToJSConsole("TeaVM worker: injecting BASIC command '"
                + new String(cmdChars) + "'");
        int cmdCharPos = 0;
        for (; cmdCharPos < cmdChars.length; cmdCharPos++) {
            mem[631 + cmdCharPos] = cmdChars[cmdCharPos];
        }
        mem[631 + cmdCharPos] = 0x0D;
        mem[198] = cmdCharPos + 1;
    }

    private void logProgramStart(AppConfigItem appConfigItem, Program program) {
        StringBuilder message = new StringBuilder();
        message.append("TeaVM worker: start name=")
                .append(appConfigItem.getName())
                .append(", type=")
                .append(appConfigItem.getFileType())
                .append(", path=")
                .append(appConfigItem.getFilePath())
                .append(", autorun=")
                .append(appConfigItem.getAutoRunCommand())
                .append(", loadAddress=")
                .append(appConfigItem.getLoadAddress());

        if ((program != null) && (program.getProgramData() != null)) {
            byte[] programData = program.getProgramData();
            message.append(", bytes=")
                    .append(programData.length)
                    .append(", first16=")
                    .append(formatBytes(programData, 16));
        } else {
            message.append(", program=null");
        }

        TeaVMWorkerGlobalScope.logToJSConsole(message.toString());
    }

    private String describeCommandQueue(Queue<char[]> cmdQueue) {
        if (cmdQueue == null) {
            return "null";
        }
        if (cmdQueue.isEmpty()) {
            return "[]";
        }

        StringBuilder message = new StringBuilder("[");
        boolean first = true;
        for (char[] cmd : cmdQueue) {
            if (!first) {
                message.append(", ");
            }
            message.append('"').append(new String(cmd)).append('"');
            first = false;
        }
        message.append(']');
        return message.toString();
    }

    private String formatBytes(byte[] data, int maxLength) {
        int length = Math.min(data.length, maxLength);
        StringBuilder message = new StringBuilder();
        for (int i = 0; i < length; i++) {
            if (i > 0) {
                message.append(' ');
            }
            int value = data[i] & 0xFF;
            if (value < 0x10) {
                message.append('0');
            }
            message.append(Integer.toHexString(value).toUpperCase());
        }
        return message.toString();
    }
}