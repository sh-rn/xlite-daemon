import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import io.cloudchains.app.net.CoinInstance;
import io.cloudchains.app.net.CoinTicker;
import io.cloudchains.app.net.api.http.client.HTTPClient;
import io.cloudchains.app.net.xrouter.XRouterPacketManager;
import io.cloudchains.app.util.AddressBalance;
import io.cloudchains.app.util.ConfigHelper;
import io.cloudchains.app.util.UTXO;
import io.cloudchains.app.wallet.WalletHelper;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManagedReadOnlyAuthorityBoundaryTest {
    private static final String HANDLER =
            "io.cloudchains.app.net.api.http.server.HTTPServerHandler";
    private static final String PASSWORD = "ManagedBoundary^test";
    private static final String INPUT_TXID =
            "0000000000000000000000000000000000000000000000000000000000000042";

    @TempDir
    Path tempDir;

    private CoinInstance coin;

    @BeforeEach
    void setUp() {
        ConfigHelper.CONFIG_DIR = tempDir.toString();
        ConfigHelper.setReadOnlyExistingProfile(false);
        CoinInstance.getCoinInstances().clear();
        CoinInstance.setAddressDiscoveryEnabled(false);
    }

    @AfterEach
    void tearDown() {
        if (coin != null)
            coin.deinit();
        CoinInstance.getCoinInstances().clear();
        CoinInstance.setAddressDiscoveryEnabled(true);
        ConfigHelper.setReadOnlyExistingProfile(false);
        ConfigHelper.CONFIG_DIR = "";
    }

    @Test
    void managedRpcDeniesEverySensitiveNameBeforeParametersAndEffects() throws Exception {
        ManagedFixture fixture = startManagedCoinWithOnePersistedAddress();
        coin = fixture.coin();
        AddressBalance address = spy(coin.getAddressKeyPairs().get(0));
        replaceFirstAddress(coin, address);
        ConfigHelper config = spy(coin.getConfigHelper());
        setField(coin, "configHelper", config);

        HTTPClient relay = mock(HTTPClient.class);
        Object handler = createHandler(coin, relay);
        JsonArray hostile = new JsonArray();
        JsonObject secretShape = new JsonObject();
        secretShape.addProperty("privateKey", fixture.privateKey());
        secretShape.addProperty("address", address.getAddress().toBase58());
        JsonArray nested = new JsonArray();
        JsonObject nestedSecret = new JsonObject();
        nestedSecret.addProperty("value", fixture.privateKey());
        nested.add(nestedSecret);
        secretShape.add("nested", nested);
        hostile.add(secretShape);
        hostile.add(JsonNull.INSTANCE);

        String[] deniedMethods = {
                "createrawtransaction", "fundrawtransaction", "walletcreatefundedpsbt",
                "signrawtransaction", "signrawtransactionwithwallet",
                "signrawtransactionwithkey", "sendrawtransaction", "sendtransaction",
                "sendtoaddress", "sendmany", "sendfrom", "send", "transfer", "move",
                "broadcast", "broadcasttransaction", "xrSendTransaction", "getnewaddress",
                "getrawchangeaddress", "importprivkey", "dumpprivkey", "importwallet",
                "dumpwallet", "importmulti", "sethdseed", "signmessage",
                "signmessagewithprivkey", "getmnemonic", "dumpmnemonic", "exportmnemonic"
        };
        for (String method : deniedMethods) {
            JsonObject response = invoke(handler, method, hostile);
            assertMethodNotFound(response);
            assertFalse(response.toString().contains(fixture.privateKey()));
            assertFalse(response.toString().contains(address.getAddress().toBase58()));
        }

        JsonArray exactCoreSignShape = new JsonArray();
        exactCoreSignShape.add("00");
        exactCoreSignShape.add(new JsonArray());
        exactCoreSignShape.add(JsonNull.INSTANCE);
        assertMethodNotFound(invoke(handler, "SiGnRaWtRaNsAcTiOn", exactCoreSignShape));

        JsonArray exactProofShape = new JsonArray();
        exactProofShape.add(address.getAddress().toBase58());
        exactProofShape.add(INPUT_TXID + ":0:0.001:" + address.getAddress().toBase58());
        assertMethodNotFound(invoke(handler, "SiGnMeSsAgE", exactProofShape));

        assertEquals(1, coin.getAddressKeyPairs().size());
        assertEquals(1, config.getAddressCount());
        verify(config, never()).writeConfig();
        verify(config, never()).loadConfig();
        verify(address, never()).getPrivateKey();
        verifyNoInteractions(relay);
        assertProfileEquals(fixture.profileBeforeManagedStart());
    }

    @Test
    void managedDirectHelpersDenyBeforeKeyPacketOrTransactionWork() throws Exception {
        ManagedFixture fixture = startManagedCoinWithOnePersistedAddress(false);
        coin = fixture.coin();
        AddressBalance address = spy(coin.getAddressKeyPairs().get(0));
        replaceFirstAddress(coin, address);
        ConfigHelper config = spy(coin.getConfigHelper());
        setField(coin, "configHelper", config);
        WalletHelper wallet = coin.getWalletHelper();

        assertThrows(IllegalStateException.class, () -> coin.generateAddress(false));
        assertThrows(IllegalStateException.class, wallet::generateAddress);
        assertThrows(IllegalStateException.class,
                () -> coin.importPrivateKey(fixture.privateKey()));
        assertThrows(IllegalStateException.class,
                () -> wallet.generateFromPrivateKey(fixture.privateKey()));

        Transaction transaction = new Transaction(coin.getNetworkParameters());
        transaction.addOutput(Coin.valueOf(50_000), address.getAddress());
        assertThrows(IllegalStateException.class,
                () -> wallet.createRawTransactionWithAllUTXOs(transaction, 0.001));
        assertThrows(IllegalStateException.class,
                () -> WalletHelper.createTransactionSimple(CoinTicker.LITECOIN,
                        address.getAddress().toBase58(), 0.0005));

        XRouterPacketManager packetManager = mock(XRouterPacketManager.class);
        setField(coin, "xRouterPacketManager", packetManager);
        assertThrows(IllegalStateException.class,
                () -> coin.sendXrMessage(null, "managed-operation", "XrSeNdTrAnSaCtIoN",
                        new HashMap<>()));
        assertNull(coin.sendXrMessage(null, "read-only-operation", "xrGetBlockCount",
                new HashMap<>()));

        ConfigHelper.setReadOnlyExistingProfile(false);
        assertThrows(IllegalStateException.class, coin::reloadConfig);
        coin.runAddressDiscovery();
        assertThrows(IllegalStateException.class,
                () -> coin.init(PASSWORD, null, false, false, false, false));

        assertEquals(1, coin.getAddressKeyPairs().size());
        assertEquals(1, config.getAddressCount());
        verify(config, never()).writeConfig();
        verify(config, never()).loadConfig();
        verify(address, never()).getPrivateKey();
        verifyNoInteractions(packetManager);
        assertProfileEquals(fixture.profileBeforeManagedStart());

        String httpClientSource = Files.readString(Path.of(
                "src/main/java/io/cloudchains/app/net/api/http/client/HTTPClient.java"));
        assertFalse(httpClientSource.contains("sendRawTransaction("));
        assertThrows(NoSuchMethodException.class,
                () -> CoinInstance.class.getMethod("getMnemonicForPw", String.class));
        assertThrows(NoSuchMethodException.class,
                () -> CoinInstance.class.getMethod("getMnemonic"));
    }

    @Test
    void unmanagedCompatibilityStillCreatesImportsProvesAndSigns() throws Exception {
        UnmanagedFixture fixture = startUnmanagedCoinWithOnePersistedAddress();
        coin = fixture.coin();
        assertFalse(coin.isManagedReadOnlyExistingProfile());
        Object handler = createHandler(coin, mock(HTTPClient.class));

        JsonArray createAddress = new JsonArray();
        assertSuccessful(invoke(handler, "getnewaddress", createAddress));

        JsonArray dump = new JsonArray();
        dump.add(fixture.address().getAddress().toBase58());
        JsonObject dumped = invoke(handler, "dumpprivkey", dump);
        assertSuccessful(dumped);
        assertEquals(fixture.privateKey(), dumped.get("result").getAsString());

        JsonArray imported = new JsonArray();
        imported.add(fixture.privateKey());
        assertSuccessful(invoke(handler, "importprivkey", imported));

        JsonArray selfProof = new JsonArray();
        selfProof.add(fixture.address().getAddress().toBase58());
        selfProof.add(fixture.address().getAddress().toBase58());
        assertSuccessful(invoke(handler, "signmessage", selfProof));

        JsonArray rawInputs = new JsonArray();
        JsonObject input = new JsonObject();
        input.addProperty("txid", INPUT_TXID);
        input.addProperty("vout", 0);
        rawInputs.add(input);
        JsonObject rawOutputs = new JsonObject();
        rawOutputs.addProperty(fixture.address().getAddress().toBase58(), "0.0005");
        JsonArray rawParams = new JsonArray();
        rawParams.add(rawInputs);
        rawParams.add(rawOutputs);
        assertSuccessful(invoke(handler, "createrawtransaction", rawParams));

        assertNotNull(coin.generateAddress(false));
        assertNotNull(coin.getWalletHelper().generateAddress());
        UTXO spendable = new UTXO(CoinTicker.LITECOIN,
                fixture.address().getAddress().toBase58(), INPUT_TXID, 0, 100, 200_000);
        fixture.address().addUtxo(spendable);
        Transaction spend = new Transaction(coin.getNetworkParameters());
        spend.addOutput(Coin.valueOf(50_000), fixture.address().getAddress());
        Transaction signed = coin.getWalletHelper()
                .createRawTransactionWithAllUTXOs(spend, 0.001);
        assertNotNull(signed);
        assertEquals(1, signed.getInputs().size());
        assertTrue(signed.getInput(0).getScriptBytes().length > 0);
        spendable.setSpent(false);
        Transaction simple = WalletHelper.createTransactionSimple(CoinTicker.LITECOIN,
                fixture.address().getAddress().toBase58(), 0.0005);
        assertNotNull(simple);
        assertEquals(1, simple.getInputs().size());
        assertTrue(simple.getInput(0).getScriptBytes().length > 0);
        assertNull(coin.sendXrMessage(null, "read-only-operation", "xrGetBlockCount",
                new HashMap<>()));
    }

    private ManagedFixture startManagedCoinWithOnePersistedAddress() throws Exception {
        return startManagedCoinWithOnePersistedAddress(true);
    }

    private ManagedFixture startManagedCoinWithOnePersistedAddress(boolean setGlobalPolicy)
            throws Exception {
        UnmanagedFixture unmanaged = startUnmanagedCoinWithOnePersistedAddress();
        String privateKey = unmanaged.privateKey();
        unmanaged.coin().deinit();
        CoinInstance.getCoinInstances().clear();
        Map<String, byte[]> before = snapshotProfile();

        ConfigHelper.setReadOnlyExistingProfile(setGlobalPolicy);
        CoinInstance managed = CoinInstance.getInstance(CoinTicker.LITECOIN);
        assertNotNull(managed);
        assertNull(managed.init(PASSWORD, null, false, false, false, true));
        assertTrue(managed.isManagedReadOnlyExistingProfile());
        assertEquals(1, managed.getAddressKeyPairs().size());
        assertProfileEquals(before);
        return new ManagedFixture(managed, privateKey, before);
    }

    private UnmanagedFixture startUnmanagedCoinWithOnePersistedAddress() throws Exception {
        ConfigHelper config = new ConfigHelper("LTC");
        config.setRpcEnabled(true);
        setField(config, "rpcPort", 19_332);
        config.writeConfig();
        CoinInstance unmanaged = CoinInstance.getInstance(CoinTicker.LITECOIN);
        assertNotNull(unmanaged);
        assertNull(unmanaged.init(PASSWORD, null, false, false, true, false));
        AddressBalance address = unmanaged.generateAddress(true);
        return new UnmanagedFixture(unmanaged, address, address.getPrivateKey().toBase58());
    }

    private static Object createHandler(CoinInstance target, HTTPClient relay) throws Exception {
        Class<?> handlerClass = Class.forName(HANDLER);
        Constructor<?> constructor = handlerClass.getDeclaredConstructor(CoinInstance.class);
        constructor.setAccessible(true);
        Object handler = constructor.newInstance(target);
        Field clientField = handlerClass.getDeclaredField("httpClient");
        clientField.setAccessible(true);
        ((HTTPClient) clientField.get(handler)).close();
        clientField.set(handler, relay);
        return handler;
    }

    private static JsonObject invoke(Object handler, String method, JsonArray params)
            throws Exception {
        Method getResponse = handler.getClass().getDeclaredMethod("getResponse", String.class,
                JsonArray.class);
        getResponse.setAccessible(true);
        return (JsonObject) getResponse.invoke(handler, method, params);
    }

    @SuppressWarnings("unchecked")
    private static void replaceFirstAddress(CoinInstance target, AddressBalance replacement)
            throws Exception {
        Field field = CoinInstance.class.getDeclaredField("addressKeyPairs");
        field.setAccessible(true);
        CopyOnWriteArrayList<AddressBalance> addresses =
                (CopyOnWriteArrayList<AddressBalance>) field.get(target);
        addresses.set(0, replacement);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private Map<String, byte[]> snapshotProfile() throws Exception {
        Map<String, byte[]> snapshot = new LinkedHashMap<>();
        Path root = tempDir.resolve("CloudChains");
        if (!Files.exists(root))
            return snapshot;
        try (var paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile).sorted().forEach(path -> {
                try {
                    snapshot.put(root.relativize(path).toString(), Files.readAllBytes(path));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
        return snapshot;
    }

    private void assertProfileEquals(Map<String, byte[]> expected) throws Exception {
        Map<String, byte[]> actual = snapshotProfile();
        assertEquals(expected.keySet(), actual.keySet());
        for (String path : expected.keySet())
            assertArrayEquals(expected.get(path), actual.get(path), path);
    }

    private static void assertSuccessful(JsonObject response) {
        assertTrue(response.get("error").isJsonNull(), response.toString());
        assertTrue(response.has("result"), response.toString());
    }

    private static void assertMethodNotFound(JsonObject response) {
        assertEquals(-32601, response.getAsJsonObject("error").get("code").getAsInt(),
                response.toString());
        assertEquals("Method not found.",
                response.getAsJsonObject("error").get("message").getAsString());
        assertTrue(response.get("result").isJsonNull(), response.toString());
    }

    private record ManagedFixture(CoinInstance coin, String privateKey,
                                  Map<String, byte[]> profileBeforeManagedStart) {
    }

    private record UnmanagedFixture(CoinInstance coin, AddressBalance address,
                                    String privateKey) {
    }
}
