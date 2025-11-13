package com.leadsyncpro.billing.api;

import com.leadsyncpro.billing.api.CreatePlanRequest.Metadata;
import com.leadsyncpro.billing.service.CreatePlanCommand;
import com.leadsyncpro.billing.service.PlanManagementService;
import com.leadsyncpro.model.billing.Plan;
import com.leadsyncpro.model.billing.Price;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/billing/plans", "/api/billing/plans"})
@Validated
@Tag(name = "Plan Management", description = "Admin endpoints for managing subscription plans")
public class PlanManagementController {

    private final PlanManagementService planManagementService;

    public PlanManagementController(PlanManagementService planManagementService) {
        this.planManagementService = planManagementService;
    }

    @Operation(
            summary = "Create a plan",
            description = "Creates a new plan definition together with its public pricing options.",
            responses = {
                @ApiResponse(
                        responseCode = "201",
                        description = "Plan created",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = PlanResponse.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Invalid payload",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))),
                @ApiResponse(
                        responseCode = "403",
                        description = "Forbidden",
                        content = @Content(mediaType = "application/json")),
                @ApiResponse(
                        responseCode = "409",
                        description = "Conflicting plan or price identifier",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class)))
            })
    @PostMapping
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanResponse createPlan(@Valid @RequestBody CreatePlanRequest request) {
        Plan plan = planManagementService.createPlan(toCommand(request));
        return toPlanResponse(plan);
    }

    private CreatePlanCommand toCommand(CreatePlanRequest request) {
        Metadata metadataRequest = request.metadata();
        CreatePlanCommand.PlanMetadata metadata = metadataRequest == null
                ? null
                : new CreatePlanCommand.PlanMetadata(
                        metadataRequest.basePrice(),
                        metadataRequest.perSeatPrice(),
                        metadataRequest.basePriceMonth(),
                        metadataRequest.perSeatPriceMonth(),
                        metadataRequest.basePriceYear(),
                        metadataRequest.perSeatPriceYear());

        List<CreatePlanCommand.PlanPriceDefinition> prices = request.prices().stream()
                .map(this::toPriceDefinition)
                .collect(Collectors.toList());

        return new CreatePlanCommand(
                request.id(),
                request.name(),
                request.description(),
                request.features(),
                metadata,
                prices);
    }

    private CreatePlanCommand.PlanPriceDefinition toPriceDefinition(CreatePlanRequest.Price price) {
        return new CreatePlanCommand.PlanPriceDefinition(
                price.id(),
                price.amount(),
                price.currency(),
                price.billingPeriod(),
                price.seatLimit(),
                price.trialDays());
    }

    private PlanResponse toPlanResponse(Plan plan) {
        List<String> features = plan.getFeatures() != null ? plan.getFeatures() : Collections.emptyList();
        Map<String, Object> metadata = plan.getMetadata() != null ? plan.getMetadata() : Collections.emptyMap();
        List<PlanPriceResponse> prices = plan.getPrices() != null
                ? plan.getPrices().stream().map(this::toPriceResponse).collect(Collectors.toList())
                : Collections.emptyList();
        return new PlanResponse(
                plan.getId(),
                plan.getCode(),
                plan.getName(),
                plan.getDescription(),
                features,
                metadata,
                prices);
    }

    private PlanPriceResponse toPriceResponse(Price price) {
        return new PlanPriceResponse(
                price.getId(),
                centsToCurrency(price.getPerSeatAmountCents()),
                price.getCurrency(),
                price.getBillingPeriod(),
                price.getSeatLimit(),
                price.getTrialDays());
    }

    private BigDecimal centsToCurrency(Long cents) {
        if (cents == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(cents, 2);
    }
}
