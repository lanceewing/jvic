package emu.jvic.teavm;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Pixmap;
import org.teavm.jso.JSObject;
import org.teavm.jso.typedarrays.ArrayBuffer;
import org.teavm.jso.typedarrays.SharedArrayBuffer;
import org.teavm.jso.typedarrays.Uint8Array;

import emu.jvic.JVicRunner;
import emu.jvic.MachineType;
import emu.jvic.Program;
import emu.jvic.config.AppConfigItem;

public class TeaVMJVicRunner extends JVicRunner {

    private static final int UNUSED_NS_PER_CYCLE_ROLLING_AVERAGE_WINDOW = 20;
    private static final int AUDIO_QUEUE_MILLIS_ROLLING_AVERAGE_WINDOW = 20;

    private double currentUnusedNanosPerCycle;
    private double minUnusedNanosPerCycle;
    private double maxUnusedNanosPerCycle;
    private double meanUnusedNanosPerCycle;
    private long unusedNanosPerCycleSampleCount;
    private final double[] rollingUnusedNanosPerCycleSamples =
            new double[UNUSED_NS_PER_CYCLE_ROLLING_AVERAGE_WINDOW];
    private double rollingUnusedNanosPerCycleSum;
    private int rollingUnusedNanosPerCycleIndex;
    private int rollingUnusedNanosPerCycleCount;
    private double headroomFactor;
    private double busyPercent;
    private double avgBatchWorkMillis;
    private double avgBatchCycles;
    private int audioQueueSamples = -1;
    private double audioQueueMillis = -1;
    private final double[] rollingAudioQueueMillisSamples =
            new double[AUDIO_QUEUE_MILLIS_ROLLING_AVERAGE_WINDOW];
    private double rollingAudioQueueMillisSum;
    private int rollingAudioQueueMillisIndex;
    private int rollingAudioQueueMillisCount;
    private double audioUnderrunCount;
    private double audioUnderrunSampleCount;
    private boolean performanceStatsAvailable;

    private JSObject worker;
    private boolean stopped;
    private final TeaVMFrameCounter frameCounter;
    private int lastConsumedFrameCount;

    public TeaVMJVicRunner() {
        super(new TeaVMKeyboardMatrix(), new TeaVMPixelData(), new TeaVMSoundGenerator());
        frameCounter = new TeaVMFrameCounter();
        ((TeaVMSoundGenerator)soundGenerator).attachAudioWorklet(this);
        TeaVMBrowser.registerPopStateListener(this::onPopState);
    }

    @Override
    public void start(AppConfigItem appConfigItem) {
        if ((TeaVMBrowser.getQueryParameter("url") == null)
                && (!"Adhoc VIC Program".equals(appConfigItem.getName()))
                && (TeaVMBrowser.getHash().indexOf('=') < 0)
                && (TeaVMBrowser.getHash().indexOf('/') < 0)) {
            TeaVMBrowser.pushState(buildProgramUrl(appConfigItem));
        }

        TeaVMProgramLoader programLoader = new TeaVMProgramLoader();
        programLoader.fetchProgram(appConfigItem, program -> createWorker(appConfigItem, program));
    }

    private void createWorker(AppConfigItem appConfigItem, Program program) {
        clearPerformanceStats();

        ArrayBuffer programArrayBuffer = convertProgramToArrayBuffer(program, appConfigItem);
        worker = TeaVMWorkerInterop.createWorker("./scripts/jvic-worker.js");
        TeaVMWorkerInterop.setOnMessage(worker, this::handleWorkerMessage);
        TeaVMWorkerInterop.setOnError(worker, this::handleWorkerError);

        TeaVMKeyboardMatrix teaVMKeyboardMatrix = (TeaVMKeyboardMatrix)keyboardMatrix;
        TeaVMPixelData teaVMPixelData = (TeaVMPixelData)pixelData;
        teaVMPixelData.clearPixels();
        TeaVMSoundGenerator teaVMSoundGenerator = (TeaVMSoundGenerator)soundGenerator;
        frameCounter.reset();
        lastConsumedFrameCount = 0;
        SharedArrayBuffer keyMatrixSAB = teaVMKeyboardMatrix.getSharedArrayBuffer();
        SharedArrayBuffer pixelDataSAB = teaVMPixelData.getSharedArrayBuffer();
        SharedArrayBuffer audioDataSAB = teaVMSoundGenerator.getSharedArrayBuffer();
        SharedArrayBuffer frameCounterSAB = frameCounter.getSharedArrayBuffer();

        TeaVMWorkerInterop.postObject(worker, "Initialise",
            TeaVMWorkerInterop.createInitialiseObject(keyMatrixSAB, pixelDataSAB,
                audioDataSAB, frameCounterSAB));
        TeaVMWorkerInterop.postArrayBufferAndObject(worker, "Start", programArrayBuffer,
                TeaVMWorkerInterop.createStartObject(appConfigItem.getName(),
                        appConfigItem.getFilePath(), appConfigItem.getFileType(),
                        appConfigItem.getMachineType(), appConfigItem.getRam(),
                        appConfigItem.getAutoRunCommand(), appConfigItem.getLoadAddress()));

        stopped = false;
        paused = false;
        soundGenerator.resumeSound();
    }

    private ArrayBuffer convertProgramToArrayBuffer(Program program, AppConfigItem appConfigItem) {
        int programDataLength = (program != null) ? program.getProgramData().length : 0;
        ArrayBuffer programArrayBuffer = ArrayBuffer.create(programDataLength + 8192 + 16384 + 4096 + 8192);
        Uint8Array programUint8Array = Uint8Array.create(programArrayBuffer);
        int index = 0;

        MachineType machineType = MachineType.valueOf(appConfigItem.getMachineType());
        byte[] basicRom = loadBasicRom(machineType);
        byte[] dos1541Rom = Gdx.files.internal("roms/dos1541.rom").readBytes();
        byte[] charRom = Gdx.files.internal("roms/char.rom").readBytes();
        byte[] kernalRom = loadKernalRom(machineType);

        for (byte romByte : basicRom) {
            programUint8Array.set(index++, (short)(romByte & 0xFF));
        }
        for (byte romByte : kernalRom) {
            programUint8Array.set(index++, (short)(romByte & 0xFF));
        }
        for (byte romByte : charRom) {
            programUint8Array.set(index++, (short)(romByte & 0xFF));
        }
        for (byte romByte : dos1541Rom) {
            programUint8Array.set(index++, (short)(romByte & 0xFF));
        }

        if (program != null) {
            for (byte programByte : program.getProgramData()) {
                programUint8Array.set(index++, (short)(programByte & 0xFF));
            }
        }

        return programArrayBuffer;
    }

    private void handleWorkerMessage(JSObject eventObject) {
        switch (TeaVMWorkerInterop.getEventType(eventObject)) {
            case "QuitGame":
                stop();
                break;

            case "WorkerError":
                Gdx.app.error("TeaVM worker detail",
                        TeaVMWorkerInterop.getNestedString(eventObject, "message"));
                break;

            case "PerformanceStats":
                updatePerformanceStats(
                        TeaVMWorkerInterop.getNestedDouble(eventObject, "avgUnusedNanosPerCycle"),
                        TeaVMWorkerInterop.getNestedDouble(eventObject, "headroomFactor"),
                        TeaVMWorkerInterop.getNestedDouble(eventObject, "busyPercent"),
                        TeaVMWorkerInterop.getNestedDouble(eventObject, "avgBatchWorkMillis"),
                        TeaVMWorkerInterop.getNestedDouble(eventObject, "avgBatchCycles"),
                        TeaVMWorkerInterop.getNestedInt(eventObject, "audioQueueSamples"),
                        TeaVMWorkerInterop.getNestedDouble(eventObject, "audioQueueMillis"));
                break;

            default:
                break;
        }
    }

    private void handleWorkerError(String message) {
        Gdx.app.error("TeaVM worker", message);
    }

    private String buildProgramUrl(AppConfigItem appConfigItem) {
        String currentUrl = TeaVMBrowser.getHref();
        String baseUrl = currentUrl.split("[?]")[0];
        int hashIndex = baseUrl.indexOf('#');
        if (hashIndex >= 0) {
            baseUrl = baseUrl.substring(0, hashIndex);
        }
        if (baseUrl.endsWith("/")) {
            return baseUrl + "#/" + slugify(appConfigItem.getName());
        }
        return baseUrl + "/#/" + slugify(appConfigItem.getName());
    }

    private void onPopState() {
        String programHashId = TeaVMBrowser.getHash();
        if ((programHashId == null) || programHashId.trim().isEmpty()) {
            if (isRunning()) {
                stop();
            }
        } else {
            TeaVMBrowser.reload();
        }
    }

    @Override
    public void reset() {
        exit = false;
        paused = false;
        stopped = false;
        frameCounter.reset();
        lastConsumedFrameCount = 0;
        TeaVMBrowser.replaceState(TeaVMBrowser.buildCleanUrl());
        worker = null;
        clearPerformanceStats();
        Gdx.graphics.setTitle("JVic - The web-based VIC 20 emulator built with libGDX");
    }

    @Override
    public void updatePixmap(Pixmap pixmap) {
        super.updatePixmap(pixmap);
        lastConsumedFrameCount = frameCounter.get();
    }

    @Override
    public boolean hasNewFrame() {
        return frameCounter.get() != lastConsumedFrameCount;
    }

    @Override
    public boolean hasStopped() {
        return ((worker != null) && stopped);
    }

    @Override
    public boolean hasTouchScreen() {
        return Gdx.input.isPeripheralAvailable(Input.Peripheral.MultitouchScreen);
    }

    @Override
    public boolean isMobile() {
        return hasTouchScreen();
    }

    @Override
    public String slugify(String input) {
        if ((input == null) || input.isEmpty()) {
            return "";
        }

        String slug = input.toLowerCase().trim();
        slug = slug.replaceAll("[^a-z0-9\\s-]", "").trim();
        slug = slug.replaceAll("[\\s-]+", "-");
        return slug;
    }

    @Override
    public void cancelImport() {
        TeaVMBrowser.replaceState(TeaVMBrowser.buildCleanUrl());
    }

    @Override
    public boolean isRunning() {
        return worker != null;
    }

    @Override
    public void sendNmi() {
        if (worker != null) {
            TeaVMWorkerInterop.postObject(worker, "SendNMI", TeaVMWorkerInterop.createEmptyObject());
        }
    }

    @Override
    public void stop() {
        paused = false;
        if (worker != null) {
            TeaVMWorkerInterop.terminate(worker);
        }
        soundGenerator.pauseSound();
        stopped = true;
    }

    @Override
    public void pause() {
        super.pause();
        if (worker != null) {
            TeaVMWorkerInterop.postObject(worker, "Pause", TeaVMWorkerInterop.createEmptyObject());
        }
    }

    @Override
    public void resume() {
        super.resume();
        if (worker != null) {
            TeaVMWorkerInterop.postObject(worker, "Unpause", TeaVMWorkerInterop.createEmptyObject());
        }
    }

    @Override
    public void changeSound(boolean soundOn) {
        super.changeSound(soundOn);
        if (!soundOn && (worker != null)) {
            TeaVMWorkerInterop.postObject(worker, "SoundOff", TeaVMWorkerInterop.createEmptyObject());
        }
    }

    @Override
    public void toggleWarpSpeed() {
        super.toggleWarpSpeed();
        if (worker != null) {
            TeaVMWorkerInterop.postObject(worker,
                    warpSpeed ? "WarpSpeedOn" : "WarpSpeedOff",
                    TeaVMWorkerInterop.createEmptyObject());
        }
    }

    @Override
    public void saveScreenshot(Pixmap screenPixmap, AppConfigItem appConfigItem) {
        Gdx.app.log("TeaVMJVicRunner", "saveScreenshot() is not implemented in the TeaVM web target.");
    }

    void notifyAudioWorkletReady() {
        if (worker != null) {
            TeaVMWorkerInterop.postObject(worker, "AudioWorkletReady", TeaVMWorkerInterop.createEmptyObject());
            if (getMachineInputProcessor() != null) {
                getMachineInputProcessor().setSpeakerOn(true);
            }
        } else {
            soundGenerator.pauseSound();
            if (getMachineInputProcessor() != null) {
                getMachineInputProcessor().setSpeakerOn(false);
            }
        }
    }

    public void updateAudioProcessorStats(double underrunCount, double underrunSampleCount) {
        this.audioUnderrunCount = underrunCount;
        this.audioUnderrunSampleCount = underrunSampleCount;
    }

    public void updatePerformanceStats(double avgUnusedNanosPerCycle, double headroomFactor,
            double busyPercent, double avgBatchWorkMillis, double avgBatchCycles,
            int audioQueueSamples, double audioQueueMillis) {
        currentUnusedNanosPerCycle = avgUnusedNanosPerCycle;
        if (unusedNanosPerCycleSampleCount == 0) {
            minUnusedNanosPerCycle = avgUnusedNanosPerCycle;
            maxUnusedNanosPerCycle = avgUnusedNanosPerCycle;
            meanUnusedNanosPerCycle = avgUnusedNanosPerCycle;
        } else {
            minUnusedNanosPerCycle = Math.min(minUnusedNanosPerCycle, avgUnusedNanosPerCycle);
            maxUnusedNanosPerCycle = Math.max(maxUnusedNanosPerCycle, avgUnusedNanosPerCycle);
            meanUnusedNanosPerCycle += (avgUnusedNanosPerCycle - meanUnusedNanosPerCycle)
                    / (unusedNanosPerCycleSampleCount + 1);
        }
        unusedNanosPerCycleSampleCount++;

        if (rollingUnusedNanosPerCycleCount == UNUSED_NS_PER_CYCLE_ROLLING_AVERAGE_WINDOW) {
            rollingUnusedNanosPerCycleSum -= rollingUnusedNanosPerCycleSamples[rollingUnusedNanosPerCycleIndex];
        } else {
            rollingUnusedNanosPerCycleCount++;
        }
        rollingUnusedNanosPerCycleSamples[rollingUnusedNanosPerCycleIndex] = avgUnusedNanosPerCycle;
        rollingUnusedNanosPerCycleSum += avgUnusedNanosPerCycle;
        rollingUnusedNanosPerCycleIndex = (rollingUnusedNanosPerCycleIndex + 1)
                % UNUSED_NS_PER_CYCLE_ROLLING_AVERAGE_WINDOW;

        this.headroomFactor = headroomFactor;
        this.busyPercent = busyPercent;
        this.avgBatchWorkMillis = avgBatchWorkMillis;
        this.avgBatchCycles = avgBatchCycles;
        this.audioQueueSamples = audioQueueSamples;
        this.audioQueueMillis = audioQueueMillis;

        if (audioQueueMillis >= 0) {
            if (rollingAudioQueueMillisCount == AUDIO_QUEUE_MILLIS_ROLLING_AVERAGE_WINDOW) {
                rollingAudioQueueMillisSum -= rollingAudioQueueMillisSamples[rollingAudioQueueMillisIndex];
            } else {
                rollingAudioQueueMillisCount++;
            }
            rollingAudioQueueMillisSamples[rollingAudioQueueMillisIndex] = audioQueueMillis;
            rollingAudioQueueMillisSum += audioQueueMillis;
            rollingAudioQueueMillisIndex = (rollingAudioQueueMillisIndex + 1)
                    % AUDIO_QUEUE_MILLIS_ROLLING_AVERAGE_WINDOW;
        }

        this.performanceStatsAvailable = true;
    }

    @Override
    public String getPerformanceStatsText() {
        if (!performanceStatsAvailable) {
            return isRunning() ? "Perf: waiting for worker stats" : "";
        }

        StringBuilder text = new StringBuilder();
        text.append("Unused ns/cycle:");
        text.append('\n');
        text.append("  cur ");
        text.append(Math.round(currentUnusedNanosPerCycle));
        text.append(", min ");
        text.append(Math.round(minUnusedNanosPerCycle));
        text.append(", max ");
        text.append(Math.round(maxUnusedNanosPerCycle));
        text.append('\n');
        text.append("  avg ");
        text.append(Math.round(meanUnusedNanosPerCycle));
        text.append(", roll");
        text.append(rollingUnusedNanosPerCycleCount);
        text.append(' ');
        text.append(Math.round(getRollingUnusedNanosPerCycleAverage()));
        text.append('\n');
        text.append("Headroom: ");
        text.append(formatDecimal(headroomFactor, 2));
        text.append("x, busy ");
        text.append(formatDecimal(busyPercent, 1));
        text.append('%');
        text.append('\n');
        text.append("Worker batch: ");
        text.append(formatDecimal(avgBatchWorkMillis, 2));
        text.append(" ms, ");
        text.append(Math.round(avgBatchCycles));
        text.append(" cycles");
        text.append('\n');

        if (audioQueueSamples >= 0) {
            text.append("Audio queue: ");
            text.append(audioQueueSamples);
            text.append(" samples, ");
            text.append(formatDecimal(audioQueueMillis, 1));
            text.append(" ms");
            if (rollingAudioQueueMillisCount > 0) {
                text.append(", roll");
                text.append(rollingAudioQueueMillisCount);
                text.append(' ');
                text.append(formatDecimal(getRollingAudioQueueMillisAverage(), 1));
                text.append(" ms");
            }
        } else {
            text.append("Audio queue: sound off");
        }

        text.append('\n');
        text.append("Audio underruns: ");
        text.append(Math.round(audioUnderrunCount));
        text.append(" (");
        text.append(Math.round(audioUnderrunSampleCount));
        text.append(" samples)");
        return text.toString();
    }

    private void clearPerformanceStats() {
        currentUnusedNanosPerCycle = 0;
        minUnusedNanosPerCycle = 0;
        maxUnusedNanosPerCycle = 0;
        meanUnusedNanosPerCycle = 0;
        unusedNanosPerCycleSampleCount = 0;
        rollingUnusedNanosPerCycleSum = 0;
        rollingUnusedNanosPerCycleIndex = 0;
        rollingUnusedNanosPerCycleCount = 0;
        headroomFactor = 0;
        busyPercent = 0;
        avgBatchWorkMillis = 0;
        avgBatchCycles = 0;
        audioQueueSamples = -1;
        audioQueueMillis = -1;
        rollingAudioQueueMillisSum = 0;
        rollingAudioQueueMillisIndex = 0;
        rollingAudioQueueMillisCount = 0;
        audioUnderrunCount = 0;
        audioUnderrunSampleCount = 0;
        performanceStatsAvailable = false;
    }

    private double getRollingUnusedNanosPerCycleAverage() {
        return (rollingUnusedNanosPerCycleCount == 0)
                ? 0
                : rollingUnusedNanosPerCycleSum / rollingUnusedNanosPerCycleCount;
    }

    private double getRollingAudioQueueMillisAverage() {
        return (rollingAudioQueueMillisCount == 0)
                ? 0
                : rollingAudioQueueMillisSum / rollingAudioQueueMillisCount;
    }

    private String formatDecimal(double value, int decimalPlaces) {
        double scale = Math.pow(10, decimalPlaces);
        double rounded = Math.round(value * scale) / scale;
        String text = Double.toString(rounded);
        if (decimalPlaces == 0) {
            return Integer.toString((int)rounded);
        }
        int decimalIndex = text.indexOf('.');
        if (decimalIndex < 0) {
            StringBuilder padded = new StringBuilder(text);
            padded.append('.');
            for (int index = 0; index < decimalPlaces; index++) {
                padded.append('0');
            }
            return padded.toString();
        }
        int fractionDigits = text.length() - decimalIndex - 1;
        if (fractionDigits < decimalPlaces) {
            StringBuilder padded = new StringBuilder(text);
            for (int index = fractionDigits; index < decimalPlaces; index++) {
                padded.append('0');
            }
            return padded.toString();
        }
        return text;
    }
}