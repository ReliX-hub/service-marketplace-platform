package com.relix.marketplace.user.entity;

import com.relix.marketplace.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UserRole role = UserRole.USER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "client_rating", precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal clientRating = BigDecimal.ZERO;

    @Column(name = "client_review_count")
    @Builder.Default
    private Integer clientReviewCount = 0;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private Set<UserCapability> capabilities = new HashSet<>();

    public void grantCapability(UserCapability.Type capability) {
        if (capabilities == null) {
            capabilities = new HashSet<>();
        }
        boolean alreadyGranted = capabilities.stream()
                .anyMatch(existing -> existing.getCapability() == capability);
        if (!alreadyGranted) {
            capabilities.add(UserCapability.builder()
                    .user(this)
                    .capability(capability)
                    .build());
        }
    }

    public Set<UserCapability.Type> getCapabilityTypes() {
        if (capabilities == null) {
            return Set.of();
        }
        return capabilities.stream()
                .map(UserCapability::getCapability)
                .collect(Collectors.toUnmodifiableSet());
    }

    public enum UserRole {
        USER, ADMIN
    }

    public enum UserStatus {
        ACTIVE, INACTIVE, SUSPENDED
    }
}
