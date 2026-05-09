package emu.jvic.teavm;

import org.teavm.jso.JSBody;
import org.teavm.jso.typedarrays.Atomics;
import org.teavm.jso.typedarrays.Float32Array;
import org.teavm.jso.typedarrays.Float64Array;
import org.teavm.jso.typedarrays.Int32Array;
import org.teavm.jso.typedarrays.SharedArrayBuffer;

/**
 * TeaVM port of the GWT shared single-producer/single-consumer float ring buffer.
 */
public class TeaVMSharedQueue {

    private static final int FLOAT_BYTES = 4;
    private static final int HEADER_BYTES = 16;

    private final int capacity;
    private final SharedArrayBuffer sharedArrayBuffer;
    private final Int32Array writePtr;
    private final Int32Array readPtr;
    private final Float64Array currentTime;
    private final Float32Array storage;

    public static SharedArrayBuffer getStorageForCapacity(int capacity) {
        int bytes = HEADER_BYTES + (capacity + 1) * FLOAT_BYTES;
        return createSharedArrayBuffer(bytes);
    }

    public TeaVMSharedQueue(SharedArrayBuffer sharedArrayBuffer) {
        this.sharedArrayBuffer = sharedArrayBuffer;
        this.capacity = (sharedArrayBuffer.getByteLength() - HEADER_BYTES) / FLOAT_BYTES;
        this.writePtr = Int32Array.create(sharedArrayBuffer, 0, 1);
        this.readPtr = Int32Array.create(sharedArrayBuffer, 4, 1);
        this.currentTime = Float64Array.create(sharedArrayBuffer, 8, 1);
        this.storage = Float32Array.create(sharedArrayBuffer, HEADER_BYTES, this.capacity);
    }

    public SharedArrayBuffer getSharedArrayBuffer() {
        return sharedArrayBuffer;
    }

    public int push(Float32Array elements) {
        int read = Atomics.load(readPtr, 0);
        int write = Atomics.load(writePtr, 0);

        if (((write + 1) % storageCapacity()) == read) {
            return 0;
        }

        int toWrite = Math.min(availableWrite(read, write), elements.getLength());
        int firstPart = Math.min(storageCapacity() - write, toWrite);
        int secondPart = toWrite - firstPart;

        copy(elements, 0, storage, write, firstPart);
        copy(elements, firstPart, storage, 0, secondPart);

        Atomics.store(writePtr, 0, (write + toWrite) % storageCapacity());
        return toWrite;
    }

    public int pop(Float32Array elements) {
        int read = Atomics.load(readPtr, 0);
        int write = Atomics.load(writePtr, 0);

        if (write == read) {
            return 0;
        }

        int toRead = Math.min(availableRead(read, write), elements.getLength());
        int firstPart = Math.min(storageCapacity() - read, toRead);
        int secondPart = toRead - firstPart;

        copy(storage, read, elements, 0, firstPart);
        copy(storage, 0, elements, firstPart, secondPart);

        Atomics.store(readPtr, 0, (read + toRead) % storageCapacity());
        return toRead;
    }

    public boolean isEmpty() {
        return Atomics.load(writePtr, 0) == Atomics.load(readPtr, 0);
    }

    public int capacity() {
        return capacity - 1;
    }

    public int availableRead() {
        return availableRead(Atomics.load(readPtr, 0), Atomics.load(writePtr, 0));
    }

    public double getCurrentTime() {
        return currentTime.get(0);
    }

    private int availableRead(int read, int write) {
        return (write + storageCapacity() - read) % storageCapacity();
    }

    private int availableWrite(int read, int write) {
        return capacity() - availableRead(read, write);
    }

    private int storageCapacity() {
        return capacity;
    }

    private void copy(Float32Array input, int inputOffset, Float32Array output, int outputOffset, int size) {
        for (int index = 0; index < size; index++) {
            output.set(outputOffset + index, input.get(inputOffset + index));
        }
    }

    @JSBody(params = "byteLength", script = "return new SharedArrayBuffer(byteLength);")
    private static native SharedArrayBuffer createSharedArrayBuffer(int byteLength);
}