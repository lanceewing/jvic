package emu.jvic.lwjgl3;

import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter;

import emu.jvic.JVic;

/** Launches the desktop (LWJGL3) application. */
public class DesktopLauncher {
    
    public static void main(String[] args) {
        if (StartupHelper.startNewJvmIfRequired()) return; // This handles macOS support and helps on Windows.
        createApplication(convertArgsToMap(args));
    }

    private static Map<String, String> convertArgsToMap(String[] args) {
        Map<String, String> argsMap = new HashMap<>();
        if ((args != null) && (args.length > 0)) {
            for (String arg : args) {
                int equalsIndex = arg.indexOf('=');
                if (equalsIndex != -1) {
                    String name = arg.substring(0, equalsIndex);
                    String value = arg.endsWith("=")? "" : arg.substring(equalsIndex + 1);
                    argsMap.put(name, value);
                }
            }
        }
        return argsMap;
    }
    
    private static Lwjgl3Application createApplication(Map<String, String> argsMap) {
        DesktopDialogHandler desktopDialogHandler = new DesktopDialogHandler();
        DesktopJVicRunner desktopJOricRunner = new DesktopJVicRunner(
                new DesktopKeyboardMatrix(), new DesktopPixelData(), 
                new DesktopSoundGenerator());
        JVic jvic = new JVic(desktopJOricRunner, desktopDialogHandler, argsMap);
        return new Lwjgl3Application(jvic, getDefaultConfiguration(jvic));
    }

    private static Lwjgl3ApplicationConfiguration getDefaultConfiguration(JVic jvic) {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("JVic");
        configuration.useVsync(true);
        //// Limits FPS to the refresh rate of the currently active monitor.
        configuration.setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate);
        //// If you remove the above line and set Vsync to false, you can get unlimited FPS, which can be
        //// useful for testing performance, but can also be very stressful to some hardware.
        //// You may also need to configure GPU drivers to fully disable Vsync; this can cause screen tearing.
        //configuration.setWindowedMode(540, 960);
        configuration.setWindowedMode(960, 640);
        configuration.setWindowIcon("libgdx128.png", "libgdx64.png", "libgdx32.png", "libgdx16.png");
        configuration.setWindowListener(new Lwjgl3WindowAdapter() {
            @Override
            public void filesDropped (String[] files) {
                // We support only a single file.
                if (files.length == 1) {
                    jvic.fileDropped(files[0], null);
                }
            }
        });
        return configuration;
    }
}