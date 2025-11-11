package com.leadsyncpro.billing.service;

import com.leadsyncpro.model.billing.Price;

/**
 * Provides pricing calculations for subscription billing.
 */
public class PricingService {

    /**
     * Computes the amount to charge for the given price and seat count.
     *
     * @param price the price definition containing base and per-seat amounts
     * @param seatCount the number of seats that should be billed
     * @return the amount in cents to charge
     * @throws IllegalArgumentException if the inputs are invalid or produce an overflow
     */
    public long computeAmount(Price price, int seatCount) {
        if (price == null) {
            throw new IllegalArgumentException("Price must not be null");
        }
        if (seatCount < 0) {
            throw new IllegalArgumentException("Seat count must not be negative");
        }

        long baseAmount = normalizeAmount(price.getBaseAmountCents(), "Base amount");
        long perSeatAmount = normalizeAmount(price.getPerSeatAmountCents(), "Per-seat amount");

        try {
            long seatComponent = Math.multiplyExact(perSeatAmount, (long) seatCount);
            return Math.addExact(baseAmount, seatComponent);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Calculated amount is outside of the supported range", ex);
        }
    }

    private long normalizeAmount(Long amount, String label) {
        long normalized = amount == null ? 0L : amount;
        if (normalized < 0) {
            throw new IllegalArgumentException(label + " must not be negative");
        }
        return normalized;
    }
}
