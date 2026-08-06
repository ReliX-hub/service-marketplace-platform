package com.relix.marketplace.storage.service;

import com.relix.marketplace.common.exception.ForbiddenException;
import com.relix.marketplace.common.exception.ResourceNotFoundException;
import com.relix.marketplace.common.exception.UnauthorizedException;
import com.relix.marketplace.engagement.entity.Engagement;
import com.relix.marketplace.engagement.repository.EngagementRepository;
import com.relix.marketplace.storage.entity.FileVisibility;
import com.relix.marketplace.storage.entity.StoredFile;
import com.relix.marketplace.storage.repository.StoredFileRepository;
import com.relix.marketplace.ticket.repository.TicketRepository;
import com.relix.marketplace.worker.entity.Credential;
import com.relix.marketplace.worker.repository.CredentialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FileAccessService {

    private final StoredFileRepository storedFileRepository;
    private final TicketRepository ticketRepository;
    private final EngagementRepository engagementRepository;
    private final CredentialRepository credentialRepository;

    public StoredFile requireReadable(String storageKey, Authentication authentication) {
        StoredFile file = storedFileRepository.findDetailedByStorageKey(storageKey)
                .orElseThrow(FileAccessService::fileNotFound);

        // Reservations and failed/staged ingests must never become addressable.
        if (file.getOwnerId() == null) {
            throw fileNotFound();
        }
        if (file.getVisibility() == FileVisibility.PUBLIC) {
            return file;
        }

        if (!isAuthenticated(authentication)) {
            throw new UnauthorizedException("Authentication is required to access this file");
        }
        if (hasAuthority(authentication, "ROLE_ADMIN")) {
            return file;
        }

        Long userId = principalUserId(authentication);
        if (!isOwnerOrParticipant(file, userId)) {
            throw new ForbiddenException(
                    "You do not have access to this private file",
                    "FILE_ACCESS_DENIED");
        }
        return file;
    }

    private boolean isOwnerOrParticipant(StoredFile file, Long userId) {
        return switch (file.getOwnerType()) {
            case TICKET_IMAGE -> ticketRepository.findById(file.getOwnerId())
                    .map(ticket -> Objects.equals(ticket.getAuthor().getId(), userId))
                    .orElse(false);
            case ENGAGEMENT_DELIVERABLE -> engagementRepository.findDetailedById(file.getOwnerId())
                    .map(engagement -> isEngagementParticipant(engagement, userId))
                    .orElse(false);
            case CREDENTIAL_DOCUMENT -> credentialRepository.findById(file.getOwnerId())
                    .map(Credential::getWorker)
                    .map(worker -> Objects.equals(worker.getUser().getId(), userId))
                    .orElse(false);
            case USER_AVATAR -> Objects.equals(file.getOwnerId(), userId);
        };
    }

    private boolean isEngagementParticipant(Engagement engagement, Long userId) {
        return Objects.equals(engagement.getClient().getId(), userId)
                || Objects.equals(engagement.getWorker().getUser().getId(), userId);
    }

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.getPrincipal() != null
                && !"anonymousUser".equals(authentication.getPrincipal());
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication.getAuthorities().stream()
                .anyMatch(granted -> authority.equals(granted.getAuthority()));
    }

    private Long principalUserId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof Long userId) {
            return userId;
        }
        if (principal instanceof String value) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException ignored) {
                // Fall through to the stable access-denied response below.
            }
        }
        throw new ForbiddenException(
                "The authenticated principal cannot access managed files",
                "FILE_ACCESS_DENIED");
    }

    private static ResourceNotFoundException fileNotFound() {
        return new ResourceNotFoundException(
                "File not found",
                "FILE_NOT_FOUND",
                Map.of());
    }
}
