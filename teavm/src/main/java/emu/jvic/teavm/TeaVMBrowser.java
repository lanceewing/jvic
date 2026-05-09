package emu.jvic.teavm;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.typedarrays.ArrayBuffer;

final class TeaVMBrowser {

    private TeaVMBrowser() {
    }

    @JSFunctor
    interface SimpleCallback extends JSObject {
        void handle();
    }

    @JSFunctor
    interface AnimationFrameCallback extends JSObject {
        void handle(double timestamp);
    }

    @JSFunctor
    interface OpenFileBinaryCallback extends JSObject {
        void complete(boolean success, String fileName, String binaryData);
    }

    @JSFunctor
    interface BinaryResourceArrayBufferCallback extends JSObject {
        void complete(ArrayBuffer arrayBuffer, int status, String responseUrl, String errorMessage);
    }

    @JSFunctor
    interface DialogConfirmCallback extends JSObject {
        void complete(boolean confirmed);
    }

    @JSFunctor
    interface DialogPromptCallback extends JSObject {
        void complete(boolean accepted, String value);
    }

    @JSFunctor
    interface DialogAlertCallback extends JSObject {
        void complete(boolean accepted, String value);
    }

    @JSBody(script = "return window.location.pathname || '';")
    static native String getPath();

    @JSBody(script = "return window.location.hash || '';")
    static native String getHash();

    @JSBody(script = "return window.location.href || '';")
    static native String getHref();

    @JSBody(script = "return window.location.hostname || '';")
    static native String getHostName();

    @JSBody(script = "return window.location.protocol || '';")
    static native String getProtocol();

    @JSBody(script = "return window.location.host || '';")
    static native String getHost();

    @JSBody(params = "name", script = "return new URLSearchParams(window.location.search).get(name);")
    static native String getQueryParameter(String name);

    @JSBody(params = "url", script = "window.history.pushState(url, '', url);")
    static native void pushState(String url);

    @JSBody(params = "url", script = "window.history.replaceState(url, '', url);")
    static native void replaceState(String url);

    @JSBody(script = "var url = new URL(window.location.href); url.pathname = '/'; url.hash = ''; url.searchParams.delete('url'); return url.toString();")
    static native String buildCleanUrl();

    @JSBody(script = "window.location.reload();")
    static native void reload();

    @JSBody(params = "url", script = "try { new URL(url); return true; } catch (err) { console.log('Sorry, the program URL does not appear to be well formed.'); return false; }")
    static native boolean isValidUrl(String url);

    @JSBody(params = { "callback" }, script = "window.addEventListener('popstate', function() { callback(); });")
    static native void registerPopStateListener(SimpleCallback callback);

    @JSBody(params = { "callback" }, script = "return window.requestAnimationFrame(callback);")
    static native int requestAnimationFrame(AnimationFrameCallback callback);

    @JSBody(params = { "message" }, script = "return window.confirm(message);")
    static native boolean confirm(String message);

    @JSBody(params = { "message", "initialValue" }, script = "return window.prompt(message, initialValue != null ? initialValue : '');")
    static native String prompt(String message, String initialValue);

    @JSBody(params = { "message" }, script = "window.alert(message);")
    static native void alert(String message);

    @JSBody(params = "message", script = "console.log(message);")
    static native void logToConsole(String message);

        @JSBody(script = "if (!window.__jvicDialog && typeof window.Dialog === 'function') { window.__jvicDialog = new window.Dialog(); } return window.__jvicDialog || null;")
        static native JSObject getDialogInstance();

        @JSBody(params = { "dialog", "message", "callback" }, script = "if (!dialog || typeof dialog.confirm !== 'function') { callback(window.confirm(message)); return; } dialog.confirm(message).then(function(res) { callback(!!res); });")
        static native void showDialogConfirm(JSObject dialog, String message, DialogConfirmCallback callback);

        @JSBody(params = { "dialog", "message", "initialValue", "callback" }, script = "if (!dialog || typeof dialog.prompt !== 'function') { var result = window.prompt(message, initialValue != null ? initialValue : ''); callback(result != null, result); return; } dialog.prompt(message, initialValue != null ? initialValue : '').then(function(res) { if (res) { callback(true, res.prompt != null ? res.prompt : ''); } else { callback(false, null); } });")
        static native void showDialogPrompt(JSObject dialog, String message, String initialValue,
            DialogPromptCallback callback);

        @JSBody(params = { "dialog", "message", "callback" }, script = "var html = String(message || '').replace(/(?:\\r\\n|\\r|\\n)/g, '<br>'); html = html.split('https://github.com/lanceewing/jvic').join(\"<a href='https://github.com/lanceewing/jvic' target='_blank'>https://github.com/lanceewing/jvic</a>\"); if (!dialog || typeof dialog.alert !== 'function') { window.alert(message); callback(true, 'OK'); return; } dialog.alert('', { showStateButtons: false, template: '<b>' + html + '</b>' }).then(function(res) { if (res) { callback(true, res === true ? 'OK' : String(res)); } else { callback(false, null); } });")
        static native void showDialogAlert(JSObject dialog, String message, DialogAlertCallback callback);

    @JSBody(params = { "url" }, script = "var req = new XMLHttpRequest(); req.open('GET', url, false); req.overrideMimeType('text/plain; charset=x-user-defined'); req.send(null); return req.status === 200 ? req.responseText : null;")
    static native String getBinaryResource(String url);

    @JSBody(params = { "url", "callback" }, script = "var req = new XMLHttpRequest(); try { console.log('TeaVM loader: requesting ' + url); req.open('GET', url, true); req.responseType = 'arraybuffer'; req.onload = function() { var response = req.response; var byteLength = response ? response.byteLength : 0; console.log('TeaVM loader: response status=' + req.status + ', bytes=' + byteLength + ', url=' + (req.responseURL || url)); callback(req.status === 200 ? response : null, req.status, req.responseURL || url, null); }; req.onerror = function() { console.log('TeaVM loader: request failed for ' + url + ': network error'); callback(null, req.status || 0, req.responseURL || url, 'network error'); }; req.send(null); } catch (err) { console.log('TeaVM loader: request failed for ' + url + ': ' + err); callback(null, 0, url, String(err)); }")
    static native void getBinaryResourceArrayBuffer(String url, BinaryResourceArrayBufferCallback callback);

        @JSBody(params = { "callback" }, script = "var input = document.createElement('input'); input.type = 'file'; input.accept = '.d64,.prg,.crt,.tap,.zip'; input.style.display = 'none'; document.body.appendChild(input);"
            + "var finish = function(success, fileName, binaryData) { if (input.parentNode) { input.parentNode.removeChild(input); } callback(success, fileName, binaryData); };"
            + "input.addEventListener('change', function() { if (!input.files || input.files.length === 0) { finish(false, null, null); return; } var file = input.files[0]; var reader = new FileReader(); reader.addEventListener('loadend', function() { var bytes = new Uint8Array(reader.result); var binary = ''; for (var i = 0; i < bytes.length; ++i) { binary += String.fromCharCode(bytes[i]); } finish(true, file.name, binary); }); reader.readAsArrayBuffer(file); });"
            + "input.addEventListener('cancel', function() { finish(false, null, null); });"
            + "input.click();")
    static native void openFileDialog(OpenFileBinaryCallback callback);

            @JSBody(params = { "callback" }, script = "var target = document.getElementById('embed-html') || document.body || document.documentElement;"
                + "var overlayId = 'jvic-drop-overlay';"
                + "var overlay = document.getElementById(overlayId);"
                + "var dragDepth = 0;"
                + "if (!overlay) {"
                + "  overlay = document.createElement('div');"
                + "  overlay.id = overlayId;"
                + "  overlay.textContent = 'Drop a VIC-20 file to load it';"
                + "  overlay.style.position = 'fixed';"
                + "  overlay.style.inset = '24px';"
                + "  overlay.style.display = 'none';"
                + "  overlay.style.alignItems = 'center';"
                + "  overlay.style.justifyContent = 'center';"
                + "  overlay.style.padding = '24px';"
                + "  overlay.style.border = '3px dashed rgba(140, 220, 255, 0.95)';"
                + "  overlay.style.borderRadius = '18px';"
                + "  overlay.style.background = 'rgba(7, 18, 28, 0.84)';"
                + "  overlay.style.color = '#f4fbff';"
                + "  overlay.style.fontFamily = 'Verdana, Geneva, sans-serif';"
                + "  overlay.style.fontSize = 'clamp(20px, 3vw, 34px)';"
                + "  overlay.style.fontWeight = '700';"
                + "  overlay.style.letterSpacing = '0.08em';"
                + "  overlay.style.textTransform = 'uppercase';"
                + "  overlay.style.textAlign = 'center';"
                + "  overlay.style.textShadow = '0 0 12px rgba(140, 220, 255, 0.35)';"
                + "  overlay.style.boxShadow = '0 0 0 1px rgba(255,255,255,0.08) inset, 0 24px 64px rgba(0, 0, 0, 0.45)';"
                + "  overlay.style.backdropFilter = 'blur(4px)';"
                + "  overlay.style.zIndex = '2147483647';"
                + "  overlay.style.pointerEvents = 'none';"
                + "  document.body.appendChild(overlay);"
                + "}"
                + "if (target && !target.dataset.jvicDropTargetStyled) {"
                + "  if (window.getComputedStyle(target).position === 'static') { target.style.position = 'relative'; }"
                + "  target.style.transition = 'box-shadow 120ms ease, filter 120ms ease';"
                + "  target.dataset.jvicDropTargetStyled = 'true';"
                + "}"
                + "var showOverlay = function() {"
                + "  dragDepth += 1;"
                + "  overlay.style.display = 'flex';"
                + "  if (target) {"
                + "    target.style.boxShadow = '0 0 0 4px rgba(140, 220, 255, 0.65), 0 0 40px rgba(140, 220, 255, 0.25)';"
                + "    target.style.filter = 'brightness(1.05)';"
                + "  }"
                + "};"
                + "var hideOverlay = function(force) {"
                + "  dragDepth = force ? 0 : Math.max(0, dragDepth - 1);"
                + "  if (dragDepth === 0) {"
                + "    overlay.style.display = 'none';"
                + "    if (target) {"
                + "      target.style.boxShadow = '';"
                + "      target.style.filter = '';"
                + "    }"
                + "  }"
                + "};"
                + "var preventDefaults = function(e) { e.preventDefault(); e.stopPropagation(); return false; };"
                + "['dragenter', 'dragover', 'dragleave', 'drop'].forEach(function(eventName) { target.addEventListener(eventName, preventDefaults, false); document.addEventListener(eventName, preventDefaults, false); });"
                + "target.addEventListener('dragenter', function() { showOverlay(); }, false);"
                + "target.addEventListener('dragover', function() { overlay.style.display = 'flex'; }, false);"
                + "target.addEventListener('dragleave', function(e) { if (!target.contains(e.relatedTarget)) { hideOverlay(false); } }, false);"
                + "window.addEventListener('dragend', function() { hideOverlay(true); }, false);"
                + "window.addEventListener('blur', function() { hideOverlay(true); }, false);"
                + "target.addEventListener('drop', function(e) {"
                + "  hideOverlay(true);"
                + "  var files = e.dataTransfer && e.dataTransfer.files ? e.dataTransfer.files : null;"
                + "  if (!files || files.length !== 1) { callback(false, null, null); return; }"
                + "  var file = files[0];"
                + "  var reader = new FileReader();"
                + "  reader.addEventListener('loadend', function() {"
                + "    var bytes = new Uint8Array(reader.result);"
                + "    var binary = '';"
                + "    for (var i = 0; i < bytes.length; ++i) { binary += String.fromCharCode(bytes[i]); }"
                + "    callback(true, file.name, binary);"
                + "  });"
                + "  reader.readAsArrayBuffer(file);"
                + "}, false);")
        static native void registerFileDropHandler(OpenFileBinaryCallback callback);
}