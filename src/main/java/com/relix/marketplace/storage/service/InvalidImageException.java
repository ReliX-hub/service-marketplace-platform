package com.relix.marketplace.storage.service;

import lombok.Getter;

import java.util.Map;

@Getter
public class InvalidImageException extends RuntimeException {

    private final String code;
    private final Map<String, Object> details;

    public InvalidImageException(String message, String code) {
        this(message, code, Map.of(), null);
    }

    public InvalidImageException(String message, String code, Map<String, Object> details) {
        this(message, code, details, null);
    }

    public InvalidImageException(
            String message,
            String code,
            Map<String, Object> details,
            Throwable cause) {
        super(message, cause);
        this.code = code;
        this.details = Map.copyOf(details);
    }
}
