package com.relix.marketplace.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base exception for expected domain failures that have a stable API contract.
 */
@Getter
public abstract class DomainException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String field;
    private final Object details;

    protected DomainException(HttpStatus status, String message, String code) {
        this(status, message, code, null, null);
    }

    protected DomainException(HttpStatus status, String message, String code, Object details) {
        this(status, message, code, null, details);
    }

    protected DomainException(
            HttpStatus status,
            String message,
            String code,
            String field,
            Object details) {
        super(message);
        this.status = status;
        this.code = code;
        this.field = field;
        this.details = details;
    }
}
