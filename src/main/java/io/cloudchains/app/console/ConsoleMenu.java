package io.cloudchains.app.console;

import io.cloudchains.app.App;
import io.cloudchains.app.Version;
import io.cloudchains.app.crypto.KeyHandler;
import io.cloudchains.app.crypto.LoginUtils;
import io.cloudchains.app.net.CoinInstance;
import io.cloudchains.app.net.CoinTicker;
import io.cloudchains.app.net.CoinTickerUtils;
import io.cloudchains.app.net.api.JSONRPCController;
import io.cloudchains.app.net.api.http.client.EXRServerPool;
import io.cloudchains.app.util.ConfigHelper;
import io.cloudchains.app.util.background.BackgroundTimerThread;

import java.io.Console;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ConsoleMenu {
    private static final String NO_MIGRATE_LEGACY_WALLET_FLAG =
            "--no-migrate-legacy-wallet";
    private static final String READ_ONLY_EXISTING_PROFILE_FLAG =
            "--read-only-existing-profile";
    private final static LogManager LOGMANAGER = LogManager.getLogManager();
    private final static Logger LOGGER = LOGMANAGER.getLogger(Logger.GLOBAL_LOGGER_NAME);
    private final String[] arguments;
    private final boolean migrateLegacyWallet;
    private final boolean readOnlyExistingProfile;
    private BackgroundTimerThread backgroundTimerThread = null;
    private boolean xliteRPC = false;

    public ConsoleMenu(String[] args) {
        configureReadOnlyExistingProfile(args);
        this.arguments = sanitiseArguments(args);
        this.migrateLegacyWallet = Arrays.stream(arguments)
                .noneMatch(NO_MIGRATE_LEGACY_WALLET_FLAG::equals);
        this.readOnlyExistingProfile = Arrays.stream(arguments)
                .anyMatch(READ_ONLY_EXISTING_PROFILE_FLAG::equals);
        LOGGER.setLevel(Level.INFO);
    }

    /**
     * Select the profile policy before App constructs logging or any class can
     * construct a ConfigHelper. Read-only startup deliberately permits exactly
     * the three flags the managed launcher needs.
     */
    public static void configureReadOnlyExistingProfile(String[] args) {
        String[] supplied = args == null ? new String[0] : args;
        boolean readOnly = false;
        for (String argument : supplied) {
            if (READ_ONLY_EXISTING_PROFILE_FLAG.equals(argument)) {
                readOnly = true;
            } else if (argument != null
                    && argument.startsWith(READ_ONLY_EXISTING_PROFILE_FLAG + "=")) {
                throw new IllegalArgumentException(
                        "Values for " + READ_ONLY_EXISTING_PROFILE_FLAG + " are not accepted.");
            }
        }

        if (readOnly) {
            List<String> approvedFlags = List.of(READ_ONLY_EXISTING_PROFILE_FLAG,
                    NO_MIGRATE_LEGACY_WALLET_FLAG, "--password");
            List<String> actualFlags = Arrays.asList(supplied);
            if (!actualFlags.contains(NO_MIGRATE_LEGACY_WALLET_FLAG)
                    || !actualFlags.contains("--password")
                    || actualFlags.size() != approvedFlags.size()
                    || !actualFlags.stream().allMatch(approvedFlags::contains)
                    || approvedFlags.stream().anyMatch(flag ->
                    actualFlags.stream().filter(flag::equals).count() != 1)) {
                throw new IllegalArgumentException(READ_ONLY_EXISTING_PROFILE_FLAG
                        + " requires exactly " + NO_MIGRATE_LEGACY_WALLET_FLAG
                        + " and --password.");
            }
        }

        ConfigHelper.setReadOnlyExistingProfile(readOnly);
    }

    private static String[] sanitiseArguments(String[] args) {
        if (args == null)
            return new String[0];

        List<String> safeArguments = new ArrayList<>();
        List<String> valueFlags = List.of("--development-endpoint", "--exr-endpoint");
        List<String> stdinOnlyFlags = List.of("--password", "--createdefaultwallet",
                "--createwalletmnemonic", "--changepassword", "--getmnemonic");
        List<String> valueFreeFlags = List.of(NO_MIGRATE_LEGACY_WALLET_FLAG,
                READ_ONLY_EXISTING_PROFILE_FLAG);

        for (int i = 0; i < args.length; i++) {
            String argument = args[i];
            if (argument == null || !argument.startsWith("--"))
                throw new IllegalArgumentException("Secret-bearing positional arguments are not accepted.");
            for (String valueFreeFlag : valueFreeFlags) {
                if (argument.startsWith(valueFreeFlag + "="))
                    throw new IllegalArgumentException(
                            "Values for " + valueFreeFlag + " are not accepted.");
            }

            safeArguments.add(argument);
            if (valueFlags.contains(argument)) {
                if (i + 1 < args.length && !args[i + 1].startsWith("--"))
                    safeArguments.add(args[++i]);
            } else if (stdinOnlyFlags.contains(argument)
                    && i + 1 < args.length && !args[i + 1].startsWith("--")) {
                throw new IllegalArgumentException(
                        "Secret values for " + argument + " must be entered through stdin.");
            } else if (valueFreeFlags.contains(argument)
                    && i + 1 < args.length && !args[i + 1].startsWith("--")) {
                throw new IllegalArgumentException(
                        "Values for " + argument + " are not accepted.");
            }
        }
        return safeArguments.toArray(new String[0]);
    }

    public void logBadPassword(String msg) {
        if (msg == null || msg.isEmpty())
            msg = "Bad password";
        LOGGER.log(Level.INFO, "[master] Error(" + CoinInstance.CoinError.CoinErrorCode.BADPASSWORD.name() + "): " + msg);
    }

    public void logBadMnemonic() {
        LOGGER.log(Level.INFO, "[master] Error(" + CoinInstance.CoinError.CoinErrorCode.BADMNEMONIC.name() + "): Bad mnemonic");
    }

    public void logBadChangePass(String msg) {
        LOGGER.log(Level.INFO, "[master] Error(" + CoinInstance.CoinError.CoinErrorCode.CHANGEPASSWORDFAILED.name() + "): " + msg);
    }

    public void init() {
        int selection;
        String newWalletStr = "";
        Scanner input = new Scanner(System.in);

        if (readOnlyExistingProfile) {
            validateReadOnlyExistingProfile();
        }

        if (KeyHandler.existsBaseECKeyFromLocal()) {
            newWalletStr = "- Disabled. Wallet already exists.";
        }

        if (arguments.length > 0) {
            for (int i = 0; i < arguments.length; i++) {
                String argument = arguments[i];

                switch (argument) {
                    case "--enablerpcandconfigure":
                        autoGenerateRPCConfig();
                        System.exit(0);
                    case "--development-endpoint": {
                        // sample endpoint url: "https://utils.blocknet.org/"
                        if (i + 1 < arguments.length) {
                            String customEndpoint = arguments[i + 1];
                            if (customEndpoint.startsWith("--")) {
                                LOGGER.log(Level.WARNING, "Invalid endpoint: " + customEndpoint);
                                break;
                            }
                            App.BASE_URL = customEndpoint;
                            i++;
                        } else {
                            String envEndpoint = App.getEnv("BASE_URL");
                            if (envEndpoint != null && !envEndpoint.isEmpty()) {
                                App.BASE_URL = envEndpoint;
                            } else {
                                LOGGER.log(Level.WARNING, "Missing custom endpoint after '--development-endpoint'");
                            }
                        }
                        break;
                    }
                    case "--exr-endpoint": {
                        if (i + 1 < arguments.length) {
                            String exrEndpoint = arguments[i + 1];
                            if (exrEndpoint.startsWith("--")) {
                                LOGGER.log(Level.WARNING, "Invalid endpoint: " + exrEndpoint);
                                break;
                            }
                            App.EXR_ENDPOINT = exrEndpoint;
                            App.exrServerPool = new EXRServerPool(App.EXR_ENDPOINT);
                            LOGGER.log(Level.INFO, "[console] EXR mode enabled with " + App.exrServerPool.getServerCount() + " servers: " + App.EXR_ENDPOINT);
                            new Thread(() -> {
                                try {
                                    Thread.sleep(1000);
                                    App.exrServerPool.probeAllCapabilities();
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            }, "EXR-Capability-Prober").start();
                            i++;
                        } else {
                            String envExrEndpoint = App.getEnv("EXR_ENDPOINT");
                            if (envExrEndpoint != null && !envExrEndpoint.isEmpty()) {
                                App.EXR_ENDPOINT = envExrEndpoint;
                                App.exrServerPool = new EXRServerPool(App.EXR_ENDPOINT);
                                LOGGER.log(Level.INFO, "[console] EXR mode enabled with " + App.exrServerPool.getServerCount() + " servers: " + App.EXR_ENDPOINT);
                                new Thread(() -> {
                                    try {
                                        Thread.sleep(1000);
                                        App.exrServerPool.probeAllCapabilities();
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                    }
                                }, "EXR-Capability-Prober").start();
                            } else {
                                LOGGER.log(Level.WARNING, "Missing EXR endpoint after '--exr-endpoint'");
                            }
                        }
                        break;
                    }
                    case "--version":
                        LOGGER.log(Level.INFO, Version.CLIENT_VERSION);
                        System.exit(0);
                        break;
                    case "--createdefaultwallet": {
                        if (KeyHandler.existsBaseECKeyFromLocal()) {
                            LOGGER.log(Level.INFO, "Wallet already exists");
                            System.exit(0);
                        }

                        String password = readPassword(input, "");
                        int strength = KeyHandler.calculatePasswordStrength(password);
                        if (strength < 9) {
                            logBadPassword(null);
                            System.exit(1);
                        }

                        String entropy = LoginUtils.loginToEntropy(password);
                        completeLogin(entropy, null, false);

                        System.exit(0);
                    }
                    case "--createwalletmnemonic": {
                        if (KeyHandler.existsBaseECKeyFromLocal()) {
                            LOGGER.log(Level.INFO, "Wallet already exists");
                            System.exit(0);
                        }

                        String password = readPassword(input, "");
                        String mnemonic = readPassword(input, "Mnemonic:\n").trim();
                        int strength = KeyHandler.calculatePasswordStrength(password);
                        if (strength < 9) {
                            logBadPassword(null);
                            System.exit(1);
                        }
                        if (mnemonic.isEmpty()) {
                            logBadMnemonic();
                            System.exit(1);
                        }

                        String entropy = LoginUtils.loginToEntropy(password);
                        completeLogin(entropy, mnemonic, false);
                        System.exit(0);
                    }
                    case "--xliterpc": {
                        // Increment RPC port by 1
                        xliteRPC = true;

                        break;
                    }
                    case NO_MIGRATE_LEGACY_WALLET_FLAG:
                    case READ_ONLY_EXISTING_PROFILE_FLAG:
                        break;
                    case "--password": {
                        String password = readPassword(input, "");
                        int strength = KeyHandler.calculatePasswordStrength(password);

                        if (!KeyHandler.existsBaseECKeyFromLocal() && strength < 9) {
                            LOGGER.log(Level.INFO, "Bad password.");
                            System.exit(1);
                        }

                        String entropy = LoginUtils.loginToEntropy(password);
                        completeLogin(entropy, null, false);

                        return;
                    }
                    case "--getmnemonic": {
                        LOGGER.log(Level.INFO, "Mnemonic export is disabled. Use an approved wallet backup flow.");
                        System.exit(0);
                    }
                    case "--changepassword": {
                        if (!KeyHandler.existsBaseECKeyFromLocal()) {
                            logBadChangePass("Wallet not found");
                            System.exit(1);
                        }

                        String currentPassword = readPassword(input, "");
                        String newPassword = readPassword(input, "");
                        if (currentPassword.isEmpty() || newPassword.isEmpty()) {
                            LOGGER.log(Level.INFO, "Password cannot be empty");
                            System.exit(1);
                        }
                        if (currentPassword.equals(newPassword)) {
                            LOGGER.log(Level.INFO, "New password must be different from old password");
                            System.exit(1);
                        }

                        // Check new password strength
                        int strength = KeyHandler.calculatePasswordStrength(newPassword);
                        if (strength < 9) {
                            LOGGER.log(Level.INFO, "Unable to change the password: New password is not strong enough");
                            System.exit(1);
                        }

                        CoinInstance.CoinError err = CoinInstance.changePassword(LoginUtils.loginToEntropy(currentPassword),
                                LoginUtils.loginToEntropy(newPassword));
                        if (err != null)
                            logBadChangePass(err.getMessage());
                        else
                            LOGGER.log(Level.INFO, "Wallet password changed successfully");

                        System.exit(0);
                    }
                    case "--help":
                        displayHelp();
                        System.exit(0);
                }
            }
        }

        String entropy = null;

        while (entropy == null) {
            LOGGER.log(Level.INFO, "-------------------------");
            LOGGER.log(Level.INFO, "1 - Create new wallet " + newWalletStr);
            LOGGER.log(Level.INFO, "2 - Decrypt wallet");
            LOGGER.log(Level.INFO, "3 - Import from mnemonic");
            LOGGER.log(Level.INFO, "4 - Quit");

            LOGGER.log(Level.INFO, "Selection: ");
            selection = input.nextInt();
            input.nextLine(); // clear buffer

            switch (selection) {
                case 1: {
                    if (KeyHandler.existsBaseECKeyFromLocal()) {
                        LOGGER.log(Level.INFO, "Key already exists");
                        return;
                    }

                    Console console = System.console();
                    String password;
                    if (console != null) {
                        password = new String(console.readPassword("Enter new password: "));
                    } else {
                        LOGGER.log(Level.INFO, "Enter new password: ");
                        password = input.next();
                    }
                    int strength = KeyHandler.calculatePasswordStrength(password);

                    if (!KeyHandler.existsBaseECKeyFromLocal() && strength < 9) {
                        LOGGER.log(Level.INFO, "Bad password.");
                        return;
                    }
                    entropy = LoginUtils.loginToEntropy(password);
                    break;
                }
                case 2: {
                    LOGGER.log(Level.INFO, "Enter password: ");
                    Console console = System.console();
                    String password;
                    if (console != null) {
                        password = new String(console.readPassword());
                    } else {
                        LOGGER.log(Level.WARNING, "Console not available, using Scanner fallback");
                        password = readPassword(input, "");
                    }
                    int strength = KeyHandler.calculatePasswordStrength(password);

                    if (!KeyHandler.existsBaseECKeyFromLocal() && strength < 9) {
                        LOGGER.log(Level.INFO, "Bad password.");
                        return;
                    }

                    entropy = LoginUtils.loginToEntropy(password);
                    break;
                }
                case 3: {
                    LOGGER.log(Level.INFO, "Enter mnemonic: ");
                    String mnemonicImport = input.nextLine().trim();

                    completeLogin(mnemonicImport, null, true);
                    return;
                }
                case 4: {
                    LOGGER.log(Level.INFO, "Exiting...");
                    System.exit(0);
                }
                default: {
                    LOGGER.log(Level.INFO, "Unknown Option.");
                }
            }
        }

        input.close();
        completeLogin(entropy, null, false);
    }

    public void deinit() {
        if (backgroundTimerThread != null)
            backgroundTimerThread.stop();
        for (CoinTicker cointicker : CoinTicker.coins()) {
            CoinInstance instance = CoinInstance.getInstance(cointicker);
            if (instance != null)
                instance.deinit();
        }
    }

    private void completeLogin(String entropy, String userMnemonic, boolean isMnemonic) {
        if (entropy == null && userMnemonic == null) {
            logBadPassword(null);
            System.exit(0);
        }

        // Measure total initialization time for all coins
        long startTime = System.currentTimeMillis();
        // Initialize Blocknet first (synchronous) as it's the active currency
        CoinInstance.CoinError coinError = CoinInstance.getInstance(CoinTicker.BLOCKNET)
                .init(entropy, userMnemonic, isMnemonic, xliteRPC, migrateLegacyWallet,
                        readOnlyExistingProfile);
        if (coinError != null) {
            String msg = "[master] Error(" + coinError.getCode().name() + "): " + coinError.getMessage();
            LOGGER.log(Level.SEVERE, msg);
            System.exit(0);
        }

        // Get all coin tickers except Blocknet (which is already initialized)
        List<CoinTicker> otherCoins = new ArrayList<>();
        for (CoinTicker cointicker : CoinTicker.coins()) {
            if (cointicker != CoinTicker.BLOCKNET && cointicker != CoinTicker.BLOCKNET_TESTNET5) {
                otherCoins.add(cointicker);
            }
        }

        // Initialize remaining coins concurrently
        initializeCoinsConcurrently(otherCoins, entropy, userMnemonic, isMnemonic, xliteRPC,
                migrateLegacyWallet);

        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        LOGGER.log(Level.INFO, "[coin] Concurrent coins initialization completed in " + totalTime + " ms");

        App.masterRPC = App.masterRPC == null ? JSONRPCController.getMasterServer() : App.masterRPC;
        if (readOnlyExistingProfile) {
            App.heightUpdateHttpClient.getAllBlockCounts();
            if (CoinInstance.getBlockCountByTicker(CoinTicker.BLOCKNET) <= 0
                    || CoinInstance.getBlockCountByTicker(CoinTicker.LITECOIN) <= 0) {
                throw new IllegalStateException(
                        "Read-only existing-profile mode requires current BLOCK and LTC heights");
            }
        }
        App.masterRPC.start();
        if (!readOnlyExistingProfile) {
            backgroundTimerThread = new BackgroundTimerThread();
            (new Thread(backgroundTimerThread)).start();
        }
        // Start EXR capability probing after wallet is decrypted
        if (App.exrServerPool != null) {
            App.exrServerPool.probeAllCapabilities();
        }
    }

    /**
     * Initialize coins concurrently using CompletableFuture
     * @param coinTickers List of coin tickers to initialize
     * @param entropy Password entropy
     * @param userMnemonic User mnemonic (if any)
     * @param isMnemonic Whether the input is a mnemonic
     * @param xliteRPC Whether to use xlite RPC
     * @param migrateLegacyWallet Whether to migrate an existing V1 wallet
     */
    private void initializeCoinsConcurrently(List<CoinTicker> coinTickers, String entropy,
                                             String userMnemonic, boolean isMnemonic,
                                             boolean xliteRPC, boolean migrateLegacyWallet) {
        if (coinTickers.isEmpty()) {
            return;
        }

        // Create thread pool with number of coins (or a reasonable limit)
        int threadCount = Math.min(coinTickers.size(), 8); // Limit to 8 threads max
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        try {
            // Filter to only enabled coins before initialization
            List<CoinTicker> enabledCoins = coinTickers.stream()
                    .filter(ticker -> ticker == CoinTicker.BLOCKNET || CoinInstance.getInstance(ticker) != null)
                    .collect(Collectors.toList());

            // Create CompletableFuture for each coin initialization
            CompletableFuture<?>[] futures = enabledCoins.stream()
                    .map(coinTicker -> CompletableFuture.runAsync(() -> {
                        try {
                            LOGGER.log(Level.FINE, "[coin] Initializing " + CoinTickerUtils.tickerToString(coinTicker) + " concurrently");
                            CoinInstance.CoinError coinError = CoinInstance.getInstance(coinTicker)
                                    .init(entropy, userMnemonic, isMnemonic, xliteRPC,
                                            migrateLegacyWallet, readOnlyExistingProfile);
                            if (coinError != null) {
                                LOGGER.log(Level.WARNING, "[" + coinTicker.name() + "] Error(" +
                                        coinError.getCode().name() + "): " + coinError.getMessage());
                            }
                        } catch (Exception e) {
                            LOGGER.log(Level.SEVERE, "Failed to initialize " + coinTicker.name(), e);
                        }
                    }, executor))
                    .toArray(CompletableFuture[]::new);

            // Wait for all initializations to complete
            CompletableFuture.allOf(futures).join();


        } finally {
            // Shutdown executor service
            executor.shutdown();
            try {
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private void autoGenerateRPCConfig() {
        for (CoinTicker cointicker : CoinTicker.coins()) {
            ConfigHelper configHelper = new ConfigHelper(CoinTickerUtils.tickerToString(cointicker));

            configHelper.setRpcUsername(generateRandomString(24));
            configHelper.setRpcPassword(generateRandomString(32));
            configHelper.setRpcEnabled(true);
            configHelper.writeConfig();
        }
        ConfigHelper masterConf = new ConfigHelper("master");
        masterConf.setRpcUsername(generateRandomString(24));
        masterConf.setRpcPassword(generateRandomString(32));
        masterConf.setRpcEnabled(true);
        masterConf.writeConfig();
    }

    private String generateRandomString(int length) {
        SecureRandom secureRandom = new SecureRandom();

        byte[] token = new byte[length];
        secureRandom.nextBytes(token);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    /**
    * Reads a secret from stdin only. Secrets must never be supplied as
    * command-line arguments or environment variables, where the operating
    * system or child processes may expose them.
    * @param input Stdin
     * @param msg Message to display on stdin (defaults to "Password:\n" if empty)
     * @return Secret string
     */
    private String readPassword(Scanner input, String msg) {
        if (msg.isEmpty())
            msg = "Password:\n";
        System.out.println(msg);
        return input.nextLine();
    }

    // Function to display help information
    private static void displayHelp() {
        System.out.print(getHelpText());
    }

    public static String getHelpText() {
        return "Usage: xlite-daemon [options]\n" +
                "Options:\n" +
                "  --enablerpcandconfigure    Enable and configure RPC\n" +
                "  --development-endpoint     Set a custom development endpoint\n" +
                "                             Example: --development-endpoint <https://url.endpoint.org/>\n" +
                "  --exr-endpoint             Set EXR endpoint for EXR server\n" +
                "                             Example: --exr-endpoint <http://exrproxy1.airdns.org:42114>\n" +
                "  --version                  Display the version\n" +
                "  --createdefaultwallet     Create a default wallet\n" +
                "  --createwalletmnemonic    Create a wallet with a mnemonic\n" +
                "  --xliterpc                Increment RPC port by 1\n" +
                "  --no-migrate-legacy-wallet\n" +
                "                           Read a legacy V1 wallet without rewriting it\n" +
                "  --read-only-existing-profile\n" +
                "                           Requires --no-migrate-legacy-wallet --password\n" +
                "                           and never writes the existing profile\n" +
                "  --password                Set password from stdin\n" +
                "                           Password is read from stdin.\n" +
                "  --getmnemonic             Mnemonic export is disabled\n" +
                "  --changepassword          Change wallet password\n" +
                "                           Both passwords are read from stdin.\n";
    }

    private void validateReadOnlyExistingProfile() {
        if (!KeyHandler.hasStructurallyValidExistingWallet()) {
            throw new IllegalStateException(
                    "Read-only existing-profile startup requires a structurally valid wallet.");
        }
        ConfigHelper master = validateRequiredConfiguration("master");
        ConfigHelper block = validateRequiredConfiguration(
                CoinTickerUtils.tickerToString(CoinTicker.BLOCKNET));
        ConfigHelper litecoin = validateRequiredConfiguration(
                CoinTickerUtils.tickerToString(CoinTicker.LITECOIN));
        if (master.getRpcPort() == block.getRpcPort()
                || master.getRpcPort() == litecoin.getRpcPort()
                || block.getRpcPort() == litecoin.getRpcPort()) {
            throw new IllegalStateException(
                    "Read-only existing-profile startup requires unique master, BLOCK and LTC RPC ports.");
        }
    }

    private ConfigHelper validateRequiredConfiguration(String ticker) {
        ConfigHelper config = new ConfigHelper(ticker);
        if (!config.isValidConfiguration() || config.getRpcPort() < 1
                || config.getRpcPort() > 65535
                || !config.isRpcEnabled() || !config.validAuth()) {
            throw new IllegalStateException("Read-only existing-profile startup requires a valid "
                    + ticker + " configuration.");
        }
        return config;
    }
}
