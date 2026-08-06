package com.relix.marketplace.auth.service;

import com.relix.marketplace.common.exception.ForbiddenException;
import com.relix.marketplace.common.exception.ResourceNotFoundException;
import com.relix.marketplace.user.entity.User;
import com.relix.marketplace.user.entity.UserCapability;
import com.relix.marketplace.user.repository.UserRepository;
import com.relix.marketplace.worker.entity.WorkerProfile;
import com.relix.marketplace.worker.repository.WorkerProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.annotation.RequestScope;

import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequestScope
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserRepository userRepository;
    private final WorkerProfileRepository workerProfileRepository;

    private User cachedUser;
    private WorkerProfile cachedWorkerProfile;
    private Set<String> cachedAuthorities;

    public Long getCurrentUserId() {
        Authentication authentication = currentAuthentication();
        Object principal = authentication.getPrincipal();

        if (principal instanceof Long userId) {
            return userId;
        }

        if (principal instanceof String value) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException ignored) {
                if (value.contains("@")) {
                    return userRepository.findByEmail(value)
                            .map(User::getId)
                            .orElseThrow(() -> new ForbiddenException("User not found for principal"));
                }
                throw new ForbiddenException("Invalid principal format");
            }
        }

        throw new ForbiddenException("Unsupported principal type: " + principal.getClass().getSimpleName());
    }

    public User getCurrentUser() {
        if (cachedUser == null) {
            Long userId = getCurrentUserId();
            cachedUser = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User", userId));

            if (cachedUser.getStatus() != User.UserStatus.ACTIVE) {
                throw new ForbiddenException("Account is not active", "ACCOUNT_INACTIVE");
            }
        }
        return cachedUser;
    }

    public boolean isAdmin() {
        getCurrentUser();
        return authorities().contains("ROLE_ADMIN");
    }

    public boolean hasCapability(UserCapability.Type capability) {
        if (capability == null) {
            return false;
        }
        getCurrentUser();
        return authorities().contains("CAP_" + capability.name());
    }

    @Transactional
    public WorkerProfile requireWorkerProfile() {
        if (cachedWorkerProfile != null) {
            return cachedWorkerProfile;
        }

        User user = getCurrentUser();
        if (!hasCapability(UserCapability.Type.WORKER)) {
            throw new ForbiddenException("Worker capability is required", "WORKER_CAPABILITY_REQUIRED");
        }

        cachedWorkerProfile = workerProfileRepository.findByUser_Id(user.getId()).orElse(null);
        if (cachedWorkerProfile == null) {
            workerProfileRepository.createDefaultIfMissing(user.getId(), user.getName());
            cachedWorkerProfile = workerProfileRepository.findByUser_Id(user.getId())
                    .orElseThrow(() -> new IllegalStateException("Worker profile could not be initialized"));
        }
        return cachedWorkerProfile;
    }

    private Authentication currentAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null
                || !authentication.isAuthenticated()) {
            throw new ForbiddenException("Not authenticated");
        }
        return authentication;
    }

    private Set<String> authorities() {
        if (cachedAuthorities == null) {
            cachedAuthorities = currentAuthentication().getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toUnmodifiableSet());
        }
        return cachedAuthorities;
    }
}
