package com.app.product.repository;

import com.app.product.entity.Product;
import java.util.Optional;
import java.time.Instant;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select product from Product product where product.id = :id and product.active = true")
    Optional<Product> findLockedActive(
            @Param("id") Long id
    );

    List<Product> findByParent_IdAndActiveTrueOrderByIdAsc(
            Long parentId
    );

    long countByParent_Id(
            Long parentId
    );

    boolean existsByParent_IdAndVariantKey(
            Long parentId,
            String variantKey
    );

    @Modifying
    @Query("update Product product set product.active = false, product.updatedAt = :now, "
            + "product.version = product.version + 1 where product.parent.id = :parentId and product.active = true")
    void deactivateChildren(
            @Param("parentId") Long parentId,
            @Param("now") Instant now
    );

    Optional<Product> findByIdAndActiveTrue(
            Long id
    );

    Page<Product> findByActiveTrue(
            Pageable pageable
    );
}
