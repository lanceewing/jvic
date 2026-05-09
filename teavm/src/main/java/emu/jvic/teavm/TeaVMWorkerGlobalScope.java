package emu.jvic.teavm;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

final class TeaVMWorkerGlobalScope {

    private TeaVMWorkerGlobalScope() {
    }

    @JSFunctor
    interface AnimationFrameCallback extends JSObject {
        void handle(double timestamp);
    }

    @JSBody(params = "callback", script = "self.onmessage = function(event) { try { callback(event.data); } catch (error) { var message = 'Worker message failure'; var nl = String.fromCharCode(10); if (event && event.data && event.data.name) { message += ' during ' + event.data.name; } if (error && error.message) { message += nl + error.message; } if (error && error.stack) { message += nl + error.stack; } else if (error) { message += nl + String(error); } try { self.postMessage({ name: 'WorkerError', object: { message: message } }); } catch (postMessageError) { } throw error; } };")
    static native void setOnMessage(TeaVMWorkerInterop.WorkerMessageCallback callback);

    @JSBody(params = { "name", "object" }, script = "self.postMessage({name: name, object: object});")
    static native void postObject(String name, JSObject object);

    @JSBody(params = "callback", script = "if (self.requestAnimationFrame) { self.requestAnimationFrame(callback); } else { setTimeout(function() { callback(self.performance ? self.performance.now() : Date.now()); }, 0); }")
    static native void requestAnimationFrame(AnimationFrameCallback callback);

    @JSBody(script = "return self.performance ? self.performance.now() : Date.now();")
    static native double getPerformanceNowTimestamp();

    @JSBody(params = "message", script = "console.log(message);")
    static native void logToJSConsole(String message);
}