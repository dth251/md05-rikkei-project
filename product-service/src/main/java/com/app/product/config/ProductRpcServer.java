package com.app.product.config;

import com.app.product.grpc.ProductRpcEndpoint;
import com.app.product.security.ProductRpcAuthentication;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.channel.nio.NioEventLoopGroup;
import io.grpc.netty.shaded.io.netty.channel.socket.nio.NioServerSocketChannel;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "product.rpc", name = "enabled", havingValue = "true")
public class ProductRpcServer implements SmartLifecycle, HealthIndicator {

    private final ProductRpcEndpoint endpoint;
    private final ProductRpcAuthentication authentication;
    private final int port;
    private Server server;
    private ExecutorService executor;
    private NioEventLoopGroup boss;
    private NioEventLoopGroup workers;

    public ProductRpcServer(
            ProductRpcEndpoint endpoint,
            @Value("${product.rpc.token:}") String credential,
            @Value("${product.rpc.port:9002}") int port
    ) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Valid Product RPC port required");
        }
        this.endpoint = endpoint;
        this.authentication = new ProductRpcAuthentication(credential);
        this.port = port;
    }

    @Override
    public synchronized void start() {
        if (isRunning()) {
            return;
        }
        executor = Executors.newFixedThreadPool(4, Thread.ofPlatform().daemon(true).name("product-rpc-", 0).factory());
        boss = new NioEventLoopGroup(1);
        workers = new NioEventLoopGroup(2);
        try {
            server = NettyServerBuilder.forAddress(new InetSocketAddress("0.0.0.0", port))
                    .channelType(NioServerSocketChannel.class).bossEventLoopGroup(boss).workerEventLoopGroup(workers)
                    .executor(executor).maxInboundMessageSize(16 * 1024).maxInboundMetadataSize(8 * 1024)
                    .maxConcurrentCallsPerConnection(16)
                    .addService(ServerInterceptors.intercept(endpoint, authentication)).build().start();
        } catch (IOException | RuntimeException exception) {
            stop();
            throw new IllegalStateException("Product RPC server startup failed");
        }
    }

    @Override
    public synchronized void stop() {
        if (server != null) {
            server.shutdown();
            try {
                if (!server.awaitTermination(5, TimeUnit.SECONDS)) {
                    server.shutdownNow();
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                server.shutdownNow();
            }
        }
        if (executor != null) {
            executor.shutdownNow();
        }
        if (workers != null) {
            workers.shutdownGracefully(0, 2, TimeUnit.SECONDS);
        }
        if (boss != null) {
            boss.shutdownGracefully(0, 2, TimeUnit.SECONDS);
        }
    }

    @Override
    public void stop(
            Runnable callback
    ) {
        stop();
        callback.run();
    }

    @Override
    public boolean isRunning() {
        return server != null && !server.isShutdown() && !server.isTerminated();
    }

    @Override
    public Health health() {
        return isRunning() ? Health.up().build() : Health.down().build();
    }
}
