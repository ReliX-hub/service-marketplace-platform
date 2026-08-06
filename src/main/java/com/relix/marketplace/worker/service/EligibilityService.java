package com.relix.marketplace.worker.service;

import com.relix.marketplace.catalog.entity.Category;
import com.relix.marketplace.catalog.repository.CategoryRepository;
import com.relix.marketplace.common.exception.ForbiddenException;
import com.relix.marketplace.common.exception.ResourceNotFoundException;
import com.relix.marketplace.worker.entity.Credential;
import com.relix.marketplace.worker.repository.CredentialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EligibilityService {

    private final CategoryRepository categoryRepository;
    private final CredentialRepository credentialRepository;

    public void assertCanServe(Long userId, Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));
        Credential.Type required = category.getRequiredCredential();
        if (required == null) {
            return;
        }

        boolean eligible = credentialRepository.existsCurrentVerifiedCredential(
                userId,
                required,
                Credential.Status.VERIFIED,
                LocalDate.now(ZoneOffset.UTC));
        if (!eligible) {
            throw new ForbiddenException(
                    "A current verified " + required + " credential is required for this category",
                    "CREDENTIAL_REQUIRED",
                    Map.of("required", required.name()));
        }
    }
}
