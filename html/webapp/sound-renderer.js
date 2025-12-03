/** 
 * Trimmed down version of Paul Adenot's ringbuf.js JavaScript class, to 
 * include only the consuming side. The SoundRenderer AudioWorkletProcessor
 * uses it to read sound samples from. The JVic web worker puts the samples 
 * into the same SharedArrayBuffer by way of the GwtSoundGenerator class.
 * 
 * Original JS code: https://github.com/padenot/ringbuf.js/blob/main/js/ringbuf.js
 */
class RingBuffer {
    
    /**
     * Constructor for RingBuffer.
     * 
     * @param {*} sab The SharedArrayBuffer to use for storage. 
     */
    constructor(sab) {
        // Maximum usable size is 1<<32 - BYTES_PER_ELEMENT bytes in the ring
        // buffer for this version, easily changeable.
        // -4 for the write ptr (uint32_t offsets)
        // -4 for the read ptr (uint32_t offsets)
        // capacity counts the empty slot to distinguish between full and empty.
        var BYTES_PER_ELEMENT = 4;
        this._capacity = (sab.byteLength - 16) / BYTES_PER_ELEMENT;
        this.buf = sab;
        this.write_ptr = new Uint32Array(this.buf, 0, 1);
        this.read_ptr = new Uint32Array(this.buf, 4, 1);
        this.currentTime = new Float64Array(this.buf, 8, 1);
        this.storage = new Float32Array(this.buf, 16, this._capacity);
    }

    /**
     * Pops up to elements.length items from the ring buffer. If the ring buffer 
     * does not have that many items available, then it populates only as many as
     * are available.
     * 
     * @param {Float32Array} elements The array to pop the items into.
     * @param {number} [offset=0] Optional offset. Defaults to 0.
     * 
     * @return The actual number of items that were popped.
     */
    pop(elements, offset = 0) {
        const rd = Atomics.load(this.read_ptr, 0);
        const wr = Atomics.load(this.write_ptr, 0);

        if (wr === rd) {
            return 0;
        }

        const to_read = Math.min(this._available_read(rd, wr), elements.length);
        const first_part = Math.min(this._storage_capacity() - rd, to_read);
        const second_part = to_read - first_part;

        this._copy(this.storage, rd, elements, offset, first_part);
        this._copy(this.storage, 0, elements, offset + first_part, second_part);

        Atomics.store(this.read_ptr, 0, (rd + to_read) % this._storage_capacity());

        return to_read;
    }

    /**
     * @return The number of elements available for reading. This can be late, and
     * report less elements that is actually in the queue, when something has just
     * been enqueued.
     */
    availableRead() {
        const rd = Atomics.load(this.read_ptr, 0);
        const wr = Atomics.load(this.write_ptr, 0);
        return this._available_read(rd, wr);
    }
    
    /**
     * @return True if the ring buffer is empty false otherwise. This can be late
     * on the reader side: it can return true even if something has just been
     * pushed.
     */
    empty() {
        const rd = Atomics.load(this.read_ptr, 0);
        const wr = Atomics.load(this.write_ptr, 0);
        
        return wr === rd;
    }

    /**
     * @return True if the ring buffer is full, false otherwise. This can be late
     * on the write side: it can return true when something has just been popped.
     */
    full() {
        const rd = Atomics.load(this.read_ptr, 0);
        const wr = Atomics.load(this.write_ptr, 0);
        
        return (wr + 1) % this._storage_capacity() === rd;
    }

    /**
     * Sets the currentTime field to the given value.
     * 
     * @param {Number} currentTime The value to set the currentTime field to.
     */
    setCurrentTime(currentTime) {
        this.currentTime[0] = currentTime;
    }

    // private methods

    /**
     * @return Number of elements available for reading, given a read and write
     * pointer.
     * @private
     */
    _available_read(rd, wr) {
        return (wr + this._storage_capacity() - rd) % this._storage_capacity();
    }

    /**
     * @return The size of the storage for elements not accounting the space for
     * the index, counting the empty slot.
     * @private
     */
    _storage_capacity() {
        return this._capacity;
    }

    /**
     * Copy `size` elements from `input`, starting at offset `offset_input`, to
     * `output`, starting at offset `offset_output`.
     * 
     * @param {Float32Array} input The array to copy from
     * @param {Number} offset_input The index at which to start the copy
     * @param {Float32Array} output The array to copy to
     * @param {Number} offset_output The index at which to start copying the elements to
     * @param {Number} size The number of elements to copy
     */
    _copy(input, offset_input, output, offset_output, size) {
        for (let i = 0; i < size; i++) {
            output[offset_output + i] = input[offset_input + i];
        }
    }
}

/**
 * AudioWorkletProcessor implementation that processes the sample data produced
 * by the JVic VIC chip sound emulation. The samples are stored in the 
 * SharedArrayBuffer, already in the expected format. All the process() method
 * does is fill the output with up to 128 samples.
 */
class SoundRenderer extends AudioWorkletProcessor {
    
    // To output 22050 samples per second, 128 each call.
    static CALLS_PER_SECOND = (22050 / 128);
    
    // The number of calls since the last debug logging reset.
    callCount = 0;
    
    startTime = 0;
    
    deltaCount = 0;
    
    /**
     * Constructor for SoundRenderer.
     */
    constructor() {
        super();
        
        this.startTime = currentTime * 1000;
        
        // Set to true after the SharedArrayBuffer is received.
        this.ready = false;
        
        this.port.onmessage = this.onmessage.bind(this);
    }

    /**
     * Used to receive the SharedArrayBuffer from which it will read input data.
     * 
     * @param {MessageEvent} event 
     */
    onmessage(event) {
        // Receive the SharedArrayBuffer from the UI thread.
        const { audioBufferSAB } = event.data;
        
        this.sampleSharedQueue = new RingBuffer(audioBufferSAB);
        
        // Tells the AudioWorkletNode that we're ready now for sample data.
        this.port.postMessage({ready: true});
        
        // Let the process() method know that it can start reading.
        this.ready = true;
    }

    /** 
     * This method is invoked by the audio thread at the rate required to output 
     * at the configured sample rate. For JVic, there is no input from the audio
     * worklet node's perspective, i.e. there is nothing connected on the input
     * side of the node, so the inputs array will be empty. Instead the data to 
     * be set into the outputs array will come from the SharedArrayBuffer that 
     * the JVicWebWorker writes to when it is executing the emulation.
     * 
     * @param inputs An array of inputs, each item of which is, in turn, an array of channels. Each channel is a Float32Array containing 128 samples. Each sample value is in range of [-1 .. 1]. If there is no active node connected to the n-th input of the node, inputs[n] will be an empty array.
     * @param outputs An array of outputs that is similar to the inputs parameter in structure. It is intended to be filled during the execution of the process() method. Each of the output channels is filled with zeros by default — the processor will output silence unless the output arrays are modified.
     */
    process(inputs, outputs) {
        let timeThisCall = currentTime * 1000;
        
        // The inputs is ignored. We get up to samples from the ring buffer instead.
        // We have only one output, with one channel (mono), sample rate 22050.
        // Sample values are float values between -1 and 1.
        
        let logDebugOutput = false;
        
        // TODO: Debug output. Remove later on.
        if (this.callCount++ >= (SoundRenderer.CALLS_PER_SECOND * 5)) {
            this.callCount = 0;
            logDebugOutput = true;
        }
        
        if (this.ready) {
            
            // Set the audio context currentTime in the SharedArrayBuffer.
            this.sampleSharedQueue.setCurrentTime(currentTime);
        
            // This will read up to the length of the channel array, usually 128. If
            // there isn't that many samples, then it populates as much as it can and
            // leaves the rest at 0, which would be silence.
            this.sampleSharedQueue.pop(outputs[0][0]);
            
            this.deltaCount++;
            
            if (logDebugOutput) {
                console.log("Available to read = " + this.sampleSharedQueue.availableRead() + 
                            ", output array len = " + outputs[0][0].length +
                            ", currentTime = " + currentTime + 
                            ", average delta = " + ((timeThisCall - this.startTime) / this.deltaCount) +
                            ", output rate = " + (currentFrame / currentTime));
            }
        } else {
			if (logDebugOutput) {
				console.log("AudioWorkletProcessor not ready. SAB not received yet.");
			}
		}

        // Returning true tells audio thread we're still outputing.
        return true;
    }
}

registerProcessor("sound-renderer", SoundRenderer);
