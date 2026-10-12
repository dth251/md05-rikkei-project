package com.app.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.app.product.grpc.generated.CatalogGrpc;
import com.app.product.grpc.generated.ProductsRequest;
import com.app.product.security.ProductRpcAuthentication;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.MetadataUtils;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@Tag("vps")
@EnabledIfEnvironmentVariable(named = "PRODUCT_RPC_VPS_TEST", matches = "true")
class ProductRpcVpsTests {

    @Test
    void privateTunnelVerifiesTransportAndCredentialWithoutBusinessWrites() throws Exception {
        String credential = System.getenv("PRODUCT_RPC_TOKEN");
        assertTrue(credential != null && credential.matches("[0-9a-f]{64}"), "Private VPS RPC fixture required");
        int port = Integer.parseInt(System.getenv().getOrDefault("PRODUCT_RPC_VPS_PORT", "19003"));
        var channel = NettyChannelBuilder.forAddress("127.0.0.1", port).usePlaintext()
                .disableServiceConfigLookUp().disableRetry().build();
        try {
            var plain = CatalogGrpc.newBlockingStub(channel).withDeadlineAfter(5, TimeUnit.SECONDS);
            var request = ProductsRequest.getDefaultInstance();
            var denied = assertThrows(StatusRuntimeException.class, () -> plain.getProducts(request));
            assertEquals(Status.Code.UNAUTHENTICATED, denied.getStatus().getCode());
            var headers = new Metadata();
            headers.put(ProductRpcAuthentication.CREDENTIAL, credential);
            var authenticated = plain.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
            var rejected = assertThrows(StatusRuntimeException.class, () -> authenticated.getProducts(request));
            assertEquals(Status.Code.INVALID_ARGUMENT, rejected.getStatus().getCode());
        } finally {
            channel.shutdownNow().awaitTermination(3, TimeUnit.SECONDS);
        }
    }
}
