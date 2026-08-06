package com.relix.marketplace.common.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

public class ResourceNotFoundException extends DomainException {

    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message, "NOT_FOUND");
    }

    public ResourceNotFoundException(String resourceType, Long id) {
        super(
                HttpStatus.NOT_FOUND,
                String.format("%s not found with id: %d", resourceType, id),
                "NOT_FOUND",
                Map.of("resourceType", resourceType, "id", id));
    }

    public ResourceNotFoundException(String message, String code, Object details) {
        super(HttpStatus.NOT_FOUND, message, code, details);
    }
}
