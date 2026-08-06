package com.relix.marketplace.storage.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Locale;

@Getter
@RequiredArgsConstructor
public enum ImageFormat {
    JPEG("image/jpeg", "jpeg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp");

    private final String contentType;
    private final String imageIoName;

    boolean matchesReaderName(String readerName) {
        String normalized = readerName.toLowerCase(Locale.ROOT);
        return switch (this) {
            case JPEG -> normalized.equals("jpeg") || normalized.equals("jpg");
            case PNG -> normalized.equals("png");
            case WEBP -> normalized.equals("webp");
        };
    }
}
