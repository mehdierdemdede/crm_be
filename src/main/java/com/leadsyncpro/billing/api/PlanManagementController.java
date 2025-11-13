package com.leadsyncpro.billing.api;

import com.leadsyncpro.billing.service.CreatePlanCommand;
import com.leadsyncpro.billing.service.PlanManagementService;
import com.leadsyncpro.model.billing.Plan;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
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
            description = "Creates a new plan entry that can later be priced and exposed publicly.",
            responses = {
                @ApiResponse(
                        responseCode = "201",
                        description = "Plan created",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = PlanResponse.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Invalid payload or duplicate plan code",
                        content = @Content(mediaType = "application/json"))
            })
    @PostMapping
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanResponse createPlan(@Valid @org.springframework.web.bind.annotation.RequestBody CreatePlanRequest request) {
        Plan plan = planManagementService.createPlan(new CreatePlanCommand(
                request.code(),
                request.name(),
                request.description(),
                request.features(),
                request.metadata()));
        return toPlanResponse(plan);
    }

    private PlanResponse toPlanResponse(Plan plan) {
        List<String> features = plan.getFeatures() != null ? plan.getFeatures() : Collections.emptyList();
        Map<String, Object> metadata = plan.getMetadata() != null ? plan.getMetadata() : Collections.emptyMap();
        List<PlanPriceResponse> prices = List.of();
        return new PlanResponse(
                plan.getId(),
                plan.getCode(),
                plan.getName(),
                plan.getDescription(),
                features,
                metadata,
                prices);
    }
}
