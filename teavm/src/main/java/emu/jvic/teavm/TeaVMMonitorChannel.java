package emu.jvic.teavm;

import java.util.ArrayList;
import java.util.List;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.typedarrays.ArrayBuffer;

import emu.jvic.debug.MonitorChannel;

/**
 * The browser's monitor channel, on the main thread: commands are posted to the web
 * worker, where the monitor engine runs next to the machine (see
 * {@link TeaVMWorkerMonitor}), and its output comes back in MonitorOutput messages.
 * Also handles the worker's requests that need the page: downloads and the file
 * picker.
 */
final class TeaVMMonitorChannel implements MonitorChannel {

    /** Posts a message to the current worker, or returns false if there is none. */
    interface WorkerPoster {
        boolean post(String name, JSObject object);

        boolean postWithBuffer(String name, ArrayBuffer buffer, JSObject object);
    }

    @JSFunctor
    interface PickCallback extends JSObject {
        void picked(String name, ArrayBuffer buffer);
    }

    private final List<Listener> listeners = new ArrayList<>();
    private final WorkerPoster poster;

    TeaVMMonitorChannel(WorkerPoster poster) {
        this.poster = poster;
    }

    @Override
    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    @Override
    public void execute(String line) {
        if (!poster.post("MonitorCommand", createLineObject(line))) {
            output("No machine is running.\n", null);
        }
    }

    @Override
    public void requestStop() {
        poster.post("MonitorOpen", TeaVMWorkerInterop.createEmptyObject());
    }

    /** Output from the worker. */
    void output(String text, String prompt) {
        for (Listener listener : new ArrayList<>(listeners)) {
            if (!text.isEmpty()) {
                listener.onOutput(text);
            }
            if ((prompt != null) && !prompt.isEmpty()) {
                listener.onPrompt(prompt);
            }
        }
    }

    /** The worker asks for the file picker (the upload command). */
    void pickFile(String name) {
        openFilePicker(name, (pickedName, buffer) -> poster.postWithBuffer("MonitorUpload", buffer,
                createUploadObject(pickedName, buffer)));
    }

    @JSBody(params = "line", script = "return { line: line };")
    private static native JSObject createLineObject(String line);

    @JSBody(params = { "name", "buffer" }, script = "return { name: name, buffer: buffer };")
    private static native JSObject createUploadObject(String name, ArrayBuffer buffer);

    /** Offers a file as a download, with a Blob and an <a download> link. */
    @JSBody(params = { "name", "buffer" }, script = ""
            + "var blob = new Blob([buffer], { type: 'application/octet-stream' });"
            + "var url = URL.createObjectURL(blob);"
            + "var a = document.createElement('a');"
            + "a.href = url; a.download = name; a.style.display = 'none';"
            + "document.body.appendChild(a); a.click(); a.remove();"
            + "setTimeout(function() { URL.revokeObjectURL(url); }, 30000);")
    static native void download(String name, ArrayBuffer buffer);

    @JSBody(params = { "name", "callback" }, script = ""
            + "var input = document.createElement('input');"
            + "input.type = 'file'; input.style.display = 'none';"
            + "input.onchange = function() {"
            + "  var file = input.files && input.files[0];"
            + "  input.remove();"
            + "  if (!file) { return; }"
            + "  file.arrayBuffer().then(function(b) { callback(name ? name : file.name, b); });"
            + "};"
            + "document.body.appendChild(input); input.click();")
    private static native void openFilePicker(String name, PickCallback callback);
}
