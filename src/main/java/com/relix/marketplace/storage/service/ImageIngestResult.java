package com.relix.marketplace.storage.service;

import java.util.Objects;

public record ImageIngestResult(
        ImageFormat sourceFormat,
        EncodedImage thumb,
        EncodedImage large) {

    public ImageIngestResult {
        Objects.requireNonNull(sourceFormat, "sourceFormat");
        Objects.requireNonNull(thumb, "thumb");
        Objects.requireNonNull(large, "large");
    }
}
