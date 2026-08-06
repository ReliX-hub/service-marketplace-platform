package com.relix.marketplace.ticket.entity;

import com.relix.marketplace.catalog.entity.Category;
import com.relix.marketplace.common.entity.BaseEntity;
import com.relix.marketplace.common.exception.BusinessException;
import com.relix.marketplace.user.entity.User;
import com.relix.marketplace.worker.entity.WorkerProfile;
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
@Table(name = "tickets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Ticket extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketKind kind;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id")
    private WorkerProfile worker;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_mode", nullable = false, length = 20)
    private PricingMode pricingMode;

    @Column(precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "budget_min", precision = 12, scale = 2)
    private BigDecimal budgetMin;

    @Column(name = "budget_max", precision = 12, scale = 2)
    private BigDecimal budgetMax;

    @Builder.Default
    @Column(nullable = false, length = 3)
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(name = "location_mode", nullable = false, length = 20)
    private LocationMode locationMode;

    @Column(length = 500)
    private String address;

    @Column(length = 100)
    private String city;

    @Column(precision = 10, scale = 8)
    private BigDecimal latitude;

    @Column(precision = 11, scale = 8)
    private BigDecimal longitude;

    @Column(name = "estimated_duration_minutes")
    private Integer estimatedDurationMinutes;

    @Column(name = "service_window_start")
    private Instant serviceWindowStart;

    @Column(name = "service_window_end")
    private Instant serviceWindowEnd;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketStatus status = TicketStatus.DRAFT;

    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    @Column(name = "cover_image_large_url", length = 500)
    private String coverImageLargeUrl;

    @Builder.Default
    @Column(name = "image_count", nullable = false)
    private Short imageCount = 0;

    @Builder.Default
    @Column(name = "view_count", nullable = false)
    private Long viewCount = 0L;

    @Builder.Default
    @Column(name = "application_count", nullable = false)
    private Integer applicationCount = 0;

    @Column(name = "expires_at")
    private Instant expiresAt;

    public boolean isEffectivelyExpiredAt(Instant instant) {
        return status == TicketStatus.EXPIRED
                || (status == TicketStatus.OPEN
                && ((expiresAt != null && !expiresAt.isAfter(instant))
                || (serviceWindowEnd != null && !serviceWindowEnd.isAfter(instant))));
    }

    public void markMatched() {
        if (status != TicketStatus.OPEN) {
            throw new BusinessException(
                    "Only an open ticket can be matched",
                    "TICKET_NOT_OPEN");
        }
        status = TicketStatus.MATCHED;
    }
}
