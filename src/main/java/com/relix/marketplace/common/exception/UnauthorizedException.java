package com.relix.marketplace.common.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends DomainException {

    public UnauthorizedException(String message) {
        this(message, "UNAUTHORIZED");
    }

    public UnauthorizedException(String message, String code) {
        super(HttpStatus.UNAUTHORIZED, message, code);
    }
}
