package com.leadsyncpro.billing.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "TokenizePaymentMethodRequest")
public record TokenizePaymentMethodRequest(
        @Schema(description = "Full name printed on the card", example = "Ada Lovelace")
                @NotBlank
                @Size(max = 255)
                String cardHolderName,
        @Schema(description = "Primary account number without spaces", example = "5528790000000008")
                @NotBlank
                @Pattern(regexp = "^[0-9]{12,19}$", message = "Card number must contain 12 to 19 digits")
                String cardNumber,
        @Schema(description = "Expiration month in MM format", example = "01")
                @NotBlank
                @Pattern(regexp = "^(0[1-9]|1[0-2])$", message = "Expiration month must be between 01 and 12")
                String expireMonth,
        @Schema(description = "Expiration year in YY or YYYY format", example = "2026")
                @NotBlank
                @Pattern(regexp = "^(?:[0-9]{2}|[0-9]{4})$", message = "Expiration year must be two or four digits")
                String expireYear,
        @Schema(description = "Card verification code", example = "123")
                @NotBlank
                @Pattern(regexp = "^[0-9]{3,4}$", message = "CVC must be three or four digits")
                String cvc) {}
