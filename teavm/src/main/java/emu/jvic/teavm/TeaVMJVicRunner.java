package emu.jvic.teavm;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Callable;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Pixmap;

import emu.jvic.JVicRunner;
import emu.jvic.Machine;
import emu.jvic.MachineType;
import emu.jvic.Program;
import emu.jvic.config.AppConfigItem;
import emu.jvic.cpu.Cpu6502;
import emu.jvic.memory.RamType;

public class TeaVMJVicRunner extends JVicRunner {

    private Machine machine;
    private Callable<Queue<char[]>> autoLoadProgram;
    private boolean stopped;
    private boolean loopScheduled;

    public TeaVMJVicRunner() {
        super(new TeaVMKeyboardMatrix(), new TeaVMPixelData(), new TeaVMSoundGenerator());
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
        programLoader.fetchProgram(appConfigItem, program -> runProgram(appConfigItem, program));
    }

    private void runProgram(AppConfigItem appConfigItem, Program program) {
        MachineType machineType = MachineType.valueOf(appConfigItem.getMachineType());
        RamType ramType = RamType.valueOf(appConfigItem.getRam());

        machine = new Machine(soundGenerator, keyboardMatrix, pixelData);

        byte[] basicRom = loadBasicRom(machineType);
        byte[] dos1541Rom = Gdx.files.internal("roms/dos1541.rom").readBytes();
        byte[] charRom = Gdx.files.internal("roms/char.rom").readBytes();
        byte[] kernalRom = loadKernalRom(machineType);

        autoLoadProgram = machine.init(basicRom, kernalRom, charRom, dos1541Rom, program, machineType, ramType);
        exit = false;
        stopped = false;
        paused = false;

        if (getMachineInputProcessor() != null) {
            getMachineInputProcessor().setSpeakerOn(true);
        }
        soundGenerator.resumeSound();
        ensureLoopRunning();
    }

    private void ensureLoopRunning() {
        if (!loopScheduled) {
            loopScheduled = true;
            TeaVMBrowser.requestAnimationFrame(this::tick);
        }
    }

    private void tick(double timestamp) {
        loopScheduled = false;

        if (exit) {
            pixelData.clearPixels();
            machine = null;
            autoLoadProgram = null;
            stopped = true;
            soundGenerator.pauseSound();
            return;
        }

        if (!paused && (machine != null)) {
            machine.update();
            handleAutoLoad();
        }

        if ((machine != null) || !stopped) {
            ensureLoopRunning();
        }
    }

    private void handleAutoLoad() {
        if (autoLoadProgram != null) {
            int[] mem = machine.getMemory().getMemoryArray();

            if (mem[0xD1] == 110) {
                try {
                    Queue<char[]> autoRunCmdQueue = autoLoadProgram.call();
                    runNextBasicCommand(autoRunCmdQueue, mem);
                    if (autoRunCmdQueue.isEmpty()) {
                        autoLoadProgram = null;
                    }
                } catch (Exception e) {
                    autoLoadProgram = null;
                }
            }

            if ((autoLoadProgram != null) && (mem[0xD1] == 220)) {
                try {
                    Queue<char[]> autoRunCmdQueue = autoLoadProgram.call();
                    runNextBasicCommand(autoRunCmdQueue, mem);
                } catch (Exception e) {
                    // Ignore and clear auto-load state.
                }
                autoLoadProgram = null;
            }
        }
    }

    private void runNextBasicCommand(Queue<char[]> cmdQueue, int[] mem) {
        if ((cmdQueue != null) && !cmdQueue.isEmpty()) {
            char[] cmdChars = cmdQueue.remove();
            int cmdCharPos = 0;
            for (; cmdCharPos < cmdChars.length; cmdCharPos++) {
                mem[631 + cmdCharPos] = cmdChars[cmdCharPos];
            }
            mem[631 + cmdCharPos] = 0x0D;
            mem[198] = cmdCharPos + 1;
        }
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
        paused = true;
        stopped = false;
        machine = null;
        autoLoadProgram = null;
        TeaVMBrowser.replaceState(TeaVMBrowser.buildCleanUrl());
        Gdx.graphics.setTitle("JVic - The web-based VIC 20 emulator built with libGDX");
    }

    @Override
    public boolean hasStopped() {
        return stopped;
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
        // No-op in the Phase 1 TeaVM bootstrap.
    }

    @Override
    public boolean isRunning() {
        return machine != null;
    }

    @Override
    public void sendNmi() {
        if (machine != null) {
            machine.getCpu().setInterrupt(Cpu6502.S_NMI);
        }
    }

    @Override
    public void stop() {
        exit = true;
        paused = false;
        soundGenerator.pauseSound();
        ensureLoopRunning();
    }

    @Override
    public void resume() {
        super.resume();
        ensureLoopRunning();
    }

    @Override
    public void saveScreenshot(Pixmap screenPixmap, AppConfigItem appConfigItem) {
        Gdx.app.log("TeaVMJVicRunner", "saveScreenshot() is not implemented in the Phase 1 TeaVM bootstrap.");
    }
}