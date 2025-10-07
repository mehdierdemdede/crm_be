package com.leadsyncpro.controller;

import com.leadsyncpro.dto.SaleRequest;
import com.leadsyncpro.dto.SaleResponse;
import com.leadsyncpro.security.UserPrincipal;
import com.leadsyncpro.service.SalesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
public class SalesController {

    private final SalesService salesService;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN','USER','SUPER_ADMIN')")
    public ResponseEntity<SaleResponse> createSale(
            @Valid @RequestBody SaleRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        SaleResponse response = salesService.createSale(
                request, currentUser.getOrganizationId(), currentUser.getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
