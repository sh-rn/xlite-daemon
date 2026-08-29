package io.cloudchains.app.net.api;

import io.cloudchains.app.net.CoinInstance;
import io.cloudchains.app.util.ConfigHelper;

import java.util.HashMap;

public class JSONRPCController {

    private static final HashMap<CoinInstance, JSONRPCServer> servers = new HashMap<>();
    private static JSONRPCMasterServer masterServer;

    public static synchronized JSONRPCMasterServer getMasterServer() {
        if (masterServer == null) {
            ConfigHelper config = new ConfigHelper("master");
            if (!config.isValidConfiguration()) {
                throw new IllegalStateException("Master RPC configuration is unavailable.");
            }
            masterServer = new JSONRPCMasterServer(config.getMasterRpcPort());
        }
        return masterServer;
    }

    public static JSONRPCServer getRPCServer(CoinInstance coinInstance) {
        if (coinInstance == null || coinInstance.getRPCPort() == -1) {
            throw new IllegalArgumentException("Bad coin instance");
        }

        if (!servers.containsKey(coinInstance)) {
            servers.put(coinInstance, new JSONRPCServer(coinInstance, coinInstance.getRPCPort()));
        }

        return servers.get(coinInstance);
    }

    public static void removeRPCServer(CoinInstance coinInstance) {
        if (coinInstance == null || coinInstance.getRPCPort() == -1) {
            throw new IllegalArgumentException("Bad coin instance");
        }

        JSONRPCServer server = servers.get(coinInstance);
        if (server == null)
            return;

        if (server.isAlive())
            server.deinit();

        servers.remove(coinInstance);
    }
}
