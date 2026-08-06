package com.relix.marketplace.worker.service;

import com.relix.marketplace.catalog.entity.Category;
import com.relix.marketplace.catalog.repository.CategoryRepository;
import com.relix.marketplace.common.exception.ForbiddenException;
import com.relix.marketplace.worker.entity.Credential;
import com.relix.marketplace.worker.repository.CredentialRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EligibilityServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CredentialRepository credentialRepository;

    @InjectMocks
    private EligibilityService eligibilityService;

    @Test
    @DisplayName("category without credential requirement allows service")
    void assertCanServe_noRequirement_allowed() {
        Category category = category(3L, null);
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(category));

        assertDoesNotThrow(() -> eligibilityService.assertCanServe(10L, 3L));

        verify(credentialRepository, never())
                .existsCurrentVerifiedCredential(any(), any(), any(), any());
    }

    @Test
    @DisplayName("current verified required credential allows service")
    void assertCanServe_verifiedCurrentCredential_allowed() {
        Category category = category(3L, Credential.Type.ELECTRICAL_LICENSE);
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(category));
        when(credentialRepository.existsCurrentVerifiedCredential(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(Credential.Type.ELECTRICAL_LICENSE),
                org.mockito.ArgumentMatchers.eq(Credential.Status.VERIFIED),
                any(LocalDate.class)))
                .thenReturn(true);

        assertDoesNotThrow(() -> eligibilityService.assertCanServe(10L, 3L));
    }

    @Test
    @DisplayName("missing or expired required credential returns stable error details")
    void assertCanServe_missingOrExpiredCredential_rejected() {
        Category category = category(3L, Credential.Type.DRIVER_LICENSE);
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(category));
        when(credentialRepository.existsCurrentVerifiedCredential(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(Credential.Type.DRIVER_LICENSE),
                org.mockito.ArgumentMatchers.eq(Credential.Status.VERIFIED),
                any(LocalDate.class)))
                .thenReturn(false);

        ForbiddenException exception = assertThrows(
                ForbiddenException.class,
                () -> eligibilityService.assertCanServe(10L, 3L));

        assertEquals("CREDENTIAL_REQUIRED", exception.getCode());
        assertEquals(Map.of("required", "DRIVER_LICENSE"), exception.getDetails());
    }

    private Category category(Long id, Credential.Type requiredCredential) {
        Category category = Category.builder()
                .code("CATEGORY")
                .name("Category")
                .requiredCredential(requiredCredential)
                .active(true)
                .build();
        category.setId(id);
        return category;
    }
}
