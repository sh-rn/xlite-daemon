import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.subgraph.orchid.encoders.Hex;
import io.cloudchains.app.net.CoinInstance;
import io.cloudchains.app.net.CoinTicker;
import io.cloudchains.app.util.AddressBalance;
import io.cloudchains.app.util.ConfigHelper;
import io.cloudchains.app.util.UTXO;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.script.ScriptBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class POR172CompatibilityTest {
    private static final String HANDLER =
            "io.cloudchains.app.net.api.http.server.HTTPServerHandler";
    private static final String INPUT_TXID =
            "0000000000000000000000000000000000000000000000000000000000000001";
    private static final long CUSTOM_SEQUENCE = 0x12345678L;
    private static final long CUSTOM_LOCKTIME = 500_000_000L;

    @Test
    void createRawTransactionPreservesCoreFieldsAndP2shOrder(@TempDir Path directory)
            throws Exception {
        CoinInstance coin = startCoin(directory);
        try {
            AddressBalance p2pkh = coin.generateAddress(false);
            String p2sh = Address.fromP2SHHash(coin.getNetworkParameters(),
                    new ECKey().getPubKeyHash()).toBase58();
            JsonArray inputs = new JsonArray();
            JsonObject input = new JsonObject();
            input.addProperty("txid", INPUT_TXID);
            input.addProperty("vout", 1);
            input.addProperty("sequence", CUSTOM_SEQUENCE);
            inputs.add(input);

            JsonObject outputs = new JsonObject();
            outputs.addProperty(p2pkh.getAddress().toBase58(), "1.23456789");
            outputs.addProperty(p2sh, "0.00000001");
            JsonArray params = new JsonArray();
            params.add(inputs);
            params.add(outputs);
            params.add(CUSTOM_LOCKTIME);

            JsonObject response = invoke(coin, "createrawtransaction", params);
            assertSuccessful(response);
            Transaction transaction = decode(coin, response.get("result").getAsString());
            assertEquals(CUSTOM_LOCKTIME, transaction.getLockTime());
            assertEquals(CUSTOM_SEQUENCE, transaction.getInput(0).getSequenceNumber());
            assertEquals(2, transaction.getOutputs().size());
            assertTrue(transaction.getOutput(0).getScriptPubKey().isPayToScriptHash());
            assertEquals(1, transaction.getOutput(0).getValue().value);
            assertEquals(123_456_789, transaction.getOutput(1).getValue().value);
        } finally {
            stopCoin(coin);
        }
    }

    @Test
    void createRawTransactionRejectsUnsafeRangesAndAmounts(@TempDir Path directory)
            throws Exception {
        CoinInstance coin = startCoin(directory);
        try {
            String address = coin.generateAddress(false).getAddress().toBase58();
            for (JsonElement invalidLocktime : new JsonElement[] {
                    new JsonPrimitive(-1), new JsonPrimitive(0x1_0000_0000L),
                    new JsonPrimitive(1.5), new JsonPrimitive("7")}) {
                JsonArray params = createAmountParams(address, "0.00100000");
                params.add(invalidLocktime);
                assertError(invoke(coin, "createrawtransaction", params), -1);
            }
            for (JsonElement invalidVout : new JsonElement[] {
                    new JsonPrimitive(-1), new JsonPrimitive(0x1_0000_0000L),
                    new JsonPrimitive(1.5), new JsonPrimitive("7")}) {
                JsonArray params = createAmountParams(address, "0.00100000");
                params.get(0).getAsJsonArray().get(0).getAsJsonObject()
                        .add("vout", invalidVout);
                assertError(invoke(coin, "createrawtransaction", params), -1006);
            }
            for (JsonElement invalidSequence : new JsonElement[] {
                    new JsonPrimitive(-1), new JsonPrimitive(0x1_0000_0000L),
                    new JsonPrimitive(1.5), new JsonPrimitive("7")}) {
                JsonArray params = createAmountParams(address, "0.00100000");
                params.get(0).getAsJsonArray().get(0).getAsJsonObject()
                        .add("sequence", invalidSequence);
                assertError(invoke(coin, "createrawtransaction", params), -1006);
            }
            for (String amount : new String[] {"0", "-0.00000001", "0.0000000001",
                    "1e-8", "NaN", "Infinity", "92233720368.54775808"})
                assertError(invoke(coin, "createrawtransaction",
                        createAmountParams(address, amount)), -1006);
        } finally {
            stopCoin(coin);
        }
    }

    @Test
    void signRawTransactionFailsClosedUntilIntentBoundAdapter(@TempDir Path directory) throws Exception {
        CoinInstance coin = startCoin(directory);
        try {
            AddressBalance owned = coin.generateAddress(false);
            owned.addUtxo(new UTXO(CoinTicker.LITECOIN, owned.getAddress().toBase58(),
                    INPUT_TXID, 0, 100, 200_000));
            Transaction transaction = new Transaction(coin.getNetworkParameters());
            transaction.setVersion(2);
            transaction.setLockTime(CUSTOM_LOCKTIME);
            TransactionInput input = transaction.addInput(Sha256Hash.wrap(INPUT_TXID), 0,
                    ScriptBuilder.createInputScript(null));
            input.setSequenceNumber(CUSTOM_SEQUENCE);
            transaction.addOutput(Coin.valueOf(100_000), owned.getAddress());
            byte[] expectedOutputScript = transaction.getOutput(0).getScriptPubKey().getProgram();
            long expectedOutputValue = transaction.getOutput(0).getValue().value;

            JsonArray params = new JsonArray();
            params.add(new String(Hex.encode(transaction.bitcoinSerialize())));
            JsonObject response = invoke(coin, "signrawtransaction", params);
            assertError(response, -32601);
            assertTrue(expectedOutputScript.length > 0);
            assertTrue(expectedOutputValue > 0);
        } finally {
            stopCoin(coin);
        }
    }

    @Test
    void signRawTransactionRejectsUnownedAndUnexpectedOptions(@TempDir Path directory)
            throws Exception {
        CoinInstance coin = startCoin(directory);
        try {
            AddressBalance destination = coin.generateAddress(false);
            Transaction transaction = new Transaction(coin.getNetworkParameters());
            transaction.addInput(Sha256Hash.wrap(INPUT_TXID), 0,
                    ScriptBuilder.createInputScript(null));
            transaction.addOutput(Coin.valueOf(100_000), destination.getAddress());
            String raw = new String(Hex.encode(transaction.bitcoinSerialize()));

            JsonArray rawParams = new JsonArray();
            rawParams.add(raw);
            assertError(invoke(coin, "signrawtransaction", rawParams), -32601);
            rawParams.add(new JsonArray());
            assertError(invoke(coin, "signrawtransaction", rawParams), -32601);
        } finally {
            stopCoin(coin);
        }
    }

    @Test
    void signRawTransactionRejectsMixedOwnedAndUnownedInputs(@TempDir Path directory)
            throws Exception {
        CoinInstance coin = startCoin(directory);
        try {
            AddressBalance owned = coin.generateAddress(false);
            owned.addUtxo(new UTXO(CoinTicker.LITECOIN, owned.getAddress().toBase58(),
                    INPUT_TXID, 0, 100, 200_000));
            Transaction transaction = new Transaction(coin.getNetworkParameters());
            transaction.addInput(Sha256Hash.wrap(INPUT_TXID), 0,
                    ScriptBuilder.createInputScript(null));
            transaction.addInput(Sha256Hash.wrap(
                    "0000000000000000000000000000000000000000000000000000000000000002"), 1,
                    ScriptBuilder.createInputScript(null));
            transaction.addOutput(Coin.valueOf(100_000), owned.getAddress());

            JsonArray params = new JsonArray();
            params.add(new String(Hex.encode(transaction.bitcoinSerialize())));
            assertError(invoke(coin, "signrawtransaction", params), -32601);
        } finally {
            stopCoin(coin);
        }
    }

    @Test
    void spentUtxosCannotAuthoriseProofOrSigning(@TempDir Path directory) throws Exception {
        CoinInstance coin = startCoin(directory);
        try {
            AddressBalance owned = coin.generateAddress(false);
            String address = owned.getAddress().toBase58();
            UTXO spent = new UTXO(CoinTicker.LITECOIN, address, INPUT_TXID,
                    7, 100, 100_000);
            spent.setSpent(true);
            owned.addUtxo(spent);

            JsonArray proof = new JsonArray();
            proof.add(address);
            proof.add(INPUT_TXID + ":7:0.001:" + address);
            assertError(invoke(coin, "signmessage", proof), -1);

            Transaction transaction = new Transaction(coin.getNetworkParameters());
            transaction.addInput(Sha256Hash.wrap(INPUT_TXID), 7,
                    ScriptBuilder.createInputScript(null));
            transaction.addOutput(Coin.valueOf(50_000), owned.getAddress());
            JsonArray signParams = new JsonArray();
            signParams.add(new String(Hex.encode(transaction.bitcoinSerialize())));
            assertError(invoke(coin, "signrawtransaction", signParams), -32601);
        } finally {
            stopCoin(coin);
        }
    }

    @Test
    void importAndExportMethodsRemainAvailableToLocalCompatibilityBoundary(@TempDir Path directory)
            throws Exception {
        CoinInstance coin = startCoin(directory);
        try {
            AddressBalance owned = coin.generateAddress(false);
            JsonArray dumpParams = new JsonArray();
            dumpParams.add(owned.getAddress().toBase58());
            JsonObject dump = invoke(coin, "dumpprivkey", dumpParams);
            assertSuccessful(dump);
            assertEquals(owned.getPrivateKey().toBase58(), dump.get("result").getAsString());

            JsonArray importParams = new JsonArray();
            importParams.add(owned.getPrivateKey().toBase58());
            assertSuccessful(invoke(coin, "importprivkey", importParams));
        } finally {
            stopCoin(coin);
        }
    }

    @Test
    void genericSendTransactionIsMethodNotFound(@TempDir Path directory) throws Exception {
        CoinInstance coin = startCoin(directory);
        try {
            JsonArray params = new JsonArray();
            params.add("address");
            params.add("1.0");
            assertError(invoke(coin, "sendtransaction", params), -32601);
        } finally {
            stopCoin(coin);
        }
    }

    @Test
    void rawBroadcastFailsClosedUntilIntentBoundAdapter(@TempDir Path directory) throws Exception {
        CoinInstance coin = startCoin(directory);
        try {
            JsonArray malformed = new JsonArray();
            malformed.add("not-a-raw-transaction");
            assertError(invoke(coin, "sendrawtransaction", malformed), -32601);

            JsonArray validShape = new JsonArray();
            validShape.add("00");
            assertError(invoke(coin, "sendrawtransaction", validShape), -32601);
        } finally {
            stopCoin(coin);
        }
    }

    @Test
    void coreUtxoProofRequiresActualOwnedOutput(@TempDir Path directory) throws Exception {
        CoinInstance coin = startCoin(directory);
        try {
            AddressBalance owned = coin.generateAddress(false);
            String address = owned.getAddress().toBase58();
            owned.addUtxo(new UTXO(CoinTicker.LITECOIN, address, INPUT_TXID,
                    7, 100, 100_000));
            JsonArray valid = new JsonArray();
            valid.add(address);
            valid.add(INPUT_TXID + ":7:0.001:" + address);
            assertSuccessful(invoke(coin, "signmessage", valid));

            JsonArray wrongAmount = new JsonArray();
            wrongAmount.add(address);
            wrongAmount.add(INPUT_TXID + ":7:0.002:" + address);
            assertError(invoke(coin, "signmessage", wrongAmount), -1);

            JsonArray arbitrary = new JsonArray();
            arbitrary.add(address);
            arbitrary.add("POR-172 arbitrary message");
            assertError(invoke(coin, "signmessage", arbitrary), -1);

            String nonWalletAddress = new ECKey()
                    .toAddress(coin.getNetworkParameters()).toBase58();
            JsonArray wrongAddress = new JsonArray();
            wrongAddress.add(address);
            wrongAddress.add(INPUT_TXID + ":7:0.001:" + nonWalletAddress);
            assertError(invoke(coin, "signmessage", wrongAddress), -1);

            JsonArray malformed = new JsonArray();
            malformed.add(address);
            malformed.add(INPUT_TXID + ":7:0.001");
            assertError(invoke(coin, "signmessage", malformed), -1);

            JsonArray uppercaseTxid = new JsonArray();
            uppercaseTxid.add(address);
            uppercaseTxid.add("ABCDEF0000000000000000000000000000000000000000000000000000000001"
                    + ":7:0.001:" + address);
            assertError(invoke(coin, "signmessage", uppercaseTxid), -1);
        } finally {
            stopCoin(coin);
        }
    }

    @Test
    void coreUtxoProofUsesGoldenCoreFloatExponentVectors(@TempDir Path directory)
            throws Exception {
        CoinInstance coin = startCoin(directory);
        try {
            AddressBalance owned = coin.generateAddress(false);
            String address = owned.getAddress().toBase58();
            String[] txids = {
                    "0000000000000000000000000000000000000000000000000000000000000011",
                    "0000000000000000000000000000000000000000000000000000000000000012",
                    "0000000000000000000000000000000000000000000000000000000000000013",
                    "0000000000000000000000000000000000000000000000000000000000000014"};
            long[] values = {10_000L, 1_000L, 10_000_000_000_000L, 100_000_000_000_000L};
            String[] canonicalAmounts = {"0.0001", "1e-05", "100000", "1e+06"};
            for (int i = 0; i < txids.length; i++) {
                owned.addUtxo(new UTXO(CoinTicker.LITECOIN, address, txids[i],
                        i, 100, values[i]));
                JsonArray proof = new JsonArray();
                proof.add(address);
                proof.add(txids[i] + ":" + i + ":" + canonicalAmounts[i] + ":" + address);
                assertSuccessful(invoke(coin, "signmessage", proof));
            }

            JsonArray nonCanonicalExponent = new JsonArray();
            nonCanonicalExponent.add(address);
            nonCanonicalExponent.add(txids[1] + ":1:0.00001:" + address);
            assertError(invoke(coin, "signmessage", nonCanonicalExponent), -1);
        } finally {
            stopCoin(coin);
        }
    }

    private static JsonArray createAmountParams(String address, String amount) {
        JsonArray inputs = new JsonArray();
        JsonObject input = new JsonObject();
        input.addProperty("txid", INPUT_TXID);
        input.addProperty("vout", 0);
        inputs.add(input);
        JsonObject outputs = new JsonObject();
        outputs.addProperty(address, amount);
        JsonArray params = new JsonArray();
        params.add(inputs);
        params.add(outputs);
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

    private static Transaction decode(CoinInstance coin, String hex) {
        return new Transaction(coin.getNetworkParameters(), Hex.decode(hex));
    }

    private static void assertSuccessful(JsonObject response) {
        assertTrue(response.get("error").isJsonNull(), response.toString());
        assertTrue(response.has("result"), response.toString());
    }

    private static void assertError(JsonObject response, int code) {
        assertEquals(code, response.getAsJsonObject("error").get("code").getAsInt(),
                response.toString());
    }
}
