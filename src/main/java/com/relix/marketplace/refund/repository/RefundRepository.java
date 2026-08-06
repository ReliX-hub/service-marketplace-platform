package com.relix.marketplace.refund.repository;

import com.relix.marketplace.refund.entity.Refund;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface RefundRepository extends JpaRepository<Refund, Long> {

    List<Refund> findByEngagement_Id(Long engagementId);

    @EntityGraph(attributePaths = "engagement")
    @Query("select refund from Refund refund where refund.engagement.id in :engagementIds")
    List<Refund> findAllByEngagement_IdIn(@Param("engagementIds") Set<Long> engagementIds);

    Optional<Refund> findFirstByEngagement_IdOrderByCreatedAtAsc(Long engagementId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"engagement", "payment"})
    @Query("select refund from Refund refund where refund.engagement.id = :engagementId")
    Optional<Refund> findByEngagementIdForUpdate(@Param("engagementId") Long engagementId);

    boolean existsByEngagement_Id(Long engagementId);

    @EntityGraph(attributePaths = {"engagement", "payment"})
    Page<Refund> findByEngagement_Client_Id(Long clientId, Pageable pageable);

    @EntityGraph(attributePaths = {"engagement", "payment"})
    Page<Refund> findAllBy(Pageable pageable);

    List<Refund> findByStatus(Refund.RefundStatus status);

    Optional<Refund> findByProviderRefundId(String providerRefundId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"engagement", "payment"})
    @Query("select refund from Refund refund where refund.providerRefundId = :providerRefundId")
    Optional<Refund> findByProviderRefundIdForUpdate(
            @Param("providerRefundId") String providerRefundId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"engagement", "payment"})
    @Query("select refund from Refund refund where refund.id = :refundId")
    Optional<Refund> findByIdForWebhookUpdate(@Param("refundId") Long refundId);

    boolean existsByPayment_PaymentIntentIdAndProviderRefundIdIsNullAndStatusIn(
            String paymentIntentId,
            Set<Refund.RefundStatus> statuses);

}
