package com.leadsyncpro.controller;

import com.leadsyncpro.service.ReportService;
import com.leadsyncpro.service.ReportService.LeadReportResponse;
import com.leadsyncpro.security.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/lead-summary")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<LeadReportResponse> getLeadSummary(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end
    ) {
        UUID orgId = currentUser.getOrganizationId();
        Instant startDate = start != null ? Instant.parse(start) : Instant.now().minus(7, ChronoUnit.DAYS);
        Instant endDate = end != null ? Instant.parse(end) : Instant.now();

        return ResponseEntity.ok(reportService.getLeadReport(orgId, startDate, endDate));
    }
}
