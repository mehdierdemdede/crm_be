package com.leadsyncpro.billing.service;

public class PlanConflictException extends RuntimeException {
    public PlanConflictException(String message) {
        super(message);
    }
}
