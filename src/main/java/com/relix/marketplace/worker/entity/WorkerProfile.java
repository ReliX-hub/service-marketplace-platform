package com.relix.marketplace.worker.entity;

import com.relix.marketplace.common.entity.BaseEntity;
import com.relix.marketplace.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "worker_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkerProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 255)
    private String headline;

    @Column(length = 500)
    private String address;

    @Column(precision = 10, scale = 8)
    private BigDecimal latitude;

    @Column(precision = 11, scale = 8)
    private BigDecimal longitude;

    @Column(precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal rating = BigDecimal.ZERO;

    @Column(name = "review_count")
    @Builder.Default
    private Integer reviewCount = 0;

    @Builder.Default
    private Boolean verified = false;

    @Column(name = "completed_jobs")
    @Builder.Default
    private Integer completedJobs = 0;

    @Column(name = "service_radius_km", precision = 8, scale = 2)
    private BigDecimal serviceRadiusKm;
}
