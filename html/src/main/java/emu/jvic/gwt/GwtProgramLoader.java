package emu.jvic.gwt;

import java.io.ByteArrayOutputStream;
import java.util.function.Consumer;

import com.akjava.gwt.jszip.JSFile;
import com.akjava.gwt.jszip.JSZip;
import com.akjava.gwt.jszip.Uint8Array;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.user.client.Window;

import emu.jvic.Program;
import emu.jvic.ProgramLoader;
import emu.jvic.config.AppConfigItem;

public class GwtProgramLoader implements ProgramLoader {

    @Override
    public void fetchProgram(AppConfigItem appConfigItem, Consumer<Program> programConsumer) {
        logToJSConsole("Fetching program '" + appConfigItem.getName() + "'");
        
        Program program = null;
        byte[] data = null;
        
        if (appConfigItem.getFileData() != null) {
            data = appConfigItem.getFileData();
            appConfigItem.setFileData(null);
        }
        // For configs such as BASIC, there is no file path, so return without program.
        else if ("".equals(appConfigItem.getFilePath())) {
            programConsumer.accept(null);
            return;
        }
        else if (!appConfigItem.getFilePath().startsWith("http")) {
            FileHandle fileHandle = Gdx.files.internal(appConfigItem.getFilePath());
            if (fileHandle != null) {
                if (fileHandle.exists()) {
                    data = fileHandle.readBytes();
                }
            }
        } 
        else {
            String binaryStr = getBinaryResource(applyFilePathOverride(appConfigItem.getFilePath()));
            if (binaryStr != null) {
                // Use the data to identify the type of program.
                data = convertBinaryStringToBytes(binaryStr);
            }
        }
        
        byte[] programData = null;
        
        if ((data != null) && (data.length >= 4)) {
            if ((data[0] == 0x16) && (data[1] == 0x16) && (data[2] == 0x16)) {
                // At least 3 0x16 bytes followed by a 0x24 is a tape file.
                appConfigItem.setFileType("TAPE");
                programData = data;
            }
            else if ((data[0] == 0x4D) && (data[1] == 0x46) && (data[2] == 0x4D)) {
                // MFM_DISK - 4D 46 4D 5F 44 49 53 4B
                appConfigItem.setFileType("DISK");
                programData = data;
            }
            else if ((data[0] == 0x50) && (data[1] == 0x4B) && (data[2] == 0x03) && (data[3] == 0x04)) {
                // ZIP starts with: 50 4B 03 04
                logToJSConsole("Scanning ZIP file...");
                
                JSZip jsZip = JSZip.loadFromArray(Uint8Array.createUint8(data));
                JsArrayString files = jsZip.getFiles();

                for (int i=0; i < files.length(); i++) {
                    String fileName = files.get(i);
                    if (!fileName.endsWith("/")) {
                        // File is not a directory, so check file content...
                        JSFile file = jsZip.getFile(fileName);
                        if (file != null) {
                            byte[] fileData = file.asUint8Array().toByteArray();
                            if (isDiskFile(fileData)) {
                                programData = fileData;
                                appConfigItem.setFileType("DISK");
                                break;
                            }
                            if (isTapeFile(fileData)) {
                                programData = fileData;
                                appConfigItem.setFileType("TAPE");
                                break;
                            }
                        }
                    }
                }
            }
            else {
                // Assume it is ROM if not TAPE or DISK.
                programData = data;
                appConfigItem.setFileType("ROM");
            }
        }
        else {
            logToJSConsole("Sorry, the format of the specified program file could not be recognised.");
            appConfigItem.setFileType("UNK");
        }
        
        if (programData != null) {
            if (!"UNK".equals(appConfigItem.getFileType())) {
                logToJSConsole("Identified " + appConfigItem.getFileType() + " image in program data.");
            }
            program = new Program();
            program.setProgramData(programData);
        }
        
        programConsumer.accept(program);
    }
    
    private boolean isTapeFile(byte[] data) {
        return ((data != null) && (data.length > 3) && 
                (data[0] == 0x16) && (data[1] == 0x16) && (data[2] == 0x16));
    }
    
    private boolean isDiskFile(byte[] data) {
        return ((data != null) && (data.length > 3) && 
                (data[0] == 0x4D) && (data[1] == 0x46) && (data[2] == 0x4D));
    }
    
    private byte[] convertBinaryStringToBytes(String binaryStr) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i=0; i<binaryStr.length(); i++) {
            out.write(binaryStr.charAt(i) & 0xFF);
        }
        return out.toByteArray();
    }

    private String applyFilePathOverride(String filePath) {
        if (Window.Location.getHostName() == "localhost") {
            String localHostBaseUrl = 
                    Window.Location.getProtocol() + "//" +
                    Window.Location.getHost() + "/";
            logToJSConsole("localHostBaseUrl: " + localHostBaseUrl);
            if (filePath.startsWith("https://vic20.games/")) {
                return filePath.replace("https://vic20.games/", localHostBaseUrl);
            }
        }
        return filePath;
    }
    
    /**
     * Fetches the given relative URL path as binary data returned in an ArrayBuffer.
     * 
     * @param url The relative URL path to fetch.
     * 
     * @return An ArrayBuffer containing the binary data of the resource.
     */
    private static native String getBinaryResource(String url) /*-{
        var req = new XMLHttpRequest();
        req.open("GET", url, false);  // The last parameter determines whether the request is asynchronous -> this case is sync.
        req.overrideMimeType('text/plain; charset=x-user-defined');
        req.send(null);
        if (req.status == 200) {                    
            return req.responseText;
        } else return null
    }-*/;
    
    private final native void logToJSConsole(String message)/*-{
        console.log(message);
    }-*/;
}
