package emu.jvic.teavm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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

    @JSFunctor
    interface MonitorCallback extends JSObject {
        void monitor(String line, JSObject resolve);
    }

    @JSFunctor
    interface TimerCallback extends JSObject {
        void fire();
    }

    /** A window.jvic.monitor() call waiting for its output. */
    private static final class Request {
        final String line;
        final JSObject resolve;
        final StringBuilder text = new StringBuilder();
        boolean sent;
        /** Whether any output has arrived for it yet. */
        boolean answered;

        Request(String line, JSObject resolve) {
            this.line = line;
            this.resolve = resolve;
        }
    }

    /** How long a request waits for more output after the last, when no prompt comes. */
    private static final int QUIET_MILLIS = 500;

    /**
     * How long a request waits for its first output: longer, as the first command
     * also loads the file store and stops the machine.
     */
    private static final int FIRST_OUTPUT_MILLIS = 5000;

    private final List<Listener> listeners = new ArrayList<>();
    private final WorkerPoster poster;
    private final Deque<Request> requests = new ArrayDeque<>();
    private int timerGeneration;

    TeaVMMonitorChannel(WorkerPoster poster) {
        this.poster = poster;
    }

    /**
     * Installs window.jvic.monitor(line), for the browser's developer tools: it runs a
     * monitor command and returns a Promise of its output. Calls are run one at a
     * time, in order. A command that leaves the machine stopped resolves when its
     * prompt arrives; one that resumes it (x, g, z...) resolves once output has been
     * quiet for half a second.
     */
    void installJavaScriptHook() {
        installHook((line, resolve) -> {
            requests.add(new Request(line, resolve));
            sendNextRequest();
        });
    }

    private void sendNextRequest() {
        Request head = requests.peek();
        if ((head == null) || head.sent) {
            return;
        }
        head.sent = true;
        if (!poster.post("MonitorCommand", createLineObject(head.line))) {
            requests.poll();
            resolve(head.resolve, "No machine is running.\n");
            sendNextRequest();
            return;
        }
        restartQuietTimer();
    }

    private void restartQuietTimer() {
        int generation = ++timerGeneration;
        Request head = requests.peek();
        int millis = ((head != null) && head.answered) ? QUIET_MILLIS : FIRST_OUTPUT_MILLIS;
        setTimeout(() -> {
            if ((generation == timerGeneration) && !requests.isEmpty()) {
                finishRequest();
            }
        }, millis);
    }

    private void finishRequest() {
        Request done = requests.poll();
        if (done != null) {
            resolve(done.resolve, done.text.toString());
        }
        sendNextRequest();
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
        Request head = requests.peek();
        if ((head != null) && head.sent) {
            head.answered = true;
            head.text.append(text);
            if ((prompt != null) && !prompt.isEmpty()) {
                finishRequest();
            } else {
                restartQuietTimer();
            }
        }
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

    @JSBody(params = "callback", script = ""
            + "window.jvic = window.jvic || {};"
            + "window.jvic.monitor = function(line) {"
            + "  return new Promise(function(resolve) { callback(String(line), resolve); });"
            + "};")
    private static native void installHook(MonitorCallback callback);

    @JSBody(params = { "resolve", "text" }, script = "resolve(text);")
    private static native void resolve(JSObject resolve, String text);

    @JSBody(params = { "callback", "millis" }, script = "setTimeout(callback, millis);")
    private static native void setTimeout(TimerCallback callback, int millis);

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
