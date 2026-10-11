package com.app.product.service;

import com.app.product.dto.request.VariantRequest;
import com.app.product.dto.response.ProductResponse;
import java.util.List;

public interface ProductVariantService {

    List<ProductResponse> list(
            Long parentId
    );

    ProductResponse create(
            Long parentId,
            VariantRequest request
    );
}
