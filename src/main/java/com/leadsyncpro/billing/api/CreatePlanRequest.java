package com.leadsyncpro.billing.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

@Schema(name = "CreatePlanRequest")
public record CreatePlanRequest(
        @NotBlank
        @Size(max = 100)
        @Schema(description = "Unique code of the plan", example = "PRO")
        String code,

        @NotBlank
        @Size(max = 255)
        @Schema(description = "Display name of the plan", example = "Professional")
        String name,

        @Schema(description = "Optional description of the plan", example = "Best for scaling teams")
        String description,

        @Schema(description = "List of plan features")
        List<@NotBlank String> features,

        @Schema(description = "Metadata bag such as pricing hints", example = "{\"basePrice_month\": 99}")
        Map<String, Object> metadata) {}
