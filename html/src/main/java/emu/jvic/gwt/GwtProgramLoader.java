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
            if (isZipFile(data)) {
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
                            if (isPcvSnapshot(fileData)) {
                                appConfigItem.setFileType("PCV");
                                programData = fileData;
                                break;
                            }
                            if (isProgramFile(fileData)) {
                                appConfigItem.setFileType("PRG");
                                programData = fileData;
                                break;
                            }
                            if (isCartFile(fileData)) {
                                appConfigItem.setFileType("CART");
                                programData = removeStartAddress(fileData);
                                break;
                            }
                            if (file.getName().toLowerCase().endsWith(".crt")) {
                                appConfigItem.setFileType("CART");
                                programData = fileData;
                                break;
                            }
                        }
                    }
                }
            }
            else if (isDiskFile(data)) {
                appConfigItem.setFileType("DISK");
                programData = data;
            }
            else if (isPcvSnapshot(data)) {
                appConfigItem.setFileType("PCV");
                programData = data;
            }
            else if (isProgramFile(data)) {
                appConfigItem.setFileType("PRG");
                programData = data;
            }
            else if (isCartFile(data)) {
                appConfigItem.setFileType("CART");
                programData = removeStartAddress(data);
            }
            else {
                // Assume CART for everything else.
                appConfigItem.setFileType("CART");
                programData = data;
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
    
    private boolean isProgramFile(byte[] data) {
        if ((data != null) && (data.length >= 2)) {
            int startAddress = ((data[1] & 0xFF) << 8) + (data[0] & 0xFF);
            return ((startAddress == 0x1201) || (startAddress == 0x0401) || (startAddress == 0x1001));
        } else {
            return false;
        }
    }
    
    private boolean isCartFile(byte[] data) {
        if ((data != null) && (data.length >= 2)) {
            int startAddress = ((data[1] & 0xFF) << 8) + (data[0] & 0xFF);
            return ((startAddress == 0xA000));
        } else {
            return false;
        }
    }
    
    private boolean isZipFile(byte[] data) {
        // ZIP starts with: 50 4B 03 04
        return ((data != null) && (data.length >= 4) &&
                (data[0] == 0x50) && (data[1] == 0x4B) && 
                (data[2] == 0x03) && (data[3] == 0x04));
    }
    
    private boolean isDiskFile(byte[] data) {
        // .D64 files are almost always 174848 bytes. Greater values are non standard.
        return ((data != null) && (data.length >= 174848));
    }
    
    private boolean isPcvSnapshot(byte[] data) {
        // PCVIC Signature : 50 43 56 49 43
        return ((data != null) && (data.length >= 5) && 
                (data[0] == 0x50) && (data[1] == 0x43) && (data[2] == 0x56) && 
                (data[3] == 0x49) && (data[4] == 0x43));
    }
    
    private byte[] removeStartAddress(byte[] data) {
        byte[] newData = new byte[data.length - 2];
        int srcIndex = 2;
        for (int i=0; srcIndex < data.length; i++, srcIndex++) {
            newData[i] = data[srcIndex];
        }
        return newData;
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
