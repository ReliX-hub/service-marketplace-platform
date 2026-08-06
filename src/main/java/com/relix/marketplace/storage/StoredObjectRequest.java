package com.relix.marketplace.storage;

import java.util.Objects;

public record StoredObjectRequest(String storageKey, byte[] content) {

    public StoredObjectRequest {
        Objects.requireNonNull(storageKey, "storageKey");
        Objects.requireNonNull(content, "content");
        if (content.length == 0) {
            throw new IllegalArgumentException("Stored object content must not be empty");
        }
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
