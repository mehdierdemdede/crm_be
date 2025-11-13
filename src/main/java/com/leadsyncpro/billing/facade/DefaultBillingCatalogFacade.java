package com.leadsyncpro.billing.facade;

import com.leadsyncpro.model.billing.Invoice;
import com.leadsyncpro.model.billing.Plan;
import com.leadsyncpro.model.billing.Price;
import com.leadsyncpro.repository.billing.InvoiceRepository;
import com.leadsyncpro.repository.billing.PlanRepository;
import com.leadsyncpro.repository.billing.PriceRepository;
import com.leadsyncpro.model.billing.BillingPeriod;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultBillingCatalogFacade implements BillingCatalogFacade {

    private final PlanRepository planRepository;
    private final PriceRepository priceRepository;
    private final InvoiceRepository invoiceRepository;

    public DefaultBillingCatalogFacade(
            PlanRepository planRepository, PriceRepository priceRepository, InvoiceRepository invoiceRepository) {
        this.planRepository = Objects.requireNonNull(planRepository, "planRepository must not be null");
        this.priceRepository = Objects.requireNonNull(priceRepository, "priceRepository must not be null");
        this.invoiceRepository = Objects.requireNonNull(invoiceRepository, "invoiceRepository must not be null");
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlanCatalogDto> getPublicPlans() {
        return planRepository.findAll().stream()
                .sorted(Comparator.comparing(Plan::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(this::toPlanCatalogDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceDetailDto getInvoice(UUID invoiceId) {
        Invoice invoice = invoiceRepository
                .findById(invoiceId)
                .orElseThrow(() -> new SubscriptionNotFoundException("Invoice %s not found".formatted(invoiceId)));
        return new InvoiceDetailDto(
                invoice.getId(),
                invoice.getSubscription() != null ? invoice.getSubscription().getId() : null,
                invoice.getExternalInvoiceId(),
                invoice.getPeriodStart(),
                invoice.getPeriodEnd(),
                invoice.getSubtotalCents(),
                invoice.getTaxCents(),
                invoice.getTotalCents(),
                invoice.getCurrency(),
                invoice.getStatus(),
                invoice.getCreatedAt(),
                invoice.getUpdatedAt());
    }

    private PlanCatalogDto toPlanCatalogDto(Plan plan) {
        List<Price> planPrices = priceRepository.findByPlan(plan).stream()
                .sorted(Comparator.comparing(Price::getBillingPeriod, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());

        List<PlanPriceDto> prices = planPrices.stream()
                .map(price -> new PlanPriceDto(
                        price.getId(),
                        price.getBillingPeriod(),
                        centsToCurrency(price.getPerSeatAmountCents()),
                        price.getCurrency(),
                        price.getSeatLimit(),
                        price.getTrialDays()))
                .collect(Collectors.toList());

        Map<String, Object> metadata = buildMetadata(plan, planPrices);

        List<String> features = plan.getFeatures() != null ? plan.getFeatures() : List.of();

        return new PlanCatalogDto(
                plan.getId(),
                plan.getCode(),
                plan.getName(),
                plan.getDescription(),
                features,
                metadata,
                prices);
    }

    private Map<String, Object> buildMetadata(Plan plan, List<Price> prices) {
        Map<String, Object> metadata = new HashMap<>();
        if (plan.getMetadata() != null) {
            metadata.putAll(plan.getMetadata());
        }

        Price defaultPrice = prices.stream().findFirst().orElse(null);
        if (defaultPrice != null) {
            metadata.putIfAbsent("basePrice", centsToCurrency(defaultPrice.getBaseAmountCents()));
            metadata.putIfAbsent("perSeatPrice", centsToCurrency(defaultPrice.getPerSeatAmountCents()));
        }

        for (Price price : prices) {
            BillingPeriod period = price.getBillingPeriod();
            if (period == null) {
                continue;
            }
            String suffix = period.name().toLowerCase();
            metadata.putIfAbsent("basePrice_" + suffix, centsToCurrency(price.getBaseAmountCents()));
            metadata.putIfAbsent("perSeatPrice_" + suffix, centsToCurrency(price.getPerSeatAmountCents()));
        }

        return metadata;
    }

    private BigDecimal centsToCurrency(Long amount) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(amount, 2);
    }
}
