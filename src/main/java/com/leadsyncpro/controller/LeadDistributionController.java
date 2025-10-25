package com.leadsyncpro.controller;

import com.leadsyncpro.dto.FacebookAdHierarchyResponse;
import com.leadsyncpro.dto.LeadDistributionRuleRequest;
import com.leadsyncpro.dto.LeadDistributionRuleResponse;
import com.leadsyncpro.model.Role;
import com.leadsyncpro.security.UserPrincipal;
import com.leadsyncpro.service.LeadDistributionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/lead-distribution/facebook")
public class LeadDistributionController {

    private final LeadDistributionService leadDistributionService;

    public LeadDistributionController(LeadDistributionService leadDistributionService) {
        this.leadDistributionService = leadDistributionService;
    }

    @GetMapping("/tree")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<FacebookAdHierarchyResponse> getHierarchy(@AuthenticationPrincipal UserPrincipal currentUser) {
        UUID organizationId = currentUser.getOrganizationId();
        if (currentUser.getRole() == Role.SUPER_ADMIN && organizationId == null) {
            throw new IllegalArgumentException("Super Admin must specify an organization to view distribution hierarchy.");
        }
        FacebookAdHierarchyResponse response = leadDistributionService.getFacebookHierarchy(organizationId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/rules")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<LeadDistributionRuleResponse>> listRules(@AuthenticationPrincipal UserPrincipal currentUser) {
        UUID organizationId = currentUser.getOrganizationId();
        if (currentUser.getRole() == Role.SUPER_ADMIN && organizationId == null) {
            throw new IllegalArgumentException("Super Admin must specify an organization to view distribution rules.");
        }
        List<LeadDistributionRuleResponse> responses = leadDistributionService.listFacebookRules(organizationId);
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/rules")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<LeadDistributionRuleResponse> upsertRule(@AuthenticationPrincipal UserPrincipal currentUser,
                                                                   @RequestBody LeadDistributionRuleRequest request) {
        UUID organizationId = currentUser.getOrganizationId();
        if (currentUser.getRole() == Role.SUPER_ADMIN && organizationId == null) {
            throw new IllegalArgumentException("Super Admin must specify an organization to save distribution rules.");
        }
        LeadDistributionRuleResponse response = leadDistributionService.upsertFacebookRule(organizationId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/rules/{ruleId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Void> deleteRule(@AuthenticationPrincipal UserPrincipal currentUser,
                                           @PathVariable UUID ruleId) {
        UUID organizationId = currentUser.getOrganizationId();
        if (currentUser.getRole() == Role.SUPER_ADMIN && organizationId == null) {
            throw new IllegalArgumentException("Super Admin must specify an organization to delete distribution rules.");
        }
        leadDistributionService.deleteRule(organizationId, ruleId);
        return ResponseEntity.noContent().build();
    }
}

