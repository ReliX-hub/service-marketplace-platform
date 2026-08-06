package com.relix.marketplace.worker.service;

import com.relix.marketplace.common.exception.BusinessException;
import com.relix.marketplace.common.exception.ConflictException;
import com.relix.marketplace.common.exception.ForbiddenException;
import com.relix.marketplace.common.exception.ResourceNotFoundException;
import com.relix.marketplace.common.dto.PageResponse;
import com.relix.marketplace.storage.dto.FileReferenceResponse;
import com.relix.marketplace.storage.entity.FileOwnerType;
import com.relix.marketplace.storage.entity.FileVisibility;
import com.relix.marketplace.storage.entity.StoredFile;
import com.relix.marketplace.storage.service.FileUrlService;
import com.relix.marketplace.storage.service.ManagedImageIngestService;
import com.relix.marketplace.storage.service.StagedFile;
import com.relix.marketplace.storage.service.StoredFileOwnershipService;
import com.relix.marketplace.user.entity.User;
import com.relix.marketplace.worker.dto.CredentialResponse;
import com.relix.marketplace.worker.dto.CredentialSubmitRequest;
import com.relix.marketplace.worker.entity.Credential;
import com.relix.marketplace.worker.entity.WorkerProfile;
import com.relix.marketplace.worker.repository.CredentialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CredentialService {

    private final CredentialRepository credentialRepository;
    private final ManagedImageIngestService managedImageIngestService;
    private final StoredFileOwnershipService storedFileOwnershipService;
    private final FileUrlService fileUrlService;

    public PageResponse<CredentialResponse> getWorkerCredentials(
            Long workerId,
            Pageable pageable) {
        return PageResponse.of(
                credentialRepository.findByWorker_Id(workerId, pageable),
                this::toResponse);
    }

    public PageResponse<CredentialResponse> getByStatus(
            Credential.Status status,
            Pageable pageable) {
        return PageResponse.of(
                credentialRepository.findByStatus(status, pageable),
                this::toResponse);
    }

    @Transactional
    public CredentialResponse submit(WorkerProfile worker, CredentialSubmitRequest request) {
        validateDates(request.getIssuedAt(), request.getExpiresAt());

        Credential credential = credentialRepository
                .findByWorker_IdAndType(worker.getId(), request.getType())
                .map(existing -> prepareResubmission(existing, today()))
                .orElseGet(() -> Credential.builder()
                        .worker(worker)
                        .type(request.getType())
                        .status(Credential.Status.PENDING)
                        .build());

        credential.setCredentialNumber(trimToNull(request.getCredentialNumber()));
        credential.setIssuedAt(request.getIssuedAt());
        credential.setExpiresAt(request.getExpiresAt());
        credential.setStatus(Credential.Status.PENDING);
        credential.setRejectionReason(null);
        credential.setReviewedBy(null);
        credential.setReviewedAt(null);

        return toResponse(credentialRepository.save(credential));
    }

    @Transactional
    public CredentialResponse uploadDocument(
            Long credentialId,
            WorkerProfile currentWorker,
            MultipartFile upload) {
        Credential credential = credentialRepository.findByIdForUpdate(credentialId)
                .orElseThrow(() -> new ResourceNotFoundException("Credential", credentialId));
        assertDocumentOwner(credential, currentWorker);
        assertDocumentMutable(credential);

        StagedFile staged = managedImageIngestService.stageLarge(
                upload,
                FileOwnerType.CREDENTIAL_DOCUMENT,
                credential.getWorker().getUser().getId());
        StoredFile replacement = storedFileOwnershipService.attach(
                        List.of(staged.id()),
                        FileOwnerType.CREDENTIAL_DOCUMENT,
                        credential.getId(),
                        FileVisibility.PRIVATE)
                .get(staged.id());

        StoredFile previous = credential.getDocumentFile();
        credential.setDocumentFile(replacement);
        credential.setDocumentUrl(null);
        if (previous != null) {
            storedFileOwnershipService.detach(List.of(previous));
        }
        return toResponse(credentialRepository.save(credential));
    }

    @Transactional
    public CredentialResponse verify(Long credentialId, User reviewer) {
        Credential credential = getRequired(credentialId);
        assertPending(credential);
        if (credential.getExpiresAt() != null && credential.getExpiresAt().isBefore(today())) {
            throw new BusinessException(
                    "An expired credential cannot be verified",
                    "CREDENTIAL_EXPIRED",
                    Map.of("credentialId", credentialId));
        }
        if (credential.getDocumentFile() == null && trimToNull(credential.getDocumentUrl()) == null) {
            throw new BusinessException(
                    "A credential document is required before verification",
                    "CREDENTIAL_DOCUMENT_REQUIRED",
                    Map.of("credentialId", credentialId));
        }

        credential.setStatus(Credential.Status.VERIFIED);
        credential.setRejectionReason(null);
        credential.setReviewedBy(reviewer);
        credential.setReviewedAt(Instant.now());
        return toResponse(credentialRepository.save(credential));
    }

    @Transactional
    public CredentialResponse reject(Long credentialId, String reason, User reviewer) {
        Credential credential = getRequired(credentialId);
        assertPending(credential);
        String normalizedReason = trimToNull(reason);
        if (normalizedReason == null) {
            throw new BusinessException("Rejection reason is required", "REJECTION_REASON_REQUIRED");
        }

        credential.setStatus(Credential.Status.REJECTED);
        credential.setRejectionReason(normalizedReason);
        credential.setReviewedBy(reviewer);
        credential.setReviewedAt(Instant.now());
        return toResponse(credentialRepository.save(credential));
    }

    private Credential prepareResubmission(Credential credential, LocalDate today) {
        boolean dateExpired = credential.getExpiresAt() != null
                && credential.getExpiresAt().isBefore(today);
        boolean canResubmit = credential.getStatus() == Credential.Status.REJECTED
                || credential.getStatus() == Credential.Status.EXPIRED
                || (credential.getStatus() == Credential.Status.VERIFIED && dateExpired);

        if (!canResubmit) {
            throw new ConflictException(
                    "A current credential record already exists for " + credential.getType(),
                    "CREDENTIAL_ALREADY_ACTIVE",
                    Map.of(
                            "credentialId", credential.getId(),
                            "type", credential.getType().name(),
                            "status", credential.getStatus().name()));
        }
        return credential;
    }

    private void assertPending(Credential credential) {
        if (credential.getStatus() != Credential.Status.PENDING) {
            throw new ConflictException(
                    "Only a pending credential can be reviewed",
                    "CREDENTIAL_NOT_PENDING",
                    Map.of(
                            "credentialId", credential.getId(),
                            "status", credential.getStatus().name()));
        }
    }

    private void assertDocumentOwner(Credential credential, WorkerProfile currentWorker) {
        if (currentWorker == null
                || !Objects.equals(credential.getWorker().getId(), currentWorker.getId())) {
            throw new ForbiddenException(
                    "Only the credential owner can upload its document",
                    "CREDENTIAL_DOCUMENT_ACCESS_DENIED",
                    Map.of("credentialId", credential.getId()));
        }
    }

    private void assertDocumentMutable(Credential credential) {
        if (credential.getStatus() != Credential.Status.PENDING) {
            throw new ConflictException(
                    "A credential document can only be changed while review is pending",
                    "CREDENTIAL_DOCUMENT_IMMUTABLE",
                    Map.of(
                            "credentialId", credential.getId(),
                            "status", credential.getStatus().name()));
        }
    }

    private Credential getRequired(Long credentialId) {
        return credentialRepository.findDetailedById(credentialId)
                .orElseThrow(() -> new ResourceNotFoundException("Credential", credentialId));
    }

    private void validateDates(LocalDate issuedAt, LocalDate expiresAt) {
        if (issuedAt != null && expiresAt != null && expiresAt.isBefore(issuedAt)) {
            throw new BusinessException(
                    "Credential expiry date cannot be before its issue date",
                    "INVALID_CREDENTIAL_DATES",
                    Map.of("issuedAt", issuedAt, "expiresAt", expiresAt));
        }
    }

    private CredentialResponse toResponse(Credential credential) {
        return CredentialResponse.builder()
                .id(credential.getId())
                .workerId(credential.getWorker().getId())
                .workerUserId(credential.getWorker().getUser().getId())
                .workerDisplayName(credential.getWorker().getDisplayName())
                .type(credential.getType())
                .status(credential.getStatus())
                .credentialNumber(credential.getCredentialNumber())
                .document(toDocumentResponse(credential))
                .issuedAt(credential.getIssuedAt())
                .expiresAt(credential.getExpiresAt())
                .rejectionReason(credential.getRejectionReason())
                .reviewedByUserId(credential.getReviewedBy() == null
                        ? null : credential.getReviewedBy().getId())
                .reviewedAt(credential.getReviewedAt())
                .createdAt(credential.getCreatedAt())
                .updatedAt(credential.getUpdatedAt())
                .build();
    }

    private FileReferenceResponse toDocumentResponse(Credential credential) {
        StoredFile managedDocument = credential.getDocumentFile();
        if (managedDocument != null) {
            return FileReferenceResponse.builder()
                    .url(fileUrlService.toExternalUrl(managedDocument))
                    .managed(true)
                    .contentType(managedDocument.getContentType())
                    .byteSize(managedDocument.getByteSize())
                    .width(managedDocument.getWidth())
                    .height(managedDocument.getHeight())
                    .build();
        }

        String legacyUrl = trimToNull(credential.getDocumentUrl());
        if (legacyUrl == null) {
            return null;
        }
        return FileReferenceResponse.builder()
                .url(legacyUrl)
                .managed(false)
                .build();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }
}
