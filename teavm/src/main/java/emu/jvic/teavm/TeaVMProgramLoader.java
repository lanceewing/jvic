package emu.jvic.teavm;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import org.teavm.jso.typedarrays.ArrayBuffer;
import org.teavm.jso.typedarrays.Uint8Array;

import emu.jvic.Program;
import emu.jvic.ProgramLoader;
import emu.jvic.config.AppConfigItem;

public class TeaVMProgramLoader extends ProgramLoader {

    @Override
    public void fetchProgram(AppConfigItem appConfigItem, Consumer<Program> programConsumer) {
        try {
            TeaVMBrowser.logToConsole("TeaVM loader: fetchProgram name=" + appConfigItem.getName()
                    + ", path=" + appConfigItem.getFilePath()
                    + ", entry=" + appConfigItem.getEntryName());
            if (appConfigItem.getFileData() != null) {
                byte[] data = appConfigItem.getFileData();
                appConfigItem.setFileData(null);
                processProgramData(appConfigItem, data, programConsumer);
                return;
            } else if ((appConfigItem.getFilePath() == null) || appConfigItem.getFilePath().trim().isEmpty()) {
                programConsumer.accept(null);
                return;
            } else if (!appConfigItem.getFilePath().startsWith("http")) {
                FileHandle fileHandle = Gdx.files.internal(appConfigItem.getFilePath());
                if ((fileHandle != null) && fileHandle.exists()) {
                    byte[] data = fileHandle.readBytes();
                    TeaVMBrowser.logToConsole("TeaVM loader: loaded local asset bytes=" + data.length);
                    processProgramData(appConfigItem, data, programConsumer);
                    return;
                }
                TeaVMBrowser.logToConsole("TeaVM loader: local asset not found: " + appConfigItem.getFilePath());
            } else {
                String resolvedFilePath = applyFilePathOverride(appConfigItem.getFilePath());
                TeaVMBrowser.logToConsole("TeaVM loader: resolved remote path=" + resolvedFilePath);
                TeaVMBrowser.getBinaryResourceArrayBuffer(resolvedFilePath,
                        (arrayBuffer, status, responseUrl, errorMessage) -> {
                            try {
                                byte[] data = null;
                                if (arrayBuffer != null) {
                                    data = convertArrayBufferToBytes(arrayBuffer);
                                    TeaVMBrowser.logToConsole("TeaVM loader: remote bytes=" + data.length);
                                } else {
                                    TeaVMBrowser.logToConsole("TeaVM loader: remote fetch returned null array buffer"
                                            + ", status=" + status
                                            + ", url=" + responseUrl
                                            + (errorMessage != null ? ", error=" + errorMessage : ""));
                                }
                                processProgramData(appConfigItem, data, programConsumer);
                            } catch (Exception e) {
                                TeaVMBrowser.logToConsole(
                                        "TeaVM loader: exception while handling remote response: " + e);
                                programConsumer.accept(null);
                            }
                        });
                return;
            }
        } catch (Exception e) {
            TeaVMBrowser.logToConsole("TeaVM loader: exception while loading program: " + e);
            programConsumer.accept(null);
            return;
        }

        processProgramData(appConfigItem, null, programConsumer);
    }

    private void processProgramData(AppConfigItem appConfigItem, byte[] data, Consumer<Program> programConsumer) {
        Program program = null;

        try {
            byte[] programData = null;

            if ((data != null) && (data.length >= 4)) {
                if (isZipFile(data)) {
                    ByteArrayInputStream bais = new ByteArrayInputStream(data);
                    ZipInputStream zis = new ZipInputStream(bais);
                    ZipEntry zipEntry = zis.getNextEntry();
                    byte[] fileData = null;
                    int numOfEntries = 0;

                    while (zipEntry != null) {
                        try {
                            if (!zipEntry.isDirectory()) {
                                String entryName = zipEntry.getName().toLowerCase();
                                boolean entryMatch = (appConfigItem.getEntryName() == null
                                        || entryName.equals(appConfigItem.getEntryName().toLowerCase())
                                        || entryName.endsWith("/" + appConfigItem.getEntryName().toLowerCase()));
                                numOfEntries++;
                                fileData = readBytesFromInputStream(zis);
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
                                    programData = fileData;
                                    appConfigItem.setFileType("PCV");
                                    break;
                                }
                                if (isProgramFile(fileData) && entryMatch) {
                                    programData = fileData;
                                    appConfigItem.setFileType("PRG");
                                    break;
                                }
                                if (isCartFile(fileData) && entryMatch) {
                                    programData = removeStartAddress(fileData);
                                    appConfigItem.setFileType("CART");
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
                            appConfigItem.setFileType("CART");
                            programData = fileData;
                            break;
                        }
                    }
                } else if (isTapeFile(data)) {
                    appConfigItem.setFileType("TAPE");
                    programData = data;
                } else if (isDiskFile(data)) {
                    appConfigItem.setFileType("DISK");
                    programData = data;
                } else if (isPcvSnapshot(data)) {
                    appConfigItem.setFileType("PCV");
                    programData = data;
                } else if (isProgramFile(data)) {
                    appConfigItem.setFileType("PRG");
                    programData = data;
                } else if (isCartFile(data)) {
                    appConfigItem.setFileType("CART");
                    programData = removeStartAddress(data);
                } else {
                    appConfigItem.setFileType("CART");
                    programData = data;
                }
            } else {
                appConfigItem.setFileType("UNK");
            }

            if (programData != null) {
                TeaVMBrowser.logToConsole("TeaVM loader: identified type=" + appConfigItem.getFileType()
                        + ", program bytes=" + programData.length);
                program = new Program(appConfigItem, programData);
            } else {
                TeaVMBrowser.logToConsole("TeaVM loader: no program data identified, fileType="
                        + appConfigItem.getFileType());
            }
        } catch (Exception e) {
            TeaVMBrowser.logToConsole("TeaVM loader: exception while decoding program data: " + e);
        }

        programConsumer.accept(program);
    }

    private String applyFilePathOverride(String filePath) {
        if ("localhost".equals(TeaVMBrowser.getHostName())) {
            String localHostBaseUrl = TeaVMBrowser.getProtocol() + "//" + TeaVMBrowser.getHost() + "/";
            if (filePath.startsWith(localHostBaseUrl)) {
                return filePath;
            }
            if (filePath.startsWith("https://vic20.games/")) {
                return filePath.replace("https://vic20.games/", localHostBaseUrl);
            }
        }
        return filePath;
    }

    private byte[] convertArrayBufferToBytes(ArrayBuffer arrayBuffer) {
        Uint8Array uint8Array = Uint8Array.create(arrayBuffer);
        byte[] bytes = new byte[uint8Array.getLength()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte)(uint8Array.get(i) & 0xFF);
        }
        return bytes;
    }

    private byte[] loadFullCartProgramData(String entryName, byte[] data, ZipInputStream zis,
            AppConfigItem appConfigItem) {
        try {
            TreeMap<String, byte[]> cartParts = new TreeMap<String, byte[]>();
            ZipEntry zipEntry = null;
            boolean stillLoading = true;

            while (stillLoading) {
                int startAddress = getStartAddress(data);
                if ((startAddress == 0x2000) || entryName.contains("[2000]") || entryName.endsWith("-20.crt")) {
                    cartParts.put("2000", removeStartAddress(data));
                } else if ((startAddress == 0x4000) || entryName.contains("[4000]")
                        || entryName.endsWith("-40.crt")) {
                    cartParts.put("4000", removeStartAddress(data));
                } else if ((startAddress == 0x6000) || entryName.contains("[6000]")
                        || entryName.endsWith("-60.crt")) {
                    cartParts.put("6000", removeStartAddress(data));
                } else if ((startAddress == 0xA000) || entryName.contains("[A000]")
                        || entryName.endsWith("-a0.crt")) {
                    cartParts.put("A000", removeStartAddress(data));
                }

                do {
                    zipEntry = zis.getNextEntry();
                } while ((zipEntry != null) && zipEntry.isDirectory());

                if (zipEntry != null) {
                    entryName = zipEntry.getName().toLowerCase();
                    data = readBytesFromInputStream(zis);
                } else {
                    stillLoading = false;
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            StringBuilder loadAddress = new StringBuilder();
            for (String loadAddrKey : cartParts.keySet()) {
                if (loadAddress.length() > 0) {
                    loadAddress.append("|");
                }
                loadAddress.append(loadAddrKey);
                byte[] partData = cartParts.get(loadAddrKey);
                baos.write(partData);
                int paddingSize = 8192 - partData.length;
                for (int i = 0; i < paddingSize; i++) {
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