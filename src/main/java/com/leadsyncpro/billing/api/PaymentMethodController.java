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

@RestController
@RequestMapping({"/payment-methods", "/api/payment-methods"})
@Validated
@Tag(name = "Payment Methods", description = "Operations related to payment method tokenization")
public class PaymentMethodController {

    private static final String TOKENIZE_REQUEST_EXAMPLE =
            "{\n"
                    + "  \"cardHolderName\": \"Ada Lovelace\",\n"
                    + "  \"cardNumber\": \"5528790000000008\",\n"
                    + "  \"expireMonth\": \"01\",\n"
                    + "  \"expireYear\": \"2026\",\n"
                    + "  \"cvc\": \"123\"\n"
                    + "}";

    private final IyzicoClient iyzicoClient;

    public PaymentMethodController(IyzicoClient iyzicoClient) {
        this.iyzicoClient = iyzicoClient;
    }

    @Operation(
            summary = "Tokenize card information",
            description = "Sends raw card details to Iyzico and returns a single-use token.",
            requestBody =
                    @RequestBody(
                            required = true,
                            content =
                                    @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = TokenizePaymentMethodRequest.class),
                                            examples =
                                                    @ExampleObject(
                                                            name = "tokenizePaymentMethod",
                                                            value = TOKENIZE_REQUEST_EXAMPLE))),
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Card tokenized successfully",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = TokenizePaymentMethodResponse.class))),
                @ApiResponse(responseCode = "400", description = "Invalid card information"),
                @ApiResponse(responseCode = "502", description = "Iyzico gateway error")
            })
    @PostMapping("/tokenize")
    @ResponseStatus(HttpStatus.OK)
    public TokenizePaymentMethodResponse tokenize(
            @Valid @org.springframework.web.bind.annotation.RequestBody TokenizePaymentMethodRequest request) {
        String token = iyzicoClient.tokenizePaymentMethod(
                request.cardHolderName(),
                request.cardNumber(),
                request.expireMonth(),
                request.expireYear(),
                request.cvc());
        return new TokenizePaymentMethodResponse(token);
    }
}
