package emu.jvic.lwjgl3;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

import emu.jvic.PixelData;
import emu.jvic.Program;
import emu.jvic.ProgramLoader;
import emu.jvic.config.AppConfigItem;

public class DesktopProgramLoader extends ProgramLoader {

    public DesktopProgramLoader(PixelData pixelData) {
    }

    @Override
    public void fetchProgram(AppConfigItem appConfigItem, Consumer<Program> programConsumer) {
        Program program = null;
        BufferedInputStream bis = null;
        byte[] data = null;
        
        try {
            if (!appConfigItem.getFilePath().startsWith("http")) {
                FileHandle fileHandle = null;
                if ("ABSOLUTE".equals(appConfigItem.getFileType())) {
                    fileHandle = Gdx.files.absolute(appConfigItem.getFilePath());
                } else {
                    fileHandle = Gdx.files.internal(appConfigItem.getFilePath());
                }
                if (fileHandle != null) {
                    if (fileHandle.exists()) {
                        data = fileHandle.readBytes();
                    }
                }
            } 
            else {
                URL url = new URL(appConfigItem.getFilePath());
                HttpURLConnection connection = (HttpURLConnection)url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "JVic - The VIC 20 Emulator");
                
                int b = 0;
                bis = new BufferedInputStream(connection.getInputStream());
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                while ((b = bis.read()) != -1 ) {
                    out.write(b);
                }
                data = out.toByteArray();
            }
            
            byte[] programData = null;
            
            if ((data != null) && (data.length >= 4)) {
                if (isZipFile(data)) {
                    // ZIP starts with: 50 4B 03 04
                    ByteArrayInputStream bais = new ByteArrayInputStream(data);
                    ZipInputStream zis = new ZipInputStream(bais);
                    ZipEntry zipEntry = zis.getNextEntry();
                    byte[] fileData = null;
                    int numOfEntries = 0;
                    
                    while (zipEntry != null) {
                        try {
                            if (!zipEntry.isDirectory()) {
                                String entryName = zipEntry.getName().toLowerCase();
                                boolean entryMatch = (appConfigItem.getEntryName() == null || 
                                        entryName.equals(appConfigItem.getEntryName().toLowerCase()) || 
                                        entryName.endsWith("/" + appConfigItem.getEntryName().toLowerCase()));
                                numOfEntries++;
                                fileData = readBytesFromInputStream(zis);
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
                                if (entryName.endsWith(".crt") && entryMatch) {
                                    appConfigItem.setFileType("CART");
                                    programData = loadFullCartProgramData(entryName, fileData, zis, appConfigItem);
                                    break;
                                }
                            }
                        } catch (IOException e) {
                            throw new RuntimeException("IO error reading zip entry: " + zipEntry.getName(), e);
                        }
                        
                        zipEntry = zis.getNextEntry();
                        
                        if ((zipEntry == null) && (numOfEntries == 1)) {
                            // If the ZIP contains only one file, and it didn't match one
                            // of the other type checks, then assume it is CART.
                            appConfigItem.setFileType("CART");
                            programData = fileData;
                            break;
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
                appConfigItem.setFileType("UNK");
            }
            
            if (programData != null) {
                program = new Program(appConfigItem, programData);
            }
                            
        } catch (Exception e) {
            // Ignore.
        } finally {
            if (bis != null) {
                try {
                    bis.close();
                } catch (Exception e2) {
                 // Ignore.
                }
            }
        }
        
        programConsumer.accept(program);
    }

    private byte[] loadFullCartProgramData(String entryName, byte[] data, ZipInputStream zis, 
            AppConfigItem appConfigItem) {
        try {
            TreeMap<String, byte[]> cartParts = new TreeMap<String, byte[]>();
            ZipEntry zipEntry = null;
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
                
                // Get next non-directory entry.
                do {
                    zipEntry = zis.getNextEntry();
                } while((zipEntry != null) && (zipEntry.isDirectory()));
                
                // If a non-directory entry was found, read the file name and data.
                if (zipEntry != null) {
                    entryName = zipEntry.getName().toLowerCase();
                    data = readBytesFromInputStream(zis);
                } else {
                    stillLoading = false;
                }
            }
            
            // Build data and app config item.
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            StringBuilder loadAddress = new StringBuilder();
            for (String loadAddrKey : cartParts.keySet()) {
                if (!loadAddress.isEmpty()) {
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
    
    private byte[] readBytesFromInputStream(InputStream is) throws IOException {
        int numOfBytesReads;
        byte[] data = new byte[256];
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        while ((numOfBytesReads = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, numOfBytesReads);
        }
        return buffer.toByteArray();
    }
}
