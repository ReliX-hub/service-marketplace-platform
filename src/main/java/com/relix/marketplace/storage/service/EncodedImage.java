package com.relix.marketplace.storage.service;

import java.util.Objects;

public record EncodedImage(
        byte[] bytes,
        String contentType,
        int width,
        int height) {

    public EncodedImage {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(contentType, "contentType");
        if (bytes.length == 0 || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Encoded image data and dimensions must be positive");
        }
        bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }

    public long byteSize() {
        return bytes.length;
    }
}
