package com.relix.marketplace.storage;

import lombok.Getter;

@Getter
public class StorageException extends RuntimeException {

    private final String code;

    public StorageException(String message, String code) {
        super(message);
        this.code = code;
    }

    public StorageException(String message, String code, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
