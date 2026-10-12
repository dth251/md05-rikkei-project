package com.app.product.security;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class ProductRpcAuthentication implements ServerInterceptor {

    public static final Metadata.Key<String> CREDENTIAL = Metadata.Key.of("x-product-rpc-key", Metadata.ASCII_STRING_MARSHALLER);
    private final byte[] credential;

    public ProductRpcAuthentication(
            String credential
    ) {
        if (credential == null || !credential.matches("[0-9a-f]{64}")) {
            throw new IllegalStateException("Configure a private 256-bit Product RPC credential");
        }
        this.credential = credential.getBytes(StandardCharsets.US_ASCII);
    }

    @Override
    public <Request, Response> ServerCall.Listener<Request> interceptCall(
            ServerCall<Request, Response> call,
            Metadata headers,
            ServerCallHandler<Request, Response> next
    ) {
        String supplied = headers.get(CREDENTIAL);
        if (supplied == null || !MessageDigest.isEqual(credential, supplied.getBytes(StandardCharsets.US_ASCII))) {
            call.close(Status.UNAUTHENTICATED.withDescription("Internal credential required"), new Metadata());
            return new ServerCall.Listener<>() { };
        }
        return next.startCall(call, headers);
    }
}
