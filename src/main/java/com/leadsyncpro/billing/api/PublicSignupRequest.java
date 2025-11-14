package com.leadsyncpro.billing.api;

import com.leadsyncpro.model.billing.BillingPeriod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Schema(name = "PublicSignupRequest")
public record PublicSignupRequest(
        @NotNull @Schema(description = "Identifier of the selected plan", example = "5d3c7f8b-8a8c-4f2e-9f8f-9b0c1d2e3f01") UUID planId,
        @NotNull @Schema(description = "Billing period for the subscription", example = "MONTH") BillingPeriod billingPeriod,
        @NotNull @Min(1) @Max(200) @Schema(description = "Number of seats requested", example = "10") Integer seatCount,
        @NotBlank @Size(max = 255) @Schema(description = "Name of the organization to be created", example = "Acme Inc.")
                String organizationName,
        @Valid @NotNull Admin admin) {

    @Schema(name = "PublicSignupAdmin")
    public record Admin(
            @NotBlank @Size(max = 100) @Schema(description = "First name of the organization admin", example = "Ada")
                    String firstName,
            @NotBlank @Size(max = 100) @Schema(description = "Last name of the organization admin", example = "Lovelace")
                    String lastName,
            @NotBlank @Email @Size(max = 255)
                    @Schema(description = "Work email address for the admin", example = "ada@acme.co")
                    String email,
            @Size(max = 40) @Schema(description = "Optional phone number for the admin", example = "+90 555 000 0000")
                    String phone) {}
}
