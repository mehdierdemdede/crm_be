package com.leadsyncpro.billing.api;

import com.leadsyncpro.billing.facade.ChangePlanCmd;
import com.leadsyncpro.billing.facade.CreateSubscriptionCmd;
import com.leadsyncpro.billing.facade.InvoiceDto;
import com.leadsyncpro.billing.facade.Proration;
import com.leadsyncpro.billing.facade.SubscriptionDto;
import com.leadsyncpro.billing.facade.SubscriptionFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/billing")
@Validated
@Tag(name = "Billing", description = "Subscription lifecycle management endpoints")
public class BillingController {

    private static final String SUBSCRIPTION_RESPONSE_EXAMPLE =
            "{\n"
                    + "  \"id\": \"5bb1d1dc-7f8a-4f1d-9c4e-0f5e3b2a1c4d\",\n"
                    + "  \"customerId\": \"8c55f5a2-3c80-4aa5-8d3b-1c8b5d9f2c0a\",\n"
                    + "  \"planCode\": \"PRO\",\n"
                    + "  \"billingPeriod\": \"MONTH\",\n"
                    + "  \"status\": \"ACTIVE\",\n"
                    + "  \"startAt\": \"2024-01-01T00:00:00Z\",\n"
                    + "  \"currentPeriodStart\": \"2024-01-01T00:00:00Z\",\n"
                    + "  \"currentPeriodEnd\": \"2024-01-31T23:59:59Z\",\n"
                    + "  \"trialEndAt\": \"2024-01-14T00:00:00Z\",\n"
                    + "  \"cancelAtPeriodEnd\": false,\n"
                    + "  \"seatCount\": 10,\n"
                    + "  \"currency\": \"USD\",\n"
                    + "  \"externalSubscriptionId\": \"sub_37f184d2f1\"\n"
                    + "}";

    private static final String CREATE_SUBSCRIPTION_REQUEST_EXAMPLE =
            "{\n"
                    + "  \"customerId\": \"8c55f5a2-3c80-4aa5-8d3b-1c8b5d9f2c0a\",\n"
                    + "  \"planCode\": \"PRO\",\n"
                    + "  \"billingPeriod\": \"MONTH\",\n"
                    + "  \"seatCount\": 10,\n"
                    + "  \"trialDays\": 14,\n"
                    + "  \"cardToken\": \"tok_1Nv0a8YgL9s\"\n"
                    + "}";

    private static final String CHANGE_PLAN_REQUEST_EXAMPLE =
            "{\n"
                    + "  \"planCode\": \"ENTERPRISE\",\n"
                    + "  \"billingPeriod\": \"YEAR\",\n"
                    + "  \"proration\": \"IMMEDIATE\"\n"
                    + "}";

    private static final String UPDATE_SEATS_REQUEST_EXAMPLE =
            "{\n"
                    + "  \"seatCount\": 25,\n"
                    + "  \"proration\": \"DEFERRED\"\n"
                    + "}";

    private static final String CANCEL_SUBSCRIPTION_REQUEST_EXAMPLE =
            "{\n  \"cancelAtPeriodEnd\": true\n}";

    private final SubscriptionFacade subscriptionFacade;

    public BillingController(SubscriptionFacade subscriptionFacade) {
        this.subscriptionFacade = subscriptionFacade;
    }

    @Operation(
            summary = "Create a new subscription",
            description =
                    "Creates a subscription for the given customer by selecting the plan, billing period, "
                            + "seat count and optional trial period.",
            requestBody =
                    @RequestBody(
                            required = true,
                            content =
                                    @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = CreateSubscriptionRequest.class),
                                            examples =
                                                    @ExampleObject(
                                                            name = "createSubscription",
                                                            value = CREATE_SUBSCRIPTION_REQUEST_EXAMPLE))),
            responses = {
                @ApiResponse(
                        responseCode = "201",
                        description = "Subscription successfully created",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = SubscriptionResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "subscription",
                                                        value = SUBSCRIPTION_RESPONSE_EXAMPLE))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Validation or business rule violation",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ProblemDetail.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Referenced customer or plan not found",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ProblemDetail.class)))
            })
    @PostMapping("/subscriptions")
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionResponse createSubscription(
            @Valid @org.springframework.web.bind.annotation.RequestBody CreateSubscriptionRequest request) {
        SubscriptionDto dto = subscriptionFacade.createSubscription(new CreateSubscriptionCmd(
                request.customerId(),
                request.planCode(),
                request.billingPeriod(),
                request.seatCount(),
                request.trialDays(),
                request.cardToken()));
        return toResponse(dto);
    }

    @Operation(
            summary = "Change the plan of a subscription",
            description = "Transitions a subscription to a different plan and billing period.",
            requestBody =
                    @RequestBody(
                            required = true,
                            content =
                                    @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = ChangePlanRequest.class),
                                            examples =
                                                    @ExampleObject(
                                                            name = "changePlan",
                                                            value = CHANGE_PLAN_REQUEST_EXAMPLE))),
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Plan successfully changed",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = SubscriptionResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "subscription",
                                                        value = SUBSCRIPTION_RESPONSE_EXAMPLE))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Business rule violation",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ProblemDetail.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Subscription or plan not found",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ProblemDetail.class)))
            })
    @PostMapping("/subscriptions/{id}/change-plan")
    public SubscriptionResponse changePlan(
            @PathVariable UUID id,
            @Valid @org.springframework.web.bind.annotation.RequestBody ChangePlanRequest request) {
        SubscriptionDto dto = subscriptionFacade.changePlan(
                id,
                new ChangePlanCmd(
                        request.planCode(),
                        request.billingPeriod(),
                        request.proration() != null ? request.proration() : Proration.defaultValue()));
        return toResponse(dto);
    }

    @Operation(
            summary = "Update subscription seat count",
            description = "Updates the number of seats allocated to the subscription and optionally controls proration.",
            requestBody =
                    @RequestBody(
                            required = true,
                            content =
                                    @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = UpdateSeatCountRequest.class),
                                            examples =
                                                    @ExampleObject(
                                                            name = "updateSeats",
                                                            value = UPDATE_SEATS_REQUEST_EXAMPLE))),
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Seat count updated",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = SubscriptionResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "subscription",
                                                        value = SUBSCRIPTION_RESPONSE_EXAMPLE))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Business rule violation",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ProblemDetail.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Subscription not found",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ProblemDetail.class)))
            })
    @PostMapping("/subscriptions/{id}/seats")
    public SubscriptionResponse updateSeatCount(
            @PathVariable UUID id,
            @Valid @org.springframework.web.bind.annotation.RequestBody UpdateSeatCountRequest request) {
        Proration proration = request.proration() != null ? request.proration() : Proration.defaultValue();
        SubscriptionDto dto = subscriptionFacade.updateSeats(id, request.seatCount(), proration);
        return toResponse(dto);
    }

    @Operation(
            summary = "Cancel subscription",
            description =
                    "Cancels a subscription immediately or marks it to be canceled at the end of the current period.",
            requestBody =
                    @RequestBody(
                            required = true,
                            content =
                                    @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = CancelSubscriptionRequest.class),
                                            examples =
                                                    @ExampleObject(
                                                            name = "cancelSubscription",
                                                            value = CANCEL_SUBSCRIPTION_REQUEST_EXAMPLE))),
            responses = {
                @ApiResponse(responseCode = "204", description = "Subscription canceled"),
                @ApiResponse(
                        responseCode = "400",
                        description = "Business rule violation",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ProblemDetail.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Subscription not found",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ProblemDetail.class)))
            })
    @PostMapping("/subscriptions/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelSubscription(
            @PathVariable UUID id,
            @Valid @org.springframework.web.bind.annotation.RequestBody CancelSubscriptionRequest request) {
        subscriptionFacade.cancel(id, request.cancelAtPeriodEnd());
    }

    @Operation(
            summary = "Get subscription details",
            description = "Retrieves the latest state of a subscription by its identifier.",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Subscription found",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = SubscriptionResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "subscription",
                                                        value = SUBSCRIPTION_RESPONSE_EXAMPLE))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Subscription not found",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ProblemDetail.class)))
            })
    @GetMapping("/subscriptions/{id}")
    public SubscriptionResponse getSubscription(@PathVariable UUID id) {
        SubscriptionDto dto = subscriptionFacade.getSubscription(id);
        return toResponse(dto);
    }

    @Operation(
            summary = "List customer subscriptions",
            description = "Returns all subscriptions that belong to the provided customer identifier.",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Subscriptions returned",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = SubscriptionResponse.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Customer not found",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ProblemDetail.class)))
            })
    @GetMapping("/customers/{id}/subscriptions")
    public List<SubscriptionResponse> listCustomerSubscriptions(@PathVariable UUID id) {
        return subscriptionFacade.getSubscriptionsByCustomer(id).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Operation(
            summary = "List customer invoices",
            description = "Returns invoices associated with all subscriptions of the specified customer.",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Invoices returned",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = InvoiceResponse.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Customer not found",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ProblemDetail.class)))
            })
    @GetMapping("/customers/{id}/invoices")
    public List<InvoiceResponse> listCustomerInvoices(@PathVariable UUID id) {
        return subscriptionFacade.getInvoicesByCustomer(id).stream()
                .map(this::toInvoiceResponse)
                .collect(Collectors.toList());
    }

    private SubscriptionResponse toResponse(SubscriptionDto dto) {
        return new SubscriptionResponse(
                dto.id(),
                dto.customerId(),
                dto.planCode(),
                dto.billingPeriod(),
                dto.status(),
                dto.startAt(),
                dto.currentPeriodStart(),
                dto.currentPeriodEnd(),
                dto.trialEndAt(),
                dto.cancelAtPeriodEnd(),
                dto.seatCount(),
                dto.currency(),
                dto.externalSubscriptionId());
    }

    private InvoiceResponse toInvoiceResponse(InvoiceDto dto) {
        return new InvoiceResponse(
                dto.id(),
                dto.subscriptionId(),
                dto.periodStart(),
                dto.periodEnd(),
                dto.totalCents(),
                dto.currency(),
                dto.status());
    }
}
