package com.leadsyncpro.billing.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "TokenizePaymentMethodResponse")
public record TokenizePaymentMethodResponse(
        @Schema(description = "Token returned by Iyzico to represent the card", example = "tok_1Nv0a8YgL9s")
                String token) {}
