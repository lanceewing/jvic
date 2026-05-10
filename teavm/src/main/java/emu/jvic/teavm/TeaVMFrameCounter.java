package emu.jvic.teavm;

import org.teavm.jso.JSBody;
import org.teavm.jso.typedarrays.Atomics;
import org.teavm.jso.typedarrays.Int32Array;
import org.teavm.jso.typedarrays.SharedArrayBuffer;

final class TeaVMFrameCounter {

    private final SharedArrayBuffer sharedArrayBuffer;
    private final Int32Array counter;

    TeaVMFrameCounter() {
        this(createSharedArrayBuffer(4));
    }

    TeaVMFrameCounter(SharedArrayBuffer sharedArrayBuffer) {
        this.sharedArrayBuffer = sharedArrayBuffer;
        this.counter = Int32Array.create(sharedArrayBuffer, 0, 1);
    }

    SharedArrayBuffer getSharedArrayBuffer() {
        return sharedArrayBuffer;
    }

    int get() {
        return Atomics.load(counter, 0);
    }

    void increment() {
        Atomics.add(counter, 0, 1);
    }

    void reset() {
        Atomics.store(counter, 0, 0);
    }

    @JSBody(params = "byteLength", script = "return new SharedArrayBuffer(byteLength);")
    private static native SharedArrayBuffer createSharedArrayBuffer(int byteLength);
}