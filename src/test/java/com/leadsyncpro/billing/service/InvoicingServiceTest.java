package com.leadsyncpro.billing.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.leadsyncpro.model.billing.BillingPeriod;
import com.leadsyncpro.model.billing.Plan;
import com.leadsyncpro.model.billing.Price;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class InvoicingServiceTest {

    private final PricingService pricingService = new PricingService();
    private final InvoicingService invoicingService = new InvoicingService(pricingService);

    @Test
    void seatIncreaseProrationUsesHalfUpRounding() {
        Plan plan = Plan.builder().code("PRO").build();
        Price price = Price.builder()
                .plan(plan)
                .billingPeriod(BillingPeriod.MONTH)
                .baseAmountCents(0L)
                .perSeatAmountCents(1500L)
                .currency("TRY")
                .build();

        Instant periodStart = Instant.parse("2024-03-01T00:00:00Z");
        Instant periodEnd = periodStart.plusSeconds(30L * 24L * 3600L);
        Instant seatChangeEffective = periodStart.plusSeconds(12L * 24L * 3600L);

        InvoicingService.ProrationInstruction instruction = InvoicingService.ProrationInstruction.seatIncrease(
                10, 14, periodStart, seatChangeEffective);

        InvoicingService.InvoiceContext context = invoicingService.generateInvoiceContext(
                price, 14, periodStart, periodEnd, instruction);

        // Seat delta = 4, remaining days 18/30 -> 4 * 1500 * 18 / 30 = 3600 (rounded half up)
        assertThat(context.amountCents()).isEqualTo(3600L);
    }
}
