package com.leadsyncpro.billing.api;

import com.leadsyncpro.billing.facade.Proration;
import com.leadsyncpro.model.billing.BillingPeriod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(name = "ChangePlanRequest")
public record ChangePlanRequest(
        @NotBlank @Schema(example = "ENTERPRISE") String planCode,
        @NotNull @Schema(example = "YEAR") BillingPeriod billingPeriod,
        @Schema(example = "IMMEDIATE") Proration proration) {}
