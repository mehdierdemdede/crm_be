package com.leadsyncpro.billing.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Schema(name = "PlanResponse")
public record PlanResponse(
        @Schema(description = "Unique identifier of the plan", example = "5d3c7f8b-8a8c-4f2e-9f8f-9b0c1d2e3f01") UUID id,
        @Schema(description = "Short code of the plan", example = "BASIC") String code,
        @Schema(description = "Display name of the plan", example = "Basic Plan") String name,
        @Schema(description = "Detailed description of the plan", example = "Entry level plan for growing teams") String description,
        @Schema(description = "Features included in the plan") List<String> features,
        @Schema(description = "Metadata that configures pricing behavior", example = "{\"perSeatPrice_month\": 15}")
                Map<String, Object> metadata,
        @Schema(description = "Pricing options available for the plan") List<PlanPriceResponse> prices) {}
