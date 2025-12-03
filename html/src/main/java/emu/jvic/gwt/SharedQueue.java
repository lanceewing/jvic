package emu.jvic.gwt;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.typedarrays.shared.Float32Array;

/** 
 * GWT Queue based on Paul Adenot's ringbuf.js JavaScript class. Paul works for
 * Mozilla on Firefox and wrote the RingBuffer JS class as a way to use the 
 * SharedArrayBuffer to send data between two JS thread (e.g. two workers, or 
 * a worker and the UI thread) without using the postMessage mechanism, which is
 * exactly what we need for the JVic GWT platform's sound sample queue.
 * 
 * Read here: https://blog.paul.cx/post/a-wait-free-spsc-ringbuffer-for-the-web/
 * 
 * Original JS code: https://github.com/padenot/ringbuf.js/blob/main/js/ringbuf.js
 */
public class SharedQueue {

    /** 
     * Allocate the SharedArrayBuffer for a Queue, based on the capacity required.
     * 
     * @param capacity The number of elements the queue will be able to hold.
     * 
     * @return A SharedArrayBuffer of the right size.
     */
    public native static JavaScriptObject getStorageForCapacity(int capacity)/*-{
        // This class only supports Float32Array, which has 4 bytes per element. The
        // extra 8 bytes are for the write and read pointers, so that they also are
        // shared by both ends.
        var BYTES_PER_ELEMENT = 4;
        var bytes = 16 + (capacity + 1) * BYTES_PER_ELEMENT;
        return new SharedArrayBuffer(bytes);
    }-*/;
    
    /**
     * Constructor for SharedQueue.
     * 
     * @param sab SharedArrayBuffer to use for SharedQueue. Obtained by calling SharedQueue.getStorageForCapacity.
     */
    public SharedQueue(JavaScriptObject sab) {
        initialise(sab);
    }
    
    private native void initialise(JavaScriptObject sab)/*-{
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
    }-*/;
    
    /**
     * Returns the SharedArrayBuffer that this SharedQueue is using for storage.
     * 
     * @return The SharedArrayBuffer that this SharedQueue is using for storage.
     */
    public native JavaScriptObject getSharedArrayBuffer()/*-{
        return this.buf;
    }-*/;

    /**
     * Push elements to the ring buffer.
     * 
     * @param elements An array of the same type used within the SharedQueue's storage.
     * 
     * @return the number of elements written to the queue.
     */
    public native int push(Float32Array elements)/*-{
        var rd = Atomics.load(this.read_ptr, 0);
        var wr = Atomics.load(this.write_ptr, 0);

        // Queue is full.
        if ((wr + 1) % (this.@emu.jvic.gwt.SharedQueue::_storage_capacity()()) === rd) {
            return 0;
        }
        
        var len = elements.length;
        var to_write = Math.min(this.@emu.jvic.gwt.SharedQueue::_available_write(II)(rd, wr), len);
        var first_part = Math.min((this.@emu.jvic.gwt.SharedQueue::_storage_capacity()()) - wr, to_write);
        var second_part = to_write - first_part;

        // Handles wrapping around in the buffer.
        this.@emu.jvic.gwt.SharedQueue::_copy(Lcom/google/gwt/typedarrays/shared/Float32Array;ILcom/google/gwt/typedarrays/shared/Float32Array;II)(
            elements, 0, this.storage, wr, first_part
        );
        this.@emu.jvic.gwt.SharedQueue::_copy(Lcom/google/gwt/typedarrays/shared/Float32Array;ILcom/google/gwt/typedarrays/shared/Float32Array;II)(
            elements, 0 + first_part, this.storage, 0, second_part
        );

        // publish the enqueued data to the other side
        Atomics.store(
            this.write_ptr,
            0,
            (wr + to_write) % (this.@emu.jvic.gwt.SharedQueue::_storage_capacity()())
        );
        
        return to_write;
    }-*/;
    
    /**
     * Read up to elements.length elements from the ring buffer. 'elements' should
     * be of the same type as is used internally by the SharedQueue for storage.
     * 
     * @param elements An array in which the elements read from the queue will be written.
     * 
     * @return The number of elements read from the queue.
     */
    public native int pop(Float32Array elements)/*-{
        var rd = Atomics.load(this.read_ptr, 0);
        var wr = Atomics.load(this.write_ptr, 0);
    
        // Queue is empty. Nothing to read.
        if (wr === rd) {
          return 0;
        }
    
        var len = elements.length;
        var to_read = Math.min(this.@emu.jvic.gwt.SharedQueue::_available_read(II)(rd, wr), len);

        var first_part = Math.min((this.@emu.jvic.gwt.SharedQueue::_storage_capacity()()) - rd, to_read);
        var second_part = to_read - first_part;
    
        this.@emu.jvic.gwt.SharedQueue::_copy(Lcom/google/gwt/typedarrays/shared/Float32Array;ILcom/google/gwt/typedarrays/shared/Float32Array;II)(
            this.storage, rd, elements, 0, first_part
        );
        this.@emu.jvic.gwt.SharedQueue::_copy(Lcom/google/gwt/typedarrays/shared/Float32Array;ILcom/google/gwt/typedarrays/shared/Float32Array;II)(
            this.storage, 0, elements, 0 + first_part, second_part
        );

        Atomics.store(this.read_ptr, 0, (rd + to_read) % (this.@emu.jvic.gwt.SharedQueue::_storage_capacity()()));
    
        return to_read;
    }-*/;
    
    /**
     * Returns {@code true} if this queue contains no elements.
     * 
     * @return {@code true} if the queue is empty; {@code false} otherwise. This can 
     * be late on the reader side: it can return true even if something has just been
     * pushed.
     */
    public native boolean isEmpty()/*-{
        var rd = Atomics.load(this.read_ptr, 0);
        var wr = Atomics.load(this.write_ptr, 0);

        return wr === rd;
    }-*/;

    /**
     * @return The usable capacity for the ring buffer: the number of elements
     * that can be stored.
     */
    public native boolean capacity()/*-{
        return this._capacity - 1;
    }-*/;
    
    /**
     * @return The number of elements available for reading. This can be late, and
     * report less elements that is actually in the queue, when something has just
     * been enqueued.
     */
    public native int availableRead()/*-{
        var rd = Atomics.load(this.read_ptr, 0);
        var wr = Atomics.load(this.write_ptr, 0);
        return this.@emu.jvic.gwt.SharedQueue::_available_read(II)(rd, wr);
    }-*/;
    
    /**
     * @return The currently stored value for the currentTime field.
     */
    public native double getCurrentTime()/*-{
        return this.currentTime[0];
    }-*/;
    
    /**
     * @return Number of elements available for reading, given a read and write
     * pointer.
     */
    private native int _available_read(int rd, int wr)/*-{
        return (wr + (this.@emu.jvic.gwt.SharedQueue::_storage_capacity()()) - rd) % (this.@emu.jvic.gwt.SharedQueue::_storage_capacity()());
    }-*/;
    
    /**
     * @return Number of elements available from writing, given a read and write
     * pointer.
     */
    private native int _available_write(int rd, int wr)/*-{
        return (this.@emu.jvic.gwt.SharedQueue::capacity()()) - (this.@emu.jvic.gwt.SharedQueue::_available_read(II)(rd, wr));
    }-*/;

    /**
     * @return The size of the storage for elements not accounting the space for
     * the index, counting the empty slot.
     * @private
     */
    private native int _storage_capacity()/*-{
        return this._capacity;
    }-*/;
    
    /**
     * Copy `size` elements from `input`, starting at offset `offset_input`, to
     * `output`, starting at offset `offset_output`.
     * 
     * @param input The array to copy from
     * @param offset_input The index at which to start the copy
     * @param output The array to copy to
     * @param offset_output The index at which to start copying the elements to
     * @param size The number of elements to copy
     */
    private native void _copy(Float32Array input, int offset_input, Float32Array output, int offset_output, int size)/*-{
        for (var i = 0; i < size; i++) {
            output[offset_output + i] = input[offset_input + i];
        }
    }-*/;
}
