package com.leadsyncpro.billing.api;

import com.leadsyncpro.billing.facade.Proration;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(name = "UpdateSeatCountRequest")
public record UpdateSeatCountRequest(
        @NotNull @Min(1) @Schema(example = "25") Integer seatCount,
        @Schema(example = "DEFERRED") Proration proration) {}
