package com.relix.marketplace.worker.service;

import com.relix.marketplace.common.exception.BusinessException;
import com.relix.marketplace.common.exception.ConflictException;
import com.relix.marketplace.common.dto.PageResponse;
import com.relix.marketplace.common.exception.ForbiddenException;
import com.relix.marketplace.storage.entity.FileOwnerType;
import com.relix.marketplace.storage.entity.FileVariant;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CredentialServiceTest {

    @Mock
    private CredentialRepository credentialRepository;

    @Mock
    private ManagedImageIngestService managedImageIngestService;

    @Mock
    private StoredFileOwnershipService storedFileOwnershipService;

    @Mock
    private FileUrlService fileUrlService;

    @InjectMocks
    private CredentialService credentialService;

    @Test
    @DisplayName("worker credential query returns pagination metadata")
    void getWorkerCredentials_returnsPageResponse() {
        WorkerProfile worker = worker(10L, 1L);
        Credential credential = credential(100L, worker, Credential.Status.PENDING);
        Pageable pageable = PageRequest.of(0, 10);
        when(credentialRepository.findByWorker_Id(10L, pageable)).thenReturn(
                new PageImpl<>(List.of(credential), pageable, 21));

        PageResponse<CredentialResponse> response =
                credentialService.getWorkerCredentials(10L, pageable);

        assertEquals(1, response.getItems().size());
        assertEquals(21, response.getTotalElements());
        assertEquals(3, response.getTotalPages());
        assertTrue(response.isHasNext());
    }

    @Test
    @DisplayName("admin credential query returns pagination metadata")
    void getByStatus_returnsPageResponse() {
        WorkerProfile worker = worker(10L, 1L);
        Credential credential = credential(100L, worker, Credential.Status.PENDING);
        Pageable pageable = PageRequest.of(0, 20);
        when(credentialRepository.findByStatus(Credential.Status.PENDING, pageable)).thenReturn(
                new PageImpl<>(List.of(credential), pageable, 1));

        PageResponse<CredentialResponse> response =
                credentialService.getByStatus(Credential.Status.PENDING, pageable);

        assertEquals(1, response.getItems().size());
        assertEquals(1, response.getTotalElements());
        assertEquals(1, response.getTotalPages());
    }

    @Test
    @DisplayName("submit creates a pending current credential record")
    void submit_newCredential_createsPendingRecord() {
        WorkerProfile worker = worker(10L, 1L);
        CredentialSubmitRequest request = request(Credential.Type.DRIVER_LICENSE);
        when(credentialRepository.findByWorker_IdAndType(10L, Credential.Type.DRIVER_LICENSE))
                .thenReturn(Optional.empty());
        when(credentialRepository.save(any(Credential.class))).thenAnswer(invocation -> {
            Credential saved = invocation.getArgument(0);
            saved.setId(100L);
            return saved;
        });

        CredentialResponse response = credentialService.submit(worker, request);

        assertEquals(100L, response.getId());
        assertEquals(Credential.Status.PENDING, response.getStatus());
        assertEquals(10L, response.getWorkerId());
        assertNull(response.getDocument());
    }

    @Test
    @DisplayName("rejected credential is resubmitted by updating the same row")
    void submit_rejectedCredential_reusesCurrentRecord() {
        WorkerProfile worker = worker(10L, 1L);
        User reviewer = user(99L, "admin@example.com");
        Credential existing = credential(100L, worker, Credential.Status.REJECTED);
        existing.setRejectionReason("Old reason");
        existing.setReviewedBy(reviewer);
        when(credentialRepository.findByWorker_IdAndType(10L, Credential.Type.DRIVER_LICENSE))
                .thenReturn(Optional.of(existing));
        when(credentialRepository.save(existing)).thenReturn(existing);

        CredentialResponse response = credentialService.submit(
                worker,
                request(Credential.Type.DRIVER_LICENSE));

        assertEquals(100L, response.getId());
        assertEquals(Credential.Status.PENDING, response.getStatus());
        assertNull(existing.getRejectionReason());
        assertNull(existing.getReviewedBy());
        assertNull(existing.getReviewedAt());
        verify(credentialRepository).save(existing);
    }

    @Test
    @DisplayName("date-expired verified credential can be resubmitted")
    void submit_dateExpiredVerifiedCredential_allowed() {
        WorkerProfile worker = worker(10L, 1L);
        Credential existing = credential(100L, worker, Credential.Status.VERIFIED);
        existing.setExpiresAt(LocalDate.now().minusDays(1));
        when(credentialRepository.findByWorker_IdAndType(10L, Credential.Type.DRIVER_LICENSE))
                .thenReturn(Optional.of(existing));
        when(credentialRepository.save(existing)).thenReturn(existing);

        CredentialResponse response = credentialService.submit(
                worker,
                request(Credential.Type.DRIVER_LICENSE));

        assertEquals(Credential.Status.PENDING, response.getStatus());
    }

    @Test
    @DisplayName("pending credential cannot be submitted again")
    void submit_pendingCredential_rejected() {
        WorkerProfile worker = worker(10L, 1L);
        Credential existing = credential(100L, worker, Credential.Status.PENDING);
        when(credentialRepository.findByWorker_IdAndType(10L, Credential.Type.DRIVER_LICENSE))
                .thenReturn(Optional.of(existing));

        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> credentialService.submit(worker, request(Credential.Type.DRIVER_LICENSE)));

        assertEquals("CREDENTIAL_ALREADY_ACTIVE", exception.getCode());
        verify(credentialRepository, never()).save(any());
    }

    @Test
    @DisplayName("verify records reviewer and review time")
    void verify_pendingCredential_marksVerified() {
        WorkerProfile worker = worker(10L, 1L);
        User reviewer = user(99L, "admin@example.com");
        Credential credential = credential(100L, worker, Credential.Status.PENDING);
        credential.setExpiresAt(LocalDate.now().plusYears(1));
        when(credentialRepository.findDetailedById(100L)).thenReturn(Optional.of(credential));
        when(credentialRepository.save(credential)).thenReturn(credential);

        CredentialResponse response = credentialService.verify(100L, reviewer);

        assertEquals(Credential.Status.VERIFIED, response.getStatus());
        assertEquals(99L, response.getReviewedByUserId());
        assertSame(reviewer, credential.getReviewedBy());
        assertTrue(credential.getReviewedAt() != null);
        assertEquals("https://documents.example.com/old.pdf", response.getDocument().url());
        assertFalse(response.getDocument().managed());
    }

    @Test
    @DisplayName("verify rejects a pending credential without a document")
    void verify_withoutDocument_rejected() {
        WorkerProfile worker = worker(10L, 1L);
        User reviewer = user(99L, "admin@example.com");
        Credential credential = credential(100L, worker, Credential.Status.PENDING);
        credential.setDocumentUrl(null);
        when(credentialRepository.findDetailedById(100L)).thenReturn(Optional.of(credential));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> credentialService.verify(100L, reviewer));

        assertEquals("CREDENTIAL_DOCUMENT_REQUIRED", exception.getCode());
        verify(credentialRepository, never()).save(any());
    }

    @Test
    @DisplayName("owner can replace a pending managed credential document")
    void uploadDocument_pendingOwner_replacesManagedDocument() {
        WorkerProfile worker = worker(10L, 1L);
        Credential credential = credential(100L, worker, Credential.Status.PENDING);
        StoredFile previous = storedFile(700L, "old.jpg");
        StoredFile replacement = storedFile(701L, "new.jpg");
        credential.setDocumentFile(previous);
        MockMultipartFile upload = new MockMultipartFile(
                "file", "license.jpg", "image/jpeg", new byte[]{1, 2, 3});

        when(credentialRepository.findByIdForUpdate(100L))
                .thenReturn(Optional.of(credential));
        when(managedImageIngestService.stageLarge(
                upload,
                FileOwnerType.CREDENTIAL_DOCUMENT,
                1L)).thenReturn(new StagedFile(701L));
        when(storedFileOwnershipService.attach(
                List.of(701L),
                FileOwnerType.CREDENTIAL_DOCUMENT,
                100L,
                FileVisibility.PRIVATE)).thenReturn(Map.of(701L, replacement));
        when(credentialRepository.save(credential)).thenReturn(credential);
        when(fileUrlService.toExternalUrl(replacement)).thenReturn("/api/files/new.jpg");

        CredentialResponse response = credentialService.uploadDocument(
                100L, worker, upload);

        assertSame(replacement, credential.getDocumentFile());
        assertNull(credential.getDocumentUrl());
        assertEquals("/api/files/new.jpg", response.getDocument().url());
        assertTrue(response.getDocument().managed());
        verify(storedFileOwnershipService).detach(List.of(previous));
    }

    @Test
    @DisplayName("non-owner cannot upload a credential document")
    void uploadDocument_nonOwner_rejectedBeforeIngest() {
        WorkerProfile owner = worker(10L, 1L);
        WorkerProfile other = worker(11L, 2L);
        Credential credential = credential(100L, owner, Credential.Status.PENDING);
        when(credentialRepository.findByIdForUpdate(100L))
                .thenReturn(Optional.of(credential));

        ForbiddenException exception = assertThrows(
                ForbiddenException.class,
                () -> credentialService.uploadDocument(
                        100L,
                        other,
                        new MockMultipartFile("file", new byte[]{1})));

        assertEquals("CREDENTIAL_DOCUMENT_ACCESS_DENIED", exception.getCode());
        verify(managedImageIngestService, never()).stageLarge(any(), any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = Credential.Status.class, names = {"VERIFIED", "REJECTED", "EXPIRED"})
    @DisplayName("non-pending credential document is immutable")
    void uploadDocument_nonPendingCredential_rejected(Credential.Status status) {
        WorkerProfile worker = worker(10L, 1L);
        Credential credential = credential(100L, worker, status);
        when(credentialRepository.findByIdForUpdate(100L))
                .thenReturn(Optional.of(credential));

        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> credentialService.uploadDocument(
                        100L,
                        worker,
                        new MockMultipartFile("file", new byte[]{1})));

        assertEquals("CREDENTIAL_DOCUMENT_IMMUTABLE", exception.getCode());
        verify(managedImageIngestService, never()).stageLarge(any(), any(), any());
    }

    @Test
    @DisplayName("reject requires a pending credential and stores normalized reason")
    void reject_pendingCredential_storesReason() {
        WorkerProfile worker = worker(10L, 1L);
        User reviewer = user(99L, "admin@example.com");
        Credential credential = credential(100L, worker, Credential.Status.PENDING);
        when(credentialRepository.findDetailedById(100L)).thenReturn(Optional.of(credential));
        when(credentialRepository.save(credential)).thenReturn(credential);

        CredentialResponse response = credentialService.reject(
                100L,
                "  Document is incomplete.  ",
                reviewer);

        assertEquals(Credential.Status.REJECTED, response.getStatus());
        assertEquals("Document is incomplete.", response.getRejectionReason());
    }

    @Test
    @DisplayName("submit rejects expiry before issue date")
    void submit_invalidDateRange_rejected() {
        WorkerProfile worker = worker(10L, 1L);
        CredentialSubmitRequest request = request(Credential.Type.DRIVER_LICENSE);
        request.setIssuedAt(LocalDate.now());
        request.setExpiresAt(LocalDate.now().minusDays(1));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> credentialService.submit(worker, request));

        assertEquals("INVALID_CREDENTIAL_DATES", exception.getCode());
        verify(credentialRepository, never()).findByWorker_IdAndType(any(), any());
    }

    private CredentialSubmitRequest request(Credential.Type type) {
        CredentialSubmitRequest request = new CredentialSubmitRequest();
        request.setType(type);
        request.setCredentialNumber("LICENSE-123");
        request.setIssuedAt(LocalDate.now().minusYears(1));
        request.setExpiresAt(LocalDate.now().plusYears(1));
        return request;
    }

    private StoredFile storedFile(Long id, String storageKey) {
        StoredFile file = StoredFile.builder()
                .storageKey(storageKey)
                .variant(FileVariant.LARGE)
                .visibility(FileVisibility.PRIVATE)
                .ownerType(FileOwnerType.CREDENTIAL_DOCUMENT)
                .ownerId(100L)
                .uploader(user(1L, "worker@example.com"))
                .contentType("image/jpeg")
                .byteSize(1_024L)
                .width(800)
                .height(600)
                .build();
        file.setId(id);
        return file;
    }

    private Credential credential(
            Long id,
            WorkerProfile worker,
            Credential.Status status) {
        Credential credential = Credential.builder()
                .worker(worker)
                .type(Credential.Type.DRIVER_LICENSE)
                .status(status)
                .credentialNumber("OLD-123")
                .documentUrl("https://documents.example.com/old.pdf")
                .build();
        credential.setId(id);
        return credential;
    }

    private WorkerProfile worker(Long workerId, Long userId) {
        WorkerProfile worker = WorkerProfile.builder()
                .user(user(userId, "worker@example.com"))
                .displayName("Demo Worker")
                .build();
        worker.setId(workerId);
        return worker;
    }

    private User user(Long id, String email) {
        User user = User.builder()
                .email(email)
                .passwordHash("hash")
                .name("Demo User")
                .role(User.UserRole.USER)
                .status(User.UserStatus.ACTIVE)
                .build();
        user.setId(id);
        return user;
    }
}
