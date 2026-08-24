import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.cloudchains.app.net.CoinInstance;
import io.cloudchains.app.net.CoinTicker;
import io.cloudchains.app.util.AddressBalance;
import io.cloudchains.app.util.ConfigHelper;
import io.cloudchains.app.util.UTXO;
import org.bitcoinj.core.ECKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class POR172XBridgeCompatibilityTest {
    private static final String HANDLER =
            "io.cloudchains.app.net.api.http.server.HTTPServerHandler";
    private static final String INPUT_TXID =
            "0000000000000000000000000000000000000000000000000000000000000001";

    @Test
    void walletOwnedSelfAddressProofSigns(@TempDir Path directory) throws Exception {
        CoinInstance coin = startCoin(directory);
        try {
            String ownedAddress = coin.generateAddress(false).getAddress().toBase58();
            JsonObject response = invoke(coin, "signmessage",
                    signMessageParams(ownedAddress, ownedAddress));
            assertSuccessful(response);
            assertTrue(response.get("result").getAsString().length() > 0);
        } finally {
            stopCoin(coin);
        }
    }

    @Test
    void selfProofRequiresWalletOwnership(@TempDir Path directory) throws Exception {
        CoinInstance coin = startCoin(directory);
        try {
            String nonWalletAddress = new ECKey()
                    .toAddress(coin.getNetworkParameters()).toBase58();
            assertError(invoke(coin, "signmessage",
                    signMessageParams(nonWalletAddress, nonWalletAddress)), -5);
        } finally {
            stopCoin(coin);
        }
    }

    @Test
    void compatibilityBoundaryIsExplicitAndImportExportAreRetained() throws Exception {
        String content = Files.readString(Path.of(
                "src/main/java/io/cloudchains/app/net/api/http/server/HTTPServerHandler.java"));
        int importStart = content.indexOf("case \"importprivkey\"");
        int signMessageStart = content.indexOf("case \"signmessage\"");
        int sendTransactionStart = content.indexOf("case \"sendtransaction\"");
        int signRawStart = content.indexOf("case \"signrawtransaction\"");
        int sendRawStart = content.indexOf("case \"sendrawtransaction\"");
        assertTrue(importStart >= 0);
        assertTrue(signMessageStart > importStart);
        assertTrue(sendTransactionStart > signMessageStart);
        assertTrue(signRawStart >= 0);
        assertTrue(sendRawStart >= 0);
        String privateMethods = content.substring(importStart, signMessageStart);
        assertTrue(privateMethods.contains("coin.importPrivateKey"));
        assertTrue(privateMethods.contains("getPrivateKey().toBase58"));
        assertTrue(content.substring(signMessageStart, sendTransactionStart)
                .contains("isCoreUtxoEntryMessage(message, addr)"));
        int proofValidation = content.indexOf("isCoreUtxoEntryMessage(message, addr)", signMessageStart);
        int privateKeyLookup = content.indexOf("address.getPrivateKey().getKey()", signMessageStart);
        assertTrue(proofValidation >= 0 && privateKeyLookup > proofValidation,
                "The signing key must not be touched before proof validation.");
        assertTrue(content.substring(sendTransactionStart)
                .contains("setRpcError(response, -32601, \"Method not found.\")"));
        int signRawEnd = content.indexOf("case \"gettxout\"", signRawStart);
        int sendRawEnd = content.indexOf("case \"getrawtransaction\"", sendRawStart);
        assertTrue(content.substring(signRawStart, signRawEnd)
                .contains("setRpcError(response, -32601, \"Method not found.\")"));
        assertTrue(content.substring(sendRawStart, sendRawEnd)
                .contains("setRpcError(response, -32601, \"Method not found.\")"));
    }

    @Test
    void coreProofUsesAnUnspentWalletOwnedUtxo(@TempDir Path directory) throws Exception {
        CoinInstance coin = startCoin(directory);
        try {
            AddressBalance owned = coin.generateAddress(false);
            String address = owned.getAddress().toBase58();
            owned.addUtxo(new UTXO(CoinTicker.LITECOIN, address, INPUT_TXID,
                    7, 100, 100_000));
            assertSuccessful(invoke(coin, "signmessage",
                    signMessageParams(address, INPUT_TXID + ":7:0.001:" + address)));
        } finally {
            stopCoin(coin);
        }
    }

    private static JsonArray signMessageParams(String address, String message) {
        JsonArray params = new JsonArray();
        params.add(address);
        params.add(message);
        return params;
    }

    private static CoinInstance startCoin(Path directory) {
        ConfigHelper.CONFIG_DIR = directory.toString();
        CoinInstance.getCoinInstances().clear();
        CoinInstance.setAddressDiscoveryEnabled(false);
        ConfigHelper config = new ConfigHelper("LTC");
        config.setRpcEnabled(true);
        config.writeConfig();
        CoinInstance coin = CoinInstance.getInstance(CoinTicker.LITECOIN);
        assertNotNull(coin);
        assertTrue(coin.init("Por172^test", null, false) == null);
        return coin;
    }

    private static void stopCoin(CoinInstance coin) {
        if (coin != null)
            coin.deinit();
        CoinInstance.getCoinInstances().clear();
        CoinInstance.setAddressDiscoveryEnabled(true);
    }

    private static JsonObject invoke(CoinInstance coin, String method, JsonArray params)
            throws Exception {
        Class<?> handlerClass = Class.forName(HANDLER);
        Constructor<?> constructor = handlerClass.getDeclaredConstructor(CoinInstance.class);
        constructor.setAccessible(true);
        Object handler = constructor.newInstance(coin);
        Method getResponse = handlerClass.getDeclaredMethod("getResponse", String.class,
                JsonArray.class);
        getResponse.setAccessible(true);
        JsonObject response = (JsonObject) getResponse.invoke(handler, method, params);
        assertNotNull(response);
        return response;
    }

    private static void assertSuccessful(JsonObject response) {
        assertTrue(response.get("error").isJsonNull(), response.toString());
    }

    private static void assertError(JsonObject response, int code) {
        assertEquals(code, response.getAsJsonObject("error").get("code").getAsInt(),
                response.toString());
    }
}
