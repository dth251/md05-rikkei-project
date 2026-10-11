package com.app.product.service.impl;

import com.app.product.config.exception.ApiException;
import com.app.product.dto.request.VariantRequest;
import com.app.product.dto.response.ProductResponse;
import com.app.product.entity.Product;
import com.app.product.mapper.ProductMapper;
import com.app.product.mapper.VariantMapper;
import com.app.product.repository.ProductRepository;
import com.app.product.service.ProductVariantService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductVariantServiceImpl implements ProductVariantService {

    private final ProductRepository products;
    private final ProductMapper mapper;
    private final VariantMapper variants;

    public ProductVariantServiceImpl(
            ProductRepository products,
            ProductMapper mapper,
            VariantMapper variants
    ) {
        this.products = products;
        this.mapper = mapper;
        this.variants = variants;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> list(
            Long parentId
    ) {
        root(products.findByIdAndActiveTrue(parentId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "Product not found")));
        return products.findByParent_IdAndActiveTrueOrderByIdAsc(parentId).stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional
    public ProductResponse create(
            Long parentId,
            VariantRequest request
    ) {
        Product parent = products.findLockedActive(parentId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "Product not found"));
        root(parent);
        var attributes = variants.normalize(request.attributes());
        if (attributes.values().stream().anyMatch(value -> value.isBlank() || value.length() > 64)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Normalized variant values must have one to sixty-four characters");
        }
        String key = variants.key(attributes);
        if (products.existsByParent_IdAndVariantKey(parentId, key)) {
            throw new ApiException(HttpStatus.CONFLICT, "Variant attributes already exist");
        }
        if (products.countByParent_Id(parentId) >= 50) {
            throw new ApiException(HttpStatus.CONFLICT, "Maximum fifty variants per product");
        }
        Product product = new Product();
        mapper.apply(request.product(), product);
        product.defineVariant(parent, attributes, key);
        return mapper.toResponse(products.saveAndFlush(product));
    }

    private void root(
            Product product
    ) {
        if (product.getParentId() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "Nested variants are not supported");
        }
    }
}
