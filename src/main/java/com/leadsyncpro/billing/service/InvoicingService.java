package com.leadsyncpro.billing.service;

import com.leadsyncpro.billing.money.MoneyRounding;
import com.leadsyncpro.model.billing.Price;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Coordinates invoice related calculations while exposing hook methods for customisation.
 */
public class InvoicingService {

    private final PricingService pricingService;
    private final MoneyRounding moneyRounding;

    public InvoicingService(PricingService pricingService, MoneyRounding moneyRounding) {
        this.pricingService = Objects.requireNonNull(pricingService, "pricingService must not be null");
        this.moneyRounding = Objects.requireNonNull(moneyRounding, "moneyRounding must not be null");
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
        return generateInvoiceContext(
                price, seatCount, requestedPeriodStart, requestedPeriodEnd, ProrationInstruction.none());
    }

    public InvoiceContext generateInvoiceContext(
            Price price,
            int seatCount,
            Instant requestedPeriodStart,
            Instant requestedPeriodEnd,
            ProrationInstruction prorationInstruction) {
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

        ProrationInstruction instruction =
                prorationInstruction == null ? ProrationInstruction.none() : prorationInstruction;
        long baseAmount = resolveBaseAmount(price, seatCount, instruction);
        long proratedAmount = applyProration(baseAmount, normalizedStart, normalizedEnd, instruction);
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
    protected long applyProration(
            long amountCents, Instant periodStart, Instant periodEnd, ProrationInstruction instruction) {
        if (instruction instanceof ProrationInstruction.SeatIncrease seatIncrease) {
            return applySeatIncreaseProration(amountCents, periodEnd, seatIncrease);
        }
        if (instruction instanceof ProrationInstruction.PlanChange planChange) {
            return planChange.type() == ProrationInstruction.PlanChangeType.UPGRADE ? amountCents : 0L;
        }
        return amountCents;
    }

    /** Hook that allows adding tax logic. */
    protected long applyTaxes(long amountCents) {
        return amountCents;
    }

    public record InvoiceContext(Instant periodStart, Instant periodEnd, long amountCents) {}

    public sealed interface ProrationInstruction
            permits ProrationInstruction.None, ProrationInstruction.SeatIncrease, ProrationInstruction.PlanChange {

        static ProrationInstruction none() {
            return None.INSTANCE;
        }

        static ProrationInstruction seatIncrease(
                int previousSeatCount, int newSeatCount, Instant billingPeriodStart, Instant changeEffectiveAt) {
            return new SeatIncrease(previousSeatCount, newSeatCount, billingPeriodStart, changeEffectiveAt);
        }

        static ProrationInstruction planUpgrade(Price previousPrice) {
            return new PlanChange(previousPrice, PlanChangeType.UPGRADE);
        }

        static ProrationInstruction planDowngrade(Price previousPrice) {
            return new PlanChange(previousPrice, PlanChangeType.DOWNGRADE);
        }

        enum PlanChangeType {
            UPGRADE,
            DOWNGRADE
        }

        final class None implements ProrationInstruction {
            private static final None INSTANCE = new None();

            private None() {}
        }

        record SeatIncrease(
                int previousSeatCount, int newSeatCount, Instant billingPeriodStart, Instant changeEffectiveAt)
                implements ProrationInstruction {

            public SeatIncrease {
                if (previousSeatCount < 0 || newSeatCount < 0) {
                    throw new IllegalArgumentException("Seat counts must not be negative");
                }
                Objects.requireNonNull(billingPeriodStart, "billingPeriodStart must not be null");
                Objects.requireNonNull(changeEffectiveAt, "changeEffectiveAt must not be null");
            }

            int seatDelta() {
                return newSeatCount - previousSeatCount;
            }
        }

        record PlanChange(Price previousPrice, PlanChangeType type) implements ProrationInstruction {
            public PlanChange {
                Objects.requireNonNull(previousPrice, "previousPrice must not be null");
                Objects.requireNonNull(type, "type must not be null");
            }
        }
    }

    private long resolveBaseAmount(Price price, int seatCount, ProrationInstruction instruction) {
        if (instruction instanceof ProrationInstruction.SeatIncrease seatIncrease) {
            int seatDelta = seatIncrease.seatDelta();
            if (seatDelta <= 0) {
                return 0L;
            }
            long perSeatAmount = price.getPerSeatAmountCents() == null ? 0L : price.getPerSeatAmountCents();
            if (perSeatAmount < 0) {
                throw new IllegalArgumentException("Per-seat amount must not be negative");
            }
            return Math.multiplyExact(perSeatAmount, seatDelta);
        }
        if (instruction instanceof ProrationInstruction.PlanChange planChange) {
            if (planChange.type() == ProrationInstruction.PlanChangeType.DOWNGRADE) {
                return 0L;
            }
            long previousAmount = pricingService.computeAmount(planChange.previousPrice(), seatCount);
            long newAmount = pricingService.computeAmount(price, seatCount);
            long delta = newAmount - previousAmount;
            return Math.max(delta, 0L);
        }
        return pricingService.computeAmount(price, seatCount);
    }

    private long applySeatIncreaseProration(
            long baseAmount, Instant periodEnd, ProrationInstruction.SeatIncrease seatIncrease) {
        if (baseAmount <= 0) {
            return 0L;
        }
        Instant changeEffectiveAt = seatIncrease.changeEffectiveAt();
        if (!periodEnd.isAfter(changeEffectiveAt)) {
            return 0L;
        }
        long totalDays = ChronoUnit.DAYS.between(seatIncrease.billingPeriodStart(), periodEnd);
        if (totalDays <= 0) {
            throw new IllegalArgumentException("Billing period must span at least one day for proration");
        }
        long remainingDays = ChronoUnit.DAYS.between(changeEffectiveAt, periodEnd);
        if (remainingDays <= 0) {
            return 0L;
        }
        BigDecimal prorated = BigDecimal.valueOf(baseAmount)
                .multiply(BigDecimal.valueOf(remainingDays))
                .divide(BigDecimal.valueOf(totalDays), 6, RoundingMode.HALF_UP);
        return moneyRounding.roundToMinorUnit(prorated);
    }
}
