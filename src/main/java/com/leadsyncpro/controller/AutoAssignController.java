package com.leadsyncpro.controller;

import com.leadsyncpro.security.UserPrincipal;
import com.leadsyncpro.service.AutoAssignService;
import com.leadsyncpro.service.AutoAssignService.AgentStatsResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/auto-assign")
public class AutoAssignController {

    private final AutoAssignService autoAssignService;

    public AutoAssignController(AutoAssignService autoAssignService) {
        this.autoAssignService = autoAssignService;
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyAuthority('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<List<AgentStatsResponse>> getAutoAssignStats(
            @AuthenticationPrincipal UserPrincipal currentUser) {

        UUID orgId = currentUser.getOrganizationId();
        return ResponseEntity.ok(autoAssignService.getAgentStats(orgId));
    }
}
