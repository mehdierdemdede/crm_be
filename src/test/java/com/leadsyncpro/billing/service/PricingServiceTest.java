package com.leadsyncpro.billing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.leadsyncpro.model.billing.BillingPeriod;
import com.leadsyncpro.model.billing.Plan;
import com.leadsyncpro.model.billing.Price;
import org.junit.jupiter.api.Test;

class PricingServiceTest {

    private final PricingService pricingService = new PricingService();

    @Test
    void computeAmountShouldCombineBaseAndSeatAmounts() {
        Plan plan = Plan.builder().code("basic").name("Basic").build();
        Price price = Price.builder()
                .plan(plan)
                .billingPeriod(BillingPeriod.MONTH)
                .baseAmountCents(1_000L)
                .perSeatAmountCents(200L)
                .currency("USD")
                .build();

        long amount = pricingService.computeAmount(price, 3);

        assertEquals(1_600L, amount);
    }

    @Test
    void computeAmountShouldAllowZeroSeatPriceComponents() {
        Plan plan = Plan.builder().code("basic").name("Basic").build();
        Price price = Price.builder()
                .plan(plan)
                .billingPeriod(BillingPeriod.MONTH)
                .baseAmountCents(null)
                .perSeatAmountCents(500L)
                .currency("USD")
                .build();

        long amount = pricingService.computeAmount(price, 2);

        assertEquals(1_000L, amount);
    }

    @Test
    void computeAmountShouldRejectNegativeSeatCount() {
        Plan plan = Plan.builder().code("basic").name("Basic").build();
        Price price = Price.builder()
                .plan(plan)
                .billingPeriod(BillingPeriod.MONTH)
                .baseAmountCents(1_000L)
                .perSeatAmountCents(100L)
                .currency("USD")
                .build();

        assertThrows(IllegalArgumentException.class, () -> pricingService.computeAmount(price, -1));
    }

    @Test
    void computeAmountShouldRejectNegativePriceComponents() {
        Plan plan = Plan.builder().code("basic").name("Basic").build();
        Price price = Price.builder()
                .plan(plan)
                .billingPeriod(BillingPeriod.MONTH)
                .baseAmountCents(-1_000L)
                .perSeatAmountCents(100L)
                .currency("USD")
                .build();

        assertThrows(IllegalArgumentException.class, () -> pricingService.computeAmount(price, 1));
    }

    @Test
    void computeAmountShouldRejectNullPrice() {
        assertThrows(IllegalArgumentException.class, () -> pricingService.computeAmount(null, 1));
    }
}
