package com.app.product.service.impl;

import com.app.product.config.exception.ApiException;
import com.app.product.dto.request.ProductRequest;
import com.app.product.dto.request.ProductSearchRequest;
import com.app.product.dto.response.ProductPageResponse;
import com.app.product.dto.response.ProductResponse;
import com.app.product.entity.Product;
import com.app.product.mapper.ProductMapper;
import com.app.product.repository.ProductRepository;
import com.app.product.repository.ProductSearchSpecification;
import com.app.product.service.ProductService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    public ProductServiceImpl(
            ProductRepository productRepository,
            ProductMapper productMapper
    ) {
        this.productRepository = productRepository;
        this.productMapper = productMapper;
    }

    @Override
    public ProductPageResponse list(
            int page,
            int size
    ) {
        return search(new ProductSearchRequest(page, size, null, null, null, null, null, null));
    }

    @Override
    public ProductPageResponse search(
            ProductSearchRequest request
    ) {
        int page = request.page() == null ? 0 : request.page();
        int size = request.size() == null ? 20 : request.size();
        if (page < 0 || page > 10000 || size < 1 || size > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Page must be between 0 and 10000 and size between 1 and 100");
        }
        if (request.minPrice() != null && request.maxPrice() != null
                && request.minPrice().compareTo(request.maxPrice()) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Minimum price exceeds maximum price");
        }
        Sort sorting = switch (request.sort() == null ? "id" : request.sort()) {
            case "id" -> Sort.by("id");
            case "name" -> Sort.by("name").and(Sort.by("id"));
            case "price-asc" -> Sort.by("unitPrice").and(Sort.by("id"));
            case "price-desc" -> Sort.by(Sort.Direction.DESC, "unitPrice").and(Sort.by("id"));
            case "newest" -> Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id"));
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported product sort");
        };
        var products = productRepository.findAll(ProductSearchSpecification.from(request), PageRequest.of(page, size, sorting));
        return new ProductPageResponse(
                products.map(productMapper::toResponse).getContent(),
                page,
                size,
                products.getTotalElements(),
                products.getTotalPages()
        );
    }

    @Override
    public ProductResponse get(
            Long id
    ) {
        return productMapper.toResponse(findActive(id));
    }

    @Override
    @Transactional
    public ProductResponse create(
            ProductRequest request
    ) {
        Product product = new Product();
        productMapper.apply(request, product);
        return productMapper.toResponse(productRepository.saveAndFlush(product));
    }

    @Override
    @Transactional
    public ProductResponse update(
            Long id,
            ProductRequest request
    ) {
        Product product = findActiveForUpdate(id);
        if (product.getParentId() != null && !product.getSku().equals(request.sku().toUpperCase(Locale.ROOT))) {
            throw new ApiException(HttpStatus.CONFLICT, "Variant SKU is immutable; create a new variant instead");
        }
        productMapper.apply(request, product);
        return productMapper.toResponse(productRepository.saveAndFlush(product));
    }

    @Override
    @Transactional
    public void delete(
            Long id
    ) {
        Product product = findActiveForUpdate(id);
        if (product.getParentId() == null) {
            productRepository.deactivateChildren(id, Instant.now());
        }
        product.setActive(false);
        productRepository.saveAndFlush(product);
    }

    private Product findActiveForUpdate(
            Long id
    ) {
        Product current = findActive(id);
        if (current.getParentId() != null) {
            productRepository.findLockedActive(current.getParentId()).orElseThrow(() ->
                    new ApiException(HttpStatus.NOT_FOUND, "Product not found"));
        }
        return productRepository.findLockedActive(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "Product not found"));
    }

    private Product findActive(
            Long id
    ) {
        return productRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Product not found"));
    }
}
