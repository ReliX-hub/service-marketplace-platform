package com.relix.marketplace.payment.repository;

import com.relix.marketplace.payment.entity.SupersededPaymentIntent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SupersededPaymentIntentRepository
        extends JpaRepository<SupersededPaymentIntent, Long> {

    boolean existsByPaymentIntentId(String paymentIntentId);

    boolean existsByPayment_IdAndPaymentIntentId(Long paymentId, String paymentIntentId);

    @Modifying
    @Query(value = """
            INSERT INTO superseded_payment_intents(payment_id, payment_intent_id)
            VALUES (:paymentId, :paymentIntentId)
            ON CONFLICT (payment_intent_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("paymentId") Long paymentId,
            @Param("paymentIntentId") String paymentIntentId);
}
