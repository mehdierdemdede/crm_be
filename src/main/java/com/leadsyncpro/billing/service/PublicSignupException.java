package com.leadsyncpro.billing.service;

import org.springframework.http.HttpStatus;

public class PublicSignupException extends RuntimeException {

    private final HttpStatus status;

    public PublicSignupException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
