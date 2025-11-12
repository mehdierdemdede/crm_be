package com.leadsyncpro.billing.facade;

/**
 * Represents how billing adjustments should be handled when a subscription changes.
 */
public enum Proration {
    IMMEDIATE,
    DEFERRED;

    public static Proration defaultValue() {
        return IMMEDIATE;
    }
}
