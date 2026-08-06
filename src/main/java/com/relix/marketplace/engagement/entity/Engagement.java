package com.relix.marketplace.engagement.entity;

import com.relix.marketplace.application.entity.Application;
import com.relix.marketplace.common.entity.BaseEntity;
import com.relix.marketplace.ticket.entity.Ticket;
import com.relix.marketplace.user.entity.User;
import com.relix.marketplace.worker.entity.WorkerProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "engagements")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Engagement extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private User client;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "worker_id", nullable = false)
    private WorkerProfile worker;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id")
    private Application application;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EngagementStatus status = EngagementStatus.ACCEPTED;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "scheduled_start")
    private Instant scheduledStart;

    @Column(name = "scheduled_end")
    private Instant scheduledEnd;

    @Column(name = "funded_at")
    private Instant fundedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "disputed_at")
    private Instant disputedAt;

    @Column(name = "dispute_reason", length = 500)
    private String disputeReason;

    @Builder.Default
    @Column(name = "deliverable_count", nullable = false)
    private Short deliverableCount = 0;
}
