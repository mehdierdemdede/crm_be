package com.leadsyncpro.billing.facade;

import com.leadsyncpro.model.billing.Invoice;
import com.leadsyncpro.model.billing.Plan;
import com.leadsyncpro.model.billing.Price;
import com.leadsyncpro.repository.billing.InvoiceRepository;
import com.leadsyncpro.repository.billing.PlanRepository;
import com.leadsyncpro.repository.billing.PriceRepository;
import java.util.Comparator;
import java.util.List;
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
        List<PlanPriceDto> prices = priceRepository.findByPlan(plan).stream()
                .sorted(Comparator.comparing(Price::getBillingPeriod, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(price -> new PlanPriceDto(
                        price.getId(),
                        price.getBillingPeriod(),
                        price.getBaseAmountCents(),
                        price.getPerSeatAmountCents(),
                        price.getCurrency()))
                .collect(Collectors.toList());
        return new PlanCatalogDto(plan.getId(), plan.getCode(), plan.getName(), plan.getDescription(), prices);
    }
}
