package emu.jvic;

import java.util.Map;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

import emu.jvic.config.AppConfigItem;
import emu.jvic.config.AppConfigItem.FileLocation;
import emu.jvic.settings.LaunchOptions;
import emu.jvic.settings.ResolvedSettings;
import emu.jvic.settings.ProgramIdentity;
import emu.jvic.settings.SettingsResolver;
import emu.jvic.settings.SettingsStore;
import emu.jvic.ui.DialogHandler;
import emu.jvic.ui.settings.SettingsPanel;

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
     * Platform specific JVicRunner implementation.
     */
    private JVicRunner jvicRunner;

    /**
     * Invoked by JVic whenever it would like to show a dialog, such as when it
     * needs the user to confirm an action, or to choose a file.
     */
    private DialogHandler dialogHandler;

    /**
     * For desktop, contains command line args. For HTML5, the hash and/or query parameters.
     */
    private Map<String, String> args;
    
    /**
     * JVic's saved preferences.
     */
    private Preferences preferences;

    /**
     * JVic's application screenshot storage.
     */
    private Preferences screenshotStore;

    /**
     * The user's saved settings.
     */
    private SettingsStore settingsStore;

    /**
     * The options jvic was launched with (URL parameters, autorun-config.txt or desktop
     * arguments), parsed from args.
     */
    private LaunchOptions launchOptions;

    /**
     * The program last started (its own configuration, before settings were applied),
     * and the launch options it was started with, so that its settings can be resolved
     * again when they change.
     */
    private AppConfigItem runningProgram;
    private LaunchOptions runningLaunchOptions;

    /**
     * The settings screen, shared by the home screen and the machine screen.
     */
    private SettingsPanel settingsPanel;

    /**
     * Constructor for JVic.
     * 
     * @param confirmHandler
     */
    public JVic(JVicRunner jvicRunner, DialogHandler dialogHandler, Map<String, String> args) {
        this.jvicRunner = jvicRunner;
        this.dialogHandler = dialogHandler;
        this.args = args;
    }

    @Override
    public void create() {
        preferences = Gdx.app.getPreferences("jvic.preferences");
        screenshotStore = Gdx.app.getPreferences("jvic_screens.store");
        settingsStore = new SettingsStore(Gdx.app.getPreferences(SettingsStore.PREFERENCES_NAME));
        launchOptions = LaunchOptions.parse(args);
        settingsPanel = new SettingsPanel(new SpriteBatch(), settingsStore, dialogHandler);
        machineScreen = new MachineScreen(this, jvicRunner, dialogHandler);
        homeScreen = new HomeScreen(this, dialogHandler);

        AppConfigItem appConfigItem = null;
        
        if ((args != null) && (args.size() > 0)) {
            if (args.containsKey("uri")) {
                // Start by checking to see if the programs.json has an entry.
                appConfigItem = homeScreen.getAppConfigItemByProgramUri(args.get("uri"));
            }
            else if (args.containsKey("url")) {
                String programUrl = args.get("url");
                appConfigItem = AppConfigItem.createAdhoc(getFilePathForProgramUrl(programUrl),
                        FileLocation.EXTERNAL);
            }
            else if (args.containsKey("filePath")) {
                appConfigItem = AppConfigItem.createAdhoc(args.get("filePath"), FileLocation.ABSOLUTE);
            }
        }
        
        setScreen(homeScreen);
        
        if (appConfigItem != null) {
            // The launch options apply only to this first launch, not to later ones
            // started from the home screen.
            homeScreen.processProgramSelection(appConfigItem, launchOptions);
        }
    }

    /**
     * Works out the configuration to start a program with, from its own configuration,
     * the user's saved settings and the launch options. The program's own configuration
     * isn't changed.
     *
     * @param program The program's own configuration.
     * @param launch  The launch options for this launch, or {@link LaunchOptions#NONE}.
     *
     * @return The configuration to start the machine with.
     */
    public AppConfigItem resolveProgramConfig(AppConfigItem program, LaunchOptions launch) {
        return new SettingsResolver(settingsStore).resolveProgram(program, launch,
                getProgramKey(program), isSettingsHidden());
    }

    /**
     * Remembers the program being started, so that its settings can be resolved again
     * while it runs.
     *
     * @param program The program's own configuration.
     * @param launch  The launch options it was started with.
     */
    public void setRunningProgram(AppConfigItem program, LaunchOptions launch) {
        this.runningProgram = program;
        this.runningLaunchOptions = launch;
    }

    /**
     * Resolves the settings for the program last started, as they are now.
     *
     * @return The settings, or null if no program has been started.
     */
    public ResolvedSettings resolveRunningProgram() {
        if (runningProgram == null) {
            return null;
        }
        return new SettingsResolver(settingsStore).resolve(runningProgram, runningLaunchOptions,
                getProgramKey(runningProgram), isSettingsHidden());
    }

    /**
     * Resolves the settings for the program last started as they would be without its
     * launch options: what the program uses from now on, which is what the settings
     * screen shows for "this program".
     *
     * @return The settings, or null if no program has been started.
     */
    public ResolvedSettings resolveRunningProgramWithoutLaunchOptions() {
        if (runningProgram == null) {
            return null;
        }
        return new SettingsResolver(settingsStore).resolve(runningProgram, LaunchOptions.NONE,
                getProgramKey(runningProgram), isSettingsHidden());
    }

    /**
     * Works out the configuration to restart the program last started with, from its
     * settings now. Its launch options still apply.
     *
     * @return The configuration, or null if no program has been started.
     */
    public AppConfigItem resolveRunningProgramConfig() {
        return (runningProgram != null) ? resolveProgramConfig(runningProgram, runningLaunchOptions) : null;
    }

    /**
     * @return The program last started (its own configuration), or null if none.
     */
    public AppConfigItem getRunningProgram() {
        return runningProgram;
    }

    /**
     * Gets the settings screen, which the home screen and the machine screen share.
     *
     * @return The SettingsPanel.
     */
    public SettingsPanel getSettingsPanel() {
        return settingsPanel;
    }

    /**
     * Gets the key that identifies a program's quick save slot and per-program settings.
     *
     * @param program The program's configuration. May be null.
     *
     * @return The key, safe to use as a file name.
     */
    public String getProgramKey(AppConfigItem program) {
        String name = ProgramIdentity.keyName(program);
        return ProgramIdentity.key(jvicRunner.slugify((name != null) ? name : ""));
    }

    /**
     * Returns true if this deployment hides the settings screen (settings=hidden). Saved
     * settings are then ignored for the whole session, so the deployment always runs as
     * its author set it up.
     *
     * @return true if settings are hidden.
     */
    public boolean isSettingsHidden() {
        return (launchOptions != null) && launchOptions.isSettingsHidden();
    }

    /**
     * Gets the user's saved settings.
     *
     * @return The SettingsStore.
     */
    public SettingsStore getSettingsStore() {
        return settingsStore;
    }

    /**
     * Gets the filePath to use for the given program URL, when used with the
     * ?url request parameter.
     * 
     * @param programUrl
     * 
     * @return
     */
    private String getFilePathForProgramUrl(String programUrl) {
        if ((programUrl.startsWith("http://localhost/")) || 
            (programUrl.startsWith("http://localhost:")) || 
            (programUrl.startsWith("https://localhost/")) || 
            (programUrl.startsWith("https://localhost:")) ||
            (programUrl.startsWith("http://127.0.0.1/")) || 
            (programUrl.startsWith("http://127.0.0.1:")) || 
            (programUrl.startsWith("https://127.0.0.1/")) || 
            (programUrl.startsWith("https://127.0.0.1:"))) {
            return programUrl;
        } else {
            return ("https://vic20.games/programs?url=" + programUrl);
        }
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
     * Gets the JVicRunner.
     * 
     * @return the JVicRunner.
     */
    public JVicRunner getJVicRunner() {
        return jvicRunner;
    }
    
    /**
     * Gets the Preferences for JVic.
     * 
     * @return The Preferences for JVic.
     */
    public Preferences getPreferences() {
        return preferences;
    }

    /**
     * Gets the screenshot store for JVic.
     * 
     * @return The screenshot store for JVic.
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
        settingsPanel.dispose();

        // Save the preferences when the emulator is closed.
        preferences.flush();
        screenshotStore.flush();
    }
    
    /**
     * Invoked when a program file (DSK, TAP or ZIP) is dropped onto the home screen.
     * 
     * @param filePath
     * @param fileData
     */
    public void fileDropped(String filePath, byte[] fileData) {
        // File drop is only 
        if (getScreen() == homeScreen) {
            AppConfigItem appConfigItem = AppConfigItem.createAdhoc(filePath, FileLocation.LOCAL);
            appConfigItem.setFileData(fileData);
            homeScreen.processProgramSelection(appConfigItem);
        }
    }
}
