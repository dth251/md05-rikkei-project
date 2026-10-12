package com.app.product.grpc;

import com.app.product.config.exception.ApiException;
import com.app.product.grpc.generated.CatalogGrpc;
import com.app.product.grpc.generated.ProductsReply;
import com.app.product.grpc.generated.ProductsRequest;
import com.app.product.mapper.ProductRpcMapper;
import com.app.product.service.ProductService;
import io.grpc.Context;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class ProductRpcEndpoint extends CatalogGrpc.CatalogImplBase {

    private final ProductService products;
    private final ProductRpcMapper mapper;
    private final MeterRegistry metrics;

    public ProductRpcEndpoint(
            ProductService products,
            ProductRpcMapper mapper,
            MeterRegistry metrics
    ) {
        this.products = products;
        this.mapper = mapper;
        this.metrics = metrics;
    }

    @Override
    public void getProducts(
            ProductsRequest request,
            StreamObserver<ProductsReply> observer
    ) {
        Timer.Sample sample = Timer.start(metrics);
        Status status = Status.OK;
        try {
            if (Context.current().isCancelled()) {
                status = Status.CANCELLED;
                observer.onError(status.asRuntimeException());
                return;
            }
            var reply = mapper.toReply(products.lookup(request.getProductIdsList()));
            if (Context.current().isCancelled()) {
                status = Status.CANCELLED;
                observer.onError(status.asRuntimeException());
                return;
            }
            observer.onNext(reply);
            observer.onCompleted();
        } catch (ApiException exception) {
            status = switch (exception.getStatus().value()) {
                case 400 -> Status.INVALID_ARGUMENT;
                case 404 -> Status.NOT_FOUND;
                default -> Status.UNAVAILABLE;
            };
            observer.onError(status.withDescription("Product lookup failed").asRuntimeException());
        } catch (RuntimeException exception) {
            status = Status.INTERNAL;
            observer.onError(status.withDescription("Product lookup unavailable").asRuntimeException());
        } finally {
            sample.stop(Timer.builder("commerce.product.rpc.duration").tag("status", status.getCode().name()).register(metrics));
        }
    }
}
