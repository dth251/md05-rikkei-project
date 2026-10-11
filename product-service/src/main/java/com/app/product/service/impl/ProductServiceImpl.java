package com.app.product.service.impl;

import com.app.product.config.exception.ApiException;
import com.app.product.dto.request.ProductRequest;
import com.app.product.dto.response.ProductPageResponse;
import com.app.product.dto.response.ProductResponse;
import com.app.product.entity.Product;
import com.app.product.mapper.ProductMapper;
import com.app.product.repository.ProductRepository;
import com.app.product.service.ProductService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Page must be nonnegative and size between 1 and 100");
        }
        var products = productRepository.findByActiveTrue(PageRequest.of(page, size, Sort.by("id")));
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
        Product product = findActive(id);
        productMapper.apply(request, product);
        return productMapper.toResponse(productRepository.saveAndFlush(product));
    }

    @Override
    @Transactional
    public void delete(
            Long id
    ) {
        Product product = findActive(id);
        product.setActive(false);
        productRepository.saveAndFlush(product);
    }

    private Product findActive(
            Long id
    ) {
        return productRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Product not found"));
    }
}
