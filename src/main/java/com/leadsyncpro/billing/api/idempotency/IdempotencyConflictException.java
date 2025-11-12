package com.leadsyncpro.billing.api.idempotency;

public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException() {
        super("Idempotency conflict: request body does not match the original request");
    }
}
