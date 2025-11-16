package com.leadsyncpro.service;

import com.leadsyncpro.dto.PlanSummaryResponse;
import com.leadsyncpro.model.billing.BillingPeriod;
import com.leadsyncpro.model.billing.Plan;
import com.leadsyncpro.model.billing.Price;
import com.leadsyncpro.repository.billing.PlanRepository;
import com.leadsyncpro.repository.billing.PriceRepository;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicPlanService {

    private final PlanRepository planRepository;
    private final PriceRepository priceRepository;

    public PublicPlanService(PlanRepository planRepository, PriceRepository priceRepository) {
        this.planRepository = Objects.requireNonNull(planRepository, "planRepository must not be null");
        this.priceRepository = Objects.requireNonNull(priceRepository, "priceRepository must not be null");
    }

    @Transactional(readOnly = true)
    public List<PlanSummaryResponse> getPlans(BillingPeriod billingPeriod, int seatCount) {
        if (billingPeriod == null) {
            throw new IllegalArgumentException("billingPeriod is required");
        }

        int normalizedSeatCount = Math.max(1, seatCount);

        return planRepository.findAll().stream()
                .filter(plan -> plan.isActive())
                .map(plan -> toPlanSummary(plan, billingPeriod, normalizedSeatCount))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(PlanSummaryResponse::name, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .collect(Collectors.toList());
    }

    private Optional<PlanSummaryResponse> toPlanSummary(Plan plan, BillingPeriod billingPeriod, int seatCount) {
        return priceRepository
                .findByPlanAndBillingPeriod(plan, billingPeriod)
                .map(price -> mapToResponse(plan, price, seatCount));
    }

    private PlanSummaryResponse mapToResponse(Plan plan, Price price, int seatCount) {
        BigDecimal basePrice = centsToCurrency(price.getBaseAmountCents());
        BigDecimal seatPrice = centsToCurrency(price.getPerSeatAmountCents());
        BigDecimal total = basePrice.add(seatPrice.multiply(BigDecimal.valueOf(seatCount)));
        Integer trialDays = price.getTrialDays() != null ? price.getTrialDays() : 0;

        return new PlanSummaryResponse(
                plan.getId(),
                plan.getCode(),
                plan.getName(),
                plan.getDescription(),
                price.getBillingPeriod(),
                basePrice,
                seatPrice,
                seatCount,
                total,
                trialDays,
                price.getSeatLimit(),
                price.getCurrency());
    }

    private BigDecimal centsToCurrency(Long amount) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(amount, 2);
    }
}
