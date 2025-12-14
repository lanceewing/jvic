package emu.jvic.config;

public class AppConfigItem {

    private String gameId;
    
    private String name = "";

    private String displayName;

    private String filePath;

    private String fileType;

    private String iconPath;

    private String machineType;

    private String ram = "RAM_AUTO";
    
    private FileLocation fileLocation = FileLocation.INTERNAL;

    private String status = "WORKING";
    
    private String autoRunCommand;
    
    private String entryName;

    // Required for the web open file feature, as the same event that selects
    // the file needs to read the data.
    private byte[] fileData;
    
    public enum FileLocation {
        INTERNAL, EXTERNAL, ABSOLUTE, CLASSPATH, LOCAL
    };
    
    /**
     * Constructor for AppConfigItem.
     */
    public AppConfigItem() {
    }

    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }
    
    /**
     * @return the fileLocation
     */
    public FileLocation getFileLocation() {
        return fileLocation;
    }

    /**
     * @param fileLocation the fileLocation to set
     */
    public void setFileLocation(FileLocation fileLocation) {
        this.fileLocation = fileLocation;
    }
    
    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the displayName
     */
    public String getDisplayName() {
        if ((displayName == null) && (name != null)) {
            int numOfSpaces = name.length() - name.replace(" ", "").length();
            displayName = name.replace(" ", "\n");
            if (numOfSpaces == 0) {
                displayName = displayName + "\n";
            }
        }
        return displayName;
    }

    /**
     * @param displayName the displayName to set
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * @return the filePath
     */
    public String getFilePath() {
        return filePath;
    }

    /**
     * @param filePath the filePath to set
     */
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    /**
     * @return the fileType
     */
    public String getFileType() {
        return fileType;
    }

    /**
     * @param fileType the fileType to set
     */
    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    /**
     * @return the iconPath
     */
    public String getIconPath() {
        return iconPath;
    }

    /**
     * @param iconPath the iconPath to set
     */
    public void setIconPath(String iconPath) {
        this.iconPath = iconPath;
    }

    /**
     * @return the machineType
     */
    public String getMachineType() {
        return machineType;
    }

    /**
     * @param machineType the machineType to set
     */
    public void setMachineType(String machineType) {
        this.machineType = machineType;
    }

    /**
     * @return The RamType.
     */
    public String getRam() {
        return ram;
    }

    /**
     * @param ram The RamType to set.
     */
    public void setRam(String ram) {
        this.ram = ram;
    }

    /**
     * @return the status
     */
    public String getStatus() {
        return status;
    }

    /**
     * @param status the status to set
     */
    public void setStatus(String status) {
        this.status = status;
    }
    
    public byte[] getFileData() {
        return fileData;
    }

    public void setFileData(byte[] fileData) {
        this.fileData = fileData;
    }

    public String getAutoRunCommand() {
        return autoRunCommand;
    }

    public void setAutoRunCommand(String autoRunCommand) {
        this.autoRunCommand = autoRunCommand;
    }

    public String getEntryName() {
        return entryName;
    }

    public void setEntryName(String entryName) {
        this.entryName = entryName;
    }
}
