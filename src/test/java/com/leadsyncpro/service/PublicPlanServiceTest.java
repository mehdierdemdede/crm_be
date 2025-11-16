package com.leadsyncpro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.leadsyncpro.dto.PlanSummaryResponse;
import com.leadsyncpro.model.billing.BillingPeriod;
import com.leadsyncpro.model.billing.Plan;
import com.leadsyncpro.model.billing.Price;
import com.leadsyncpro.repository.billing.PlanRepository;
import com.leadsyncpro.repository.billing.PriceRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PublicPlanServiceTest {

    @Mock private PlanRepository planRepository;
    @Mock private PriceRepository priceRepository;

    @InjectMocks private PublicPlanService publicPlanService;

    private Plan activePlan;
    private Price monthlyPrice;

    @BeforeEach
    void setUp() {
        activePlan = Plan.builder().id(UUID.randomUUID()).code("basic").name("Basic").active(true).build();
        monthlyPrice = Price.builder()
                .plan(activePlan)
                .billingPeriod(BillingPeriod.MONTHLY)
                .baseAmountCents(2_000L)
                .perSeatAmountCents(300L)
                .currency("USD")
                .trialDays(14)
                .build();
    }

    @Test
    void getPlansShouldRequireBillingPeriod() {
        assertThatThrownBy(() -> publicPlanService.getPlans(null, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getPlansShouldReturnActivePlansWithComputedTotal() {
        when(planRepository.findAll()).thenReturn(List.of(activePlan));
        when(priceRepository.findByPlanAndBillingPeriod(activePlan, BillingPeriod.MONTHLY))
                .thenReturn(Optional.of(monthlyPrice));

        List<PlanSummaryResponse> responses = publicPlanService.getPlans(BillingPeriod.MONTHLY, 5);

        assertThat(responses).hasSize(1);
        PlanSummaryResponse response = responses.get(0);
        assertThat(response.planId()).isEqualTo(activePlan.getId());
        assertThat(response.basePrice().intValue()).isEqualTo(20); // 2000 cents
        assertThat(response.pricePerSeat().intValue()).isEqualTo(3); // 300 cents
        assertThat(response.totalPrice()).isEqualByComparingTo("35.00");
        assertThat(response.trialDays()).isEqualTo(14);
        assertThat(response.billingPeriod()).isEqualTo(BillingPeriod.MONTHLY);
    }

    @Test
    void getPlansShouldIgnoreInactivePlans() {
        Plan inactivePlan = Plan.builder().id(UUID.randomUUID()).code("legacy").name("Legacy").active(false).build();
        when(planRepository.findAll()).thenReturn(List.of(activePlan, inactivePlan));
        when(priceRepository.findByPlanAndBillingPeriod(activePlan, BillingPeriod.MONTHLY))
                .thenReturn(Optional.of(monthlyPrice));

        List<PlanSummaryResponse> responses = publicPlanService.getPlans(BillingPeriod.MONTHLY, 1);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).planCode()).isEqualTo("basic");
    }
}
