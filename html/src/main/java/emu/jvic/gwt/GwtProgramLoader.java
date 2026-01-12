package emu.jvic.gwt;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.TreeMap;
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

public class GwtProgramLoader extends ProgramLoader {

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
                        String entryName = file.getName().toLowerCase();
                        boolean entryMatch = (appConfigItem.getEntryName() == null || 
                                entryName.equals(appConfigItem.getEntryName().toLowerCase()));
                        if (file != null) {
                            byte[] fileData = file.asUint8Array().toByteArray();
                            if (isTapeFile(fileData) && entryMatch) {
                                programData = fileData;
                                appConfigItem.setFileType("TAPE");
                                break;
                            }
                            if (isDiskFile(fileData) && entryMatch) {
                                programData = fileData;
                                appConfigItem.setFileType("DISK");
                                break;
                            }
                            if (isPcvSnapshot(fileData) && entryMatch) {
                                appConfigItem.setFileType("PCV");
                                programData = fileData;
                                break;
                            }
                            if (isProgramFile(fileData) && entryMatch) {
                                appConfigItem.setFileType("PRG");
                                programData = fileData;
                                break;
                            }
                            if (isCartFile(fileData) && entryMatch) {
                                appConfigItem.setFileType("CART");
                                programData = removeStartAddress(fileData);
                                break;
                            }
                            if (file.getName().toLowerCase().endsWith(".crt") && entryMatch) {
                                appConfigItem.setFileType("CART");
                                programData = loadFullCartProgramData(entryName, fileData, jsZip, i, appConfigItem);
                                break;
                            }
                        }
                    }
                }
            }
            else if (isTapeFile(data)) {
                appConfigItem.setFileType("TAPE");
                programData = data;
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
    
    private byte[] loadFullCartProgramData(String entryName, byte[] data, 
            JSZip jsZip, int fileNum, AppConfigItem appConfigItem) {
        try {
            TreeMap<String, byte[]> cartParts = new TreeMap<String, byte[]>();
            JsArrayString files = jsZip.getFiles();
            String fileName = null;
            boolean stillLoading = true;
            
            while (stillLoading) {
                int startAddress = getStartAddress(data);
                if ((startAddress == 0x2000) || (entryName.contains("[2000]")) || 
                        (entryName.endsWith("-20.crt"))) {
                    cartParts.put("2000", removeStartAddress(data));
                }
                else if ((startAddress == 0x4000) || (entryName.contains("[4000]")) || 
                        (entryName.endsWith("-40.crt"))) {
                    cartParts.put("4000", removeStartAddress(data));
                }
                else if ((startAddress == 0x6000) || (entryName.contains("[6000]")) || 
                        (entryName.endsWith("-60.crt"))) {
                    cartParts.put("6000", removeStartAddress(data));
                }
                else if ((startAddress == 0xA000) || (entryName.contains("[A000]")) || 
                        (entryName.endsWith("-a0.crt"))) {
                    cartParts.put("A000", removeStartAddress(data));
                }
                
                do {
                    fileName = null;
                    fileNum++;
                    if (fileNum < files.length()) {
                        fileName = files.get(fileNum);
                    }
                } while((fileName != null) && (fileName.endsWith("/")));
                
                // If a non-directory entry was found, read the file name and data.
                if (fileName != null) {
                    JSFile file = jsZip.getFile(fileName);
                    entryName = file.getName().toLowerCase();
                    data = file.asUint8Array().toByteArray();
                } else {
                    stillLoading = false;
                }
            }
            
            // Build data and app config item.
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            StringBuilder loadAddress = new StringBuilder();
            for (String loadAddrKey : cartParts.keySet()) {
                if (loadAddress.length() > 0) {
                    loadAddress.append("|");
                }
                loadAddress.append(loadAddrKey);
                byte[] partData = cartParts.get(loadAddrKey);
                baos.write(partData);
                // If less than 8192, then pad with 00s.
                int paddingSize = (8192 - partData.length);
                for (int i=0; i<paddingSize; i++) {
                    baos.write(0);
                }
            }
            
            appConfigItem.setLoadAddress(loadAddress.toString());
            
            return baos.toByteArray();
            
        } catch (IOException ioe) {
            return data;
        }
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
