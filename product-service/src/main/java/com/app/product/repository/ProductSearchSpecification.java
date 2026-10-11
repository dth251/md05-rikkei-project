package com.app.product.repository;

import com.app.product.dto.request.ProductSearchRequest;
import com.app.product.entity.Product;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

public final class ProductSearchSpecification {

    private ProductSearchSpecification() {
    }

    public static Specification<Product> from(
            ProductSearchRequest request
    ) {
        return (root, query, builder) -> {
            var conditions = new ArrayList<Predicate>();
            conditions.add(builder.isTrue(root.get("active")));
            if (present(request.q())) {
                String pattern = "%" + escape(request.q().strip().toLowerCase(Locale.ROOT)) + "%";
                conditions.add(builder.or(
                        builder.like(builder.lower(root.get("name")), pattern, '\\'),
                        builder.like(builder.lower(root.get("sku")), pattern, '\\'),
                        builder.like(builder.lower(root.get("description")), pattern, '\\')));
            }
            if (present(request.category())) {
                conditions.add(builder.equal(builder.lower(root.get("category")),
                        request.category().strip().toLowerCase(Locale.ROOT)));
            }
            if (present(request.brand())) {
                conditions.add(builder.equal(builder.lower(root.get("brand")),
                        request.brand().strip().toLowerCase(Locale.ROOT)));
            }
            if (request.minPrice() != null) {
                conditions.add(builder.greaterThanOrEqualTo(root.get("unitPrice"), request.minPrice()));
            }
            if (request.maxPrice() != null) {
                conditions.add(builder.lessThanOrEqualTo(root.get("unitPrice"), request.maxPrice()));
            }
            return builder.and(conditions.toArray(Predicate[]::new));
        };
    }

    private static boolean present(
            String value
    ) {
        return value != null && !value.isBlank();
    }

    private static String escape(
            String value
    ) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
