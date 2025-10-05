package com.leadsyncpro.controller;



import com.leadsyncpro.dto.*;
import com.leadsyncpro.model.Lead;
import com.leadsyncpro.model.LeadActivityLog;
import com.leadsyncpro.model.LeadStatus;
import com.leadsyncpro.model.LeadStatusLog;
import com.leadsyncpro.repository.LeadStatusLogRepository;
import com.leadsyncpro.security.UserPrincipal;
import com.leadsyncpro.service.LeadActionService;
import com.leadsyncpro.service.LeadActivityLogService;
import com.leadsyncpro.service.LeadService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/leads")
public class LeadController {

    private final LeadService leadService;
    private final LeadStatusLogRepository leadStatusLogRepository;
    private final LeadActionService leadActionService;
    private final LeadActivityLogService leadActivityLogService;

    public LeadController(LeadService leadService,
                          LeadStatusLogRepository leadStatusLogRepository,
                          LeadActionService leadActionService,
                          LeadActivityLogService leadActivityLogService) {
        this.leadService = leadService;
        this.leadStatusLogRepository = leadStatusLogRepository;
        this.leadActionService = leadActionService;
        this.leadActivityLogService = leadActivityLogService;
    }

    // 🔹 Create Lead
    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'USER', 'SUPER_ADMIN')")
    public ResponseEntity<Lead> createLead(@Valid @RequestBody LeadCreateRequest request,
                                           @AuthenticationPrincipal UserPrincipal currentUser) {
        Lead newLead = leadService.createLead(currentUser.getOrganizationId(), request);
        return new ResponseEntity<>(newLead, HttpStatus.CREATED);
    }

    // 🔹 Get All Leads
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'USER', 'SUPER_ADMIN')")
    public ResponseEntity<List<Lead>> getAllLeads(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(required = false) String campaign,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID assigneeId) {

        List<Lead> leads = leadService.getLeadsByOrganization(
                currentUser.getOrganizationId(), campaign, status, assigneeId);
        return ResponseEntity.ok(leads);
    }

    // 🔹 Get Single Lead
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'USER', 'SUPER_ADMIN')")
    public ResponseEntity<Lead> getLeadById(@PathVariable UUID id,
                                            @AuthenticationPrincipal UserPrincipal currentUser) {
        Lead lead = leadService.getLeadById(id, currentUser.getOrganizationId());
        return ResponseEntity.ok(lead);
    }

    // 🔹 Update Lead
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'USER', 'SUPER_ADMIN')")
    public ResponseEntity<Lead> updateLead(@PathVariable UUID id,
                                           @Valid @RequestBody LeadUpdateRequest request,
                                           @AuthenticationPrincipal UserPrincipal currentUser) {
        Lead updatedLead = leadService.updateLead(id, currentUser.getOrganizationId(), request);
        return ResponseEntity.ok(updatedLead);
    }

    // 🔹 Delete Lead
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Void> deleteLead(@PathVariable UUID id,
                                           @AuthenticationPrincipal UserPrincipal currentUser) {
        leadService.deleteLead(id, currentUser.getOrganizationId());
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    // 🔹 Import Leads (placeholder)
    @PostMapping("/import")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<String> importLeads(@RequestParam("file") MultipartFile file,
                                              @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok("Lead import initiated for organization: " + currentUser.getOrganizationId());
    }

    // 🔹 Export Leads (placeholder)
    @GetMapping("/export")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<byte[]> exportLeads(@AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok().body(new byte[0]);
    }

    // 🔹 Update Lead Status
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyAuthority('USER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Lead> updateLeadStatus(@PathVariable UUID id,
                                                 @RequestParam LeadStatus status,
                                                 @AuthenticationPrincipal UserPrincipal currentUser) {
        Lead updated = leadService.updateLeadStatus(id, status, currentUser.getId());
        return ResponseEntity.ok(updated);
    }

    // 🔹 Lead Status Logları
    @GetMapping("/{leadId}/status-logs")
    @PreAuthorize("hasAnyAuthority('USER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<LeadStatusLog>> getLeadStatusLogs(@PathVariable UUID leadId) {
        List<LeadStatusLog> logs = leadStatusLogRepository.findByLeadIdOrderByCreatedAtDesc(leadId);
        if (logs.isEmpty()) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(logs);
    }

    // ===================================================================
    // ✅ ACTION LOGS (Agent tarafından yapılan: arama, mesaj, not vb.)
    // ===================================================================

    @PostMapping("/{leadId}/actions")
    @PreAuthorize("hasAnyAuthority('USER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<LeadActionResponse> addLeadAction(
            @PathVariable UUID leadId,
            @Valid @RequestBody LeadActionRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        LeadActionResponse created = leadActionService.createActionForLead(
                leadId, currentUser.getOrganizationId(), currentUser.getId(), request);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @GetMapping("/{leadId}/actions")
    @PreAuthorize("hasAnyAuthority('USER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<List<LeadActionResponse>> getLeadActions(
            @PathVariable UUID leadId,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        List<LeadActionResponse> logs =
                leadActionService.getActionsForLead(leadId, currentUser.getOrganizationId());
        if (logs.isEmpty()) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(logs);
    }

    // ===================================================================
    // ✅ ACTIVITY LOGS (Sistemsel otomatik loglar)
    // ===================================================================

    @PostMapping("/{leadId}/activity-logs")
    @PreAuthorize("hasAnyAuthority('USER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<LeadActivityLog> addActivityLog(
            @PathVariable UUID leadId,
            @RequestBody LeadLogRequest req,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        LeadActivityLog log = leadActivityLogService.addLog(leadId, currentUser.getId(), req);
        return ResponseEntity.ok(log);
    }

    @GetMapping("/{leadId}/activity-logs")
    @PreAuthorize("hasAnyAuthority('USER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<List<LeadActivityLog>> getActivityLogs(@PathVariable UUID leadId) {
        List<LeadActivityLog> logs = leadActivityLogService.getLogs(leadId);
        return ResponseEntity.ok(logs);
    }
}
