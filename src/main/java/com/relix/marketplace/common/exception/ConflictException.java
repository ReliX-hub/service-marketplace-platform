package com.relix.marketplace.common.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends DomainException {

    public ConflictException(String message, String code) {
        super(HttpStatus.CONFLICT, message, code);
    }

    public ConflictException(String message, String code, Object details) {
        super(HttpStatus.CONFLICT, message, code, details);
    }

    public ConflictException(String message, String code, String field, Object details) {
        super(HttpStatus.CONFLICT, message, code, field, details);
    }
}
