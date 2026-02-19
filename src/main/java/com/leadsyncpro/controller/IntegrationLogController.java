package com.leadsyncpro.controller;

import com.leadsyncpro.model.IntegrationLog;
import com.leadsyncpro.model.IntegrationPlatform;
import com.leadsyncpro.model.Role;
import com.leadsyncpro.repository.IntegrationLogRepository;
import com.leadsyncpro.security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/integrations/logs")
public class IntegrationLogController {

    private final IntegrationLogRepository integrationLogRepository;

    public IntegrationLogController(IntegrationLogRepository integrationLogRepository) {
        this.integrationLogRepository = integrationLogRepository;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Page<IntegrationLog>> getLogs(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(required = false) IntegrationPlatform platform,
            @RequestParam(required = false) UUID organizationId,
            Pageable pageable) {

        UUID targetOrgId = resolveOrganizationId(currentUser, organizationId);

        // Ensure sorting by startedAt desc if not specified
        if (pageable.getSort().isUnsorted()) {
            pageable = org.springframework.data.domain.PageRequest.of(
                    pageable.getPageNumber(),
                    pageable.getPageSize(),
                    Sort.by(Sort.Direction.DESC, "startedAt"));
        }

        return ResponseEntity.ok(integrationLogRepository.findAllByOrganizationIdAndOptionalPlatform(
                targetOrgId, platform, pageable));
    }

    private UUID resolveOrganizationId(UserPrincipal currentUser, UUID requestedOrganizationId) {
        if (currentUser.getRole() == Role.SUPER_ADMIN && requestedOrganizationId != null) {
            return requestedOrganizationId;
        }
        return currentUser.getOrganizationId();
    }
}
