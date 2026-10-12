package com.app.order.service;

import com.app.order.dto.response.ProductSnapshot;
import java.util.List;

public interface ProductCatalog {

    ProductSnapshot get(Long productId);

    default List<ProductSnapshot> getAll(
            List<Long> productIds
    ) {
        return productIds.stream().map(this::get).toList();
    }
}
