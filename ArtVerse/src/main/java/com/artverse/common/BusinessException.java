package com.artverse.common;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final int status;
    private final String provider;

    public BusinessException(int status, String message) {
        super(message);
        this.status = status;
        this.provider = null;
    }

    public BusinessException(int status, String message, String provider) {
        super(message);
        this.status = status;
        this.provider = provider;
    }
}
