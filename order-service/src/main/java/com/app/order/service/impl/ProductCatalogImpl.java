package com.app.order.service.impl;

import com.app.order.config.exception.ApiException;
import com.app.order.dto.response.ProductSnapshot;
import com.app.order.service.ProductCatalog;
import com.app.order.mapper.ProductRpcMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.app.order.grpc.generated.CatalogGrpc;
import com.app.order.grpc.generated.ProductsRequest;
import io.grpc.ManagedChannel;
import io.grpc.ConnectivityState;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.channel.nio.NioEventLoopGroup;
import io.grpc.netty.shaded.io.netty.channel.socket.nio.NioSocketChannel;
import io.grpc.stub.MetadataUtils;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PreDestroy;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpClientErrorException;

@Service
public class ProductCatalogImpl implements ProductCatalog {

    private final RestClient client;
    private final ManagedChannel channel;
    private final NioEventLoopGroup rpcEvents;
    private final CatalogGrpc.CatalogBlockingStub rpc;
    private final long deadlineMs;
    private final MeterRegistry metrics;
    private final ProductRpcMapper mapper;
    private final ExecutorService rpcExecutor;

    public ProductCatalogImpl(
            RestClient.Builder builder,
            @Value("${product.base-url}") String baseUrl,
            @Value("${product.rpc.enabled:false}") boolean enabled,
            @Value("${product.rpc.host:product-service}") String host,
            @Value("${product.rpc.port:9002}") int port,
            @Value("${product.rpc.token:}") String credential,
            @Value("${product.rpc.deadline-ms:2500}") long deadlineMs,
            MeterRegistry metrics,
            ProductRpcMapper mapper
    ) {
        if (deadlineMs < 100 || deadlineMs > 5000) {
            throw new IllegalArgumentException("Product RPC deadline must be between 100 and 5000 ms");
        }
        this.deadlineMs = deadlineMs;
        this.metrics = metrics;
        this.mapper = mapper;
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(Duration.ofSeconds(3));
        client = builder.baseUrl(baseUrl).requestFactory(factory).build();
        if (enabled) {
            if (credential == null || !credential.matches("[0-9a-f]{64}") || host == null || host.isBlank()
                    || port < 1 || port > 65535) {
                throw new IllegalStateException("Valid private Product RPC configuration required");
            }
            rpcEvents = new NioEventLoopGroup(1);
            rpcExecutor = Executors.newFixedThreadPool(2,
                    Thread.ofPlatform().daemon(true).name("order-product-rpc-", 0).factory());
            channel = NettyChannelBuilder.forAddress(host, port).usePlaintext()
                    .eventLoopGroup(rpcEvents).channelType(NioSocketChannel.class)
                    .executor(rpcExecutor).maxInboundMessageSize(128 * 1024).disableRetry().build();
            Metadata headers = new Metadata();
            headers.put(Metadata.Key.of("x-product-rpc-key", Metadata.ASCII_STRING_MARSHALLER), credential);
            rpc = CatalogGrpc.newBlockingStub(channel).withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
        } else {
            rpcEvents = null;
            rpcExecutor = null;
            channel = null;
            rpc = null;
        }
    }

    @Override
    public ProductSnapshot get(Long productId) {
        return getAll(List.of(productId)).getFirst();
    }

    @Override
    public List<ProductSnapshot> getAll(
            List<Long> productIds
    ) {
        if (productIds == null || productIds.isEmpty() || productIds.size() > 50
                || productIds.stream().anyMatch(id -> id == null || id <= 0)
                || productIds.stream().distinct().count() != productIds.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid product batch");
        }
        if (rpc == null) {
            return productIds.stream().map(this::getHttp).toList();
        }
        Timer.Sample sample = Timer.start(metrics);
        Status.Code status = Status.Code.OK;
        try {
            // A previous Product outage may leave the shared channel in reconnect backoff.
            // Resume connection now, then wait only within the existing per-call deadline.
            if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) {
                channel.resetConnectBackoff();
            }
            var reply = rpc.withWaitForReady().withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS)
                    .getProducts(ProductsRequest.newBuilder().addAllProductIds(productIds).build());
            return mapper.toSnapshots(reply, productIds);
        } catch (StatusRuntimeException exception) {
            status = exception.getStatus().getCode();
            throw new ApiException(status == Status.Code.NOT_FOUND ? HttpStatus.NOT_FOUND : HttpStatus.SERVICE_UNAVAILABLE,
                    status == Status.Code.NOT_FOUND ? "Product not found" : "Product catalog unavailable");
        } catch (RuntimeException exception) {
            status = Status.Code.INTERNAL;
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Product catalog unavailable");
        } finally {
            sample.stop(Timer.builder("commerce.order.product.rpc.duration").tag("status", status.name()).register(metrics));
        }
    }

    private ProductSnapshot getHttp(
            Long productId
    ) {
        try {
            JsonNode body = client.get().uri("/api/products/{id}", productId)
                    .retrieve().body(JsonNode.class);
            JsonNode data = body == null ? null : body.path("data");
            if (data == null || !data.path("active").asBoolean() || data.path("id").asLong() != productId
                    || !data.path("unitPrice").isNumber()) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Product catalog unavailable");
            }
            return new ProductSnapshot(productId, data.path("sku").asText(),
                    data.path("name").asText(), data.path("unitPrice").decimalValue());
        } catch (HttpClientErrorException.NotFound exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Product not found");
        } catch (RestClientException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Product catalog unavailable");
        }
    }

    @PreDestroy
    public void close() {
        if (channel != null) {
            channel.shutdown();
            try {
                if (!channel.awaitTermination(3, TimeUnit.SECONDS)) {
                    channel.shutdownNow();
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                channel.shutdownNow();
            }
            rpcEvents.shutdownGracefully(0, 2, TimeUnit.SECONDS);
            rpcExecutor.shutdownNow();
        }
    }
}
