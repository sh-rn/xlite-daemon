import io.cloudchains.app.console.ArgMenu;
import io.cloudchains.app.console.ConsoleMenu;
import io.netty.channel.Channel;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class POR171BoundaryTest {
    private static final Path SOURCE_ROOT = Path.of("src/main/java");
    private static final Path HANDLER = SOURCE_ROOT.resolve(
            "io/cloudchains/app/net/api/http/server/HTTPServerHandler.java");
    private static final Path MASTER_HANDLER = SOURCE_ROOT.resolve(
            "io/cloudchains/app/net/api/http/master/HTTPServerHandler.java");

    @Test
    void rpcServersBindOnlyToLoopbackAndStopSafely() throws Exception {
        String asset = Files.readString(SOURCE_ROOT.resolve(
                "io/cloudchains/app/net/api/JSONRPCServer.java"));
        String master = Files.readString(SOURCE_ROOT.resolve(
                "io/cloudchains/app/net/api/JSONRPCMasterServer.java"));
        for (String source : new String[]{asset, master}) {
            assertTrue(source.contains("InetAddress.getByName(\"127.0.0.1\")"));
            assertFalse(source.contains("InetAddress.getLoopbackAddress()"));
            assertFalse(source.contains("bootstrap.bind(port)"));
            assertFalse(source.contains("bootstrap.bind(InetAddress.getByName(\"127.0.0.1\"), port).sync()"));
            assertTrue(source.contains("bindFuture"));
            assertTrue(source.contains("shutdownGracefully(0, 2"));
            assertTrue(source.contains("close().awaitUninterruptibly"));
        }

        assertServerBindsAndStops("io.cloudchains.app.net.api.JSONRPCServer", true);
        assertServerBindsAndStops("io.cloudchains.app.net.api.JSONRPCMasterServer", false);
        assertDeinitBeforeBind("io.cloudchains.app.net.api.JSONRPCServer", true);
        assertDeinitBeforeBind("io.cloudchains.app.net.api.JSONRPCMasterServer", false);
    }

    @Test
    void secretsAreReadFromStdinOnlyAndLegacyArgumentMenuIsDisabled() throws Exception {
        Method readPassword = ConsoleMenu.class.getDeclaredMethod(
                "readPassword", Scanner.class, String.class);
        readPassword.setAccessible(true);
        assertThrows(IllegalArgumentException.class,
                () -> new ConsoleMenu(new String[]{"--password", "cli-secret"}));
        ConsoleMenu menu = new ConsoleMenu(new String[]{"--password"});
        assertEquals("stdin-secret", readPassword.invoke(menu,
                new Scanner("stdin-secret\n"), ""));

        String console = Files.readString(SOURCE_ROOT.resolve(
                "io/cloudchains/app/console/ConsoleMenu.java"));
        assertFalse(console.contains("WALLET_PASSWORD"));
        assertFalse(console.contains("WALLET_MNEMONIC"));
        assertFalse(console.contains("return args[argPos]"));
        assertTrue(console.contains("sanitiseArguments"));
        assertTrue(console.contains("Mnemonic export is disabled."));

        ArgMenu menuStub = new ArgMenu(new String[]{"--new-wallet", "cli-secret"});
        assertThrows(IllegalStateException.class, menuStub::init);
    }

    @Test
    void legacyNoMigrationOptionIsValueFreeAndOrderIndependent() throws Exception {
        Field migrateLegacyWallet = ConsoleMenu.class.getDeclaredField("migrateLegacyWallet");
        migrateLegacyWallet.setAccessible(true);

        ConsoleMenu optionFirst = new ConsoleMenu(new String[]{
                "--no-migrate-legacy-wallet", "--password"});
        ConsoleMenu passwordFirst = new ConsoleMenu(new String[]{
                "--password", "--no-migrate-legacy-wallet"});
        ConsoleMenu defaultPolicy = new ConsoleMenu(new String[]{"--password"});

        assertFalse(migrateLegacyWallet.getBoolean(optionFirst));
        assertFalse(migrateLegacyWallet.getBoolean(passwordFirst));
        assertTrue(migrateLegacyWallet.getBoolean(defaultPolicy));
        assertThrows(IllegalArgumentException.class,
                () -> new ConsoleMenu(new String[]{
                        "--no-migrate-legacy-wallet", "unexpected-value", "--password"}));
        assertThrows(IllegalArgumentException.class,
                () -> new ConsoleMenu(new String[]{
                        "--no-migrate-legacy-wallet=unexpected-value", "--password"}));
        assertTrue(ConsoleMenu.getHelpText().contains("--no-migrate-legacy-wallet"));
        assertTrue(ConsoleMenu.getHelpText().contains(
                "Read a legacy V1 wallet without rewriting it"));
    }

    @Test
    void rpcDiagnosticsNeverIncludePayloadsOrSecrets() throws Exception {
        for (Path handlerPath : new Path[]{HANDLER, MASTER_HANDLER}) {
            String source = Files.readString(handlerPath);
            assertFalse(source.contains("params.get(i).toString()"));
            assertFalse(source.contains("PARAM \" + i"));
            assertFalse(source.contains("Failed Content: \" + content"));
            assertFalse(source.contains("Response content: \" +"));
            assertFalse(source.contains("Signed Raw Transaction: \" +"));
            assertFalse(source.contains("Raw transaction = \" +"));
            assertFalse(source.contains("LOGGER.log(Level.WARNING, \"[http-master] Failed to parse JSON-RPC request\", e)"));
            assertFalse(source.contains("printStackTrace"));
            assertTrue(source.contains("RPC request received."));
            assertTrue(source.contains("regionMatches(true, 0, \"Basic \", 0, \"Basic \".length())"));
            assertFalse(source.contains("regionMatches(true, 0, \"Basic\", 0, \"Basic\".length())"));
        }
    }

    @Test
    void readmeDocumentsTheRestrictedLocalBoundary() throws Exception {
        String readme = Files.readString(Path.of("README.md"));
        assertTrue(readme.contains("loopback only"));
        assertTrue(readme.contains("stdin only"));
        assertTrue(readme.contains("importprivkey"));
        assertTrue(readme.contains("dumpprivkey"));
        assertTrue(readme.contains("sendtransaction` is not available"));
        assertTrue(readme.contains("canonical Core `UtxoEntry` proof"));
        assertTrue(readme.contains("uppercase transaction IDs are rejected"));
        assertTrue(readme.contains("intent-bound Unified adapter"));
        assertFalse(readme.contains("WALLET_PASSWORD"));
        assertFalse(readme.contains("WALLET_MNEMONIC"));
    }

    @Test
    void readOnlyStartupRequiresRealBlockAndLitecoinHeightsBeforeMasterRpc() throws Exception {
        String console = Files.readString(SOURCE_ROOT.resolve(
                "io/cloudchains/app/console/ConsoleMenu.java"));
        int refresh = console.indexOf("App.heightUpdateHttpClient.getAllBlockCounts();");
        int blockGate = console.indexOf(
                "CoinInstance.getBlockCountByTicker(CoinTicker.BLOCKNET) <= 0");
        int litecoinGate = console.indexOf(
                "CoinInstance.getBlockCountByTicker(CoinTicker.LITECOIN) <= 0");
        int masterStart = console.indexOf("App.masterRPC.start();");

        assertTrue(refresh >= 0);
        assertTrue(blockGate > refresh);
        assertTrue(litecoinGate > blockGate);
        assertTrue(masterStart > litecoinGate);
    }

    private static void assertServerBindsAndStops(String className, boolean asset)
            throws Exception {
        Thread server = createServer(className, asset);
        Field channelField = server.getClass().getDeclaredField("channel");
        channelField.setAccessible(true);
        server.start();

        Channel channel = null;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline && channel == null && server.isAlive()) {
            channel = (Channel) channelField.get(server);
            Thread.sleep(10);
        }
        assertNotNull(channel, "RPC server did not bind before its thread exited");
        assertTrue(channel.localAddress() instanceof InetSocketAddress);
        InetSocketAddress address = (InetSocketAddress) channel.localAddress();
        assertEquals("127.0.0.1", address.getAddress().getHostAddress());

        deinit(server);
        deinit(server);
        server.join(TimeUnit.SECONDS.toMillis(5));
        assertFalse(server.isAlive(), "RPC server thread did not stop");
    }

    private static void assertDeinitBeforeBind(String className, boolean asset) throws Exception {
        Thread server = createServer(className, asset);
        Field channelField = server.getClass().getDeclaredField("channel");
        channelField.setAccessible(true);
        deinit(server);
        deinit(server);
        server.start();
        server.join(TimeUnit.SECONDS.toMillis(5));
        assertFalse(server.isAlive(), "Pre-bind deinitialisation did not stop the server");
        assertTrue(channelField.get(server) == null,
                "A server deinitialised before start must not bind");
    }

    private static Thread createServer(String className, boolean asset) throws Exception {
        Class<?> type = Class.forName(className);
        Constructor<?> constructor;
        Object instance;
        if (asset) {
            constructor = type.getDeclaredConstructor(
                    Class.forName("io.cloudchains.app.net.CoinInstance"), int.class);
            constructor.setAccessible(true);
            instance = constructor.newInstance(null, 0);
        } else {
            constructor = type.getDeclaredConstructor(int.class);
            constructor.setAccessible(true);
            instance = constructor.newInstance(0);
        }
        return (Thread) instance;
    }

    private static void deinit(Thread server) throws Exception {
        Method deinit = server.getClass().getDeclaredMethod("deinit");
        deinit.setAccessible(true);
        deinit.invoke(server);
    }
}
