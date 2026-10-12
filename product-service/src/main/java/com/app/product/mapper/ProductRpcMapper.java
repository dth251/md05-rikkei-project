package com.app.product.mapper;

import com.app.product.dto.response.ProductResponse;
import com.app.product.grpc.generated.CatalogProduct;
import com.app.product.grpc.generated.ProductsReply;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ProductRpcMapper {

    public ProductsReply toReply(
            List<ProductResponse> products
    ) {
        var reply = ProductsReply.newBuilder();
        for (ProductResponse product : products) {
            reply.addProducts(CatalogProduct.newBuilder().setId(product.id()).setSku(product.sku())
                    .setName(product.name()).setUnitPrice(product.unitPrice().toPlainString()).setActive(product.active()));
        }
        return reply.build();
    }
}
