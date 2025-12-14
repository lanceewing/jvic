package emu.jvic.gwt;

import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.backends.gwt.GwtApplication;
import com.badlogic.gdx.backends.gwt.GwtApplicationConfiguration;
import com.google.gwt.typedarrays.shared.Int8Array;
import com.google.gwt.typedarrays.shared.TypedArrays;
import com.google.gwt.user.client.Window;

import emu.jvic.JVic;
import emu.jvic.JVicRunner;

/** Launches the GWT application. */
public class GwtLauncher extends GwtApplication {
    
    private JVic jvic;
    
    @Override
    public GwtApplicationConfiguration getConfig () {
        // Resizable application, uses available space in browser with no padding:
        GwtApplicationConfiguration cfg = new GwtApplicationConfiguration(true);
        cfg.padVertical = 0;
        cfg.padHorizontal = 0;
        return cfg;
        // If you want a fixed size application, comment out the above resizable section,
        // and uncomment below:
        //return new GwtApplicationConfiguration(640, 480);
    }

    @Override
    public ApplicationListener createApplicationListener () {
        Map<String, String> argsMap = new HashMap<>();
        
        String urlPath = Window.Location.getPath();
        
        // JVic supports loading games with a hash path.
        if ("/".equals(urlPath) || "".equals(urlPath)) {
            String hash = Window.Location.getHash().toLowerCase();
            if ((hash != null) && (hash.length() > 0)) {
                if (hash.startsWith("#/")) {
                    String programId = hash.substring(2);
                    argsMap.put("uri", programId);
                }
            } else {
                // JVic also supports loading from a provided URL.
                String programUrl = Window.Location.getParameter("url");
                if ((programUrl != null) && (!programUrl.trim().equals(""))) {
                    logToJSConsole("Attempting to load program from URL: " + programUrl);
                    if (isProgramURLValid(programUrl)) {
                        argsMap.put("url", programUrl);
                    } else {
                        // Remove the url param if the value is not valid.
                        String cleanURL = Window.Location.createUrlBuilder()
                                .removeParameter("url")
                                .buildString();
                        updateURLWithoutReloading(cleanURL);
                    }
                }
            }
        }
        
        GwtDialogHandler gwtDialogHandler = new GwtDialogHandler();
        JVicRunner jvicRunner = new GwtJVicRunner(
                new GwtKeyboardMatrix(), 
                new GwtPixelData());
        jvic = new JVic(jvicRunner, gwtDialogHandler, argsMap);
        registerFileDropEventHandler();
        return jvic;
    }
    
    private native void registerFileDropEventHandler() /*-{
        var that = this;
        $wnd.document.getElementById('embed-html').ondragover = $entry(function(e) {
            e.preventDefault();
            return false;
        });
        $wnd.document.getElementById('embed-html').ondrop = $entry(function(e) {
            e.preventDefault();
            
            if ((e.dataTransfer.items) && 
                (e.dataTransfer.items.length == 1) && 
                (e.dataTransfer.items[0].kind == 'file')) {
                
                var item = e.dataTransfer.items[0];
                
                Promise.all([].map.call([item.getAsFile()], function (file) {
                    return new Promise(function (resolve, reject) {
                        var reader = new FileReader();
                        // NOTE 1: loadend called regards of whether it was successful or not.
                        // NOTE 2: file has .name, .size and .lastModified fields.
                        reader.addEventListener("loadend", function (event) {
                            resolve({
                                fileName: file.name,
                                filePath: file.webkitRelativePath? file.webkitRelativePath : '',
                                fileData: reader.result
                            });
                        });
                        reader.readAsArrayBuffer(file);
                    });
                })).then(function (results) {
                    // The results param is an array of result objects
                    that.@emu.jvic.gwt.GwtLauncher::onFileDrop([Lemu/jvic/gwt/GwtOpenFileResult;)(results);
                });
            }
        });
    }-*/;

    private void onFileDrop(GwtOpenFileResult[] dropFileResultArray) {
        if (dropFileResultArray.length == 1) {
            GwtOpenFileResult dropFileResult = dropFileResultArray[0];
            
            Int8Array fileDataInt8Array = TypedArrays.createInt8Array(dropFileResult.getFileData());
            byte[] fileByteArray = new byte[fileDataInt8Array.byteLength()];
            for (int index=0; index<fileDataInt8Array.byteLength(); index++) {
                fileByteArray[index] = fileDataInt8Array.get(index);
            }
            
            jvic.fileDropped(dropFileResult.getFileName(), fileByteArray);
        }
    }
    
    private boolean isProgramURLValid(String url) {
        String lcProgramURL = url.toLowerCase();
        if (lcProgramURL.endsWith(".tgz")) {
            logToJSConsole("Sorry, JVic does not support tgz files.");
            return false;
        } else {
            return isURLValid(url);
        }
    }
    
    private final native boolean isURLValid(String url)/*-{
        try {
            new URL(url);
            return true;
        } catch (err) {
            console.log("Sorry, the program URL does not appear to be well formed.");
            return false;
        }
    }-*/;
    
    // NOTE: This version does not add anything to the history.
    private static native void updateURLWithoutReloading(String newURL) /*-{
        $wnd.history.replaceState(newURL, "", newURL);
    }-*/;
    
    private final native void logToJSConsole(String message)/*-{
        console.log(message);
    }-*/;
}
