package com.leadsyncpro.controller;

import com.leadsyncpro.dto.*;
import com.leadsyncpro.model.Lead;
import com.leadsyncpro.model.LeadActivityLog;
import com.leadsyncpro.model.LeadStatus;
import com.leadsyncpro.model.LeadStatusLog;
import com.leadsyncpro.security.UserPrincipal;
import com.leadsyncpro.service.LeadActionService;
import com.leadsyncpro.service.LeadActivityLogService;
import com.leadsyncpro.service.LeadService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/leads")
public class LeadController {

    private final LeadService leadService;
    private final LeadActionService leadActionService;
    private final LeadActivityLogService leadActivityLogService;

    public LeadController(
            LeadService leadService,
            LeadActionService leadActionService,
            LeadActivityLogService leadActivityLogService) {
        this.leadService = leadService;
        this.leadActionService = leadActionService;
        this.leadActivityLogService = leadActivityLogService;
    }

    // --------------------------------------------------------------
    // 🔹 CRUD İşlemleri
    // --------------------------------------------------------------

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'USER', 'SUPER_ADMIN')")
    public ResponseEntity<Page<Lead>> getAllLeads(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(required = false) String campaign,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID assigneeId,
            Pageable pageable // ✅ pagination + sorting otomatik
    ) {
        Page<Lead> leads = leadService.getLeadsByOrganizationPaged(
                currentUser.getOrganizationId(), campaign, status, assigneeId, pageable);
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

        LeadResponse updated = leadService.updateLeadStatus(id, requestedStatus, currentUser.getId(), currentUser.getOrganizationId());
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/{leadId}/status-logs")
    @PreAuthorize("hasAnyAuthority('USER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<LeadStatusLog>> getLeadStatusLogs(@PathVariable UUID leadId,
                                                                 @AuthenticationPrincipal UserPrincipal currentUser) {
        List<LeadStatusLog> logs = leadService.getLeadStatusLogs(leadId, currentUser.getOrganizationId());
        if (logs.isEmpty()) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(logs);
    }

    // --------------------------------------------------------------
    // 🔹 Lead Action Logs (kullanıcı eylemleri)
    // --------------------------------------------------------------

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
        if (actions.isEmpty()) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(actions);
    }

    // --------------------------------------------------------------
    // 🔹 Lead Activity Logs (sistem logları)
    // --------------------------------------------------------------

    @PostMapping("/{leadId}/activity-logs")
    @PreAuthorize("hasAnyAuthority('USER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<LeadActivityLog> addActivityLog(@PathVariable UUID leadId,
                                                          @RequestBody LeadLogRequest req,
                                                          @AuthenticationPrincipal UserPrincipal currentUser) {
        LeadActivityLog log = leadActivityLogService.addLog(leadId, currentUser.getOrganizationId(), currentUser.getId(), req);
        return ResponseEntity.ok(log);
    }

    @GetMapping("/{leadId}/activity-logs")
    @PreAuthorize("hasAnyAuthority('USER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<List<LeadActivityLog>> getActivityLogs(@PathVariable UUID leadId,
                                                                 @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(leadActivityLogService.getLogs(leadId, currentUser.getOrganizationId()));
    }

    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Lead> assignLead(
            @PathVariable UUID id,
            @RequestParam UUID userId,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        Lead updated = leadService.assignLead(id, userId, currentUser.getOrganizationId());
        return ResponseEntity.ok(updated);
    }


}
