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

import emu.jvic.Program;
import emu.jvic.ProgramLoader;
import emu.jvic.config.AppConfigItem;

public class TeaVMProgramLoader extends ProgramLoader {

    @Override
    public void fetchProgram(AppConfigItem appConfigItem, Consumer<Program> programConsumer) {
        Program program = null;
        byte[] data = null;

        try {
            if (appConfigItem.getFileData() != null) {
                data = appConfigItem.getFileData();
                appConfigItem.setFileData(null);
            } else if ((appConfigItem.getFilePath() == null) || appConfigItem.getFilePath().trim().isEmpty()) {
                programConsumer.accept(null);
                return;
            } else if (!appConfigItem.getFilePath().startsWith("http")) {
                FileHandle fileHandle = Gdx.files.internal(appConfigItem.getFilePath());
                if ((fileHandle != null) && fileHandle.exists()) {
                    data = fileHandle.readBytes();
                }
            } else {
                String binaryStr = TeaVMBrowser.getBinaryResource(applyFilePathOverride(appConfigItem.getFilePath()));
                if (binaryStr != null) {
                    data = convertBinaryStringToBytes(binaryStr);
                }
            }

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
                program = new Program(appConfigItem, programData);
            }
        } catch (Exception e) {
            // Ignore. The caller will receive a null program.
        }

        programConsumer.accept(program);
    }

    private String applyFilePathOverride(String filePath) {
        if ("localhost".equals(TeaVMBrowser.getHostName())) {
            String localHostBaseUrl = TeaVMBrowser.getProtocol() + "//" + TeaVMBrowser.getHost() + "/";
            if (filePath.startsWith("https://vic20.games/")) {
                return filePath.replace("https://vic20.games/", localHostBaseUrl);
            }
        }
        return filePath;
    }

    private byte[] convertBinaryStringToBytes(String binaryStr) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < binaryStr.length(); i++) {
            out.write(binaryStr.charAt(i) & 0xFF);
        }
        return out.toByteArray();
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