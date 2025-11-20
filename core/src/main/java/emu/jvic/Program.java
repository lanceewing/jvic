package emu.jvic;

import emu.jvic.config.AppConfigItem;

/**
 * Holds details about a program that can be run in the VIC 20 emulator.
 */
public class Program {

    private AppConfigItem appConfigItem;
    
    private byte[] programData;
    
    public Program() {
    }
    
    public Program(AppConfigItem appConfigItem, byte[] programData) {
        this.appConfigItem = appConfigItem;
        this.programData = programData;
    }
    
    public byte[] getProgramData() {
        return programData;
    }
    
    public void setProgramData(byte[] programData) {
        this.programData = programData;
    }

    public AppConfigItem getAppConfigItem() {
        return appConfigItem;
    }

    public void setAppConfigItem(AppConfigItem appConfigItem) {
        this.appConfigItem = appConfigItem;
    }
    
    public String getProgramType() {
        return appConfigItem.getFileType();
    }
    
    public String getFilePath() {
        return appConfigItem.getFilePath();
    }
}
