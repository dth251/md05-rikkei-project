package com.app.payment.repository;

import com.app.payment.entity.VnpayPayment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VnpayPaymentRepository extends JpaRepository<VnpayPayment, Long> {

    Optional<VnpayPayment> findByReference(String reference);

    @Query("select attempt.paymentId from VnpayPayment attempt where attempt.reference = :reference")
    Optional<Long> findPaymentIdByReference(@Param("reference") String reference);
}
