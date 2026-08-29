package io.cloudchains.app.util;

import com.google.common.base.Preconditions;
import io.cloudchains.app.App;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class ConfigHelper {
    private final static LogManager LOGMANAGER = LogManager.getLogManager();
    private final static Logger LOGGER = LOGMANAGER.getLogger(Logger.GLOBAL_LOGGER_NAME);

    private String tickerStr;
    private File file;
    private FileWriter fileWriter;
    private boolean validConfiguration;

    private double fee;
    private boolean feeFlat;
    private boolean rpcEnabled;
    private String rpcUsername;
    private String rpcPassword;
    private int rpcPort;
    private int addressCount;

    // Override specific configuration directory (useful in unit tests)
    public static String CONFIG_DIR = ""; // Must not end with [/], e.g. /home/user/.config, not /home/user/.config/
    private static volatile boolean readOnlyExistingProfile;
    private static boolean configurationOpened;

    public ConfigHelper(String tickerStr) {
        synchronized (ConfigHelper.class) {
            configurationOpened = true;
        }
        this.tickerStr = tickerStr;

        try {
            file = this.getFile();
            if (file == null)
                return;
            loadConfig();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[config] Failed to initialize config for " + tickerStr, e);
        }
    }

    public void loadConfig() {
        validConfiguration = false;
        if (file == null)
            return;
        try {
            String rawConfig = new String(Files.readAllBytes(file.toPath()));
            if (rawConfig.isEmpty()) {
                if (readOnlyExistingProfile) {
                    LOGGER.log(Level.WARNING, "[config] Read-only profile has an empty configuration for " + tickerStr);
                    return;
                }
                fee = 0.0001;
                feeFlat = true;
                rpcEnabled = false;
                rpcUsername = "";
                rpcPassword = "";
                if (this.tickerStr.equalsIgnoreCase("master")) {
                    rpcPort = 9955;
                } else {
                    rpcPort = -1000;
                }
                addressCount = 0;

                writeConfig();
                validConfiguration = true;
                return;
            }

            JSONObject config = new JSONObject(rawConfig);

            final String[] configKeys = new String[]{
                    "fee",
                    "feeFlat",
                    "rpcEnabled",
                    "rpcUsername",
                    "rpcPassword",
                    "rpcPort",
                    "addressCount"
            };

            for (String configKey : configKeys) {
                if (!config.has(configKey)) {
                    LOGGER.log(Level.FINER, "[config] Warning: Configuration file does not contain required value '" + configKey + "'. This will probably break things later on.");
                    if (readOnlyExistingProfile) {
                        LOGGER.log(Level.WARNING, "[config] Read-only profile is missing required configuration value '" + configKey + "' for " + tickerStr);
                        return;
                    }
                }
            }

            fee = config.getDouble("fee");
            feeFlat = config.getBoolean("feeFlat");
            rpcEnabled = config.getBoolean("rpcEnabled");
            rpcUsername = config.getString("rpcUsername");
            rpcPassword = config.getString("rpcPassword");
            rpcPort = config.getInt("rpcPort");

            if (!config.has("addressCount")) {
                setAddressCount(0);
                writeConfig();
            } else {
                addressCount = config.getInt("addressCount");
            }
            if (readOnlyExistingProfile && addressCount < 0) {
                LOGGER.log(Level.WARNING, "[config] Read-only profile has a negative address count for " + tickerStr);
                return;
            }
            validConfiguration = true;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[config] Error reading config file for " + tickerStr, e);
        }
    }

    private File getFile() {
        String userHome = getLocalDataDirectory();
        Preconditions.checkNotNull(userHome);

        File home = new File(userHome);
        File settingsDirectory = new File(home, "settings");
        if (!settingsDirectory.exists()) {
            if (readOnlyExistingProfile) {
                LOGGER.log(Level.WARNING, "[config] Read-only profile is missing settings directory for " + tickerStr);
                return null;
            }
            if (!settingsDirectory.mkdirs()) {
                LOGGER.log(Level.FINER, "[config] ERROR: Could not create base/settings directory!");
                return null;
            }
        }

        File configFile = new File(settingsDirectory, "config-" + tickerStr + ".json");
        if (readOnlyExistingProfile) {
            if (!configFile.isFile()) {
                LOGGER.log(Level.WARNING, "[config] Read-only profile is missing configuration for " + tickerStr);
                return null;
            }
            return configFile;
        }
        try {
            if (!configFile.createNewFile() && !configFile.exists())
                return null;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "[config] IOException creating config file for " + tickerStr, e);
        }

        return configFile;
    }

    public void setFee(double fee) {
        this.fee = fee;
    }

    public void setFlatFee(boolean flat) {
        this.feeFlat = flat;
    }

    public void setRpcEnabled(boolean isEnabled) {
        this.rpcEnabled = isEnabled;
    }

    public void setRpcUsername(String user) {
        this.rpcUsername = user;
    }

    public void setRpcPassword(String pass) {
        this.rpcPassword = pass;
    }

    public void setRpcPort(int rpcPort) {
        if (PortCheck.available(rpcPort))
            this.rpcPort = rpcPort;
        else
            setRpcPort(rpcPort + 1);
    }

    public void setAddressCount(int addressCount) {
        this.addressCount = addressCount;
    }

    public double getFee() {
        return fee;
    }

    public boolean isFlatFee() {
        return feeFlat;
    }

    public boolean isRpcEnabled() {
        return rpcEnabled;
    }

    public String getRpcUsername() {
        return rpcUsername;
    }

    public String getRpcPassword() {
        return rpcPassword;
    }

    public int getMasterRpcPort() {
        if (rpcPort == -1000) {
            rpcPort = 9955;
        }

        return rpcPort;
    }

    public int getRpcPort() {
        return rpcPort;
    }

    public int getAddressCount() {
        return addressCount;
    }

    /**
     * Whether this helper loaded a complete configuration file without creating
     * or repairing it.
     */
    public boolean isValidConfiguration() {
        return validConfiguration;
    }

    public boolean validAuth() {
        return rpcUsername != null && !rpcUsername.equals("") && rpcPassword != null && !rpcPassword.equals("");
    }

    public void writeConfig() {
        if (readOnlyExistingProfile) {
            throw new IllegalStateException("Read-only existing-profile mode forbids configuration writes.");
        }
        if (file == null) {
            LOGGER.log(Level.WARNING, "[config] Cannot write configuration for " + tickerStr + ": file is unavailable");
            return;
        }
        try {
            fileWriter = new FileWriter(file, false);

            JSONObject config = new JSONObject();
            config.put("fee", fee);
            config.put("feeFlat", feeFlat);
            config.put("rpcEnabled", rpcEnabled);
            config.put("rpcUsername", rpcUsername);
            config.put("rpcPassword", rpcPassword);
            config.put("rpcPort", rpcPort);
            config.put("addressCount", addressCount);

            fileWriter.write(config.toString(4));
            fileWriter.flush();
            fileWriter.close();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "[config] IOException writing config for " + tickerStr, e);
        }
    }

    public static String getLocalDataDirectory() {
        String userHomeDir;
        if (CONFIG_DIR.isEmpty()) {
            String OS = (System.getProperty("os.name")).toLowerCase();

            if (OS.contains("win")) {
                userHomeDir = App.getEnv("AppData");
            } else if (OS.contains("nix") || OS.contains("nux") || OS.contains("aix")) {
                userHomeDir = System.getProperty("user.home") + File.separator + ".config";
            } else if (OS.contains("mac")) {
                userHomeDir = System.getProperty("user.home") + File.separator + "Library" + File.separator + "Application Support";
            } else {
                userHomeDir = System.getProperty("user.home") + File.separator + ".config";
            }
            userHomeDir += File.separator + "CloudChains" + File.separator;
        } else {
            userHomeDir = CONFIG_DIR + File.separator + "CloudChains" + File.separator;
        }

        File directory = new File(userHomeDir);
        if (!directory.exists() && !readOnlyExistingProfile) {
            directory.mkdir();
        }

        return userHomeDir;
    }

    /**
     * Select the process-wide policy before any configuration object is made.
     * A packaged runtime cannot change this policy after profile configuration
     * has been opened. The explicit CONFIG_DIR test override remains mutable so
     * isolated unit tests can reset their process-global fixture state.
     */
    public static synchronized void setReadOnlyExistingProfile(boolean readOnly) {
        if (configurationOpened && CONFIG_DIR.isEmpty()
                && readOnlyExistingProfile != readOnly) {
            throw new IllegalStateException(
                    "Read-only existing-profile policy cannot change after configuration is opened.");
        }
        readOnlyExistingProfile = readOnly;
    }

    public static boolean isReadOnlyExistingProfile() {
        return readOnlyExistingProfile;
    }
}
