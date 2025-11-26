package com.leadsyncpro.billing.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

@Schema(name = "PublicSignupPaymentResponse")
public record PublicSignupPaymentResponse(
        @Schema(description = "Result status of the payment initialization", example = "SUCCESS")
                String status,
        @Schema(description = "Identifier for the created subscription or token", example = "tok_1Nv0a8YgL9s")
                @Nullable String subscriptionId,
        @Schema(description = "Identifier assigned by Iyzico for the subscription", example = "sub_123456")
                @Nullable String iyzicoSubscriptionId,
        @Schema(description = "Identifier assigned by Iyzico for the customer", example = "cus_123456")
                @Nullable String iyzicoCustomerId,
        @Schema(description = "Optional human readable message", example = "Payment method token generated successfully")
                @Nullable String message,
        @Schema(description = "Whether the created subscription includes a trial", example = "false")
                @Nullable Boolean hasTrial) {

    public enum Status {
        SUCCESS,
        FAILURE
    }
}
