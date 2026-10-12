package com.app.product;

import static org.junit.jupiter.api.Assertions.*;

import com.app.product.dto.request.ProductRequest;
import com.app.product.grpc.ProductRpcEndpoint;
import com.app.product.grpc.generated.CatalogGrpc;
import com.app.product.grpc.generated.ProductsRequest;
import com.app.product.security.ProductRpcAuthentication;
import com.app.product.service.ProductService;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.MetadataUtils;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ProductRpcTests {

    private static final String TEST_KEY = "a".repeat(64);
    @Autowired
    private ProductService products;
    @Autowired
    private ProductRpcEndpoint endpoint;
    private Server server;
    private ManagedChannel channel;
    private CatalogGrpc.CatalogBlockingStub authenticated;

    @BeforeEach
    void openTransport() throws Exception {
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name).directExecutor()
                .addService(ServerInterceptors.intercept(endpoint, new ProductRpcAuthentication(TEST_KEY)))
                .build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        Metadata headers = new Metadata();
        headers.put(ProductRpcAuthentication.CREDENTIAL, TEST_KEY);
        authenticated = CatalogGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
    }

    @AfterEach
    void closeTransport() throws Exception {
        channel.shutdownNow().awaitTermination(3, TimeUnit.SECONDS);
        server.shutdownNow().awaitTermination(3, TimeUnit.SECONDS);
    }

    @Test
    void batchOfFiftyPreservesInputOrderAndExactDecimalPrice() {
        List<Long> ids = new ArrayList<>();
        for (int index = 0; index < 50; index++) {
            ids.add(products.create(new ProductRequest("RPC-" + UUID.randomUUID(), "RPC fixture", null,
                    new BigDecimal("12.50"), "RPC", "Fixture")).id());
        }
        var reversed = ids.reversed();
        var reply = authenticated.getProducts(request(reversed));
        assertEquals(reversed, reply.getProductsList().stream().map(item -> item.getId()).toList());
        assertTrue(reply.getProductsList().stream().allMatch(item -> item.getActive()
                && new BigDecimal(item.getUnitPrice()).compareTo(new BigDecimal("12.50")) == 0));
    }

    @Test
    void invalidBatchAndRetiredProductsFailWithoutPartialReply() {
        for (List<Long> ids : List.of(List.<Long>of(), List.of(0L), List.of(1L, 1L),
                java.util.stream.LongStream.rangeClosed(1, 51).boxed().toList())) {
            assertStatus(Status.Code.INVALID_ARGUMENT, () -> authenticated.getProducts(request(ids)));
        }
        var product = products.create(new ProductRequest("RPC-" + UUID.randomUUID(), "Retired RPC fixture", null,
                new BigDecimal("1.00"), "RPC", "Fixture"));
        products.delete(product.id());
        assertStatus(Status.Code.NOT_FOUND, () -> authenticated.getProducts(request(List.of(product.id()))));
    }

    @Test
    void missingOrWrongInternalCredentialIsDenied() {
        var plain = CatalogGrpc.newBlockingStub(channel);
        assertStatus(Status.Code.UNAUTHENTICATED, () -> plain.getProducts(request(List.of(1L))));
        Metadata wrong = new Metadata();
        wrong.put(ProductRpcAuthentication.CREDENTIAL, "b".repeat(64));
        var invalid = plain.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(wrong));
        assertStatus(Status.Code.UNAUTHENTICATED, () -> invalid.getProducts(request(List.of(1L))));
    }

    private ProductsRequest request(List<Long> ids) {
        return ProductsRequest.newBuilder().addAllProductIds(ids).build();
    }

    private void assertStatus(Status.Code expected, org.junit.jupiter.api.function.Executable call) {
        var error = assertThrows(StatusRuntimeException.class, call);
        assertEquals(expected, error.getStatus().getCode());
        assertFalse(error.getMessage().contains(TEST_KEY));
    }
}
