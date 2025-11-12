package com.leadsyncpro.billing.api.idempotency;

public class IdempotencyKeyMissingException extends RuntimeException {

    public IdempotencyKeyMissingException() {
        super("Idempotency-Key header is required for this operation");
    }
}
