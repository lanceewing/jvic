package emu.jvic.desktop;

import javax.swing.JOptionPane;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;

import emu.jvic.JVic;
import emu.jvic.ui.ConfirmHandler;
import emu.jvic.ui.ConfirmResponseHandler;

public class DesktopLauncher {
	public static void main (String[] arg) {
		LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();
		config.width = 540;
		config.height = 960;
		config.title = "JVic  v0.1";
		new LwjglApplication(new JVic(new ConfirmHandler() {

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
		  
		}), config);
	}
}
