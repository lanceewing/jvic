package emu.jvic.teavm;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.typedarrays.SharedArrayBuffer;

final class TeaVMPSGAudioWorklet {

    @JSFunctor
    interface ReadyCallback extends JSObject {
        void handle();
    }

    @JSFunctor
    interface StatsCallback extends JSObject {
        void handle(double underrunCount, double underrunSampleCount);
    }

    private final JSObject handle;

    TeaVMPSGAudioWorklet(TeaVMSharedQueue sampleSharedQueue, TeaVMJVicRunner jvicRunner) {
        this.handle = createHandle(sampleSharedQueue.getSharedArrayBuffer(),
                jvicRunner::notifyAudioWorkletReady,
                jvicRunner::updateAudioProcessorStats);
    }

    boolean isReady() {
        return isReady(handle);
    }

    void resume() {
        resume(handle);
    }

    void suspend() {
        suspend(handle);
    }

    boolean isRunning() {
        return isRunning(handle);
    }

    void resetStats() {
        resetStats(handle);
    }

    @JSBody(params = { "audioBufferSAB", "readyCallback", "statsCallback" }, script = "var handle = { ready: false, registering: false, audioBufferSAB: audioBufferSAB, audioWorkletNode: null, audioContext: null };"
            + "var ua = navigator.userAgent.toLowerCase();"
            + "var isIOS = ((ua.indexOf('iphone') >= 0 && ua.indexOf('like iphone') < 0) || (ua.indexOf('ipad') >= 0 && ua.indexOf('like ipad') < 0) || (ua.indexOf('ipod') >= 0 && ua.indexOf('like ipod') < 0) || (ua.indexOf('mac os x') >= 0 && navigator.maxTouchPoints > 0));"
            + "if (isIOS && navigator.audioSession) { navigator.audioSession.type = 'playback'; }"
            + "try { handle.audioContext = new AudioContext({ sampleRate: 22050 }); } catch (e) { console.log('Failed to create AudioContext. Error was: ' + e); }"
            + "if (handle.audioContext && handle.audioContext.state === 'running') { handle.audioContext.suspend(); }"
            + "handle.registerAudioWorklet = function() {"
            + "  if (!handle.audioContext || handle.audioContext.state !== 'running') { return; }"
            + "  if (handle.audioWorkletNode) { readyCallback(); return; }"
            + "  if (handle.registering) { return; }"
            + "  handle.registering = true;"
            + "  handle.audioContext.audioWorklet.addModule('./sound-renderer.js').then(function() {"
            + "    handle.audioWorkletNode = new AudioWorkletNode(handle.audioContext, 'sound-renderer', { numberOfInputs: 0, numberOfOutputs: 1, outputChannelCount: [1] });"
            + "    handle.audioWorkletNode.port.onmessage = function(event) {"
            + "      var message = event.data || {};"
            + "      if (message.ready) { handle.ready = true; readyCallback(); }"
            + "      else if (message.type === 'AudioProcessorStats') { statsCallback(message.underrunCount || 0, message.underrunSampleCount || 0); }"
            + "    };"
            + "    handle.audioWorkletNode.port.postMessage({ audioBufferSAB: handle.audioBufferSAB });"
            + "    handle.audioWorkletNode.connect(handle.audioContext.destination);"
            + "  }).catch(function(e) { handle.registering = false; console.log('Failed to register AudioWorkletProcessor. Error was: ' + e); });"
            + "};"
            + "return handle;")
    private static native JSObject createHandle(SharedArrayBuffer audioBufferSAB,
            ReadyCallback readyCallback, StatsCallback statsCallback);

    @JSBody(params = "handle", script = "return !!handle.ready;")
    private static native boolean isReady(JSObject handle);

    @JSBody(params = "handle", script = "var isUserInteraction = (navigator.userActivation && navigator.userActivation.isActive); if (!handle.audioContext) { return; } if (handle.audioContext.state === 'suspended') { if (isUserInteraction) { handle.audioContext.resume().then(function() { handle.registerAudioWorklet(); }).catch(function(e) { console.log('AudioContext was not able to resume. Exception was: ' + e); }); } } else if (handle.audioContext.state === 'running') { handle.registerAudioWorklet(); }")
    private static native void resume(JSObject handle);

    @JSBody(params = "handle", script = "if (handle.audioContext && handle.audioContext.state === 'running') { handle.audioContext.suspend(); }")
    private static native void suspend(JSObject handle);

    @JSBody(params = "handle", script = "return !!(handle.audioContext && handle.audioContext.state === 'running');")
    private static native boolean isRunning(JSObject handle);

    @JSBody(params = "handle", script = "if (handle.audioWorkletNode) { handle.audioWorkletNode.port.postMessage({ type: 'ResetStats' }); }")
    private static native void resetStats(JSObject handle);
}