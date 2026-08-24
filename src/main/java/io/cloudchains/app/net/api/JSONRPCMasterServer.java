package io.cloudchains.app.net.api;

import io.cloudchains.app.net.api.http.master.HTTPServerInitializer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.net.InetAddress;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class JSONRPCMasterServer extends Thread {
    private final static LogManager LOGMANAGER = LogManager.getLogManager();
    private final static Logger LOGGER = LOGMANAGER.getLogger(Logger.GLOBAL_LOGGER_NAME);

    private final int port;
    private volatile boolean stopping = false;

    private volatile Channel channel;
    private volatile ChannelFuture bindFuture;
    private volatile EventLoopGroup workerGroup;
    private final Object lifecycleLock = new Object();

    JSONRPCMasterServer(int port) {
        this.port = port;
    }

    public void run() {
        EventLoopGroup group = new NioEventLoopGroup(2);
        boolean alreadyStopping;
        synchronized (lifecycleLock) {
            workerGroup = group;
            alreadyStopping = stopping;
        }
        if (alreadyStopping) {
            shutdown(group);
            return;
        }
        try {
            LOGGER.log(Level.INFO, "[rpc] Starting master RPC server on port " + port + ".");

            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(group)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new HTTPServerInitializer());

            ChannelFuture pendingBind;
            synchronized (lifecycleLock) {
                if (stopping)
                    return;
                pendingBind = bootstrap.bind(InetAddress.getByName("127.0.0.1"), port);
                bindFuture = pendingBind;
                if (stopping) {
                    pendingBind.cancel(false);
                    return;
                }
            }

            Channel boundChannel = pendingBind.sync().channel();
            synchronized (lifecycleLock) {
                if (stopping) {
                    close(boundChannel);
                    return;
                }
                channel = boundChannel;
            }
            boundChannel.closeFuture().sync();
        } catch (Exception e) {
            if (!stopping) {
                LOGGER.log(Level.WARNING, "[rpc-master] RPC server failed to bind or operate ("
                        + e.getClass().getSimpleName() + ").");
            }
        } finally {
            close(channel);
            shutdown(group);
        }
    }

    public void deinit() {
        Channel currentChannel;
        ChannelFuture pendingBind;
        EventLoopGroup group;
        synchronized (lifecycleLock) {
            stopping = true;
            LOGGER.log(Level.FINER, "[json-rpc-server] Interrupting server.");
            currentChannel = channel;
            pendingBind = bindFuture;
            group = workerGroup;
        }
        if (pendingBind != null && !pendingBind.isDone())
            pendingBind.cancel(false);
        close(currentChannel);
        shutdown(group);
    }

    private static void close(Channel currentChannel) {
        if (currentChannel != null && currentChannel.isOpen())
            currentChannel.close().awaitUninterruptibly(2, TimeUnit.SECONDS);
    }

    private static void shutdown(EventLoopGroup group) {
        if (group != null)
            group.shutdownGracefully(0, 2, TimeUnit.SECONDS).awaitUninterruptibly(2, TimeUnit.SECONDS);
    }
}
