package com.relix.marketplace.application.entity;

import com.relix.marketplace.common.entity.BaseEntity;
import com.relix.marketplace.ticket.entity.Ticket;
import com.relix.marketplace.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "applications",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_applications_ticket_applicant",
                columnNames = {"ticket_id", "applicant_id"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Application extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "applicant_id", nullable = false)
    private User applicant;

    @Column(name = "proposed_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal proposedAmount;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "proposed_start")
    private Instant proposedStart;

    @Column(name = "proposed_end")
    private Instant proposedEnd;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationStatus status = ApplicationStatus.PENDING;

    public boolean isPending() {
        return status == ApplicationStatus.PENDING;
    }
}
