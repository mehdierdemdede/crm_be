package com.leadsyncpro.billing.api;

import com.leadsyncpro.billing.integration.iyzico.IyzicoClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Provides a backwards compatible alias for legacy clients that still call the
 * /api/payments/initialize endpoint while the rest of the platform exposes
 * payment method tokenization under /api/payment-methods/tokenize.
 */
@RestController
@RequestMapping({"/payments", "/api/payments"})
@Validated
@Tag(name = "Payments", description = "Legacy payment initialization endpoints")
public class PaymentController {

    private static final String INITIALIZE_REQUEST_EXAMPLE =
            "{\n"
                    + "  \"planId\": \"5d3c7f8b-8a8c-4f2e-9f8f-9b0c1d2e3f01\",\n"
                    + "  \"billingPeriod\": \"MONTH\",\n"
                    + "  \"seatCount\": 10,\n"
                    + "  \"account\": {\n"
                    + "    \"firstName\": \"Ada\",\n"
                    + "    \"lastName\": \"Lovelace\",\n"
                    + "    \"email\": \"ada@acme.co\",\n"
                    + "    \"password\": \"P@ssw0rd!\",\n"
                    + "    \"phone\": \"+90 555 000 0000\"\n"
                    + "  },\n"
                    + "  \"organization\": {\n"
                    + "    \"organizationName\": \"Acme Inc.\",\n"
                    + "    \"country\": \"TR\",\n"
                    + "    \"taxNumber\": \"1234567890\",\n"
                    + "    \"companySize\": \"51-100\"\n"
                    + "  },\n"
                    + "  \"card\": {\n"
                    + "    \"cardHolderName\": \"Ada Lovelace\",\n"
                    + "    \"cardNumber\": \"5528790000000008\",\n"
                    + "    \"expireMonth\": 1,\n"
                    + "    \"expireYear\": 2026,\n"
                    + "    \"cvc\": \"123\"\n"
                    + "  }\n"
                    + "}";

    private final IyzicoClient iyzicoClient;

    public PaymentController(IyzicoClient iyzicoClient) {
        this.iyzicoClient = iyzicoClient;
    }

    @Operation(
            summary = "Initialize a payment",
            description =
                    "Legacy alias that tokenizes raw card details and returns a single-use token "
                            + "compatible with the old /api/payments/initialize contract.",
            requestBody =
                    @RequestBody(
                            required = true,
                            content =
                                    @Content(
                                            mediaType = "application/json",
                                            schema =
                                                    @Schema(
                                                            implementation =
                                                                    PublicSignupPaymentInitializeRequest.class),
                                            examples =
                                                    @ExampleObject(
                                                            name = "initializePayment",
                                                            value = INITIALIZE_REQUEST_EXAMPLE))),
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Payment initialized successfully",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = PublicSignupPaymentResponse.class))),
                @ApiResponse(responseCode = "400", description = "Invalid card information"),
                @ApiResponse(responseCode = "502", description = "Iyzico gateway error")
            })
    @PostMapping("/initialize")
    @ResponseStatus(HttpStatus.OK)
    public PublicSignupPaymentResponse initialize(
            @Valid
                    @org.springframework.web.bind.annotation.RequestBody
                    PublicSignupPaymentInitializeRequest request) {
        PublicSignupPaymentInitializeRequest.Card card = request.resolvedCard();
        if (card == null) {
            throw new IllegalArgumentException("Card details are required");
        }
        String token = iyzicoClient.tokenizePaymentMethod(
                card.cardHolderName(),
                card.cardNumber(),
                card.formattedExpireMonth(),
                card.formattedExpireYear(),
                card.cvc());
        return new PublicSignupPaymentResponse(
                PublicSignupPaymentResponse.Status.SUCCESS.name(),
                token,
                null,
                null,
                "Payment method token generated successfully",
                Boolean.FALSE);
    }
}
