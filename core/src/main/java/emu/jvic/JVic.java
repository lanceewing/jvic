package emu.jvic;

import java.util.Map;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

import emu.jvic.config.AppConfigItem;
import emu.jvic.memory.RamType;
import emu.jvic.ui.DialogHandler;

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
                AppConfigItem adhocProgram = new AppConfigItem();
                adhocProgram.setName("Adhoc VIC Program");
                adhocProgram.setFilePath(getFilePathForProgramUrl(programUrl));
                if (programUrl.toUpperCase().contains("NTSC")) {
                    adhocProgram.setMachineType("NTSC");
                } else {
                    adhocProgram.setMachineType("PAL");
                }
                adhocProgram.setRam("RAM_AUTO");
                appConfigItem = adhocProgram;
            }
        }
        
        setScreen(homeScreen);
        
        if (appConfigItem != null) {
            applyConfigArgs(appConfigItem, args);
            homeScreen.processProgramSelection(appConfigItem);
        }
    }
    
    /**
     * Checks for and applies any additional config that might be provided in the args Map.
     * 
     * @param adhocProgram
     * @param args
     */
    private void applyConfigArgs(AppConfigItem appConfigItem, Map<String, String> args) {
        applyRamConfig(appConfigItem, args.get("ram"));
        applyTvConfig(appConfigItem, args.get("tv"));
        applyProgramType(appConfigItem, args.get("type"));
        if (args.containsKey("entry")) {
            appConfigItem.setEntryName(args.get("entry"));
        }
        if (args.containsKey("addr")) {
            appConfigItem.setLoadAddress(args.get("addr"));
        }
        if (args.containsKey("cmd")) {
            appConfigItem.setAutoRunCommand(args.get("cmd"));
        }
    }
    
    /**
     * Checks for and applies program type config that might be provided in the args Map.
     * 
     * @param appConfigItem
     * @param tv One of TAPE, DISK, PRG, CART, PCV.
     */
    private void applyProgramType(AppConfigItem appConfigItem, String type) {
        if (type != null) {
            switch (type.toUpperCase()) {
                case "TAPE":
                    appConfigItem.setFileType("TAPE");
                    break;
                case "DISK":
                    appConfigItem.setFileType("DISK");
                    break;
                case "PRG":
                    appConfigItem.setFileType("PRG");
                    break;
                case "CART":
                    appConfigItem.setFileType("CART");
                    break;
                case "PCV":
                    appConfigItem.setFileType("PCV");
                    break;
                default:
                    break;
            }
        }
    }
    
    /**
     * Checks for and applies TV config that might be provided in the args Map.
     * 
     * @param appConfigItem
     * @param tv Either "PAL" or "NTSC"; or null if not set.
     */
    private void applyTvConfig(AppConfigItem appConfigItem, String tv) {
        if (tv != null) {
            switch (tv.toUpperCase()) {
                case "PAL":
                    appConfigItem.setMachineType("PAL");
                    break;
                case "NTSC":
                    appConfigItem.setMachineType("NTSC");
                    break;
                case "VIC44":
                    appConfigItem.setMachineType("VIC44");
                    break;
                case "VIC44K":
                    appConfigItem.setMachineType("VIC44K");
                default:
                    break;
            }
        }
    }
    
    /**
     * Checks for and applies RAM config that might be provided in the args Map.
     * 
     * @param appConfigItem
     * @param ram
     */
    private void applyRamConfig(AppConfigItem appConfigItem, String ram) {
        if (ram != null) {
            switch (ram.toUpperCase()) {
                case "0K":
                case "UNEXPANDED":
                case "UNEXP":
                    appConfigItem.setRam(RamType.RAM_UNEXPANDED.name());
                    break;
                case "3K":
                    appConfigItem.setRam(RamType.RAM_3K.name());
                    break;
                case "8K":
                    appConfigItem.setRam(RamType.RAM_8K.name());
                    break;
                case "16K":
                    appConfigItem.setRam(RamType.RAM_16K.name());
                    break;
                case "24K": 
                    appConfigItem.setRam(RamType.RAM_24K.name());
                    break;
                case "32K":
                    appConfigItem.setRam(RamType.RAM_32K.name());
                    break;
                case "35K":
                    appConfigItem.setRam(RamType.RAM_35K.name());
                    break;
                default:
                    // Not recognised.
                    break;
            }
        }
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
            AppConfigItem appConfigItem = new AppConfigItem();
            appConfigItem.setName("Adhoc VIC Program");
            appConfigItem.setFilePath(filePath);
            appConfigItem.setFileType("ABSOLUTE");
            if (filePath.toUpperCase().contains("NTSC")) {
                appConfigItem.setMachineType("NTSC");
            } else {
                appConfigItem.setMachineType("PAL");
            }
            appConfigItem.setRam("RAM_AUTO");
            appConfigItem.setFileData(fileData);
            homeScreen.processProgramSelection(appConfigItem);
        }
    }
}
