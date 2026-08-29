import io.cloudchains.app.console.ConsoleMenu;
import io.cloudchains.app.crypto.KeyHandler;
import io.cloudchains.app.net.CoinInstance;
import io.cloudchains.app.net.CoinTicker;
import io.cloudchains.app.util.ConfigHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReadOnlyExistingProfileTest {
    private static final String PASSWORD = "ReadOnlyPass123!";
    private static final String MNEMONIC =
            "one two three cake neutral benefit quick hip level mother fine burst";

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        ConfigHelper.setReadOnlyExistingProfile(false);
        ConfigHelper.CONFIG_DIR = tempDir.toString();
        writeRequiredConfigurations(0);
    }

    @AfterEach
    void tearDown() {
        CoinInstance.getCoinInstances().clear();
        CoinInstance.setAddressDiscoveryEnabled(true);
        ConfigHelper.setReadOnlyExistingProfile(false);
        ConfigHelper.CONFIG_DIR = "";
    }

    @Test
    void readOnlyV2WalletAndProfileRemainByteIdentical() throws Exception {
        writeV2Wallet();
        assertReadOnlyWalletAndProfileAreUnchanged();
    }

    @Test
    void readOnlyV1WalletAndProfileRemainByteIdentical() throws Exception {
        writeV1Wallet();
        assertReadOnlyWalletAndProfileAreUnchanged();
    }

    @Test
    void malformedWalletIsRejectedBeforePasswordHandling() throws Exception {
        Path keyFile = walletPath();
        Files.createDirectories(keyFile.getParent());
        Files.writeString(keyFile, "VERSION:2\n"
                + Base64.getEncoder().encodeToString(new byte[19]) + "\n"
                + Base64.getEncoder().encodeToString(new byte[16]) + "\n"
                + Base64.getEncoder().encodeToString(new byte[16]) + "\n");
        Map<String, byte[]> before = snapshotProfile();

        ConfigHelper.setReadOnlyExistingProfile(true);
        assertFalse(KeyHandler.hasStructurallyValidExistingWallet());

        assertProfileUnchanged(before);
    }

    @Test
    void negativeAddressCountIsInvalidAndDoesNotGetRepaired() throws Exception {
        writeV2Wallet();
        writeConfiguration("BLOCK", 41419, -1, false);
        Map<String, byte[]> before = snapshotProfile();

        ConfigHelper.setReadOnlyExistingProfile(true);
        ConfigHelper block = new ConfigHelper("BLOCK");
        assertFalse(block.isValidConfiguration());

        assertProfileUnchanged(before);
    }

    @Test
    void malformedRequiredProfileFailsBeforeReadingPasswordStdin() throws Exception {
        writeV2Wallet();
        Files.writeString(tempDir.resolve("CloudChains").resolve("settings")
                .resolve("config-LTC.json"), "{");
        assertPreflightFailsBeforePasswordRead();
    }

    @Test
    void disabledRequiredRpcFailsBeforeReadingPasswordStdin() throws Exception {
        writeV2Wallet();
        writeConfiguration("LTC", 9332, 0, false, false, true);
        assertPreflightFailsBeforePasswordRead();
    }

    @Test
    void missingRequiredRpcCredentialsFailBeforeReadingPasswordStdin() throws Exception {
        writeV2Wallet();
        writeConfiguration("BLOCK", 41419, 0, false, true, false);
        assertPreflightFailsBeforePasswordRead();
    }

    @Test
    void duplicateRequiredPortsFailBeforeReadingPasswordStdin() throws Exception {
        writeV2Wallet();
        writeConfiguration("LTC", 41419, 0, false);
        assertPreflightFailsBeforePasswordRead();
    }

    @Test
    void globalReadOnlyCoinInitialisationDoesNotRepairNegativeAddressCount() throws Exception {
        writeV2Wallet();
        writeConfiguration("BLOCK", 41419, -1, false, false, true);
        Map<String, byte[]> before = snapshotProfile();
        ConfigHelper.setReadOnlyExistingProfile(true);
        CoinInstance.setAddressDiscoveryEnabled(false);

        CoinInstance block = CoinInstance.getInstance(CoinTicker.BLOCKNET);
        assertNotNull(block);
        assertNull(block.init(PASSWORD, null, false));

        assertProfileUnchanged(before);
    }

    @Test
    void globalReadOnlyDefaultCoinInitialisationDoesNotMigrateV1OrWrite() throws Exception {
        writeV1Wallet();
        writeConfiguration("BLOCK", 41419, 0, false, false, true);
        Map<String, byte[]> before = snapshotProfile();
        ConfigHelper.setReadOnlyExistingProfile(true);
        CoinInstance.setAddressDiscoveryEnabled(false);

        CoinInstance block = CoinInstance.getInstance(CoinTicker.BLOCKNET);
        assertNotNull(block);
        assertNull(block.init(PASSWORD, null, false));

        assertProfileUnchanged(before);
    }

    private void assertPreflightFailsBeforePasswordRead() {
        InputStream originalStdin = System.in;
        System.setIn(new InputStream() {
            @Override
            public int read() {
                throw new AssertionError("Password stdin was read before profile preflight.");
            }
        });
        try {
            assertThrows(IllegalStateException.class,
                    () -> new ConsoleMenu(new String[]{"--read-only-existing-profile",
                            "--no-migrate-legacy-wallet", "--password"}).init());
        } finally {
            System.setIn(originalStdin);
        }
    }

    @Test
    void readOnlyFlagAllowsOnlyTheExactManagedInvocation() {
        assertDoesNotThrow(() -> ConsoleMenu.configureReadOnlyExistingProfile(new String[]{
                "--read-only-existing-profile", "--no-migrate-legacy-wallet", "--password"}));
        assertTrue(ConfigHelper.isReadOnlyExistingProfile());

        assertThrows(IllegalArgumentException.class,
                () -> ConsoleMenu.configureReadOnlyExistingProfile(new String[]{
                        "--read-only-existing-profile", "--no-migrate-legacy-wallet",
                        "--password", "--password"}));
        assertThrows(IllegalArgumentException.class,
                () -> ConsoleMenu.configureReadOnlyExistingProfile(new String[]{
                        "--read-only-existing-profile=value", "--no-migrate-legacy-wallet",
                        "--password"}));
    }

    private void assertReadOnlyWalletAndProfileAreUnchanged() throws Exception {
        Map<String, byte[]> before = snapshotProfile();
        ConfigHelper.setReadOnlyExistingProfile(true);

        assertTrue(KeyHandler.hasStructurallyValidExistingWallet());
        char[] password = PASSWORD.toCharArray();
        try {
            assertEquals(Arrays.asList(MNEMONIC.split(" ")),
                    KeyHandler.getBaseSeed(password, false));
        } finally {
            Arrays.fill(password, '\0');
        }
        assertTrue(new ConfigHelper("master").isValidConfiguration());
        assertTrue(new ConfigHelper("BLOCK").isValidConfiguration());
        assertTrue(new ConfigHelper("LTC").isValidConfiguration());

        assertProfileUnchanged(before);
    }

    private void writeRequiredConfigurations(int addressCount) throws Exception {
        writeConfiguration("master", 9955, addressCount, true);
        writeConfiguration("BLOCK", 41419, addressCount, false);
        writeConfiguration("LTC", 9332, addressCount, false);
    }

    private void writeConfiguration(String ticker, int port, int addressCount,
                                    boolean master) throws Exception {
        writeConfiguration(ticker, port, addressCount, master, true, true);
    }

    private void writeConfiguration(String ticker, int port, int addressCount, boolean master,
                                    boolean rpcEnabled, boolean credentialsPresent) throws Exception {
        Path settings = tempDir.resolve("CloudChains").resolve("settings");
        Files.createDirectories(settings);
        String credentials = master || credentialsPresent
                ? "\"rpcUsername\": \"managed-user\",\n  \"rpcPassword\": \"managed-password\","
                : "\"rpcUsername\": \"\",\n  \"rpcPassword\": \"\",";
        Files.writeString(settings.resolve("config-" + ticker + ".json"), "{\n"
                + "  \"fee\": 0.0001,\n"
                + "  \"feeFlat\": true,\n"
                + "  \"rpcEnabled\": " + rpcEnabled + ",\n"
                + "  " + credentials + "\n"
                + "  \"rpcPort\": " + port + ",\n"
                + "  \"addressCount\": " + addressCount + "\n"
                + "}\n");
    }

    private void writeV2Wallet() {
        char[] password = PASSWORD.toCharArray();
        try {
            assertTrue(KeyHandler.importFromMnemonic(Arrays.asList(MNEMONIC.split(" ")), password));
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private void writeV1Wallet() throws Exception {
        byte[] salt = new byte[20];
        new SecureRandom().nextBytes(salt);
        char[] password = PASSWORD.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(password, salt, 16_384, 256);
        byte[] keyBytes = null;
        try {
            keyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
                    .generateSecret(spec).getEncoded();
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"));
            byte[] encrypted = cipher.doFinal(MNEMONIC.getBytes(StandardCharsets.UTF_8));
            Path keyFile = walletPath();
            Files.createDirectories(keyFile.getParent());
            Files.writeString(keyFile, Base64.getEncoder().encodeToString(salt) + "\n"
                    + Base64.getEncoder().encodeToString(encrypted) + "\n");
        } finally {
            spec.clearPassword();
            Arrays.fill(password, '\0');
            if (keyBytes != null) {
                Arrays.fill(keyBytes, (byte) 0);
            }
            Arrays.fill(salt, (byte) 0);
        }
    }

    private Path walletPath() {
        return tempDir.resolve("CloudChains").resolve("key.dat");
    }

    private Map<String, byte[]> snapshotProfile() throws Exception {
        Map<String, byte[]> snapshot = new LinkedHashMap<>();
        Path profile = tempDir.resolve("CloudChains");
        try (var paths = Files.walk(profile)) {
            paths.sorted().forEach(path -> {
                try {
                    String relativePath = profile.relativize(path).toString();
                    if (Files.isDirectory(path)) {
                        snapshot.put("directory:" + relativePath, new byte[0]);
                    } else if (Files.isRegularFile(path)) {
                        snapshot.put("file:" + relativePath, Files.readAllBytes(path));
                    }
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
        }
        return snapshot;
    }

    private void assertProfileUnchanged(Map<String, byte[]> before) throws Exception {
        Map<String, byte[]> after = snapshotProfile();
        assertEquals(before.keySet(), after.keySet());
        for (String path : before.keySet()) {
            assertArrayEquals(before.get(path), after.get(path), path);
        }
    }
}
