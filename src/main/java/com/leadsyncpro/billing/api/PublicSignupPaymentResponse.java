package com.leadsyncpro.billing.api;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@Schema(name = "PublicSignupPaymentResponse")
@JsonInclude(Include.NON_NULL)
public record PublicSignupPaymentResponse(
        @Schema(description = "Result status of the payment initialization", example = "SUCCESS")
                String status,
        @Schema(description = "Identifier for the created subscription or token", example = "tok_1Nv0a8YgL9s")
                String subscriptionId,
        @Schema(description = "Identifier assigned by Iyzico for the subscription", example = "sub_123456")
                String iyzicoSubscriptionId,
        @Schema(description = "Identifier assigned by Iyzico for the customer", example = "cus_123456")
                String iyzicoCustomerId,
        @Schema(description = "Optional human readable message", example = "Payment method token generated successfully")
                String message,
        @Schema(description = "Whether the created subscription includes a trial", example = "false")
                Boolean hasTrial) {

    public enum Status {
        SUCCESS,
        FAILURE
    }
}
