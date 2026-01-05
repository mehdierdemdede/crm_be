package com.leadsyncpro.controller;

import com.leadsyncpro.dto.*;
import com.leadsyncpro.model.Lead;
import com.leadsyncpro.security.UserPrincipal;
import com.leadsyncpro.service.LeadActionService;
import com.leadsyncpro.service.LeadService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/leads")
public class LeadController {

    private final LeadService leadService;
    private final LeadActionService leadActionService;

    public LeadController(
            LeadService leadService,
            LeadActionService leadActionService) {
        this.leadService = leadService;
        this.leadActionService = leadActionService;
    }

    // --------------------------------------------------------------
    // 🔹 CRUD İşlemleri
    // --------------------------------------------------------------

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'USER', 'SUPER_ADMIN')")
    public ResponseEntity<Page<Lead>> getAllLeads(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String campaignId,
            @RequestParam(required = false) UUID assignedUserId,
            @RequestParam(required = false) Boolean unassigned,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            Pageable pageable // ✅ pagination + sorting otomatik
    ) {
        Page<Lead> leads = leadService.getLeadsByOrganizationPaged(
                currentUser.getOrganizationId(),
                search,
                status,
                language,
                campaignId,
                assignedUserId,
                unassigned,
                startDate,
                endDate,
                pageable);
        return ResponseEntity.ok(leads);
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'USER', 'SUPER_ADMIN')")
    public ResponseEntity<Lead> createLead(@Valid @RequestBody LeadCreateRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        Lead newLead = leadService.createLead(currentUser.getOrganizationId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(newLead);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'USER', 'SUPER_ADMIN')")
    public ResponseEntity<LeadResponse> getLeadById(@PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(leadService.getLeadById(id, currentUser.getOrganizationId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'USER', 'SUPER_ADMIN')")
    public ResponseEntity<Lead> updateLead(@PathVariable UUID id,
            @Valid @RequestBody LeadUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        Lead updated = leadService.updateLead(id, currentUser.getOrganizationId(), request);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Void> deleteLead(@PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        leadService.deleteLead(id, currentUser.getOrganizationId());
        return ResponseEntity.noContent().build();
    }

    // --------------------------------------------------------------
    // 🔹 Status Güncelleme ve Logları
    // --------------------------------------------------------------

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyAuthority('USER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<LeadResponse> updateLeadStatus(@PathVariable UUID id,
            @RequestParam(value = "status", required = false) String statusParam,
            @RequestBody(required = false) LeadStatusUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        String requestedStatus = null;
        if (request != null && request.getStatus() != null && !request.getStatus().isBlank()) {
            requestedStatus = request.getStatus();
        }
        if (requestedStatus == null && statusParam != null && !statusParam.isBlank()) {
            requestedStatus = statusParam;
        }

        if (requestedStatus == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lead status is required");
        }

        LeadResponse updated = leadService.updateLeadStatus(id, requestedStatus, currentUser.getId(),
                currentUser.getOrganizationId());
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{leadId}/actions")
    @PreAuthorize("hasAnyAuthority('USER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<LeadActionResponse> addLeadAction(@PathVariable UUID leadId,
            @Valid @RequestBody LeadActionRequest req,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        LeadActionResponse created = leadActionService.createActionForLead(
                leadId, currentUser.getOrganizationId(), currentUser.getId(), req);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{leadId}/actions")
    @PreAuthorize("hasAnyAuthority('USER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<List<LeadActionResponse>> getLeadActions(@PathVariable UUID leadId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        List<LeadActionResponse> actions = leadActionService.getActionsForLead(
                leadId, currentUser.getOrganizationId());
        if (actions.isEmpty())
            return ResponseEntity.noContent().build();
        return ResponseEntity.ok(actions);
    }

    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Lead> assignLead(
            @PathVariable UUID id,
            @RequestParam(value = "userId", required = false) String userIdParam,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        UUID userId = null;
        if (StringUtils.hasText(userIdParam)) {
            userId = UUID.fromString(userIdParam);
        }

        Lead updated = leadService.assignLead(id, userId, currentUser.getOrganizationId());
        return ResponseEntity.ok(updated);
    }

}
