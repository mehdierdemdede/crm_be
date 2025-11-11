package com.leadsyncpro.billing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.leadsyncpro.model.billing.BillingPeriod;
import com.leadsyncpro.model.billing.Plan;
import com.leadsyncpro.model.billing.Price;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class InvoicingServiceTest {

    private final PricingService pricingService = new PricingService();

    @Test
    void generateInvoiceContextShouldReturnNormalizedPeriodAndAmount() {
        Plan plan = Plan.builder().code("basic").name("Basic").build();
        Price price = Price.builder()
                .plan(plan)
                .billingPeriod(BillingPeriod.MONTH)
                .baseAmountCents(2_000L)
                .perSeatAmountCents(500L)
                .currency("USD")
                .build();
        Instant start = Instant.parse("2024-01-01T00:00:00Z");
        Instant end = start.plus(30, ChronoUnit.DAYS);
        InvoicingService invoicingService = new InvoicingService(pricingService);

        InvoicingService.InvoiceContext context =
                invoicingService.generateInvoiceContext(price, 2, start, end);

        assertEquals(start, context.periodStart());
        assertEquals(end, context.periodEnd());
        assertEquals(3_000L, context.amountCents());
    }

    @Test
    void generateInvoiceContextShouldAllowHookOverrides() {
        Plan plan = Plan.builder().code("basic").name("Basic").build();
        Price price = Price.builder()
                .plan(plan)
                .billingPeriod(BillingPeriod.MONTH)
                .baseAmountCents(2_000L)
                .perSeatAmountCents(500L)
                .currency("USD")
                .build();
        Instant requestedStart = Instant.parse("2024-02-01T00:00:00Z");
        Instant requestedEnd = requestedStart.plus(30, ChronoUnit.DAYS);

        InvoicingService invoicingService = new InvoicingService(pricingService) {
            @Override
            protected Instant determinePeriodStart(Instant candidateStart) {
                return candidateStart.plus(1, ChronoUnit.DAYS);
            }

            @Override
            protected Instant determinePeriodEnd(Instant candidateEnd) {
                return candidateEnd.minus(1, ChronoUnit.DAYS);
            }

            @Override
            protected long applyProration(long amountCents, Instant periodStart, Instant periodEnd) {
                assertEquals(requestedStart.plus(1, ChronoUnit.DAYS), periodStart);
                assertEquals(requestedEnd.minus(1, ChronoUnit.DAYS), periodEnd);
                return amountCents / 2;
            }

            @Override
            protected long applyTaxes(long amountCents) {
                return amountCents + 250;
            }
        };

        InvoicingService.InvoiceContext context =
                invoicingService.generateInvoiceContext(price, 2, requestedStart, requestedEnd);

        assertEquals(requestedStart.plus(1, ChronoUnit.DAYS), context.periodStart());
        assertEquals(requestedEnd.minus(1, ChronoUnit.DAYS), context.periodEnd());
        assertEquals(1_750L, context.amountCents());
    }

    @Test
    void generateInvoiceContextShouldRejectInvalidPeriods() {
        Plan plan = Plan.builder().code("basic").name("Basic").build();
        Price price = Price.builder()
                .plan(plan)
                .billingPeriod(BillingPeriod.MONTH)
                .baseAmountCents(2_000L)
                .perSeatAmountCents(500L)
                .currency("USD")
                .build();
        Instant start = Instant.parse("2024-03-01T00:00:00Z");
        Instant end = start.minus(1, ChronoUnit.DAYS);
        InvoicingService invoicingService = new InvoicingService(pricingService);

        assertThrows(
                IllegalArgumentException.class,
                () -> invoicingService.generateInvoiceContext(price, 1, start, end));
    }
}
