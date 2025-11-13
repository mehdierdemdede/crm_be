package com.leadsyncpro.billing.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.leadsyncpro.model.billing.BillingPeriod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

@Schema(name = "CreatePlanRequest")
public record CreatePlanRequest(
        @NotBlank
        @Size(max = 100)
        @Schema(description = "Unique code of the plan", example = "growth")
        String id,

        @NotBlank
        @Size(max = 255)
        @Schema(description = "Display name of the plan", example = "Growth")
        String name,

        @Size(max = 1_000)
        @Schema(description = "Optional description of the plan", example = "Orta ölçekli ekipler için")
        String description,

        @Schema(description = "List of plan features")
        List<@NotBlank(message = "Feature must not be blank") String> features,

        @Valid
        @Schema(description = "Optional metadata with pricing hints")
        Metadata metadata,

        @NotEmpty
        @Valid
        @Schema(description = "At least one pricing option must be provided")
        List<Price> prices) {

    @Schema(name = "CreatePlanRequest.Metadata")
    public record Metadata(
            @DecimalMin(value = "0.0", message = "basePrice must be positive or zero")
                    @Digits(integer = 12, fraction = 2)
                    @PositiveOrZero
                    @JsonProperty("basePrice")
                    BigDecimal basePrice,

            @DecimalMin(value = "0.0", message = "perSeatPrice must be positive or zero")
                    @Digits(integer = 12, fraction = 2)
                    @PositiveOrZero
                    @JsonProperty("perSeatPrice")
                    BigDecimal perSeatPrice,

            @DecimalMin(value = "0.0", message = "basePrice_month must be positive or zero")
                    @Digits(integer = 12, fraction = 2)
                    @PositiveOrZero
                    @JsonProperty("basePrice_month")
                    BigDecimal basePriceMonth,

            @DecimalMin(value = "0.0", message = "perSeatPrice_month must be positive or zero")
                    @Digits(integer = 12, fraction = 2)
                    @PositiveOrZero
                    @JsonProperty("perSeatPrice_month")
                    BigDecimal perSeatPriceMonth,

            @DecimalMin(value = "0.0", message = "basePrice_year must be positive or zero")
                    @Digits(integer = 12, fraction = 2)
                    @PositiveOrZero
                    @JsonProperty("basePrice_year")
                    BigDecimal basePriceYear,

            @DecimalMin(value = "0.0", message = "perSeatPrice_year must be positive or zero")
                    @Digits(integer = 12, fraction = 2)
                    @PositiveOrZero
                    @JsonProperty("perSeatPrice_year")
                    BigDecimal perSeatPriceYear) {}

    @Schema(name = "CreatePlanRequest.Price")
    public record Price(
            @NotBlank
                    @Schema(description = "Client-side identifier for the price", example = "growth-monthly")
                    String id,

            @NotNull
                    @DecimalMin(value = "0.0", message = "amount must be positive or zero")
                    @Digits(integer = 12, fraction = 2)
                    @PositiveOrZero
                    @Schema(description = "Per seat amount", example = "120")
                    BigDecimal amount,

            @NotBlank
                    @Pattern(regexp = "^[A-Za-z]{3}$", message = "Currency must be a 3-letter ISO code")
                    @Schema(description = "Currency code", example = "TRY")
                    String currency,

            @NotNull
                    @Schema(description = "Billing period", example = "MONTH")
                    BillingPeriod billingPeriod,

            @PositiveOrZero
                    @Schema(description = "Optional seat limit")
                    Integer seatLimit,

            @PositiveOrZero
                    @Schema(description = "Optional trial days")
                    Integer trialDays) {}
}
