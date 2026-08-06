package com.relix.marketplace.storage.service;

import com.relix.marketplace.storage.config.StorageProperties;
import com.relix.marketplace.storage.entity.StoredFile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FileUrlService {

    private static final String FILE_PATH_PREFIX = "/api/files/";

    private final StorageProperties properties;

    /**
     * Returns the environment-independent path that may be persisted in domain tables.
     */
    public String pathForKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("storageKey is required");
        }
        return FILE_PATH_PREFIX + storageKey;
    }

    /**
     * Returns a client-facing URL without ever persisting the configured deployment host.
     */
    public String toExternalUrl(StoredFile file) {
        if (file == null) {
            return null;
        }
        return toExternalUrl(pathForKey(file.getStorageKey()));
    }

    /**
     * Expands managed relative paths and leaves retained external legacy URLs unchanged.
     */
    public String toExternalUrl(String storedPathOrExternalUrl) {
        if (storedPathOrExternalUrl == null || storedPathOrExternalUrl.isBlank()) {
            return storedPathOrExternalUrl;
        }
        if (!storedPathOrExternalUrl.startsWith(FILE_PATH_PREFIX)) {
            return storedPathOrExternalUrl;
        }

        String baseUrl = normalizedBaseUrl();
        return baseUrl.isEmpty() ? storedPathOrExternalUrl : baseUrl + storedPathOrExternalUrl;
    }

    private String normalizedBaseUrl() {
        String configured = properties.getPublicBaseUrl();
        if (configured == null) {
            return "";
        }
        String normalized = configured.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
