package com.leadsyncpro.controller;

import com.leadsyncpro.dto.LeadCreateRequest;
import com.leadsyncpro.dto.LeadUpdateRequest;
import com.leadsyncpro.model.Lead;
import com.leadsyncpro.service.LeadService;
import com.leadsyncpro.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/leads")
public class LeadController {

    private final LeadService leadService;

    public LeadController(LeadService leadService) {
        this.leadService = leadService;
    }

    // Create Lead
    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'USER', 'SUPER_ADMIN')")
    public ResponseEntity<Lead> createLead(@Valid @RequestBody LeadCreateRequest request,
                                           @AuthenticationPrincipal UserPrincipal currentUser) {
        Lead newLead = leadService.createLead(currentUser.getOrganizationId(), request);
        return new ResponseEntity<>(newLead, HttpStatus.CREATED);
    }

    // Get all Leads for current organization with filters
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

    // Get single Lead by ID
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'USER', 'SUPER_ADMIN')")
    public ResponseEntity<Lead> getLeadById(@PathVariable UUID id,
                                            @AuthenticationPrincipal UserPrincipal currentUser) {
        Lead lead = leadService.getLeadById(id, currentUser.getOrganizationId());
        return ResponseEntity.ok(lead);
    }

    // Update Lead
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'USER', 'SUPER_ADMIN')")
    public ResponseEntity<Lead> updateLead(@PathVariable UUID id,
                                           @Valid @RequestBody LeadUpdateRequest request,
                                           @AuthenticationPrincipal UserPrincipal currentUser) {
        Lead updatedLead = leadService.updateLead(id, currentUser.getOrganizationId(), request);
        return ResponseEntity.ok(updatedLead);
    }

    // Delete Lead
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')") // Only Admin/SuperAdmin can delete
    public ResponseEntity<Void> deleteLead(@PathVariable UUID id,
                                           @AuthenticationPrincipal UserPrincipal currentUser) {
        leadService.deleteLead(id, currentUser.getOrganizationId());
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    // Import Leads (Placeholder)
    @PostMapping("/import")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<String> importLeads(@RequestParam("file") MultipartFile file,
                                              @AuthenticationPrincipal UserPrincipal currentUser) {
        // Implement CSV/Excel parsing and saving leads to DB
        // Ensure each imported lead gets the correct organization_id
        return ResponseEntity.ok("Lead import initiated for organization: " + currentUser.getOrganizationId());
    }

    // Export Leads (Placeholder)
    @GetMapping("/export")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<byte[]> exportLeads(@AuthenticationPrincipal UserPrincipal currentUser) {
        // Implement fetching leads and generating CSV/Excel
        // Ensure only leads for the current organization are exported
        return ResponseEntity.ok().body(new byte[0]); // Return actual file bytes
    }
}
