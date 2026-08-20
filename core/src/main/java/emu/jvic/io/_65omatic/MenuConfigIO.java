package emu.jvic.io._65omatic;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

import emu.jvic.MachineType;
import emu.jvic.memory.MemoryMappedChip;
import emu.jvic.memory.RamType;

/**
 * MenuConfigIO
 * 
 * This class implements the memory-mapped I/O for the 65omatic menu system's 
 * configuration API, providing a 4-byte I/O register interface at VIC 20 I/O Block 2 
 * ($9800-$9803). The API follows a command-response protocol with status codes,
 * allowing the menu ROM to configure KERNAL variant and RAM expansion settings.
 * 
 * Configuration is persisted using the libgdx Preferences API, which provides
 * platform-appropriate storage.
 * 
 * @author Lance Ewing
 */
public class MenuConfigIO extends MemoryMappedChip {

    // 65omatic menu I/O register addresses (I/O Block 2)
    private static final int CONFIG_CMD            = 0;    // Command register (write-only)
    private static final int CONFIG_PARAM          = 1;    // Parameter register (read/write)
    private static final int CONFIG_STATUS         = 2;    // Status register (read-only)
    private static final int BOOT_FLAGS            = 3;    // Boot flags (read/write)

    // 65omatic I/O command opcodes
    private static final int CMD_NOP               = 0x00;
    private static final int CMD_SET_KERNAL        = 0x01;
    private static final int CMD_SET_RAM_CONFIG    = 0x02;
    private static final int CMD_GET_CONFIG        = 0x03;
    private static final int CMD_TRIGGER_RESET     = 0x04;
    private static final int CMD_WRITE_CONFIG      = 0x05;
    private static final int CMD_GET_BOOT_MODE     = 0x06;
    private static final int CMD_SET_BOOT_ON_RESET = 0x07;

    // Status codes returned by commands (read from CONFIG_STATUS register)
    private static final int STATUS_OK             = 0x00;
    private static final int STATUS_BUSY           = 0x01;
    private static final int STATUS_ERROR          = 0x02;

    // Boot mode constants
    public static final int BOOT_ON_FIRST_BOOT    = 0x00;
    public static final int BOOT_SKIP_MENU        = 0xFF;

    // RAM configuration constants
    private static final int RAM_CONFIG_0K         = 0;    // No expansion
    private static final int RAM_CONFIG_3K         = 1;    // 3K: $0400-$0FFF
    private static final int RAM_CONFIG_8K         = 2;    // 8K: $2000-$3FFF
    private static final int RAM_CONFIG_16K        = 3;    // 16K: $2000-$3FFF + $4000-$5FFF
    private static final int RAM_CONFIG_24K        = 4;    // 24K: $2000-$7FFF
    private static final int RAM_CONFIG_32K        = 5;    // 32K: $2000-$3FFF + $4000-$5FFF + $6000-$7FFF + $A000-$BFFF
    private static final int RAM_CONFIG_35K        = 6;    // 35K: all expansion

    // KERNAL type constants
    private static final int KERNAL_PAL            = 0;
    private static final int KERNAL_NTSC           = 1;

    // Preference storage constants
    private static final String PREFS_NAME         = "65omatic_config";
    private static final String PREFS_KERNAL_TYPE  = "kernal_type";
    private static final String PREFS_RAM_CONFIG   = "ram_config";
    private static final String PREFS_BOOT_MODE    = "boot_mode";
    private static final String PREFS_FIRST_BOOT   = "first_boot";

    // Default configuration (first boot)
    private static final int DEFAULT_KERNAL_TYPE   = KERNAL_PAL;         // 0
    private static final int DEFAULT_RAM_CONFIG    = RAM_CONFIG_8K;      // 2
    private static final int DEFAULT_BOOT_MODE     = BOOT_ON_FIRST_BOOT; // 0x00

    // In-memory configuration state (persisted separately in flash)
    private int kernalType;            // Current KERNAL type (0=PAL, 1=NTSC)
    private int ramConfig;             // Current RAM configuration (0-6)
    private int bootMode;              // Boot mode (0x00=show menu, 0xFF=skip menu)
    private boolean isDirty;           // True if config has unsaved changes

    // Last parameter written (used for parameter passing in command protocol)
    private int lastParameter;

    // Status from last command
    private int lastStatus;

    /**
     * Callback interface for system reset. The emulator should implement this
     * to perform a warm reset when CMD_TRIGGER_RESET is executed.
     */
    public interface ResetCallback {
        void triggerReset();
    }

    private ResetCallback resetCallback;

    /**
     * Constructor for MenuConfigIO.
     * 
     * Creates a new MenuConfigIO instance and loads the persisted configuration
     * from preferences. If this is the first boot (no stored configuration), default
     * values are used (PAL KERNAL, 8K RAM expansion).
     * 
     * @param resetCallback Optional callback to invoke when CMD_TRIGGER_RESET is executed.
     *                     If null, reset command will succeed but have no effect.
     */
    public MenuConfigIO(ResetCallback resetCallback) {
        super();
        this.resetCallback = resetCallback;
        loadConfigurationFromPreferences();
    }

    /**
     * Alternative constructor for MenuConfigIO with no reset callback.
     * Useful for testing or when reset functionality is not needed.
     */
    public MenuConfigIO() {
        this(null);
    }

    /**
     * Reads a value from the menu configuration I/O registers.
     * 
     * The I/O block is mapped to $9800-$9803, containing four registers:
     * - $9800 (CONFIG_CMD):    Write-only command register; reads return 0
     * - $9801 (CONFIG_PARAM):  Parameter/response data register
     * - $9802 (CONFIG_STATUS): Status code from last command
     * - $9803 (BOOT_FLAGS):    Current boot mode setting
     * 
     * @param address The address to read (masked to lower 4 bits for register offset)
     * 
     * @return The byte value at the specified register
     */
    @Override
    public int readMemory(int address) {
        System.out.println("MenuConfigIO.readMemory() - address: " + Integer.toHexString(address));

        switch (address & 0x000F) {

            case CONFIG_CMD:
                // CONFIG_CMD (0x9800) is write-only; return 0 for reads
                return 0;

            case CONFIG_PARAM:
                // CONFIG_PARAM (0x9801) returns parameter data or command response
                // For CMD_GET_CONFIG: returns KERNAL type; menu ROM issues separate commands for other values
                // For other commands: returns last parameter written
                return lastParameter;

            case CONFIG_STATUS:
                // CONFIG_STATUS (0x9802) returns status from last command execution
                // Values: 0x00=OK, 0x01=BUSY, 0x02=ERROR
                return lastStatus;

            case BOOT_FLAGS:
                // BOOT_FLAGS (0x9803) returns current boot mode
                // 0x00 = show menu on boot (BOOT_ON_FIRST_BOOT)
                // 0xFF = skip menu on boot (BOOT_SKIP_MENU)
                return bootMode;

            default:
                // Other addresses in I/O Block 2 return 0
                return 0;
        }
    }

    /**
     * Writes a value to the menu configuration I/O registers.
     * 
     * The write protocol:
     * 1. Write parameter to CONFIG_PARAM ($9801)
     * 2. Write command opcode to CONFIG_CMD ($9800)
     * 3. Read status from CONFIG_STATUS ($9802)
     * 4. If command returns data, read from CONFIG_PARAM ($9801)
     * 
     * @param address The address being written (masked to lower 4 bits for register offset)
     * @param value   The byte value to write
     */
    @Override
    public void writeMemory(int address, int value) {
        System.out.println("MenuConfigIO.writeMemory() - address: " + Integer.toHexString(address) + ", value: " + Integer.toHexString(value));

        switch (address & 0x000F) {

            case CONFIG_CMD:
                // CONFIG_CMD (0x9800) receives command opcode, which triggers execution
                handleCommand(value & 0xFF);
                break;

            case CONFIG_PARAM:
                // CONFIG_PARAM (0x9801) holds parameter data for commands
                // This register is written first, before the command is written to CONFIG_CMD
                lastParameter = value & 0xFF;
                break;

            case CONFIG_STATUS:
                // CONFIG_STATUS (0x9802) is read-only; ignore writes
                break;

            case BOOT_FLAGS:
                // BOOT_FLAGS (0x9803) can be written to set boot mode
                // Valid values: 0x00 (show menu) or 0xFF (skip menu)
                if (isValidBootMode(value & 0xFF)) {
                    System.out.println("MenuConfigIO.writeMemory() - BOOT_FLAGS set to: " + Integer.toHexString(value & 0xFF));
                    bootMode = value & 0xFF;
                    isDirty = true;
                    lastStatus = STATUS_OK;
                } else {
                    lastStatus = STATUS_ERROR;
                }
                break;

            default:
                // Other addresses in I/O Block 2 are ignored
                break;
        }
    }

    /**
     * Handles command execution. This method is called when a command opcode is
     * written to the CONFIG_CMD register. It processes the command, updates the
     * in-memory state, and sets appropriate status codes.
     * 
     * @param command The command opcode (0x00-0x07)
     */
    private void handleCommand(int command) {
        switch (command) {
            case CMD_NOP:
                // CMD_NOP (0x00): No Operation
                // Used to test if I/O block is responding; always returns OK
                lastStatus = STATUS_OK;
                break;

            case CMD_SET_KERNAL:
                // CMD_SET_KERNAL (0x01): Set KERNAL Variant
                // Parameter: 0=PAL, 1=NTSC
                // Sets which KERNAL to load on next boot
                if (isValidKernalType(lastParameter)) {
                    System.out.println("MenuConfigIO.handleCommand() - CMD_SET_KERNAL, lastParameter: " + Integer.toHexString(lastParameter));
                    kernalType = lastParameter;
                    isDirty = true;
                    lastStatus = STATUS_OK;
                } else {
                    lastStatus = STATUS_ERROR;
                }
                break;

            case CMD_SET_RAM_CONFIG:
                // CMD_SET_RAM_CONFIG (0x02): Set RAM Configuration
                // Parameter: 0-6 (0=no expansion, 6=35K all blocks)
                // Sets which RAM blocks to enable on next boot
                if (isValidRamConfig(lastParameter)) {
                    System.out.println("MenuConfigIO.handleCommand() - CMD_SET_RAM_CONFIG, lastParameter: " + Integer.toHexString(lastParameter));
                    ramConfig = lastParameter;
                    isDirty = true;
                    lastStatus = STATUS_OK;
                } else {
                    lastStatus = STATUS_ERROR;
                }
                break;

            case CMD_GET_CONFIG:
                // CMD_GET_CONFIG (0x03): Get Current Configuration
                // Returns current in-memory configuration without updating it
                // Returns KERNAL type; menu ROM issues separate commands for other values
                System.out.println("MenuConfigIO.handleCommand() - CMD_GET_CONFIG, returning KERNAL type: " + Integer.toHexString(kernalType));
                lastParameter = kernalType;
                lastStatus = STATUS_OK;
                break;

            case CMD_TRIGGER_RESET:
                // CMD_TRIGGER_RESET (0x04): Trigger System Reset
                // Initiates warm reset to apply current configuration
                // If a reset callback is registered, invoke it
                System.out.println("MenuConfigIO.handleCommand() - CMD_TRIGGER_RESET, invoking reset callback");
                if (resetCallback != null) {
                    resetCallback.triggerReset();
                }
                lastStatus = STATUS_OK;
                break;

            case CMD_WRITE_CONFIG:
                // CMD_WRITE_CONFIG (0x05): Save Configuration to Flash (Preferences)
                // Persists current in-memory configuration to persistent storage
                // Configuration survives power cycles (application restart in emulator)
                System.out.println("MenuConfigIO.handleCommand() - CMD_WRITE_CONFIG, saving configuration to preferences");
                saveConfigurationToPreferences();
                lastStatus = STATUS_OK;
                // Note: In a real system, this would verify CRC and flash write success
                break;

            case CMD_GET_BOOT_MODE:
                // CMD_GET_BOOT_MODE (0x06): Get Current Boot Mode
                // Returns whether system shows menu on next boot
                // 0x00 = show menu, 0xFF = skip menu
                System.out.println("MenuConfigIO.handleCommand() - CMD_GET_BOOT_MODE, returning bootMode: " + Integer.toHexString(bootMode));
                lastParameter = bootMode;
                lastStatus = STATUS_OK;
                break;

            case CMD_SET_BOOT_ON_RESET:
                System.out.println("MenuConfigIO.handleCommand() - CMD_SET_BOOT_ON_RESET, lastParameter: " + Integer.toHexString(lastParameter));
                // CMD_SET_BOOT_ON_RESET (0x07): Set Boot-on-Reset Mode
                // Parameter: 0x00 (show menu) or 0xFF (skip menu on reboot)
                // Controls whether menu appears after system reset
                if (isValidBootMode(lastParameter)) {
                    bootMode = lastParameter;
                    System.out.println("MenuConfigIO.handleCommand() - bootMode set to: " + Integer.toHexString(bootMode));
                    isDirty = true;
                    lastStatus = STATUS_OK;
                } else {
                    lastStatus = STATUS_ERROR;
                }
                break;

            default:
                // Unknown command - set error status
                lastStatus = STATUS_ERROR;
                break;
        }
    }

    /**
     * Loads configuration from persistent storage (libgdx Preferences).
     * If this is the first boot (no stored configuration), default values are loaded.
     * 
     * The configuration is stored as individual preference values in libgdx's
     * cross-platform preferences system, which provides appropriate storage
     * (e.g., SharedPreferences on Android, Registry on Windows).
     */
    private void loadConfigurationFromPreferences() {
        Preferences prefs = Gdx.app.getPreferences(PREFS_NAME);
        
        // Check if this is the first boot by seeing if we have a stored config
        boolean isFirstBoot = prefs.getBoolean(PREFS_FIRST_BOOT, true);

        System.out.println("MenuConfigIO: isFirstBoot=" + isFirstBoot);
        
        if (isFirstBoot) {
            // First boot: use default values
            kernalType = DEFAULT_KERNAL_TYPE;
            ramConfig = DEFAULT_RAM_CONFIG;
            bootMode = DEFAULT_BOOT_MODE;
            isDirty = false;
        } else {
            // Load from persistent storage
            kernalType = prefs.getInteger(PREFS_KERNAL_TYPE, DEFAULT_KERNAL_TYPE);
            ramConfig = prefs.getInteger(PREFS_RAM_CONFIG, DEFAULT_RAM_CONFIG);
            bootMode = prefs.getInteger(PREFS_BOOT_MODE, DEFAULT_BOOT_MODE);
            isDirty = false;
        }
        
        lastParameter = 0;
        lastStatus = STATUS_OK;
    }

    /**
     * Saves the current in-memory configuration to persistent storage (libgdx Preferences).
     * This implements the CMD_WRITE_CONFIG command, persisting the configuration so it
     * survives application restarts.
     * 
     * In a real 65omatic system, this would write a 16-byte configuration structure
     * with CRC16 checksum to flash memory. Here we use libgdx Preferences for
     * cross-platform compatibility.
     */
    private void saveConfigurationToPreferences() {
        Preferences prefs = Gdx.app.getPreferences(PREFS_NAME);
        
        // Store individual configuration values
        prefs.putInteger(PREFS_KERNAL_TYPE, kernalType);
        prefs.putInteger(PREFS_RAM_CONFIG, ramConfig);
        prefs.putInteger(PREFS_BOOT_MODE, bootMode);
        
        // Mark that we've completed first boot (configuration exists)
        prefs.putBoolean(PREFS_FIRST_BOOT, false);
        
        // Flush to storage
        prefs.flush();
        
        // Clear dirty flag after successful save
        isDirty = false;
    }

    /**
     * Validates that the KERNAL type parameter is valid.
     * 
     * @param kernalType The KERNAL type value to validate
     * @return true if valid (0=PAL or 1=NTSC), false otherwise
     */
    private boolean isValidKernalType(int kernalType) {
        return (kernalType == KERNAL_PAL || kernalType == KERNAL_NTSC);
    }

    /**
     * Validates that the RAM configuration parameter is valid.
     * 
     * @param ramConfig The RAM configuration value to validate
     * @return true if valid (0-6), false if invalid (7+)
     */
    private boolean isValidRamConfig(int ramConfig) {
        return (ramConfig >= RAM_CONFIG_0K && ramConfig <= RAM_CONFIG_35K);
    }

    /**
     * Validates that the boot mode parameter is valid.
     * 
     * @param bootMode The boot mode value to validate
     * @return true if valid (0x00 or 0xFF), false otherwise
     */
    private boolean isValidBootMode(int bootMode) {
        return (bootMode == BOOT_ON_FIRST_BOOT || bootMode == BOOT_SKIP_MENU);
    }

    /**
     * Getter method for the current KERNAL type configuration.
     * The emulator uses this to determine which KERNAL ROM to load.
     * 
     * @return The current KERNAL type (KERNAL_PAL=0 or KERNAL_NTSC=1)
     */
    public int getKernalType() {
        return kernalType;
    }

    /**
     * Getter method for the current KERNAL machine type.
     * 
     * @return The current KERNAL machine type (MachineType.PAL or MachineType.NTSC)
     */
    public MachineType getKernalMachineType() {
        return (kernalType == KERNAL_PAL) ? MachineType.PAL : MachineType.NTSC;
    }

    /**
     * Getter method for the current RAM expansion configuration.
     * The emulator uses this to determine which RAM blocks to enable.
     * 
     * @return The current RAM configuration code (0-6)
     *         0 = no expansion
     *         1 = 3K ($0400-$0FFF)
     *         2 = 8K ($2000-$3FFF)
     *         3 = 16K ($2000-$5FFF)
     *         4 = 24K ($2000-$7FFF)
     *         5 = 32K ($2000-$3FFF + $4000-$5FFF + $6000-$7FFF + $A000-$BFFF)
     *         6 = 35K (all expansion blocks)
     */
    public int getRamConfig() {
        return ramConfig;
    }

    public RamType getRamType() {
        switch (ramConfig) {
            case RAM_CONFIG_0K:
                return RamType.RAM_UNEXPANDED;
            case RAM_CONFIG_3K:
                return RamType.RAM_3K;
            case RAM_CONFIG_8K:
                return RamType.RAM_8K;
            case RAM_CONFIG_16K:
                return RamType.RAM_16K;
            case RAM_CONFIG_24K:
                return RamType.RAM_24K;
            case RAM_CONFIG_32K:
                return RamType.RAM_32K;
            case RAM_CONFIG_35K:
                return RamType.RAM_35K;
            default:
                return RamType.RAM_AUTO;
        }
    }

    /**
     * Getter method for the current boot mode.
     * 
     * @return The current boot mode (BOOT_ON_FIRST_BOOT=0x00 or BOOT_SKIP_MENU=0xFF)
     */
    public int getBootMode() {
        return bootMode;
    }

    /**
     * Checks if configuration has unsaved changes.
     * 
     * @return true if configuration has been modified but not saved to preferences
     */
    public boolean isDirty() {
        return isDirty;
    }
}

