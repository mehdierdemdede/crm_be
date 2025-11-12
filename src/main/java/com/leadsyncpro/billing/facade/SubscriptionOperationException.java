package com.leadsyncpro.billing.facade;

public class SubscriptionOperationException extends RuntimeException {

    public SubscriptionOperationException(String message) {
        super(message);
    }

    public SubscriptionOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
