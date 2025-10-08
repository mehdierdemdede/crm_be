package com.leadsyncpro.controller;

import com.leadsyncpro.exception.ResourceNotFoundException;
import com.leadsyncpro.model.*;
import com.leadsyncpro.repository.IntegrationLogRepository;
import com.leadsyncpro.security.UserPrincipal;
import com.leadsyncpro.service.IntegrationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/integrations")
public class IntegrationController {

    private final IntegrationService integrationService;
    private final IntegrationLogRepository integrationLogRepository;

    @Value("${app.oauth2.frontend-success-redirect-url}")
    private String frontendSuccessRedirectUrl;

    @Value("${app.oauth2.frontend-error-redirect-url}")
    private String frontendErrorRedirectUrl;

    public IntegrationController(IntegrationService integrationService,
                                 IntegrationLogRepository integrationLogRepository) {
        this.integrationService = integrationService;
        this.integrationLogRepository = integrationLogRepository;
    }

    @GetMapping("/oauth2/authorize/{registrationId}")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<String> authorizeIntegration(@PathVariable String registrationId,
                                                       @AuthenticationPrincipal UserPrincipal currentUser) {
        UUID organizationId = currentUser.getOrganizationId();
        if (organizationId == null) {
            throw new IllegalArgumentException("Super Admin must specify an organization to integrate.");
        }
        String authorizationUrl = integrationService.getAuthorizationUrl(registrationId, organizationId, currentUser.getId());
        return ResponseEntity.ok(authorizationUrl);
    }

    @GetMapping("/oauth2/callback/{registrationId}")
    public RedirectView oauth2Callback(@PathVariable String registrationId,
                                       @RequestParam String code,
                                       @RequestParam String state,
                                       @RequestParam(required = false) String error) {
        if (error != null) {
            return new RedirectView(frontendErrorRedirectUrl + "?message=OAuth2_Error&details=" + error);
        }
        try {
            integrationService.handleOAuth2Callback(registrationId, code, state);
            return new RedirectView(frontendSuccessRedirectUrl);
        } catch (Exception e) {
            return new RedirectView(frontendErrorRedirectUrl + "?message=Integration_Failed&details=" + e.getMessage());
        }
    }

    @GetMapping("/oauth2/test-success")
    public ResponseEntity<String> oauth2TestSuccess() {
        return ResponseEntity.ok("OAuth2 entegrasyonu backend tarafında BAŞARILI oldu! Frontend'e yönlendirildiniz.");
    }

    @GetMapping("/oauth2/test-error")
    public ResponseEntity<String> oauth2TestError(@RequestParam String message, @RequestParam String details) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("OAuth2 entegrasyonu backend tarafında HATA verdi: " + message + " - Detaylar: " + details);
    }

    @GetMapping("/{platform}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<IntegrationConfig> getIntegration(@PathVariable String platform,
                                                            @AuthenticationPrincipal UserPrincipal currentUser) {
        UUID organizationId = currentUser.getOrganizationId();
        if (currentUser.getRole() == Role.SUPER_ADMIN && organizationId == null) {
            throw new IllegalArgumentException("Super Admin must specify organization ID to retrieve integration config.");
        }

        IntegrationPlatform integrationPlatform = IntegrationPlatform.valueOf(platform.toUpperCase());
        IntegrationConfig config = integrationService.getIntegrationConfig(organizationId, integrationPlatform)
                .orElseThrow(() -> new ResourceNotFoundException("Integration not found for platform " + platform));
        return ResponseEntity.ok(config);
    }

    @DeleteMapping("/{platform}")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<Void> deleteIntegration(@PathVariable String platform,
                                                  @AuthenticationPrincipal UserPrincipal currentUser) {
        UUID organizationId = currentUser.getOrganizationId();
        if (currentUser.getRole() == Role.SUPER_ADMIN && organizationId == null) {
            throw new IllegalArgumentException("Super Admin must specify organization ID to delete integration config.");
        }

        IntegrationPlatform integrationPlatform = IntegrationPlatform.valueOf(platform.toUpperCase());
        integrationService.deleteIntegrationConfig(organizationId, integrationPlatform);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/fetch-leads/{platform}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<String> fetchLeadsManually(@PathVariable String platform,
                                                     @AuthenticationPrincipal UserPrincipal currentUser) {
        UUID organizationId = currentUser.getOrganizationId();
        if (currentUser.getRole() == Role.SUPER_ADMIN && organizationId == null) {
            throw new IllegalArgumentException("Super Admin must specify organization ID to fetch leads.");
        }

        IntegrationPlatform integrationPlatform = IntegrationPlatform.valueOf(platform.toUpperCase());
        if (integrationPlatform == IntegrationPlatform.GOOGLE) {
            integrationService.fetchGoogleLeads(organizationId);
        } else if (integrationPlatform == IntegrationPlatform.FACEBOOK) {
            integrationService.fetchFacebookLeads(organizationId);
        } else {
            return ResponseEntity.badRequest().body("Unsupported platform: " + platform);
        }
        return ResponseEntity.ok("Lead fetching initiated for " + platform + " for organization " + organizationId);
    }

    /**
     * 🔹 NEW: Fetch integration logs (optional platform filter)
     * Accessible by Admin and Super Admin
     */
    @GetMapping("/logs")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<IntegrationLog>> getIntegrationLogs(
            @RequestParam(required = false) String platform,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        UUID organizationId = currentUser.getOrganizationId();
        List<IntegrationLog> logs;

        if (platform != null && !platform.isBlank()) {
            IntegrationPlatform integrationPlatform = IntegrationPlatform.valueOf(platform.toUpperCase());
            logs = integrationLogRepository.findAll().stream()
                    .filter(l -> l.getOrganizationId().equals(organizationId)
                            && l.getPlatform() == integrationPlatform)
                    .toList();
        } else {
            logs = integrationLogRepository.findAll().stream()
                    .filter(l -> l.getOrganizationId().equals(organizationId))
                    .toList();
        }

        return ResponseEntity.ok(logs);
    }
}
