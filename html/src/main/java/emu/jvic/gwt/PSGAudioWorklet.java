package emu.jvic.gwt;

import com.google.gwt.core.client.JavaScriptObject;

import emu.jvic.worker.Worker;

/**
 * Creates and manages the AudioWorklet for playing the VIC chip sample data.
 */
public class PSGAudioWorklet {

    /**
     * The SharedQueue that the worker puts the samples in to.
     */
    private SharedQueue sampleSharedQueue;
    
    /**
     * The GWT JVicRunner, which is the client side, i.e. UI thread. The 
     * PSGAudioWorklet uses it to get hold of the current Worker reference, 
     * which changes between game executions.
     */
    private GwtJVicRunner gwtJVicRunner;
    
    /**
     * Constructor for PSGAudioWorklet.
     * 
     * @param sampleSharedQueue SharedQueue that the worker puts the samples in.
     * @param gwtJVicRunner 
     */
    public PSGAudioWorklet(SharedQueue sampleSharedQueue, GwtJVicRunner gwtJVicRunner) {
        this.sampleSharedQueue = sampleSharedQueue;
        this.gwtJVicRunner = gwtJVicRunner;
        
        initialise(sampleSharedQueue.getSharedArrayBuffer());
    }

    /**
     * Initialises the PSGAudioWorkler, by creating the AudioContext, then 
     * registering an AudioWorkletProcessor, sending it the SharedArrayBuffer
     * that will contain the audio sample data, and then creating an
     * AudioWorkletNode, connected to the standard audio output destination,
     * to make use of the AudioWorkletProcessor. If this is run outside of a
     * user gesture, then the resume handler is not invoked until the next 
     * call to resume within a user gesture.
     * 
     * @param audioBufferSAB The SharedArrayBuffer to get the sound sample data from.
     */
    private native void initialise(JavaScriptObject audioBufferSAB)/*-{
        var ua = navigator.userAgent.toLowerCase();
        var isIOS = (
            (ua.indexOf("iphone") >= 0 && ua.indexOf("like iphone") < 0) ||
            (ua.indexOf("ipad") >= 0 && ua.indexOf("like ipad") < 0) ||
            (ua.indexOf("ipod") >= 0 && ua.indexOf("like ipod") < 0) ||
            (ua.indexOf("mac os x") >= 0 && navigator.maxTouchPoints > 0) // New ipads show up as macs in user agent, but they have a touch screen
        );
        if (isIOS && navigator.audioSession) {
            // See: https://bugs.webkit.org/show_bug.cgi?id=237322
            console.log("Setting AudioSession type to 'playback' for iOS.");
            navigator.audioSession.type = "playback";
        }
        
        this.ready = false;
    
        // Store for later use by resume handler.
        this.audioBufferSAB = audioBufferSAB;
        
        try {
            // If this is not executing within a user gesture, then it will be suspended.
            this.audioContext = new AudioContext({sampleRate: 22050});
        }
        catch (e) {
            console.log("Failed to create AudioContext. Error was: " + e);
        }
        
        if (this.audioContext) {
            if (this.audioContext.state == "running") {
                // For Chrome, it may be that the state is already running at startup. We
                // want all browsers and platforms to be consistent, so we suspend manually.
                console.log("AudioContext is already running at startup. Suspend until program loads.");
                this.audioContext.suspend();
            }
            else {
                // If not running, we only try to resume if we're in a user interaction.
                console.log("AudioContext not running at startup.");
            }
        }
    }-*/;
    
    private final native void registerAudioWorklet(JavaScriptObject psgAudioWorklet)/*-{
        var that = psgAudioWorklet;
        if (that.audioContext.state === "running") {
            // If the AudioWorkletNode has not yet been set up, then do so.
            if (!that.registering) {
                // Ensure that this is done only once.
                that.registering = true;
            
                console.log("Adding AudioWorkletProcessor module...");
            
                that.audioContext.audioWorklet.addModule('/sound-renderer.js').then(function() {
                    that.audioWorkletNode = new AudioWorkletNode(
                        that.audioContext, 
                        "sound-renderer",
                        {
                            numberOfInputs: 0,
                            numberOfOutputs: 1, 
                            outputChannelCount: [1]
                        }
                    );
                    
                    that.audioWorkletNode.port.onmessage = function() {
                        // There is only one message we can receive, which is ready.
                        console.log("Received ready message from AudioWorkletProcessor.");
                        that.@emu.jvic.gwt.PSGAudioWorklet::notifyAudioReady()();
                        that.ready = true;
                    };
                    
                    console.log("Sending audio buffer SAB to AudioWorkletProcessor...");
                    
                    // Send SharedArrayBuffer for SharedQueue to audio worklet processor.
                    that.audioWorkletNode.port.postMessage({audioBufferSAB: that.audioBufferSAB});
                    
                    console.log("Connecting AudioWorklet to audio output destination...");
                    
                    // The AudioWorkletNode has only the output connection. The 'input' is
                    // read directly from the SharedArrayBuffer by the AudioWorkletProcessor.
                    that.audioWorkletNode.connect(that.audioContext.destination);
                });
            } else {
                console.log("AudioWorkletProcessor is already registered.");
            }
        }
    }-*/;
    
    /**
     * Notifies the web worker that the audio worklet is ready for sample data.
     */
    public void notifyAudioReady() {
        Worker worker = gwtJVicRunner.getCurrentWorker();
        if (worker != null) {
            logToJSConsole("Sending AudioWorkletReady message to web worker...");
            worker.postObject("AudioWorkletReady", JavaScriptObject.createObject());
            if (gwtJVicRunner.getMachineInputProcessor() != null) {
                logToJSConsole("Worker is running, so turning speaker on...");
                gwtJVicRunner.getMachineInputProcessor().setSpeakerOn(true);
            }
        } else {
            // If worker isn't running, then suspend audio for now.
            suspend();
            logToJSConsole("Worker not running, so turnning speaker off.");
            if (gwtJVicRunner.getMachineInputProcessor() != null) {
                gwtJVicRunner.getMachineInputProcessor().setSpeakerOn(false);
            }
        }
    }
    
    public native boolean isReady()/*-{
        return this.ready;
    }-*/;
    
    /**
     * This is invoked whenever the sound output should be resumed.
     */
    public native void resume()/*-{
        var isUserInteraction = (navigator.userActivation && navigator.userActivation.isActive);
        var that = this;
        
        if (this.audioContext && (this.audioContext.state === "suspended")) {
            if (isUserInteraction) {
                console.log("Inside a user interaction, so try to resume AudioContext.");
                this.audioContext.resume().then(function() {
                    console.log("AudioContext has successfully resumed.");
                    that.@emu.jvic.gwt.PSGAudioWorklet::registerAudioWorklet(Lcom/google/gwt/core/client/JavaScriptObject;)(that);
                })['catch'](function(e) {
                    // NOTE: The ['catch'] is required due to old Rhino issue.
                    console.log("AudioContext was not able to resume. Exception was: " + e);
                    console.log("AudioContext state is: " + that.audioContext.state);
                });
            } else {
                console.log("Not inside a user interaction, so skip AudioContext resume.");
            }
        } else {
            console.log("AudioContext is already " + this.audioContext.state);
        }
    }-*/;
    
    /**
     * Suspends the output of audio.
     */
    public native void suspend()/*-{
        if (this.audioContext && (this.audioContext.state === "running")) {
            this.audioContext.suspend();
        }
    }-*/;
    
    /**
     * Returns whether the audio worklet is currently running. If suspended, it
     * will return false.
     * 
     * @return
     */
    public native boolean isRunning()/*-{
        return (this.audioContext && (this.audioContext.state === "running"));
    }-*/;
    
    private final native void logToJSConsole(String message)/*-{
        console.log(message);
    }-*/;
}
