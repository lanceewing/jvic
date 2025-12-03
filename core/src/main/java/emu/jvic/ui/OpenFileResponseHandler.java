package emu.jvic.ui;

/**
 * Interface that is called by a DialogHandler openFileDialog() implementation when 
 * the user has either selected a file, or cancelled file selection.
 * 
 * @author Lance Ewing
 */
public interface OpenFileResponseHandler {

    void openFileResult(boolean success, String filePath, byte[] fileData);
  
}
