package com.leadsyncpro.billing.facade;

import java.util.List;
import java.util.UUID;

public record PlanCatalogDto(
        UUID id,
        String code,
        String name,
        String description,
        List<PlanPriceDto> prices) {}
