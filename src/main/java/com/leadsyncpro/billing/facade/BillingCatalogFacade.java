package com.leadsyncpro.billing.facade;

import java.util.List;
import java.util.UUID;

public interface BillingCatalogFacade {

    List<PlanCatalogDto> getPublicPlans();

    InvoiceDetailDto getInvoice(UUID invoiceId);
}
