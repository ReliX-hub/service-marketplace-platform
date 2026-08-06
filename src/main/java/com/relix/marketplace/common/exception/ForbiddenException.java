package com.relix.marketplace.common.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends DomainException {

    public ForbiddenException(String message) {
        this(message, "FORBIDDEN");
    }

    public ForbiddenException(String message, String code) {
        super(HttpStatus.FORBIDDEN, message, code);
    }

    public ForbiddenException(String message, String code, Object details) {
        super(HttpStatus.FORBIDDEN, message, code, details);
    }

    public ForbiddenException(String message, String code, String field, Object details) {
        super(HttpStatus.FORBIDDEN, message, code, field, details);
    }
}
