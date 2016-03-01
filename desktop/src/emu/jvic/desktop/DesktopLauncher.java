package emu.jvic.desktop;

import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;

import emu.jvic.JVicGdx;
import emu.jvic.ui.ConfirmHandler;
import emu.jvic.ui.ConfirmResponseHandler;

public class DesktopLauncher {
	public static void main (String[] arg) {
		LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();
		config.width = 540;
		config.height = 960;
		new LwjglApplication(new JVicGdx(new ConfirmHandler() {

      @Override
      public void confirm(String message, ConfirmResponseHandler confirmResponseHandler) {
        // TODO: Implement confirmation dialog for desktop version.
      }
		  
		}), config);
	}
}
