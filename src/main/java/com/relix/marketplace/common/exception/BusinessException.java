package com.relix.marketplace.common.exception;

import org.springframework.http.HttpStatus;

public class BusinessException extends DomainException {

    public BusinessException(String message, String code) {
        super(HttpStatus.BAD_REQUEST, message, code);
    }

    public BusinessException(String message, String code, Object details) {
        super(HttpStatus.BAD_REQUEST, message, code, details);
    }

    public BusinessException(String message, String code, String field, Object details) {
        super(HttpStatus.BAD_REQUEST, message, code, field, details);
    }
}
