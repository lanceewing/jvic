package emu.jvic.lwjgl3;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

import emu.jvic.PixelData;
import emu.jvic.Program;
import emu.jvic.ProgramLoader;
import emu.jvic.config.AppConfigItem;

public class DesktopProgramLoader implements ProgramLoader {

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
                                    programData = fileData;
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
