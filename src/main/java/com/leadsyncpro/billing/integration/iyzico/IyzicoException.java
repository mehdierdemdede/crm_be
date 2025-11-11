package com.leadsyncpro.billing.integration.iyzico;

public class IyzicoException extends RuntimeException {

    public IyzicoException(String message) {
        super(message);
    }

    public IyzicoException(String message, Throwable cause) {
        super(message, cause);
    }
}
