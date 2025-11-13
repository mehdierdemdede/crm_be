package com.leadsyncpro.billing.service;

public class PlanValidationException extends RuntimeException {
    public PlanValidationException(String message) {
        super(message);
    }
}
