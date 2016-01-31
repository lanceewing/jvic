package emu.jvic;

import com.badlogic.gdx.Game;

/**
 * The main entry point in to the cross-platform part of the JVic emulator. A multi-screen
 * libGDX application needs to extend the Game class, which is what we do here. It allows 
 * us to have other screens, such as various menu screens.
 * 
 * @author Lance Ewing
 */
public class JVicGdx extends Game {
  
  /**
   * This is the main screen of the JVic emulator.
   */
  private MachineScreen machineScreen;
	
	@Override
	public void create () {
	  machineScreen = new MachineScreen();
		setScreen(machineScreen);
	}
}
