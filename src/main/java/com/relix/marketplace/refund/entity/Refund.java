package com.relix.marketplace.refund.entity;

import com.relix.marketplace.common.entity.BaseEntity;
import com.relix.marketplace.engagement.entity.Engagement;
import com.relix.marketplace.payment.entity.Payment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "refunds")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Refund extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "engagement_id", nullable = false)
    private Engagement engagement;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 500)
    private String reason;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RefundStatus status = RefundStatus.PENDING;

    @Column(name = "provider_refund_id", unique = true, length = 64)
    private String providerRefundId;

    @Column(name = "provider_status", length = 40)
    private String providerStatus;

    @Column(name = "failure_message", length = 500)
    private String failureMessage;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    public enum RefundStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED
    }
}
