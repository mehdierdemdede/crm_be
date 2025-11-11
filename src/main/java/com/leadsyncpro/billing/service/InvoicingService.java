package com.leadsyncpro.billing.service;

import com.leadsyncpro.model.billing.Price;
import java.time.Instant;
import java.util.Objects;

/**
 * Coordinates invoice related calculations while exposing hook methods for customisation.
 */
public class InvoicingService {

    private final PricingService pricingService;

    public InvoicingService(PricingService pricingService) {
        this.pricingService = Objects.requireNonNull(pricingService, "pricingService must not be null");
    }

    /**
     * Creates a lightweight invoice context that contains the resolved period boundaries and
     * the final amount to charge.
     *
     * @param price the price definition to use
     * @param seatCount the number of seats to bill
     * @param requestedPeriodStart the requested period start
     * @param requestedPeriodEnd the requested period end
     * @return a new {@link InvoiceContext}
     */
    public InvoiceContext generateInvoiceContext(
            Price price, int seatCount, Instant requestedPeriodStart, Instant requestedPeriodEnd) {
        if (price == null) {
            throw new IllegalArgumentException("Price must not be null");
        }
        Instant normalizedStart = determinePeriodStart(
                Objects.requireNonNull(requestedPeriodStart, "requestedPeriodStart must not be null"));
        Instant normalizedEnd =
                determinePeriodEnd(Objects.requireNonNull(requestedPeriodEnd, "requestedPeriodEnd must not be null"));

        if (!normalizedEnd.isAfter(normalizedStart)) {
            throw new IllegalArgumentException("Billing period end must be after the start");
        }

        long baseAmount = pricingService.computeAmount(price, seatCount);
        long proratedAmount = applyProration(baseAmount, normalizedStart, normalizedEnd);
        long finalAmount = applyTaxes(proratedAmount);

        if (finalAmount < 0) {
            throw new IllegalStateException("Final invoice amount cannot be negative");
        }

        return new InvoiceContext(normalizedStart, normalizedEnd, finalAmount);
    }

    /** Hook that allows fine-tuning the period start. */
    protected Instant determinePeriodStart(Instant candidateStart) {
        return candidateStart;
    }

    /** Hook that allows fine-tuning the period end. */
    protected Instant determinePeriodEnd(Instant candidateEnd) {
        return candidateEnd;
    }

    /** Hook that allows implementing proration logic. */
    protected long applyProration(long amountCents, Instant periodStart, Instant periodEnd) {
        return amountCents;
    }

    /** Hook that allows adding tax logic. */
    protected long applyTaxes(long amountCents) {
        return amountCents;
    }

    public record InvoiceContext(Instant periodStart, Instant periodEnd, long amountCents) {}
}
