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
    private final LeadActionService leadActionService;
    private final LeadStatusLogRepository leadStatusLogRepository;
    private final LeadActivityLogService leadActivityLogService;

    public LeadController(LeadService leadService, LeadActionService leadActionService, LeadStatusLogRepository leadStatusLogRepository, LeadActivityLogService leadActivityLogService) {
        this.leadService = leadService;
        this.leadActionService = leadActionService;
        this.leadStatusLogRepository = leadStatusLogRepository;
        this.leadActivityLogService = leadActivityLogService;
    }

    // ───────────────────────────────
    // CREATE LEAD
    // ───────────────────────────────
    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'USER', 'SUPER_ADMIN')")
    public ResponseEntity<Lead> createLead(@Valid @RequestBody LeadCreateRequest request,
                                           @AuthenticationPrincipal UserPrincipal currentUser) {
        Lead newLead = leadService.createLead(currentUser.getOrganizationId(), request);
        return new ResponseEntity<>(newLead, HttpStatus.CREATED);
    }

    // ───────────────────────────────
    // GET ALL LEADS (with filters)
    // ───────────────────────────────
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

    // ───────────────────────────────
    // GET SINGLE LEAD BY ID
    // ───────────────────────────────
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'USER', 'SUPER_ADMIN')")
    public ResponseEntity<Lead> getLeadById(@PathVariable UUID id,
                                            @AuthenticationPrincipal UserPrincipal currentUser) {
        Lead lead = leadService.getLeadById(id, currentUser.getOrganizationId());
        return ResponseEntity.ok(lead);
    }

    // ───────────────────────────────
    // UPDATE LEAD (PUT)
    // ───────────────────────────────
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'USER', 'SUPER_ADMIN')")
    public ResponseEntity<Lead> updateLead(@PathVariable UUID id,
                                           @Valid @RequestBody LeadUpdateRequest request,
                                           @AuthenticationPrincipal UserPrincipal currentUser) {
        Lead updatedLead = leadService.updateLead(id, currentUser.getOrganizationId(), request);
        return ResponseEntity.ok(updatedLead);
    }

    // ───────────────────────────────
    // DELETE LEAD
    // ───────────────────────────────
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Void> deleteLead(@PathVariable UUID id,
                                           @AuthenticationPrincipal UserPrincipal currentUser) {
        leadService.deleteLead(id, currentUser.getOrganizationId());
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    // ───────────────────────────────
    // PATCH — UPDATE LEAD STATUS
    // ───────────────────────────────
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyAuthority('USER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Lead> updateLeadStatus(
            @PathVariable UUID id,
            @RequestParam LeadStatus status,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        Lead updated = leadService.updateLeadStatus(id, status, currentUser.getId());
        return ResponseEntity.ok(updated);
    }

    // ───────────────────────────────
    // PATCH — ASSIGN LEAD TO USER
    // ───────────────────────────────
    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Lead> assignLead(@PathVariable UUID id,
                                           @RequestParam(required = false) UUID userId,
                                           @AuthenticationPrincipal UserPrincipal currentUser) {
        Lead updated = leadService.assignLead(id, userId, currentUser.getOrganizationId());
        return ResponseEntity.ok(updated);
    }

    // ───────────────────────────────
    // BULK ASSIGN LEADS
    // ───────────────────────────────
    @PostMapping("/assign/bulk")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Void> bulkAssign(@Valid @RequestBody BulkAssignRequest request,
                                           @AuthenticationPrincipal UserPrincipal currentUser) {
        leadService.bulkAssign(request.getLeadIds(), request.getUserId(), currentUser.getOrganizationId());
        return ResponseEntity.ok().build();
    }

    // ───────────────────────────────
    // GET STATUS LOGS OF A LEAD
    // ───────────────────────────────
    @GetMapping("/{leadId}/status-logs")
    @PreAuthorize("hasAnyAuthority('USER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<LeadStatusLog>> getLeadStatusLogs(@PathVariable UUID leadId) {
        List<LeadStatusLog> logs = leadStatusLogRepository.findByLeadIdOrderByCreatedAtDesc(leadId);
        if (logs.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(logs);
    }

    // ───────────────────────────────
    // IMPORT LEADS (CSV/EXCEL placeholder)
    // ───────────────────────────────
    @PostMapping("/import")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<String> importLeads(@RequestParam("file") MultipartFile file,
                                              @AuthenticationPrincipal UserPrincipal currentUser) {
        // TODO: implement CSV/Excel import
        return ResponseEntity.ok("Lead import initiated for organization: " + currentUser.getOrganizationId());
    }

    // ───────────────────────────────
    // EXPORT LEADS (CSV/EXCEL placeholder)
    // ───────────────────────────────
    @GetMapping("/export")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<byte[]> exportLeads(@AuthenticationPrincipal UserPrincipal currentUser) {
        // TODO: implement export logic
        return ResponseEntity.ok().body(new byte[0]);
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyAuthority('USER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<LeadStatsResponse> getLeadStats(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(required = false) String start,  // ISO-8601, opsiyonel
            @RequestParam(required = false) String end     // ISO-8601, opsiyonel
    ) {
        LeadStatsResponse res = leadService.getDashboardStats(currentUser.getOrganizationId(), start, end);
        return ResponseEntity.ok(res);
    }

    // 🔹 Lead'e yeni log ekleme
    @PostMapping("/{leadId}/logs")
    @PreAuthorize("hasAnyAuthority('USER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<LeadActivityLog> addLeadLog(@PathVariable UUID leadId,
                                                      @RequestBody LeadLogRequest req,
                                                      @AuthenticationPrincipal UserPrincipal currentUser) {
        LeadActivityLog log = leadActivityLogService.addLog(leadId, currentUser.getId(), req);
        return ResponseEntity.ok(log);
    }

    // 🔹 Lead loglarını listeleme
    @GetMapping("/{leadId}/logs")
    @PreAuthorize("hasAnyAuthority('USER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<List<LeadActivityLog>> getLeadLogs(@PathVariable UUID leadId) {
        List<LeadActivityLog> logs = leadActivityLogService.getLogs(leadId);
        return ResponseEntity.ok(logs);
    }

    /**
     * Get action logs for a lead (notes, calls, etc.)
     */
    @GetMapping("/{leadId}/logs")
    @PreAuthorize("hasAnyAuthority('USER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<List<LeadActionResponse>> getLeadLogs(
            @PathVariable UUID leadId,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        List<LeadActionResponse> logs = leadActionService.getActionsForLead(leadId, currentUser.getOrganizationId());
        if (logs.isEmpty()) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(logs);
    }

    /**
     * Add an action log for a lead.
     */
    @PostMapping("/{leadId}/logs")
    @PreAuthorize("hasAnyAuthority('USER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<LeadActionResponse> addLeadLog(
            @PathVariable UUID leadId,
            @Valid @RequestBody LeadActionRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        LeadActionResponse created = leadActionService.createActionForLead(leadId, currentUser.getOrganizationId(), currentUser.getId(), request);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

}
