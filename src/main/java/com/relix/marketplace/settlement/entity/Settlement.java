package com.relix.marketplace.settlement.entity;

import com.relix.marketplace.common.entity.BaseEntity;
import com.relix.marketplace.engagement.entity.Engagement;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "settlements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Settlement extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "engagement_id", nullable = false, unique = true)
    @NotNull
    private Engagement engagement;

    @Column(name = "total_price", nullable = false, precision = 12, scale = 2)
    @NotNull
    @PositiveOrZero
    private BigDecimal totalAmount;

    @Column(name = "platform_fee", nullable = false, precision = 12, scale = 2)
    @NotNull
    @PositiveOrZero
    private BigDecimal platformFee;

    @Column(name = "provider_payout", nullable = false, precision = 12, scale = 2)
    @NotNull
    @PositiveOrZero
    private BigDecimal workerPayout;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SettlementStatus status = SettlementStatus.PENDING;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "batch_id", length = 50)
    private String batchId;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    public enum SettlementStatus {
        PENDING, PROCESSING, COMPLETED, FAILED
    }
}
