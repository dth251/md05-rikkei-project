package com.app.product.service;

import com.app.product.dto.request.ProductRequest;
import com.app.product.dto.request.ProductSearchRequest;
import com.app.product.dto.response.ProductPageResponse;
import com.app.product.dto.response.ProductResponse;

public interface ProductService {

    ProductPageResponse search(
            ProductSearchRequest request
    );

    ProductPageResponse list(
            int page,
            int size
    );

    ProductResponse get(
            Long id
    );

    ProductResponse create(
            ProductRequest request
    );

    ProductResponse update(
            Long id,
            ProductRequest request
    );

    void delete(
            Long id
    );
}
