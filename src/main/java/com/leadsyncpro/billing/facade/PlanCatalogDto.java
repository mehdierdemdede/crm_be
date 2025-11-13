package com.leadsyncpro.billing.facade;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PlanCatalogDto(
        UUID id,
        String code,
        String name,
        String description,
        List<String> features,
        Map<String, Object> metadata,
        List<PlanPriceDto> prices) {}
