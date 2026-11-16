package emu.jvic.teavm;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

import emu.jvic.debug.MonitorChannel;

/**
 * The monitor as an HTML panel on the right of the page, for the browser: plain DOM
 * text, so it has the browser's own selection, copy and find, which the in-canvas
 * console (Alt+H) can't offer. It shares the monitor channel with the Alt+H console.
 * Typing in it doesn't reach the emulated keyboard. Opened and closed with
 * window.jvic.console() or the monitor's domconsole command.
 */
final class TeaVMDomConsole implements MonitorChannel.Listener {

    @JSFunctor
    interface LineCallback extends JSObject {
        void line(String text);
    }

    private final MonitorChannel channel;
    private JSObject panel;
    private String prompt = "";

    TeaVMDomConsole(MonitorChannel channel) {
        this.channel = channel;
    }

    /** Shows the panel (creating it the first time, and stopping the machine), or hides it. */
    void toggle() {
        if (panel == null) {
            panel = createPanel(line -> {
                append(prompt + line + "\n");
                channel.execute(line);
            });
            channel.addListener(this);
            channel.requestStop();
            return;
        }
        if (isVisible(panel)) {
            setVisible(panel, false);
        } else {
            setVisible(panel, true);
            channel.requestStop();
        }
    }

    @Override
    public void onOutput(String text) {
        if (panel != null) {
            append(text);
        }
    }

    @Override
    public void onPrompt(String newPrompt) {
        prompt = newPrompt;
        if (panel != null) {
            setPrompt(panel, newPrompt);
        }
    }

    private void append(String text) {
        appendText(panel, text);
    }

    @JSBody(params = "callback", script = ""
            + "var panel = document.createElement('div');"
            + "panel.style.cssText = 'position:fixed;right:0;top:0;width:42vw;min-width:280px;height:100vh;"
            + "background:#101014;color:#d0d0d0;font:13px/1.35 Consolas,Menlo,monospace;display:flex;"
            + "flex-direction:column;z-index:10000;border-left:2px solid #8c8cf2;box-sizing:border-box;';"
            + "var bar = document.createElement('div');"
            + "bar.style.cssText = 'display:flex;justify-content:space-between;align-items:center;"
            + "padding:4px 8px;background:#1c1c28;color:#b8b8ff;';"
            + "bar.textContent = 'jvic monitor';"
            + "var close = document.createElement('button');"
            + "close.textContent = 'Close';"
            + "close.style.cssText = 'background:#2a2a3a;color:#ddd;border:1px solid #555;cursor:pointer;';"
            + "close.onclick = function() { panel.style.display = 'none'; };"
            + "bar.appendChild(close);"
            + "var pre = document.createElement('pre');"
            + "pre.style.cssText = 'flex:1;overflow:auto;margin:0;padding:6px 8px;white-space:pre-wrap;"
            + "word-break:break-all;user-select:text;';"
            + "var row = document.createElement('div');"
            + "row.style.cssText = 'display:flex;padding:4px 8px;background:#000;';"
            + "var prompt = document.createElement('span');"
            + "prompt.style.cssText = 'white-space:pre;color:#8c8cf2;';"
            + "var input = document.createElement('input');"
            + "input.style.cssText = 'flex:1;background:#000;color:#fff;border:none;outline:none;font:inherit;';"
            + "input.setAttribute('spellcheck', 'false');"
            + "row.appendChild(prompt); row.appendChild(input);"
            + "panel.appendChild(bar); panel.appendChild(pre); panel.appendChild(row);"
            + "document.body.appendChild(panel);"
            + "var history = []; var index = 0;"
            + "var stop = function(e) { e.stopPropagation(); };"
            + "input.addEventListener('keyup', stop); input.addEventListener('keypress', stop);"
            + "input.addEventListener('keydown', function(e) {"
            + "  e.stopPropagation();"
            + "  if (e.key === 'Enter') {"
            + "    var line = input.value; input.value = '';"
            + "    if (line.trim().length > 0) { history.push(line); }"
            + "    index = history.length;"
            + "    callback(line);"
            + "  } else if (e.key === 'ArrowUp' && history.length > 0) {"
            + "    index = Math.max(0, index - 1); input.value = history[index]; e.preventDefault();"
            + "  } else if (e.key === 'ArrowDown') {"
            + "    index = Math.min(history.length, index + 1);"
            + "    input.value = (index < history.length) ? history[index] : ''; e.preventDefault();"
            + "  }"
            + "});"
            + "panel.jvicPre = pre; panel.jvicPrompt = prompt; panel.jvicInput = input;"
            + "setTimeout(function() { input.focus(); }, 0);"
            + "return panel;")
    private static native JSObject createPanel(LineCallback callback);

    /** Appends text, keeping at most about a megabyte, and scrolls to the end. */
    @JSBody(params = { "panel", "text" }, script = ""
            + "var pre = panel.jvicPre;"
            + "var atEnd = (pre.scrollTop + pre.clientHeight) >= (pre.scrollHeight - 4);"
            + "pre.appendChild(document.createTextNode(text));"
            + "if (pre.textContent.length > 1048576) { pre.textContent = pre.textContent.slice(-786432); }"
            + "if (atEnd) { pre.scrollTop = pre.scrollHeight; }")
    private static native void appendText(JSObject panel, String text);

    @JSBody(params = { "panel", "prompt" }, script = "panel.jvicPrompt.textContent = prompt;")
    private static native void setPrompt(JSObject panel, String prompt);

    @JSBody(params = "panel", script = "return panel.style.display !== 'none';")
    private static native boolean isVisible(JSObject panel);

    @JSBody(params = { "panel", "visible" }, script = ""
            + "panel.style.display = visible ? 'flex' : 'none';"
            + "if (visible) { setTimeout(function() { panel.jvicInput.focus(); }, 0); }")
    private static native void setVisible(JSObject panel, boolean visible);
}
