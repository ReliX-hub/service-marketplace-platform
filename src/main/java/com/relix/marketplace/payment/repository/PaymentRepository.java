package com.relix.marketplace.payment.repository;

import com.relix.marketplace.payment.entity.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @EntityGraph(attributePaths = {"engagement"})
    Optional<Payment> findByEngagement_Id(Long engagementId);

    Optional<Payment> findByPaymentIntentId(String paymentIntentId);

    Optional<Payment> findByChargeId(String chargeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"engagement"})
    @Query("select payment from Payment payment where payment.paymentIntentId = :paymentIntentId")
    Optional<Payment> findByPaymentIntentIdForUpdate(@Param("paymentIntentId") String paymentIntentId);
}
