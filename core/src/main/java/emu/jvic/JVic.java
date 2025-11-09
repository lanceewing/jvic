package emu.jvic;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

import emu.jvic.ui.ConfirmHandler;

/**
 * The main entry point in to the cross-platform part of the JVic emulator. A
 * multi-screen libGDX application needs to extend the Game class, which is what
 * we do here. It allows us to have other screens, such as various menu screens.
 * 
 * @author Lance Ewing
 */
public class JVic extends Game {

    /**
     * This is the screen that is used to show the running emulation.
     */
    private MachineScreen machineScreen;

    /**
     * This is the screen that shows the boot options and programs to load.
     */
    private HomeScreen homeScreen;

    /**
     * Invoked by JVic whenever it would like the user to confirm an action.
     */
    private ConfirmHandler confirmHandler;

    /**
     * JOric's saved preferences.
     */
    private Preferences preferences;

    /**
     * JOric's application screenshot storage.
     */
    private Preferences screenshotStore;

    /**
     * Constructor for JVicGdx.
     * 
     * @param confirmHandler
     */
    public JVic(ConfirmHandler confirmHandler) {
        this.confirmHandler = confirmHandler;
    }

    @Override
    public void create() {
        preferences = Gdx.app.getPreferences("jvic.preferences");
        screenshotStore = Gdx.app.getPreferences("jvic_screens.store");

        machineScreen = new MachineScreen(this, confirmHandler);
        homeScreen = new HomeScreen(this, confirmHandler);
        setScreen(homeScreen);
    }

    /**
     * Gets the MachineScreen.
     * 
     * @return The MachineScreen.
     */
    public MachineScreen getMachineScreen() {
        return machineScreen;
    }

    /**
     * Gets the HomeScreen.
     * 
     * @return the HomeScreen.
     */
    public HomeScreen getHomeScreen() {
        return homeScreen;
    }

    /**
     * Gets the Preferences for JOric.
     * 
     * @return The Preferences for JOric.
     */
    public Preferences getPreferences() {
        return preferences;
    }

    /**
     * Gets the screenshot store for JOric.
     * 
     * @return The screenshot store for JOric.
     */
    public Preferences getScreenshotStore() {
        return screenshotStore;
    }

    @Override
    public void dispose() {
        super.dispose();

        // For now we'll dispose the MachineScreen here. As the emulator grows and
        // adds more screens, this may be managed in a different way. Note that the
        // super dispose does not call dispose on the screen.
        machineScreen.dispose();
        homeScreen.dispose();

        // Save the preferences when the emulator is closed.
        preferences.flush();
        screenshotStore.flush();
    }
}
