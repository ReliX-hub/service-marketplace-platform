package com.relix.marketplace.worker.entity;

import com.relix.marketplace.common.entity.BaseEntity;
import com.relix.marketplace.storage.entity.FileVariant;
import com.relix.marketplace.storage.entity.StoredFile;
import com.relix.marketplace.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
        name = "worker_credentials",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_worker_credentials_worker_type",
                columnNames = {"worker_id", "type"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Credential extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "worker_id", nullable = false)
    private WorkerProfile worker;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.PENDING;

    @Column(name = "credential_number", length = 100)
    private String credentialNumber;

    @Column(name = "document_url", length = 500)
    private String documentUrl;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_file_id")
    private StoredFile documentFile;

    @Column(name = "issued_at")
    private LocalDate issuedAt;

    @Column(name = "expires_at")
    private LocalDate expiresAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    public boolean isVerifiedAndCurrent(LocalDate today) {
        return status == Status.VERIFIED
                && (expiresAt == null || !expiresAt.isBefore(today));
    }

    @PrePersist
    @PreUpdate
    private void requireLargeDocumentVariant() {
        if (documentFile != null && documentFile.getVariant() != FileVariant.LARGE) {
            throw new IllegalStateException("Credential documents must reference a LARGE stored-file variant");
        }
    }

    @Schema(description = "Supported worker credential categories")
    public enum Type {
        ELECTRICAL_LICENSE,
        DRIVER_LICENSE,
        BACKGROUND_CHECK
    }

    @Schema(description = "Administrative credential review lifecycle")
    public enum Status {
        PENDING,
        VERIFIED,
        REJECTED,
        EXPIRED
    }
}
