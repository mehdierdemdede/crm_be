package com.leadsyncpro.controller;

import com.leadsyncpro.dto.SaleRequest;
import com.leadsyncpro.dto.SaleResponse;
import com.leadsyncpro.security.UserPrincipal;
import com.leadsyncpro.service.SalesService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
public class SalesController {

    private final SalesService salesService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN','USER','SUPER_ADMIN')")
    public ResponseEntity<?> getAllSales(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(required = false) UUID leadId,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            org.springframework.data.domain.Pageable pageable) {

        if (leadId != null) {
            return ResponseEntity.ok(salesService.getSalesByLead(leadId, currentUser.getOrganizationId()));
        }

        if (startDate != null && endDate != null) {
            java.time.Instant start = java.time.Instant.parse(startDate);
            java.time.Instant end = java.time.Instant.parse(endDate);

            // Allow filtering by specific user if provided, otherwise show all (if
            // permitted)
            // If current user is restricted (e.g. USER role), they might only see their own
            // sales.
            // For now, we trust the service to handle data scoping via organizationId.
            // But strict RBAC would enforce userId = currentUser.getId() for regular users.
            UUID targetUserId = userId;

            // Simple check: if not admin/super_admin, maybe force own id?
            // Assuming for now that we want to View Member Details, so we pass that
            // member's ID.

            return ResponseEntity
                    .ok(salesService.getSalesByDateRange(currentUser.getOrganizationId(), targetUserId, start, end));
        }

        return ResponseEntity.ok(salesService.getAllSales(currentUser.getOrganizationId(), pageable));
    }

    /**
     * 💾 Satış kaydı oluştur (belge yükleme opsiyonel)
     */
    @PostMapping(consumes = { "multipart/form-data" })
    @PreAuthorize("hasAnyAuthority('ADMIN','USER','SUPER_ADMIN')")
    public ResponseEntity<SaleResponse> createSale(
            @RequestPart("data") SaleRequest req,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        SaleResponse created = salesService.createSale(
                req, file, currentUser.getOrganizationId(), currentUser.getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping(consumes = { "application/json" })
    @PreAuthorize("hasAnyAuthority('ADMIN','USER','SUPER_ADMIN')")
    public ResponseEntity<SaleResponse> createSaleJson(
            @RequestBody SaleRequest req,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        SaleResponse created = salesService.createSale(
                req, null, currentUser.getOrganizationId(), currentUser.getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{saleId}")
    @PreAuthorize("hasAnyAuthority('ADMIN','USER','SUPER_ADMIN')")
    public ResponseEntity<SaleResponse> getSaleById(@PathVariable UUID saleId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        SaleResponse response = salesService.getSaleById(saleId, currentUser.getOrganizationId());
        return ResponseEntity.ok(response);
    }
}
