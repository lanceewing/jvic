package emu.jvic.teavm;

import java.util.ArrayList;
import java.util.List;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.typedarrays.ArrayBuffer;
import org.teavm.jso.typedarrays.Uint8Array;

import emu.jvic.Machine;
import emu.jvic.debug.MonitorChannel;
import emu.jvic.monitor.InMemoryMonitorFileSystem;
import emu.jvic.monitor.MonitorFileException;
import emu.jvic.monitor.MonitorSession;

/**
 * The monitor's worker side in the TeaVM build: the {@link MonitorSession} that runs
 * next to the machine, and its in-memory file store, which is preloaded from OPFS
 * (JVic/Monitor) the first time the monitor is used and written back to it in the
 * background. Nothing here exists until the monitor is first used, so a user who
 * never opens it pays nothing.
 *
 * <p>Messages from the main thread: MonitorOpen, MonitorCommand {line}, MonitorUpload
 * {name, buffer} and MonitorStartup {moncommands, monlog, initbreak}. Messages to it:
 * MonitorOutput {text, prompt}, MonitorDownload {name, buffer}, MonitorPickFile {name}
 * and MonitorQuit.
 */
final class TeaVMWorkerMonitor {

    /** At most this much output is posted per animation frame; the rest is dropped. */
    private static final int MAX_OUTPUT_PER_FRAME = 256 * 1024;

    /** The worker's warp setting, which the monitor's warp command changes. */
    interface Warp {
        boolean isWarp();

        void setWarp(boolean warp);
    }

    @JSFunctor
    interface PreloadCallback extends JSObject {
        void done(JSObject entries);
    }

    @JSFunctor
    interface FetchCallback extends JSObject {
        void done(ArrayBuffer buffer, String error);
    }

    private final Warp warp;
    private final InMemoryMonitorFileSystem fileSystem = new InMemoryMonitorFileSystem();
    private final StringBuilder output = new StringBuilder();
    private final List<Runnable> waitingForStore = new ArrayList<>();

    private MonitorSession session;
    private Machine machine;
    private JSObject opfsState;
    private boolean storeReady;
    private boolean outputDropped;
    private String prompt;

    /** True while startup options wait for the store, so the machine must not run yet. */
    private boolean holdMachine;

    TeaVMWorkerMonitor(Warp warp) {
        this.warp = warp;
    }

    /** Whether the monitor has been used, so the worker must use its debug loop. */
    boolean isActive() {
        return session != null;
    }

    boolean isHoldingMachine() {
        return holdMachine;
    }

    /** Called when the worker has started a machine. */
    void onMachineStarted(Machine machine) {
        this.machine = machine;
        if ((session != null) && !holdMachine) {
            session.attach(machine);
        }
    }

    /**
     * Handles a monitor message from the main thread.
     *
     * @return false if the message isn't a monitor message.
     */
    boolean handleMessage(String type, JSObject event) {
        switch (type) {
            case "MonitorOpen":
                ensureSession();
                whenStoreReady(() -> session.requestStop());
                return true;
            case "MonitorCommand": {
                String line = TeaVMWorkerInterop.getNestedString(event, "line");
                ensureSession();
                whenStoreReady(() -> session.execute(line));
                return true;
            }
            case "MonitorUpload": {
                String name = TeaVMWorkerInterop.getNestedString(event, "name");
                byte[] data = toBytes((ArrayBuffer) TeaVMWorkerInterop.getNestedObject(event, "buffer"));
                ensureSession();
                whenStoreReady(() -> store(name, data));
                return true;
            }
            case "MonitorStartup":
                startup(TeaVMWorkerInterop.getNestedString(event, "moncommands"),
                        TeaVMWorkerInterop.getNestedString(event, "monlog"),
                        TeaVMWorkerInterop.getNestedString(event, "initbreak"));
                return true;
            default:
                return false;
        }
    }

    /**
     * Called once per animation frame, after any emulation: lets the session handle
     * a stopped machine, posts the frame's output, and writes changed files to OPFS.
     */
    void onAnimationFrame() {
        if (session == null) {
            return;
        }
        if ((machine != null) && machine.isDebuggerStopped() && !holdMachine) {
            session.onMachineStopped();
        }
        flushOutput();
        flushStore();
    }

    /** Called when the machine completes a frame, while the monitor is active. */
    void onFrame() {
        if (session != null) {
            session.onFrame();
        }
    }

    // ------------------------------------------------------------------------------

    private void ensureSession() {
        if (session != null) {
            return;
        }
        session = new MonitorSession(new MonitorSession.Platform() {
            @Override
            public void resumeEmulation() {
                // The worker's loop sees that the machine is no longer stopped.
            }

            @Override
            public void quit() {
                TeaVMWorkerGlobalScope.postObject("MonitorQuit", TeaVMWorkerInterop.createEmptyObject());
            }

            @Override
            public boolean isWarp() {
                return warp.isWarp();
            }

            @Override
            public void setWarp(boolean on) {
                warp.setWarp(on);
            }

            @Override
            public boolean pickFile(String name) {
                TeaVMWorkerGlobalScope.postObject("MonitorPickFile", createNameObject((name != null) ? name : ""));
                return true;
            }

            @Override
            public boolean fetchUrl(String url, String name) {
                String storeName = ((name != null) && !name.isEmpty()) ? name : lastPart(url);
                fetch(url, (buffer, error) -> {
                    if (buffer != null) {
                        store(storeName, toBytes(buffer));
                    } else {
                        session.print("Cannot fetch " + url + ": " + error + "\n");
                    }
                });
                return true;
            }

            @Override
            public boolean isFileStoreHidden() {
                // Files live in the browser's store until downloaded.
                return true;
            }

            @Override
            public boolean toggleDomConsole() {
                TeaVMWorkerGlobalScope.postObject("MonitorDomConsole", TeaVMWorkerInterop.createEmptyObject());
                return true;
            }

            @Override
            public boolean downloadFile(String name, byte[] data) {
                TeaVMWorkerGlobalScope.postObject("MonitorDownload", createDownloadObject(name, toArrayBuffer(data)));
                return true;
            }
        });
        session.setFileSystem(fileSystem);
        session.addListener(new MonitorChannel.Listener() {
            @Override
            public void onOutput(String text) {
                if (output.length() < MAX_OUTPUT_PER_FRAME) {
                    output.append(text);
                } else {
                    outputDropped = true;
                }
            }

            @Override
            public void onPrompt(String newPrompt) {
                prompt = newPrompt;
            }
        });
        opfsState = createState();
        preload(entries -> {
            int count = length(entries);
            for (int i = 0; i < count; i++) {
                ArrayBuffer buffer = entryBuffer(entries, i);
                fileSystem.preload(entryPath(entries, i), (buffer != null) ? toBytes(buffer) : null);
            }
            storeReady = true;
            List<Runnable> waiting = new ArrayList<>(waitingForStore);
            waitingForStore.clear();
            for (Runnable action : waiting) {
                action.run();
            }
        });
        if ((machine != null) && !holdMachine) {
            session.attach(machine);
        }
    }

    private void whenStoreReady(Runnable action) {
        if (storeReady) {
            action.run();
        } else {
            waitingForStore.add(action);
        }
    }

    /** The URL startup options: holds the machine until the store (and URL) is ready. */
    private void startup(String monCommands, String monLog, String initBreakText) {
        int initBreak = MonitorSession.INITBREAK_NONE;
        if (!initBreakText.isEmpty()) {
            try {
                initBreak = MonitorSession.parseInitBreak(initBreakText);
            } catch (IllegalArgumentException e) {
                TeaVMWorkerGlobalScope.logToJSConsole("JVic monitor: " + e.getMessage());
            }
        }
        String commands = monCommands.isEmpty() ? null : monCommands;
        String log = monLog.isEmpty() ? null : monLog;
        if ((commands == null) && (log == null) && (initBreak == MonitorSession.INITBREAK_NONE)) {
            return;
        }
        holdMachine = true;
        ensureSession();
        int finalInitBreak = initBreak;
        whenStoreReady(() -> {
            if ((commands != null) && (commands.startsWith("http://") || commands.startsWith("https://"))) {
                String name = lastPart(commands);
                fetch(commands, (buffer, error) -> {
                    if (buffer != null) {
                        fileSystem.write(name, toBytes(buffer));
                    } else {
                        TeaVMWorkerGlobalScope.logToJSConsole("JVic monitor: cannot fetch " + commands + ": " + error);
                    }
                    releaseMachine(name, log, finalInitBreak);
                });
            } else {
                releaseMachine(commands, log, finalInitBreak);
            }
        });
    }

    private void releaseMachine(String commands, String log, int initBreak) {
        session.setStartup(commands, log, initBreak);
        holdMachine = false;
        if (machine != null) {
            session.attach(machine);
        }
    }

    private void store(String name, byte[] data) {
        try {
            fileSystem.write(name, data);
            session.print("Stored \"" + name + "\" (" + data.length + " bytes).\n");
        } catch (MonitorFileException e) {
            session.print("Cannot store \"" + name + "\": " + e.getMessage() + "\n");
        }
    }

    private void flushOutput() {
        if (outputDropped) {
            output.append("\n[jvic: monitor output was cut short; there was too much in one frame]\n");
            outputDropped = false;
        }
        if ((output.length() == 0) && (prompt == null)) {
            return;
        }
        TeaVMWorkerGlobalScope.postObject("MonitorOutput",
                createOutputObject(output.toString(), (prompt != null) ? prompt : ""));
        output.setLength(0);
        prompt = null;
    }

    /** Writes the store's changes to OPFS, in order, through one promise chain. */
    private void flushStore() {
        for (String path : fileSystem.takeDirtyPaths()) {
            if (fileSystem.isDirectory(path)) {
                queueMkdir(opfsState, path);
            } else if (fileSystem.exists(path)) {
                queueWrite(opfsState, path, toArrayBuffer(fileSystem.read(path)));
            } else {
                queueRemove(opfsState, path);
            }
        }
    }

    private static String lastPart(String url) {
        String path = url;
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        String name = path.substring(path.lastIndexOf('/') + 1);
        return name.isEmpty() ? "download.bin" : name;
    }

    static byte[] toBytes(ArrayBuffer buffer) {
        if (buffer == null) {
            return new byte[0];
        }
        Uint8Array source = Uint8Array.create(buffer);
        byte[] data = new byte[buffer.getByteLength()];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) source.get(i);
        }
        return data;
    }

    static ArrayBuffer toArrayBuffer(byte[] data) {
        ArrayBuffer buffer = ArrayBuffer.create(data.length);
        Uint8Array target = Uint8Array.create(buffer);
        for (int i = 0; i < data.length; i++) {
            target.set(i, (short) (data[i] & 0xFF));
        }
        return buffer;
    }

    // ------------------------------------------------------------------------------
    // JavaScript.
    // ------------------------------------------------------------------------------

    @JSBody(params = { "text", "prompt" }, script = "return { text: text, prompt: prompt };")
    private static native JSObject createOutputObject(String text, String prompt);

    @JSBody(params = "name", script = "return { name: name };")
    private static native JSObject createNameObject(String name);

    @JSBody(params = { "name", "buffer" }, script = "return { name: name, buffer: buffer };")
    private static native JSObject createDownloadObject(String name, ArrayBuffer buffer);

    @JSBody(params = "entries", script = "return entries.length;")
    private static native int length(JSObject entries);

    @JSBody(params = { "entries", "i" }, script = "return entries[i].path;")
    private static native String entryPath(JSObject entries, int i);

    @JSBody(params = { "entries", "i" }, script = "return entries[i].buffer;")
    private static native ArrayBuffer entryBuffer(JSObject entries, int i);

    @JSBody(script = "return { chain: Promise.resolve() };")
    private static native JSObject createState();

    @JSBody(params = "callback", script = ""
            + "var out = [];"
            + "var walk = function(dir, prefix) {"
            + "  var it = dir.entries();"
            + "  var step = function() {"
            + "    return it.next().then(function(r) {"
            + "      if (r.done) { return null; }"
            + "      var name = r.value[0], handle = r.value[1], path = prefix + '/' + name;"
            + "      if (handle.kind === 'directory') {"
            + "        out.push({ path: path, buffer: null });"
            + "        return walk(handle, path).then(step);"
            + "      }"
            + "      return handle.getFile().then(function(f) { return f.arrayBuffer(); })"
            + "        .then(function(b) { out.push({ path: path, buffer: b }); return step(); });"
            + "    });"
            + "  };"
            + "  return step();"
            + "};"
            + "if (!(self.navigator && self.navigator.storage && self.navigator.storage.getDirectory)) {"
            + "  callback(out); return;"
            + "}"
            + "self.navigator.storage.getDirectory()"
            + "  .then(function(root) { return root.getDirectoryHandle('JVic', { create: true }); })"
            + "  .then(function(j) { return j.getDirectoryHandle('Monitor', { create: true }); })"
            + "  .then(function(m) { return walk(m, ''); })"
            + "  .then(function() { callback(out); })"
            + "  .catch(function(e) { console.error('JVic monitor OPFS preload failed', e); callback(out); });")
    private static native void preload(PreloadCallback callback);

    /** The OPFS directory for a store path's parent, created as needed, then fn(dir, name). */
    private static final String WITH_PARENT = ""
            + "var withParent = function(path, fn) {"
            + "  var parts = path.split('/').filter(function(p) { return p.length > 0; });"
            + "  var name = parts.pop();"
            + "  var p = self.navigator.storage.getDirectory()"
            + "    .then(function(root) { return root.getDirectoryHandle('JVic', { create: true }); })"
            + "    .then(function(j) { return j.getDirectoryHandle('Monitor', { create: true }); });"
            + "  parts.forEach(function(part) {"
            + "    p = p.then(function(d) { return d.getDirectoryHandle(part, { create: true }); });"
            + "  });"
            + "  return p.then(function(d) { return fn(d, name); });"
            + "};";

    @JSBody(params = { "state", "path", "buffer" }, script = WITH_PARENT
            + "if (!(self.navigator && self.navigator.storage && self.navigator.storage.getDirectory)) { return; }"
            + "state.chain = state.chain.catch(function() {}).then(function() {"
            + "  return withParent(path, function(dir, name) {"
            + "    return dir.getFileHandle(name, { create: true }).then(function(h) {"
            + "      return h.createWritable().then(function(w) {"
            + "        return w.write(buffer).then(function() { return w.close(); });"
            + "      });"
            + "    });"
            + "  });"
            + "}).catch(function(e) { console.error('JVic monitor OPFS write failed', path, e); });")
    private static native void queueWrite(JSObject state, String path, ArrayBuffer buffer);

    @JSBody(params = { "state", "path" }, script = WITH_PARENT
            + "if (!(self.navigator && self.navigator.storage && self.navigator.storage.getDirectory)) { return; }"
            + "state.chain = state.chain.catch(function() {}).then(function() {"
            + "  return withParent(path, function(dir, name) { return dir.getDirectoryHandle(name, { create: true }); });"
            + "}).catch(function(e) { console.error('JVic monitor OPFS mkdir failed', path, e); });")
    private static native void queueMkdir(JSObject state, String path);

    @JSBody(params = { "state", "path" }, script = WITH_PARENT
            + "if (!(self.navigator && self.navigator.storage && self.navigator.storage.getDirectory)) { return; }"
            + "state.chain = state.chain.catch(function() {}).then(function() {"
            + "  return withParent(path, function(dir, name) { return dir.removeEntry(name); });"
            + "}).catch(function(e) { console.error('JVic monitor OPFS remove failed', path, e); });")
    private static native void queueRemove(JSObject state, String path);

    @JSBody(params = { "url", "callback" }, script = ""
            + "fetch(url).then(function(r) {"
            + "  if (!r.ok) { throw new Error('HTTP ' + r.status); }"
            + "  return r.arrayBuffer();"
            + "}).then(function(b) { callback(b, null); }, function(e) { callback(null, String(e && e.message ? e.message : e)); });")
    private static native void fetch(String url, FetchCallback callback);
}
