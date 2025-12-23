package emu.jvic;

import java.util.function.Consumer;

import emu.jvic.config.AppConfigItem;

public abstract class ProgramLoader {
    
    protected boolean isProgramFile(byte[] data) {
        if ((data != null) && (data.length >= 2)) {
            int startAddress = ((data[1] & 0xFF) << 8) + (data[0] & 0xFF);
            return ((startAddress == 0x1201) || (startAddress == 0x0401) || (startAddress == 0x1001));
        } else {
            return false;
        }
    }
    
    protected boolean isCartFile(byte[] data) {
        if ((data != null) && (data.length >= 2)) {
            int startAddress = getStartAddress(data);
            return ((startAddress == 0xA000) || (startAddress == 0x6000));
        } else {
            return false;
        }
    }
    
    protected boolean isZipFile(byte[] data) {
        // ZIP starts with: 50 4B 03 04
        return ((data != null) && (data.length >= 4) &&
                (data[0] == 0x50) && (data[1] == 0x4B) && 
                (data[2] == 0x03) && (data[3] == 0x04));
    }
    
    protected boolean isDiskFile(byte[] data) {
        // .D64 files are almost always 174848 bytes. Greater values are non standard.
        return ((data != null) && (data.length >= 174848));
    }
    
    protected boolean isPcvSnapshot(byte[] data) {
        // PCVIC Signature : 50 43 56 49 43
        return ((data != null) && (data.length >= 5) && 
                (data[0] == 0x50) && (data[1] == 0x43) && (data[2] == 0x56) && 
                (data[3] == 0x49) && (data[4] == 0x43));
    }
    
    protected int getStartAddress(byte[] data) {
        return ((data[1] & 0xFF) << 8) + (data[0] & 0xFF);
    }
    
    protected byte[] removeStartAddress(byte[] data) {
        int startAddress = getStartAddress(data);
        if ((startAddress == 0xA000) || (startAddress == 0x6000) || (startAddress == 0x4000) || (startAddress == 0x2000)) {
            byte[] newData = new byte[data.length - 2];
            int srcIndex = 2;
            for (int i=0; srcIndex < data.length; i++, srcIndex++) {
                newData[i] = data[srcIndex];
            }
            return newData;
        } else {
            return data;
        }
    }

    public abstract void fetchProgram(AppConfigItem appConfigItem, Consumer<Program> programConsumer);
    
}
