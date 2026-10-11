package com.app.product.controller;

import com.app.product.dto.request.VariantRequest;
import com.app.product.dto.response.ApiResponse;
import com.app.product.dto.response.ProductResponse;
import com.app.product.service.ProductVariantService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products/{parentId}/variants")
public class ProductVariantController {

    private final ProductVariantService variants;

    public ProductVariantController(
            ProductVariantService variants
    ) {
        this.variants = variants;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductResponse>>> list(
            @PathVariable Long parentId
    ) {
        return ResponseEntity.ok(ApiResponse.success(variants.list(parentId), "Variants retrieved"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> create(
            @PathVariable Long parentId,
            @Valid @RequestBody VariantRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                variants.create(parentId, request), "Variant created"));
    }
}
