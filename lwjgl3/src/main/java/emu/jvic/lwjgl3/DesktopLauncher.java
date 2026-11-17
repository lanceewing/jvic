package emu.jvic.lwjgl3;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter;

import emu.jvic.JVic;
import emu.jvic.lwjgl3.headless.HeadlessJVicRunner;
import emu.jvic.lwjgl3.monitor.BinaryMonitorServer;
import emu.jvic.lwjgl3.monitor.RemoteTextMonitor;
import emu.jvic.monitor.MonitorSession;

/** Launches the desktop (LWJGL3) application. */
public class DesktopLauncher {
    
    public static void main(String[] args) {
        Map<String, String> argsMap = convertArgsToMap(args);
        if ("true".equalsIgnoreCase(argsMap.get("headless"))) {
            // No window, so none of the windowing setup below. Checked before
            // StartupHelper, which may start a new JVM and lose standard input.
            System.exit(HeadlessJVicRunner.run(argsMap));
        }
        if (StartupHelper.startNewJvmIfRequired()) return; // This handles macOS support and helps on Windows.
        createApplication(argsMap);
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
        DesktopJVicRunner desktopJVicRunner = new DesktopJVicRunner(
                new DesktopKeyboardMatrix(), new DesktopPixelData(), 
                new DesktopSoundGenerator());
        startRemoteMonitor(desktopJVicRunner, argsMap.get("remotemonitor"));
        startBinaryMonitor(desktopJVicRunner, argsMap.get("binarymonitor"));
        setMonitorStartup(desktopJVicRunner, argsMap);
        JVic jvic = new JVic(desktopJVicRunner, desktopDialogHandler, argsMap);
        return new Lwjgl3Application(jvic, getDefaultConfiguration(jvic));
    }

    /**
     * Starts VICE's remote text monitor if the remotemonitor option is given:
     * {@code remotemonitor=on} for port 6510, {@code remotemonitor=<port>}, or VICE's
     * address form {@code remotemonitor=ip4://127.0.0.1:<port>}. It always listens on
     * 127.0.0.1 only.
     */
    private static void startRemoteMonitor(DesktopJVicRunner runner, String option) {
        if ((option == null) || option.equalsIgnoreCase("off") || option.equalsIgnoreCase("false")) {
            return;
        }
        int port = RemoteTextMonitor.DEFAULT_PORT;
        String portText = option.substring(option.lastIndexOf(':') + 1);
        if (!portText.isEmpty() && portText.chars().allMatch(Character::isDigit)) {
            port = Integer.parseInt(portText);
        }
        try {
            runner.startRemoteMonitor(port);
            System.out.println("Remote monitor listening on 127.0.0.1:" + port);
        } catch (IOException e) {
            System.err.println("Could not start the remote monitor on port " + port + ": " + e.getMessage());
        }
    }

    /**
     * Starts VICE's binary monitor if the binarymonitor option is given: on (port
     * 6502), a port, or VICE's ip4://127.0.0.1:port form. Only on 127.0.0.1.
     */
    private static void startBinaryMonitor(DesktopJVicRunner runner, String option) {
        if ((option == null) || option.equalsIgnoreCase("off") || option.equalsIgnoreCase("false")) {
            return;
        }
        int port = BinaryMonitorServer.DEFAULT_PORT;
        String portText = option.substring(option.lastIndexOf(':') + 1);
        if (!portText.isEmpty() && portText.chars().allMatch(Character::isDigit)) {
            port = Integer.parseInt(portText);
        }
        try {
            runner.startBinaryMonitor(port);
            System.out.println("Binary monitor listening on 127.0.0.1:" + port);
        } catch (IOException e) {
            System.err.println("Could not start the binary monitor on port " + port + ": " + e.getMessage());
        }
    }

    /**
     * Applies VICE's startup options for the monitor, if given: moncommands=<file>,
     * monlog=<file> and initbreak=reset|ready|<hex address>.
     */
    private static void setMonitorStartup(DesktopJVicRunner runner, Map<String, String> argsMap) {
        String monCommands = argsMap.get("moncommands");
        String monLog = argsMap.get("monlog");
        String initBreakText = argsMap.get("initbreak");
        if ((monCommands == null) && (monLog == null) && (initBreakText == null)) {
            return;
        }
        int initBreak = MonitorSession.INITBREAK_NONE;
        if (initBreakText != null) {
            try {
                initBreak = MonitorSession.parseInitBreak(initBreakText);
            } catch (IllegalArgumentException e) {
                System.err.println("jvic: " + e.getMessage());
            }
        }
        runner.setMonitorStartup(monCommands, monLog, initBreak);
    }

    private static Lwjgl3ApplicationConfiguration getDefaultConfiguration(JVic jvic) {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("");
        configuration.useVsync(true);
        //// Limits FPS to the refresh rate of the currently active monitor.
        configuration.setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate);
        //// If you remove the above line and set Vsync to false, you can get unlimited FPS, which can be
        //// useful for testing performance, but can also be very stressful to some hardware.
        //// You may also need to configure GPU drivers to fully disable Vsync; this can cause screen tearing.
        //configuration.setWindowedMode(540, 960);
        configuration.setWindowedMode(1440, 960);
        configuration.setWindowIcon("jvic-128x128.png", "jvic-64x64.png", "jvic-32x32.png", "jvic-16x16.png");
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