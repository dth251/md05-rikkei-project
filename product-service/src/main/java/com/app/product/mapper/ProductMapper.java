package com.app.product.mapper;

import com.app.product.dto.request.ProductRequest;
import com.app.product.dto.response.ProductResponse;
import com.app.product.entity.Product;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    public void apply(
            ProductRequest request,
            Product product
    ) {
        product.setSku(request.sku().toUpperCase(Locale.ROOT));
        product.setName(request.name().trim());
        product.setDescription(trim(request.description()));
        product.setUnitPrice(request.unitPrice());
        product.setCategory(trim(request.category()));
        product.setBrand(trim(request.brand()));
    }

    public ProductResponse toResponse(
            Product product
    ) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getUnitPrice(),
                product.getCategory(),
                product.getBrand(),
                product.isActive(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }

    private String trim(
            String value
    ) {
        return value == null ? null : value.trim();
    }
}
