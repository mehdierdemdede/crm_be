package com.leadsyncpro.controller;

import com.leadsyncpro.dto.PlanSummaryResponse;
import com.leadsyncpro.model.billing.BillingPeriod;
import com.leadsyncpro.service.PublicPlanService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/plans")
public class PlanController {

    private final PublicPlanService publicPlanService;

    public PlanController(PublicPlanService publicPlanService) {
        this.publicPlanService = publicPlanService;
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<PlanSummaryResponse> listPlans(
            @RequestParam BillingPeriod billingPeriod, @RequestParam(defaultValue = "1") int seatCount) {
        if (seatCount < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seat count must be at least 1");
        }
        return publicPlanService.getPlans(billingPeriod, seatCount);
    }
}
