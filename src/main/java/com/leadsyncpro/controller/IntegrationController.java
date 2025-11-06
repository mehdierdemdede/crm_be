package com.leadsyncpro.controller;

import com.leadsyncpro.dto.IntegrationStatusResponse;
import com.leadsyncpro.dto.LeadSyncResult;
import com.leadsyncpro.exception.ResourceNotFoundException;
import com.leadsyncpro.model.*;
import com.leadsyncpro.repository.IntegrationLogRepository;
import com.leadsyncpro.security.UserPrincipal;
import com.leadsyncpro.service.IntegrationService;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
                                                       HttpServletRequest request,
                                                       @RequestParam(value = "organizationId", required = false)
                                                       UUID organizationIdParam) {
        UUID organizationId = resolveOrganizationId(currentUser, organizationIdParam);
        String requestBaseUrl = determineRequestBaseUrl(request);
        String authorizationUrl = integrationService.getAuthorizationUrl(
                registrationId,
                organizationId,
                currentUser.getId(),
                requestBaseUrl
        );
        return ResponseEntity.ok(authorizationUrl);
    }

    @GetMapping("/oauth2/callback/{registrationId}")
    public ResponseEntity<String> oauth2Callback(@PathVariable String registrationId,
                                                 @RequestParam(required = false) String code,
                                                 @RequestParam(required = false) String state,
                                                 @RequestParam(required = false) String error,
                                                 HttpServletRequest request) {
        if (error != null) {
            return buildPopupErrorPage("OAuth2 işlemi tamamlanamadı", error);
        }

        if (!StringUtils.hasText(code) || !StringUtils.hasText(state)) {
            return buildPopupErrorPage("Eksik OAuth2 parametreleri", "code veya state değeri bulunamadı.");
        }

        try {
            String requestBaseUrl = determineRequestBaseUrl(request);
            integrationService.handleOAuth2Callback(registrationId, code, state, requestBaseUrl);
            return buildPopupSuccessPage("Facebook entegrasyonu başarıyla tamamlandı.");
        } catch (Exception e) {
            return buildPopupErrorPage("Entegrasyon sırasında hata oluştu", e.getMessage());
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
                                                            @AuthenticationPrincipal UserPrincipal currentUser,
                                                            @RequestParam(value = "organizationId", required = false)
                                                            UUID organizationIdParam) {
        UUID organizationId = resolveOrganizationId(currentUser, organizationIdParam);

        IntegrationPlatform integrationPlatform = resolvePlatform(platform);
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

        IntegrationPlatform integrationPlatform = resolvePlatform(platform);
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

        IntegrationPlatform integrationPlatform = resolvePlatform(platform);
        LeadSyncResult result = switch (integrationPlatform) {
            case GOOGLE -> integrationService.fetchGoogleLeads(organizationId);
            case FACEBOOK -> integrationService.fetchFacebookLeads(organizationId);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Manual lead fetch is not supported for platform: " + platform);
        };
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
            integrationPlatform = resolvePlatform(platform);
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

    private ResponseEntity<String> buildPopupSuccessPage(String message) {
        String resolvedMessage = StringUtils.hasText(message) ? message : "";
        String redirectUrl = StringUtils.hasText(frontendSuccessRedirectUrl) ? frontendSuccessRedirectUrl : "";

        Map<String, String> values = new HashMap<>();
        values.put("PAGE_TITLE", "Facebook Entegrasyonu");
        values.put("STATUS_BADGE", "Bağlantı tamamlandı");
        values.put("HEADING", "Facebook bağlantısı başarılı!");
        values.put("MESSAGE", HtmlUtils.htmlEscape(resolvedMessage));
        values.put("FALLBACK_HTML", buildSuccessFallbackHtml(redirectUrl));
        values.put("PAYLOAD_STATUS", "success");
        values.put("PAYLOAD_MESSAGE", escapeForJsString(resolvedMessage));
        values.put("PAYLOAD_REDIRECT_URL", escapeForJsString(redirectUrl));

        String body = renderTemplate("integration-success.html", values);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(body);
    }

    private ResponseEntity<String> buildPopupErrorPage(String title, String errorMessage) {
        String resolvedTitle = StringUtils.hasText(title) ? title : "Hata oluştu";
        String resolvedMessage = StringUtils.hasText(errorMessage) ? errorMessage : "Bilinmeyen bir hata oluştu.";
        String redirectUrl = StringUtils.hasText(frontendErrorRedirectUrl) ? frontendErrorRedirectUrl : "";

        Map<String, String> values = new HashMap<>();
        values.put("PAGE_TITLE", "Facebook Entegrasyonu Hatası");
        values.put("STATUS_BADGE", "Bağlantı başarısız");
        values.put("HEADING", HtmlUtils.htmlEscape(resolvedTitle));
        values.put("MESSAGE", HtmlUtils.htmlEscape(resolvedMessage));
        values.put("FALLBACK_HTML", buildErrorFallbackHtml(redirectUrl));
        values.put("PAYLOAD_STATUS", "error");
        values.put("PAYLOAD_MESSAGE", escapeForJsString(resolvedMessage));
        values.put("PAYLOAD_REDIRECT_URL", escapeForJsString(redirectUrl));

        String body = renderTemplate("integration-error.html", values);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(body);
    }

    private String buildSuccessFallbackHtml(String redirectUrl) {
        if (StringUtils.hasText(redirectUrl)) {
            return "Bu pencere otomatik kapanmazsa <a href=\"" + HtmlUtils.htmlEscape(redirectUrl) + "\">buraya tıklayın</a>.";
        }
        return "Bu pencere otomatik kapanmazsa bu pencereyi kapatabilirsiniz.";
    }

    private String buildErrorFallbackHtml(String redirectUrl) {
        if (StringUtils.hasText(redirectUrl)) {
            return "Sorun devam ederse <a href=\"" + HtmlUtils.htmlEscape(redirectUrl) + "\">entegrasyon sayfasına dönün</a>.";
        }
        return "Sorun devam ederse destek ekibimizle iletişime geçin.";
    }

    private String renderTemplate(String templateName, Map<String, String> values) {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("templates/" + templateName)) {
            if (inputStream == null) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Şablon bulunamadı: " + templateName);
            }
            String template = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            String rendered = template;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String key = "{{" + entry.getKey() + "}}";
                rendered = rendered.replace(key, entry.getValue());
            }
            return rendered;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Şablon yüklenirken hata oluştu: " + templateName, e);
        }
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

    private IntegrationPlatform resolvePlatform(String platform) {
        if (!StringUtils.hasText(platform)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Platform must not be empty.");
        }

        try {
            return IntegrationPlatform.valueOf(platform.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid integration platform: " + platform, ex);
        }
    }

    private String determineRequestBaseUrl(HttpServletRequest request) {
        String baseUrl = buildBaseUrlFromForwardedHeaders(request);

        if (!StringUtils.hasText(baseUrl)) {
            ServletUriComponentsBuilder builder = ServletUriComponentsBuilder.fromRequest(request);
            builder.replacePath(request.getContextPath());
            builder.replaceQuery(null);
            baseUrl = builder.build().toUriString();
        }

        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    private String buildBaseUrlFromForwardedHeaders(HttpServletRequest request) {
        ForwardedHeaderDetails details = ForwardedHeaderDetails.from(request);
        if (details == null || !details.hasHost()) {
            return null;
        }

        StringBuilder url = new StringBuilder();
        url.append(details.getScheme()).append("://").append(details.getHost());

        if (details.shouldAppendPort()) {
            url.append(":").append(details.getPort());
        }

        String combinedPath = combinePaths(details.getPrefix(), request.getContextPath());
        if (StringUtils.hasText(combinedPath)) {
            url.append(combinedPath);
        }

        return url.toString();
    }

    private String combinePaths(String first, String second) {
        String combined = normalizePath(first) + normalizePath(second);
        if (!StringUtils.hasText(combined)) {
            return "";
        }
        return combined.startsWith("/") ? combined : "/" + combined;
    }

    private String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return "";
        }
        String normalized = path.trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private static class ForwardedHeaderDetails {
        private final String scheme;
        private final String host;
        private final String port;
        private final String prefix;

        private ForwardedHeaderDetails(String scheme, String host, String port, String prefix) {
            this.scheme = scheme;
            this.host = host;
            this.port = port;
            this.prefix = prefix;
        }

        static ForwardedHeaderDetails from(HttpServletRequest request) {
            String forwarded = request.getHeader("Forwarded");
            if (StringUtils.hasText(forwarded)) {
                ForwardedHeaderDetails parsed = parseForwardedHeader(forwarded);
                if (parsed != null) {
                    if (!StringUtils.hasText(parsed.scheme)) {
                        return new ForwardedHeaderDetails(request.getScheme(), parsed.host, parsed.port, parsed.prefix);
                    }
                    return parsed;
                }
            }

            String host = firstHeaderValue(request, "X-Forwarded-Host");
            String scheme = firstHeaderValue(request, "X-Forwarded-Proto");
            String port = firstHeaderValue(request, "X-Forwarded-Port");
            String prefix = firstHeaderValue(request, "X-Forwarded-Prefix");

            if (!StringUtils.hasText(host)) {
                return null;
            }

            if (!StringUtils.hasText(scheme)) {
                scheme = request.getScheme();
            }

            return new ForwardedHeaderDetails(scheme, host, port, prefix);
        }

        private static ForwardedHeaderDetails parseForwardedHeader(String header) {
            String[] parts = header.split(",");
            if (parts.length == 0) {
                return null;
            }

            String first = parts[0];
            String scheme = extractToken(first, "proto");
            String host = extractToken(first, "host");

            if (!StringUtils.hasText(host)) {
                return null;
            }

            HostPort hostPort = splitHostAndPort(host);

            return new ForwardedHeaderDetails(
                    scheme,
                    hostPort.host(),
                    hostPort.port(),
                    extractToken(first, "prefix")
            );
        }

        private static HostPort splitHostAndPort(String value) {
            if (!StringUtils.hasText(value)) {
                return new HostPort(null, null);
            }

            String host = value;
            String port = null;

            if (value.startsWith("[") && value.contains("]")) {
                int closingIndex = value.indexOf(']');
                host = value.substring(0, closingIndex + 1);
                if (value.length() > closingIndex + 1 && value.charAt(closingIndex + 1) == ':') {
                    port = value.substring(closingIndex + 2);
                }
            } else if (value.contains(":")) {
                String[] hostParts = value.split(":", 2);
                host = hostParts[0];
                port = hostParts[1];
            }

            return new HostPort(host, port);
        }

        private static String extractToken(String value, String token) {
            String[] segments = value.split(";");
            for (String segment : segments) {
                String[] keyValue = segment.trim().split("=", 2);
                if (keyValue.length == 2 && token.equalsIgnoreCase(keyValue[0].trim())) {
                    return trimQuotes(keyValue[1].trim());
                }
            }
            return null;
        }

        private static String trimQuotes(String value) {
            if (value == null) {
                return null;
            }
            if (value.startsWith("\"") && value.endsWith("\"")) {
                return value.substring(1, value.length() - 1);
            }
            return value;
        }

        private static String firstHeaderValue(HttpServletRequest request, String headerName) {
            String headerValue = request.getHeader(headerName);
            if (!StringUtils.hasText(headerValue)) {
                return null;
            }
            if (headerValue.contains(",")) {
                return headerValue.split(",")[0].trim();
            }
            return headerValue.trim();
        }

        boolean hasHost() {
            return StringUtils.hasText(host);
        }

        String getScheme() {
            return StringUtils.hasText(scheme) ? scheme : "https";
        }

        String getHost() {
            return host;
        }

        String getPort() {
            return port;
        }

        boolean shouldAppendPort() {
            if (!StringUtils.hasText(port)) {
                return false;
            }
            if (hostContainsPort(host)) {
                return false;
            }
            if ("https".equalsIgnoreCase(getScheme()) && "443".equals(port)) {
                return false;
            }
            if ("http".equalsIgnoreCase(getScheme()) && "80".equals(port)) {
                return false;
            }
            return true;
        }

        String getPrefix() {
            return prefix;
        }

        private boolean hostContainsPort(String hostValue) {
            if (!StringUtils.hasText(hostValue)) {
                return false;
            }
            if (hostValue.startsWith("[") && hostValue.contains("]")) {
                int closingIndex = hostValue.indexOf(']');
                return hostValue.length() > closingIndex + 1 && hostValue.charAt(closingIndex + 1) == ':';
            }
            return hostValue.contains(":");
        }

        private record HostPort(String host, String port) {
        }
    }
}
