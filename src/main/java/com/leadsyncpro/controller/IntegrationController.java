package com.leadsyncpro.controller;

import com.leadsyncpro.dto.IntegrationStatusResponse;
import com.leadsyncpro.dto.LeadSyncResult;
import com.leadsyncpro.exception.ResourceNotFoundException;
import com.leadsyncpro.model.*;
import com.leadsyncpro.repository.IntegrationLogRepository;
import com.leadsyncpro.security.UserPrincipal;
import com.leadsyncpro.service.IntegrationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;

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
                                                       @AuthenticationPrincipal UserPrincipal currentUser,
                                                       @RequestParam(value = "organizationId", required = false)
                                                       UUID organizationIdParam) {
        UUID organizationId = resolveOrganizationId(currentUser, organizationIdParam);
        String authorizationUrl = integrationService.getAuthorizationUrl(registrationId, organizationId, currentUser.getId());
        return ResponseEntity.ok(authorizationUrl);
    }

    @GetMapping("/oauth2/callback/{registrationId}")
    public ResponseEntity<String> oauth2Callback(@PathVariable String registrationId,
                                                 @RequestParam(required = false) String code,
                                                 @RequestParam(required = false) String state,
                                                 @RequestParam(required = false) String error) {
        if (error != null) {
            return buildPopupErrorPage("OAuth2 işlemi tamamlanamadı", error);
        }

        if (code == null || state == null) {
            return buildPopupErrorPage("Eksik OAuth2 parametreleri", "code veya state değeri bulunamadı.");
        }

        try {
            integrationService.handleOAuth2Callback(registrationId, code, state);
            return buildPopupSuccessPage("Facebook entegrasyonu başarıyla tamamlandı.");
        } catch (Exception e) {
            return buildPopupErrorPage("Entegrasyon sırasında hata oluştu", e.getMessage());
        }
    }

    private ResponseEntity<String> buildPopupSuccessPage(String message) {
        String redirectUrl = frontendSuccessRedirectUrl == null ? "#" : frontendSuccessRedirectUrl;
        String escapedMessage = HtmlUtils.htmlEscape(message == null ? "" : message);
        String html = "<!DOCTYPE html>" +
                "<html lang=\"tr\">" +
                "<head>" +
                "  <meta charset=\"UTF-8\" />" +
                "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />" +
                "  <title>Facebook Entegrasyonu</title>" +
                "  <style>" +
                "    body { font-family: 'Segoe UI', Tahoma, sans-serif; background: #f5f7fb; color: #1f2937; margin: 0; padding: 32px; display: flex; align-items: center; justify-content: center; height: 100vh; }" +
                "    .card { background: #ffffff; border-radius: 16px; box-shadow: 0 20px 45px -20px rgba(15, 23, 42, 0.35); padding: 32px 40px; max-width: 420px; text-align: center; }" +
                "    .card h1 { font-size: 24px; margin-bottom: 12px; color: #047857; }" +
                "    .card p { font-size: 16px; line-height: 1.5; margin-bottom: 0; }" +
                "    .status-badge { display: inline-flex; align-items: center; justify-content: center; padding: 8px 16px; border-radius: 999px; font-weight: 600; background: rgba(16, 185, 129, 0.15); color: #047857; margin-bottom: 20px; letter-spacing: 0.4px; text-transform: uppercase; font-size: 12px; }" +
                "    .fallback { margin-top: 24px; font-size: 14px; color: #6b7280; }" +
                "    a { color: #2563eb; text-decoration: none; font-weight: 600; }" +
                "    a:hover { text-decoration: underline; }" +
                "  </style>" +
                "</head>" +
                "<body>" +
                "  <div class=\"card\">" +
                "    <div class=\"status-badge\">Bağlantı tamamlandı</div>" +
                "    <h1>Facebook bağlantısı başarılı!</h1>" +
                "    <p>" + escapedMessage + "</p>" +
                "    <p class=\"fallback\">Bu pencere otomatik kapanmazsa <a href=\"" + HtmlUtils.htmlEscape(redirectUrl) + "\">buraya tıklayın</a>.</p>" +
                "  </div>" +
                "  <script>" +
                "    const payload = { source: 'crm-pro-oauth', status: 'success', redirectUrl: '" + escapeForJsString(redirectUrl) + "' };" +
                "    function closeWindow() { window.close(); }" +
                "    if (window.opener && !window.opener.closed) {" +
                "      window.opener.postMessage(payload, '*');" +
                "      setTimeout(closeWindow, 900);" +
                "    } else {" +
                "      if (payload.redirectUrl) { setTimeout(function () { window.location.href = payload.redirectUrl; }, 1500); }" +
                "    }" +
                "  </script>" +
                "</body>" +
                "</html>";
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }

    private ResponseEntity<String> buildPopupErrorPage(String title, String errorMessage) {
        String redirectUrl = frontendErrorRedirectUrl == null ? "" : frontendErrorRedirectUrl;
        String escapedTitle = HtmlUtils.htmlEscape(title == null ? "Hata oluştu" : title);
        String rawMessage = errorMessage == null ? "Bilinmeyen bir hata oluştu." : errorMessage;
        String escapedMessage = HtmlUtils.htmlEscape(rawMessage);
        String html = "<!DOCTYPE html>" +
                "<html lang=\"tr\">" +
                "<head>" +
                "  <meta charset=\"UTF-8\" />" +
                "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />" +
                "  <title>Facebook Entegrasyonu Hatası</title>" +
                "  <style>" +
                "    body { font-family: 'Segoe UI', Tahoma, sans-serif; background: #fdf2f8; color: #1f2937; margin: 0; padding: 32px; display: flex; align-items: center; justify-content: center; min-height: 100vh; }" +
                "    .card { background: #ffffff; border-radius: 16px; box-shadow: 0 20px 45px -20px rgba(190, 24, 93, 0.35); padding: 32px 40px; max-width: 460px; text-align: center; }" +
                "    .card h1 { font-size: 24px; margin-bottom: 12px; color: #be123c; }" +
                "    .card p { font-size: 16px; line-height: 1.5; margin-bottom: 12px; }" +
                "    .status-badge { display: inline-flex; align-items: center; justify-content: center; padding: 8px 16px; border-radius: 999px; font-weight: 600; background: rgba(244, 63, 94, 0.15); color: #be123c; margin-bottom: 20px; letter-spacing: 0.4px; text-transform: uppercase; font-size: 12px; }" +
                "    button { margin-top: 20px; background: #be123c; color: white; border: none; padding: 12px 20px; border-radius: 999px; font-size: 15px; font-weight: 600; cursor: pointer; }" +
                "    button:hover { background: #9f1239; }" +
                "    .fallback { margin-top: 16px; font-size: 14px; color: #6b7280; }" +
                "    a { color: #2563eb; text-decoration: none; font-weight: 600; }" +
                "    a:hover { text-decoration: underline; }" +
                "  </style>" +
                "</head>" +
                "<body>" +
                "  <div class=\"card\">" +
                "    <div class=\"status-badge\">Bağlantı başarısız</div>" +
                "    <h1>" + escapedTitle + "</h1>" +
                "    <p>" + escapedMessage + "</p>" +
                "    <button onclick=\"window.close();\">Pencereyi kapat</button>" +
                "    <p class=\"fallback\">Sorun devam ederse <a href=\"" + HtmlUtils.htmlEscape(redirectUrl.isBlank() ? "#" : redirectUrl) + "\">entegrasyon sayfasına dönün</a>.</p>" +
                "  </div>" +
                "  <script>" +
                "    const payload = { source: 'crm-pro-oauth', status: 'error', message: '" + escapeForJsString(rawMessage) + "', redirectUrl: '" + escapeForJsString(redirectUrl) + "' };" +
                "    if (window.opener && !window.opener.closed) {" +
                "      window.opener.postMessage(payload, '*');" +
                "    }" +
                "  </script>" +
                "</body>" +
                "</html>";
        return ResponseEntity.status(HttpStatus.OK)
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }

    private String escapeForJsString(String input) {
        if (input == null) {
            return "";
        }
        return input
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
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
                                                            @AuthenticationPrincipal UserPrincipal currentUser,
                                                            @RequestParam(value = "organizationId", required = false)
                                                            UUID organizationIdParam) {
        UUID organizationId = resolveOrganizationId(currentUser, organizationIdParam);

        IntegrationPlatform integrationPlatform = IntegrationPlatform.valueOf(platform.toUpperCase());
        IntegrationConfig config = integrationService.getIntegrationConfig(organizationId, integrationPlatform)
                .orElseThrow(() -> new ResourceNotFoundException("Integration not found for platform " + platform));
        return ResponseEntity.ok(config);
    }

    @GetMapping("/status")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<IntegrationStatusResponse>> getIntegrationStatuses(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(value = "organizationId", required = false) UUID organizationIdParam) {

        UUID organizationId = resolveOrganizationId(currentUser, organizationIdParam);

        List<IntegrationStatusResponse> statuses = integrationService.getIntegrationStatuses(organizationId);
        return ResponseEntity.ok(statuses);
    }

    @DeleteMapping("/{platform}")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<Void> deleteIntegration(@PathVariable String platform,
                                                  @AuthenticationPrincipal UserPrincipal currentUser,
                                                  @RequestParam(value = "organizationId", required = false)
                                                  UUID organizationIdParam) {
        UUID organizationId = resolveOrganizationId(currentUser, organizationIdParam);

        IntegrationPlatform integrationPlatform = IntegrationPlatform.valueOf(platform.toUpperCase());
        integrationService.deleteIntegrationConfig(organizationId, integrationPlatform);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/fetch-leads/{platform}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<LeadSyncResult> fetchLeadsManually(@PathVariable String platform,
                                                             @AuthenticationPrincipal UserPrincipal currentUser,
                                                             @RequestParam(value = "organizationId", required = false)
                                                             UUID organizationIdParam) {
        UUID organizationId = resolveOrganizationId(currentUser, organizationIdParam);

        IntegrationPlatform integrationPlatform = IntegrationPlatform.valueOf(platform.toUpperCase());
        LeadSyncResult result;
        if (integrationPlatform == IntegrationPlatform.GOOGLE) {
            result = integrationService.fetchGoogleLeads(organizationId);
        } else if (integrationPlatform == IntegrationPlatform.FACEBOOK) {
            result = integrationService.fetchFacebookLeads(organizationId);
        } else {
            throw new IllegalArgumentException("Unsupported platform: " + platform);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 🔹 NEW: Fetch integration logs (optional platform filter)
     * Accessible by Admin and Super Admin
     */
    @GetMapping("/logs")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<IntegrationLog>> getIntegrationLogs(
            @RequestParam(required = false) String platform,
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(value = "organizationId", required = false) UUID organizationIdParam,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {

        UUID organizationId = resolveOrganizationId(currentUser, organizationIdParam);
        IntegrationPlatform integrationPlatform = null;
        if (platform != null && !platform.isBlank()) {
            integrationPlatform = IntegrationPlatform.valueOf(platform.toUpperCase());
        }

        Sort sort = Sort.by(Sort.Direction.DESC, "startedAt");
        boolean usePagination = page != null || size != null;

        if (usePagination) {
            int resolvedPage = page != null && page >= 0 ? page : 0;
            int resolvedSize = size != null && size > 0 ? size : 20;
            Pageable pageable = PageRequest.of(resolvedPage, resolvedSize, sort);
            Page<IntegrationLog> logsPage = integrationLogRepository
                    .findAllByOrganizationIdAndOptionalPlatform(organizationId, integrationPlatform, pageable);
            return ResponseEntity.ok(logsPage.getContent());
        }

        List<IntegrationLog> logs = integrationLogRepository
                .findAllByOrganizationIdAndOptionalPlatform(organizationId, integrationPlatform, sort);

        return ResponseEntity.ok(logs);
    }

    private UUID resolveOrganizationId(UserPrincipal currentUser, UUID requestedOrganizationId) {
        UUID organizationId = currentUser.getOrganizationId();

        if (currentUser.getRole() == Role.SUPER_ADMIN) {
            UUID effectiveId = requestedOrganizationId != null ? requestedOrganizationId : organizationId;
            if (effectiveId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Super Admin must specify organizationId via query parameter or within the token.");
            }
            return effectiveId;
        }

        if (organizationId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Organization information is missing for the current user.");
        }

        if (requestedOrganizationId != null && !organizationId.equals(requestedOrganizationId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You are not authorized to access data for the requested organization.");
        }

        return organizationId;
    }
}
