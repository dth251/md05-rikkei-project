package com.app.product.repository;

import com.app.product.entity.Product;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByIdAndActiveTrue(
            Long id
    );

    Page<Product> findByActiveTrue(
            Pageable pageable
    );
}
