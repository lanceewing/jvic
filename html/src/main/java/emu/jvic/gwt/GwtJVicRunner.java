package emu.jvic.gwt;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.typedarrays.shared.ArrayBuffer;
import com.google.gwt.typedarrays.shared.TypedArrays;
import com.google.gwt.typedarrays.shared.Uint8Array;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Window;
import com.google.gwt.webworker.client.ErrorEvent;
import com.google.gwt.webworker.client.ErrorHandler;

import emu.jvic.JVicRunner;
import emu.jvic.KeyboardMatrix;
import emu.jvic.MachineType;
import emu.jvic.PixelData;
import emu.jvic.Program;
import emu.jvic.config.AppConfigItem;
import emu.jvic.worker.MessageEvent;
import emu.jvic.worker.MessageHandler;
import emu.jvic.worker.Worker;

/**
 * GWT implementation of the JVicRunner. It uses a web worker to perform the execution
 * of the AGI interpreter animation ticks.
 */
public class GwtJVicRunner extends JVicRunner {

    /**
     * The web worker that will execute the VIC 20 emulator in the background.
     */
    private Worker worker;
    
    /**
     * Indicates that the GWT GwtJVicRunner is in the stopped state, i.e. it was previously
     * running a game but the game has now stopped, e.g. due to the user quitting the game.
     */
    private boolean stopped;
    
    /**
     * Constructor for GwtJVicRunner.
     * 
     * @param keyboardMatrix
     * @param pixelData
     */
    public GwtJVicRunner(KeyboardMatrix keyboardMatrix, PixelData pixelData) {
        super(keyboardMatrix, pixelData, null);
        
        soundGenerator = new GwtSoundGenerator(this);
        
        registerPopStateEventHandler();
    }

    private native void registerPopStateEventHandler() /*-{
        var that = this;
        var oldHandler = $wnd.onpopstate;
        $wnd.onpopstate = $entry(function(e) {
            that.@emu.jvic.gwt.GwtJVicRunner::onPopState(Lcom/google/gwt/user/client/Event;)(e);
            if (oldHandler) {
                oldHandler();
            }
        });
    }-*/;
    
    private void onPopState(Event e) {
        String newURL = Window.Location.getHref();
        String programHashId = Window.Location.getHash();
        
        logToJSConsole("PopState - newURL: " + newURL + ", programHashId: " + programHashId);
        
        // If the URL does not have a hash, then it has gone back to the home screen.
        if ((programHashId == null) || (programHashId.trim().equals(""))) {
            if (isRunning()) {
                stop();
            }
        } else {
            Window.Location.reload();
        }
    }
    
    @Override
    public void start(AppConfigItem appConfigItem) {
        // Do not change the URL if jvic was invoked with "url" request param, or had
        // a path part or query param in the hash.
        if ((Window.Location.getParameter("url") == null) && 
            (!"Adhoc VIC 20 Program".equals(appConfigItem.getName())) && 
            (Window.Location.getHash().indexOf('=') < 0) && 
            (Window.Location.getHash().indexOf('/') < 0)) {
            
            // The URL Builder doesn't add a / before the #, so we do this ourselves.
            String newURL = Window.Location.createUrlBuilder().setPath("/").setHash(null).buildString();
            if (newURL.endsWith("/")) {
                newURL += "#/";
            } else {
                newURL += "/#/";
            }
            newURL += slugify(appConfigItem.getName());
            
            updateURLWithoutReloading(newURL);
        }
        
        GwtProgramLoader programLoader = new GwtProgramLoader();
        programLoader.fetchProgram(appConfigItem, p -> createWorker(appConfigItem, p));
    }

    private ArrayBuffer convertProgramToArrayBuffer(Program program, AppConfigItem appConfigItem) {
        int programDataLength = (program != null? program.getProgramData().length : 0);
        ArrayBuffer programArrayBuffer = TypedArrays.createArrayBuffer(
                programDataLength + 8192 + 16384 + 4096 + 8192);
        Uint8Array programUint8Array = TypedArrays.createUint8Array(programArrayBuffer);
        int index = 0;
        
        MachineType machineType = MachineType.valueOf(appConfigItem.getMachineType());
        byte[] basicRom = loadBasicRom(machineType);
        byte[] dos1541Rom = Gdx.files.internal("roms/dos1541.rom").readBytes();
        byte[] charRom = Gdx.files.internal("roms/char.rom").readBytes();
        byte[] kernalRom = loadKernalRom(machineType);
        
        for (int i=0; i < basicRom.length; index++, i++) {
            programUint8Array.set(index, (basicRom[i] & 0xFF));
        }
        for (int i=0; i < kernalRom.length; index++, i++) {
            programUint8Array.set(index, (kernalRom[i] & 0xFF));
        }
        for (int i=0; i < charRom.length; index++, i++) {
            programUint8Array.set(index, (charRom[i] & 0xFF));
        }
        for (int i=0; i < dos1541Rom.length; index++, i++) {
            programUint8Array.set(index, (dos1541Rom[i] & 0xFF));
        }
        
        if (program != null) {
            for (int i=0; i < programDataLength; index++, i++) {
                programUint8Array.set(index, (program.getProgramData()[i] & 0xFF));
            }
        }
        return programArrayBuffer;
    }
    
    /**
     * Creates a new web worker to run the VIC 20 program.
     * 
     * @param program Contains the raw data of the VIC 20 program to run.
     */
    public void createWorker(AppConfigItem appConfigItem, Program program) {
        // Convert program bytes to ArrayBuffer.
        ArrayBuffer programArrayBuffer = convertProgramToArrayBuffer(program, appConfigItem);
        
        worker = Worker.create("/worker/worker.nocache.js");
        
        final MessageHandler webWorkerMessageHandler = new MessageHandler() {
            @Override
            public void onMessage(MessageEvent event) {
                JavaScriptObject eventObject = event.getDataAsObject();
                
                switch (getEventType(eventObject)) {
                    case "QuitGame":
                        // This message is sent from the worker when the program has ended, usually
                        // due to the user quitting the program.
                        stop();
                        break;
                        
                    default:
                        // Unknown. Ignore.
                }
            }
        };

        final ErrorHandler webWorkerErrorHandler = new ErrorHandler() {
            @Override
            public void onError(final ErrorEvent pEvent) {
                Gdx.app.error("client onError", "Received message: " + pEvent.getMessage());
            }
        };

        worker.setOnMessage(webWorkerMessageHandler);
        worker.setOnError(webWorkerErrorHandler);
        
        // In order to facilitate the communication with the worker, we must send
        // all SharedArrayBuffer objects to the webworker.
        GwtKeyboardMatrix gwtKeyboardMatrix = (GwtKeyboardMatrix)keyboardMatrix;
        GwtPixelData gwtPixelData = (GwtPixelData)pixelData;
        gwtPixelData.clearPixels();
        GwtSoundGenerator gwtPSG = (GwtSoundGenerator)soundGenerator;
        JavaScriptObject keyMatrixSAB = gwtKeyboardMatrix.getSharedArrayBuffer();
        JavaScriptObject pixelDataSAB = gwtPixelData.getSharedArrayBuffer();
        JavaScriptObject audioDataSAB = gwtPSG.getSharedArrayBuffer();
        
        // We currently send one message to Initialise, using the SharedArrayBuffers,
        // then another message to Start the machine with the given game data. The 
        // game data is "transferred", whereas the others are not but rather shared.
        worker.postObject("Initialise", createInitialiseObject(
                keyMatrixSAB, 
                pixelDataSAB,
                audioDataSAB));
        worker.postArrayBufferAndObject("Start", 
                programArrayBuffer,
                createStartObject(
                        appConfigItem.getName(),
                        appConfigItem.getFilePath(),
                        appConfigItem.getFileType(),
                        appConfigItem.getMachineType(),
                        appConfigItem.getRam(),
                        appConfigItem.getAutoRunCommand(),
                        appConfigItem.getLoadAddress())
                );
        
        // Resume sound output whenever a new instance of JVic is starting up.
        gwtPSG.resumeSound();
    }
    
    /**
     * Creates a JavaScript object, wrapping the objects to send to the web worker to
     * initialise the Machine.
     * 
     * @param keyMatrixSAB 
     * @param pixelDataSAB 
     * @param audioDataSAB 
     * 
     * @return The created object.
     */
    private native JavaScriptObject createInitialiseObject(
            JavaScriptObject keyMatrixSAB, 
            JavaScriptObject pixelDataSAB,
            JavaScriptObject audioDataSAB)/*-{
        return { 
            keyMatrixSAB: keyMatrixSAB,
            pixelDataSAB: pixelDataSAB,
            audioDataSAB: audioDataSAB
        };
    }-*/;
    
    /**
     * Creates a JavaScript object using the given parameters to send in the Start
     * message to the web worker.
     * 
     * @param name
     * @param filePath
     * @param fileType
     * @param machineType
     * @param ramType
     * @param autoRunCommand
     * @param loadAddress
     * 
     * @return
     */
    private native JavaScriptObject createStartObject(
            String name, String filePath, String fileType, String machineType, 
            String ramType, String autoRunCommand, String loadAddress
            )/*-{
        return {
            name: name,
            filePath: filePath,
            fileType: fileType,
            machineType: machineType,
            ramType: ramType,
            autoRunCommand: autoRunCommand,
            loadAddress: loadAddress
        };
    }-*/;
    
    private native String getEventType(JavaScriptObject obj)/*-{
        return obj.name;
    }-*/;

    private native JavaScriptObject getEmbeddedObject(JavaScriptObject obj)/*-{
        return obj.object;
    }-*/;

    private native ArrayBuffer getArrayBuffer(JavaScriptObject obj)/*-{
        return obj.buffer;
    }-*/;

    private native int getNestedInt(JavaScriptObject obj, String fieldName)/*-{
        return obj.object[fieldName];
    }-*/;
    
    private static native void updateURLWithoutReloading(String newURL) /*-{
        $wnd.history.pushState(newURL, "", newURL);
    }-*/;
    
    private void clearUrl() {
        String newURL = Window.Location.createUrlBuilder()
                .setPath("/")
                .setHash(null)
                .removeParameter("url")
                .buildString();
        updateURLWithoutReloading(newURL);
    }

    @Override
    public void stop() {
        // Kill off the web worker immediately. Ensure that any playing sound is stopped.
        paused = false;
        worker.terminate();
        soundGenerator.pauseSound();
        stopped = true;
    }
    
    @Override
    public void reset() {
        // Resets to the original state, as if a game has not been previously run.
        paused = false;
        stopped = false;
        worker = null;
        
        clearUrl();
        
        Gdx.graphics.setTitle("JVic - The web-based VIC 20 emulator built with libGDX");
    }

    @Override
    public void pause() {
        super.pause();
        
        if (worker != null) {
            worker.postObject("Pause", JavaScriptObject.createObject());
        }
    }
    
    @Override
    public void resume() {
        super.resume();
        
        if (worker != null) {
            worker.postObject("Unpause", JavaScriptObject.createObject());
        }
    }
    
    @Override
    public boolean hasTouchScreen() {
        return hasTouchScreenHtml();
    }
    
    private native boolean hasTouchScreenHtml() /*-{
        if ("maxTouchPoints" in navigator) {
            return navigator.maxTouchPoints > 0;
        } else if ("msMaxTouchPoints" in navigator) {
            return navigator.msMaxTouchPoints > 0;
        } else {
            return false;
        }
    }-*/;

    @Override
    public boolean isMobile() {
        return isMobileHtml();
    }
    
    private native boolean isMobileHtml() /*-{
        if (navigator.userAgentData) {
            return navigator.userAgentData.mobile;
        } else {
            // Fall back to user-agent parsing, as some browsers don't support above yet.
            if (navigator.platform.indexOf("Win") != -1) return false;
            if (navigator.platform.indexOf("Mac") != -1) return false;
            if (navigator.platform.indexOf("Android") != -1) return true;
            if (navigator.platform.indexOf("iPhone") != -1) return true;
            if (navigator.platform.indexOf("iPad") != -1) return true;
            // For other devices, we'll use touch screen logic.
            if ("maxTouchPoints" in navigator) {
                return navigator.maxTouchPoints > 0;
            } else if ("msMaxTouchPoints" in navigator) {
                return navigator.msMaxTouchPoints > 0;
            } else {
                return false;
            }
        }
    }-*/;

    @Override
    public String slugify(String input) {
        return slugifyHtml(input);
    }
    
    private native String slugifyHtml(String input) /*-{
        if (!input) return '';

        // Make lower case and trim.
        var slug = input.toLowerCase().trim();

        // Remove accents from characters.
        slug = slug.normalize('NFD').replace(/[\u0300-\u036f]/g, '')

        // Replace invalid chars with spaces.
        slug = slug.replace(/[^a-z0-9\s-]/g, '').trim();

        // Replace multiple spaces or hyphens with a single hyphen.
        slug = slug.replace(/[\s-]+/g, '-');

        return slug;
    }-*/;

    @Override
    public void cancelImport() {
        clearUrl();
    }

    @Override
    public boolean hasStopped() {
        return ((worker != null) && stopped);
    }
    
    @Override
    public boolean isRunning() {
        return (worker != null);
    }
    
    @Override
    public void changeSound(boolean soundOn) {
        super.changeSound(soundOn);
        
        // In addition to the default behaviour, the GWT platform needs to send 
        // a message to the web worker, in the case of sound off.
        if (soundOn) {
            // Nothing to do. We'll get the audio ready callback later on.
        } else {
            worker.postObject("SoundOff", JavaScriptObject.createObject());
        }
    }
    
    @Override
    public void sendNmi() {
        worker.postObject("SendNMI", JavaScriptObject.createObject());
    }
    
    @Override
    public void toggleWarpSpeed() {
        super.toggleWarpSpeed();
        
        if (warpSpeed) {
            worker.postObject("WarpSpeedOn", JavaScriptObject.createObject());
        } else {
            worker.postObject("WarpSpeedOff", JavaScriptObject.createObject());
        }
    }
    
    @Override
    public void saveScreenshot(Pixmap screenPixmap, AppConfigItem appConfigItem) {
        // Not supported yet by the HTML5/GWT version.
    }

    public Worker getCurrentWorker() {
        return worker;
    }

    private final native void logToJSConsole(String message)/*-{
        console.log(message);
    }-*/;
}
