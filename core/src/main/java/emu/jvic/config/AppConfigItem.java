package emu.jvic.config;

import emu.jvic.MachineType;
import emu.jvic.memory.RamType;

public class AppConfigItem {

  private String name = "";
 
  private String displayName;
  
  private String filePath;
  
  private String fileType;
  
  private String iconPath;
  
  private MachineType machineType;
  
  private RamType ram = RamType.RAM_AUTO;
  
  private String status = "WORKING";

  /**
   * Constructor for AppConfigItem.
   */
  public AppConfigItem() {
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
  public MachineType getMachineType() {
    return machineType;
  }

  /**
   * @param machineType the machineType to set
   */
  public void setMachineType(MachineType machineType) {
    this.machineType = machineType;
  }

  /**
   * @return The RamType.
   */
  public RamType getRam() {
    return ram;
  }

  /**
   * @param ram The RamType to set.
   */
  public void setRam(RamType ram) {
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
}
