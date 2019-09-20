package emu.jvic.cpu;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.Ignore;
import org.junit.Test;

public class CpuFullFunctionalTest extends CpuBaseTestCase {

  
  private void loadBinFile() {
    byte[] data = loadFileFromClassspath("emu/joric/cpu/6502_functional_test.bin");
    if (data != null) {
      int[] mem = memory.getMemoryArray();
      for (int i=0; i<data.length; i++) {
        mem[i] = ((int)data[i]) & 0xFF;
      }
      System.out.println("Bin data of length " + data.length + " loaded.");
    }
  }
  
  //@Ignore
  @Test
  public void testRunFunctionalTest() {
    loadBinFile();
    
    cpu.setProgramCounter(0x0400);   // TODO: Check that this is the starting address!
    
    //for (int i=0; i<1000000; i++) {
    while (true) {
      cpu.step();   // TODO: Would emulateCycle be better?
    }
    
    
  }
  
  /**
   * Loads and returns the contents of the given file as a byte array.
   * 
   * @param fileName The name of the file to load.
   * 
   * @return a byte array containing the contents of the file.
   */
  public byte[] loadFileFromClassspath(String fileName) {
    byte content[] = null;
    InputStream is = null;
    BufferedInputStream bis = null;
    ByteArrayOutputStream baos = null;
    byte buf[] = new byte[1024];
    int bytesRead = 0;

    // Construct a buffered reader to read in the file from the JAR file.
    ClassLoader classLoader = CpuFullFunctionalTest.class.getClassLoader();
    is = classLoader.getResourceAsStream(fileName);

    if (is != null) {
      try {
        bis = new BufferedInputStream(is);
        baos = new ByteArrayOutputStream();

        // Read in the contents of the file.
        while ((bytesRead = bis.read(buf, 0, 1024)) != -1) {
          baos.write(buf, 0, bytesRead);
        }
      } catch (IOException ioe) {
        System.err.println("Error loading file from classpath : " + fileName);
        System.exit(-1);
      }

      // Get file contents as a byte array.
      content = baos.toByteArray();
    }

    return content;
  }
  
}
