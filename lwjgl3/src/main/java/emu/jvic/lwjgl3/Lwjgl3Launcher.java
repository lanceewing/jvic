package emu.jvic.lwjgl3;

import javax.swing.JOptionPane;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

import emu.jvic.JVic;
import emu.jvic.ui.ConfirmHandler;
import emu.jvic.ui.ConfirmResponseHandler;

/** Launches the desktop (LWJGL3) application. */
public class Lwjgl3Launcher {
    public static void main(String[] args) {
        if (StartupHelper.startNewJvmIfRequired()) return; // This handles macOS support and helps on Windows.
        createApplication();
    }

    private static Lwjgl3Application createApplication() {
        return new Lwjgl3Application(new JVic(new ConfirmHandler() {
            @Override
            public void confirm(final String message, final ConfirmResponseHandler responseHandler) {
              Gdx.app.postRunnable(new Runnable() {
                @Override
                public void run() {
                  int output = JOptionPane.showConfirmDialog(null, "Please confirm", message, JOptionPane.YES_NO_OPTION);
                  if (output != 0) {
                    responseHandler.no();
                  } else {
                    responseHandler.yes();
                  }
                }
              });
            }
        }), getDefaultConfiguration());
    }

    private static Lwjgl3ApplicationConfiguration getDefaultConfiguration() {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("JVic");
        configuration.useVsync(true);
        //// Limits FPS to the refresh rate of the currently active monitor.
        configuration.setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate);
        //// If you remove the above line and set Vsync to false, you can get unlimited FPS, which can be
        //// useful for testing performance, but can also be very stressful to some hardware.
        //// You may also need to configure GPU drivers to fully disable Vsync; this can cause screen tearing.
        configuration.setWindowedMode(540, 960);
        configuration.setWindowIcon("libgdx128.png", "libgdx64.png", "libgdx32.png", "libgdx16.png");
        return configuration;
    }
}