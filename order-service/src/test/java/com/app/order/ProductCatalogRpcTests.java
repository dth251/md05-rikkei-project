package com.app.order;

import static org.junit.jupiter.api.Assertions.*;

import com.app.order.config.exception.ApiException;
import com.app.order.grpc.generated.CatalogGrpc;
import com.app.order.grpc.generated.CatalogProduct;
import com.app.order.grpc.generated.ProductsReply;
import com.app.order.grpc.generated.ProductsRequest;
import com.app.order.mapper.ProductRpcMapper;
import com.app.order.service.impl.ProductCatalogImpl;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

class ProductCatalogRpcTests {

    private static final String TEST_KEY = "a".repeat(64);
    private Server server;
    private ProductCatalogImpl client;
    private final AtomicInteger calls = new AtomicInteger();

    @AfterEach
    void closeTransport() throws Exception {
        if (client != null) {
            client.close();
        }
        if (server != null) {
            server.shutdownNow().awaitTermination(3, TimeUnit.SECONDS);
        }
    }

    @Test
    void fiftyProductsUseOneAuthenticatedWireCallAndPreserveOrder() throws Exception {
        start((request, observer) -> {
            calls.incrementAndGet();
            var reply = ProductsReply.newBuilder();
            request.getProductIdsList().reversed().forEach(id -> reply.addProducts(product(id, "12.50")));
            observer.onNext(reply.build());
            observer.onCompleted();
        }, TEST_KEY, 2500);
        List<Long> ids = java.util.stream.LongStream.rangeClosed(1, 50).boxed().toList();
        var snapshots = client.getAll(ids);
        assertEquals(ids, snapshots.stream().map(item -> item.id()).toList());
        assertEquals(new BigDecimal("12.50"), snapshots.getFirst().unitPrice());
        assertEquals(1, calls.get());
    }

    @Test
    void notFoundRemains404AndNoHttpFallbackOccurs() throws Exception {
        start((request, observer) -> observer.onError(Status.NOT_FOUND.asRuntimeException()), TEST_KEY, 2500);
        assertFailure(HttpStatus.NOT_FOUND);
    }

    @Test
    void wrongCredentialFails503() throws Exception {
        start((request, observer) -> fail("Unauthenticated call reached catalog"), "b".repeat(64), 2500);
        assertFailure(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void deadlineFails503WithinBoundWithoutHttpFallback() throws Exception {
        start((request, observer) -> { }, TEST_KEY, 250);
        long started = System.nanoTime();
        assertFailure(HttpStatus.SERVICE_UNAVAILABLE);
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 2000);
    }

    @Test
    void stoppedCatalogFails503() throws Exception {
        start((request, observer) -> { }, TEST_KEY, 500);
        server.shutdownNow().awaitTermination(3, TimeUnit.SECONDS);
        assertFailure(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void malformedPartialDuplicateAndInactiveRepliesFail503() throws Exception {
        var replies = List.of(ProductsReply.getDefaultInstance(),
                ProductsReply.newBuilder().addProducts(product(1, "1.00")).addProducts(product(1, "1.00")).build(),
                ProductsReply.newBuilder().addProducts(product(1, "1e3")).build(),
                ProductsReply.newBuilder().addProducts(product(1, "0.00")).build(),
                ProductsReply.newBuilder().addProducts(product(1, "1.00").toBuilder().setActive(false)).build());
        AtomicInteger next = new AtomicInteger();
        start((request, observer) -> {
            observer.onNext(replies.get(next.getAndIncrement()));
            observer.onCompleted();
        }, TEST_KEY, 2500);
        for (var ignored : replies) {
            assertFailure(HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private void start(BiConsumer<ProductsRequest, StreamObserver<ProductsReply>> handler,
                       String credential, long deadline) throws Exception {
        ServerInterceptor authentication = new ServerInterceptor() {
            @Override
            public <Request, Response> io.grpc.ServerCall.Listener<Request> interceptCall(
                    io.grpc.ServerCall<Request, Response> call, Metadata headers,
                    io.grpc.ServerCallHandler<Request, Response> next) {
                if (!TEST_KEY.equals(headers.get(Metadata.Key.of("x-product-rpc-key", Metadata.ASCII_STRING_MARSHALLER)))) {
                    call.close(Status.UNAUTHENTICATED, new Metadata());
                    return new io.grpc.ServerCall.Listener<>() { };
                }
                return next.startCall(call, headers);
            }
        };
        server = NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 0))
                .addService(ServerInterceptors.intercept(new CatalogGrpc.CatalogImplBase() {
                    @Override
                    public void getProducts(ProductsRequest request, StreamObserver<ProductsReply> observer) {
                        handler.accept(request, observer);
                    }
                }, authentication)).build().start();
        client = new ProductCatalogImpl(RestClient.builder(), "http://127.0.0.1:1", true, "127.0.0.1",
                server.getPort(), credential, deadline, new SimpleMeterRegistry(), new ProductRpcMapper());
    }

    private CatalogProduct product(long id, String amount) {
        return CatalogProduct.newBuilder().setId(id).setSku("SKU-" + id).setName("RPC fixture")
                .setUnitPrice(amount).setActive(true).build();
    }

    private void assertFailure(HttpStatus expected) {
        var error = assertThrows(ApiException.class, () -> client.getAll(List.of(1L)));
        assertEquals(expected, error.getStatus());
        assertFalse(error.getMessage().contains(TEST_KEY));
    }
}
