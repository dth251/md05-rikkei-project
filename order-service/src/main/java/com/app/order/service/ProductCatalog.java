package com.app.order.service;

import com.app.order.dto.response.ProductSnapshot;

public interface ProductCatalog {

    ProductSnapshot get(Long productId);
}
