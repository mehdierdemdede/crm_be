package com.leadsyncpro.controller;

import com.leadsyncpro.exception.ResourceNotFoundException;
import com.leadsyncpro.model.IntegrationConfig;
import com.leadsyncpro.model.IntegrationPlatform;
import com.leadsyncpro.model.Role;
import com.leadsyncpro.security.UserPrincipal;
import com.leadsyncpro.service.IntegrationService;
import org.springframework.beans.factory.annotation.Value; // Yeni import
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

    // Yeni eklenen @Value alanları
    @Value("${app.oauth2.frontend-success-redirect-url}")
    private String frontendSuccessRedirectUrl;

    @Value("${app.oauth2.frontend-error-redirect-url}")
    private String frontendErrorRedirectUrl;

    public IntegrationController(IntegrationService integrationService) {
        this.integrationService = integrationService;
    }

    /**
     * Initiates the OAuth2 authorization flow for a given platform.
     * Accessible by Super Admin.
     * Frontend will redirect to the URL returned by this endpoint.
     */
    @GetMapping("/oauth2/authorize/{registrationId}")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')") // Only Super Admin can initiate integrations
    public ResponseEntity<String> authorizeIntegration(@PathVariable String registrationId,
                                                       @AuthenticationPrincipal UserPrincipal currentUser) {
        UUID organizationId = currentUser.getOrganizationId();

        if (organizationId == null) {
            throw new IllegalArgumentException("Super Admin must specify an organization to integrate.");
        }

        String authorizationUrl = integrationService.getAuthorizationUrl(registrationId, organizationId, currentUser.getId());
        return ResponseEntity.ok(authorizationUrl);
    }

    /**
     * Handles the OAuth2 callback from Google/Facebook.
     * This endpoint is public (permitAll) as it's the redirect URI for the OAuth provider.
     */
    @GetMapping("/oauth2/callback/{registrationId}")
    public RedirectView oauth2Callback(@PathVariable String registrationId,
                                       @RequestParam String code,
                                       @RequestParam String state,
                                       @RequestParam(required = false) String error) {
        if (error != null) {
            // Hata durumunda frontend hata URL'ine yönlendir
            return new RedirectView(frontendErrorRedirectUrl + "?message=OAuth2_Error&details=" + error); // DEĞİŞİKLİK
        }
        try {
            integrationService.handleOAuth2Callback(registrationId, code, state);
            // Başarılı durumda frontend başarı URL'ine yönlendir
            return new RedirectView(frontendSuccessRedirectUrl); // DEĞİŞİKLİK
        } catch (Exception e) {
            // Hata durumunda frontend hata URL'ine yönlendir
            return new RedirectView(frontendErrorRedirectUrl + "?message=Integration_Failed&details=" + e.getMessage()); // DEĞİŞİKLİK
        }
    }

    // YENİ EKLENEN TEST ENDPOINT'LERİ (React frontend olmadan test etmek için)
    @GetMapping("/oauth2/test-success")
    public ResponseEntity<String> oauth2TestSuccess() {
        return ResponseEntity.ok("OAuth2 entegrasyonu backend tarafında BAŞARILI oldu! Frontend'e yönlendirildiniz.");
    }

    @GetMapping("/oauth2/test-error")
    public ResponseEntity<String> oauth2TestError(@RequestParam String message, @RequestParam String details) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("OAuth2 entegrasyonu backend tarafında HATA verdi: " + message + " - Detaylar: " + details);
    }

    /**
     * Gets the integration configuration for a specific organization and platform.
     * Accessible by Admin and Super Admin.
     */
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

    /**
     * Deletes an integration configuration.
     * Accessible by Super Admin.
     */
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
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    /**
     * Triggers manual lead fetching for a platform.
     * Accessible by Admin and Super Admin.
     */
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
}