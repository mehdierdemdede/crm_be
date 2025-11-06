// src/main/java/com/leadsyncpro/service/IntegrationService.java
package com.leadsyncpro.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pemistahl.lingua.api.Language;
import com.github.pemistahl.lingua.api.LanguageDetector;
import com.github.pemistahl.lingua.api.LanguageDetectorBuilder;
import com.leadsyncpro.dto.IntegrationStatusResponse;
import com.leadsyncpro.dto.LeadSyncResult;
import com.leadsyncpro.exception.ResourceNotFoundException;
import com.leadsyncpro.model.*;
import com.leadsyncpro.repository.CampaignRepository;
import com.leadsyncpro.repository.IntegrationConfigRepository;
import com.leadsyncpro.repository.IntegrationLogRepository;
import com.leadsyncpro.repository.LeadRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.text.Normalizer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;


@Service
public class IntegrationService {

    private static final Logger logger = LoggerFactory.getLogger(IntegrationService.class);

    private final IntegrationConfigRepository integrationConfigRepository;
    private final EncryptionService encryptionService;
    private final ClientRegistrationRepository clientRegistrationRepository;
    private final RestTemplate restTemplate;
    private final LeadRepository leadRepository;
    private final IntegrationLogRepository integrationLogRepository;
    private final CampaignRepository campaignRepository;
    private final ObjectMapper objectMapper;
    private final AutoAssignService autoAssignService;


    @Value("${app.encryption.key}")
    private String encryptionKey;

    @Value("${app.backend.base-url}")
    private String backendBaseUrl;

    private static String asString(Object o) { return o == null ? null : o.toString(); }
    private static String safe(String s) { return s == null ? "" : s; }
    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    private static Instant parseInstant(String iso) {
        try { return iso != null ? Instant.parse(iso) : null; } catch (Exception e) { return null; }
    }
    private static Boolean asBoolean(Object o) {
        if (o instanceof Boolean) return (Boolean) o;
        if (o instanceof String) return "true".equalsIgnoreCase((String) o);
        return null;
    }
    private static String firstValue(Object values) {
        if (values instanceof List && !((List<?>) values).isEmpty()) {
            Object v0 = ((List<?>) values).get(0);
            return v0 != null ? v0.toString() : null;
        } else if (values instanceof String) {
            return (String) values;
        }
        return null;
    }

    private static String normalizeFieldKey(String key) {
        if (key == null) {
            return null;
        }
        String normalized = Normalizer.normalize(key, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        normalized = normalized.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
        return normalized;
    }

    private static boolean containsAnyToken(String value, String... tokens) {
        if (value == null) {
            return false;
        }
        for (String token : tokens) {
            if (token != null && !token.isEmpty() && value.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAllTokens(String value, String... tokens) {
        if (value == null) {
            return false;
        }
        for (String token : tokens) {
            if (token == null || token.isEmpty() || !value.contains(token)) {
                return false;
            }
        }
        return tokens.length > 0;
    }

    private String resolveRedirectUri(String redirectUri, String requestBaseUrl) {
        if (redirectUri != null && redirectUri.contains("{baseUrl}")) {
            redirectUri = redirectUri.replace("{baseUrl}", determineBackendBaseUrl(requestBaseUrl));
        }
        return redirectUri;
    }

    private String determineBackendBaseUrl(String requestBaseUrl) {
        String configCandidate = isBlank(backendBaseUrl) ? null : backendBaseUrl;
        if (configCandidate != null) {
            return trimTrailingSlash(configCandidate);
        }

        String requestCandidate = isBlank(requestBaseUrl) ? null : requestBaseUrl;
        if (requestCandidate != null) {
            return trimTrailingSlash(requestCandidate);
        }

        return "http://localhost:8080";
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return null;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static boolean isNameFieldKey(String normalizedKey) {
        if (normalizedKey == null || normalizedKey.isEmpty()) {
            return false;
        }
        if (containsAnyToken(normalizedKey, "company", "firma", "entreprise")) {
            return false;
        }
        if (containsAnyToken(normalizedKey, "fullname", "contactname")) {
            return true;
        }
        if (normalizedKey.equals("name") || normalizedKey.endsWith("name")) {
            return true;
        }
        if (containsAllTokens(normalizedKey, "prenom", "nom")
                || containsAllTokens(normalizedKey, "first", "last", "name")) {
            return true;
        }
        return false;
    }

    private static boolean isFirstNameFieldKey(String normalizedKey) {
        if (normalizedKey == null) {
            return false;
        }
        return containsAnyToken(normalizedKey,
                "firstname", "givenname", "forename", "prenom", "vorname", "firstnamefield");
    }

    private static boolean isLastNameFieldKey(String normalizedKey) {
        if (normalizedKey == null) {
            return false;
        }
        if (containsAnyToken(normalizedKey, "lastname", "surname", "familyname", "achternaam", "nachname")) {
            return true;
        }
        return normalizedKey.contains("nom") && !normalizedKey.contains("prenom");
    }

    private static boolean isEmailFieldKey(String normalizedKey) {
        if (normalizedKey == null) {
            return false;
        }
        if (normalizedKey.contains("email") || normalizedKey.contains("courriel")) {
            return true;
        }
        return normalizedKey.endsWith("mailaddress");
    }

    private static boolean isPhoneFieldKey(String normalizedKey) {
        if (normalizedKey == null) {
            return false;
        }
        return containsAnyToken(normalizedKey,
                "phone", "tel", "telefon", "telefono", "gsm", "mobile", "whatsapp", "cell", "cel");
    }

    @SuppressWarnings("unchecked")
    private static String nextUrl(Map body) {
        if (body == null) return null;
        Map<String,Object> paging = (Map<String,Object>) body.get("paging");
        if (paging == null) return null;
        return (String) paging.get("next");
    }

    private static final LanguageDetector detector = LanguageDetectorBuilder
            .fromAllLanguages()
            .withPreloadedLanguageModels()
            .build();


    public static String detectLanguageFromText(String text) {
        if (text == null || text.isBlank()) return null;
        Language lang = detector.detectLanguageOf(text);
        return lang != null ? lang.getIsoCode639_1().name() : null; // örn: "EN"
    }


    private void updateConnectionStatus(IntegrationConfig config,
                                        IntegrationConnectionStatus status,
                                        String statusMessage,
                                        String errorMessage) {
        if (config == null) {
            return;
        }

        config.setConnectionStatus(status);
        config.setStatusMessage(statusMessage);

        if (status == IntegrationConnectionStatus.ERROR) {
            config.setLastErrorAt(Instant.now());
            config.setLastErrorMessage(errorMessage);
        } else {
            config.setLastErrorAt(null);
            config.setLastErrorMessage(null);
        }
    }

    private IntegrationConnectionStatus resolveEffectiveStatus(IntegrationConfig config) {
        if (config == null) {
            return IntegrationConnectionStatus.DISCONNECTED;
        }

        IntegrationConnectionStatus status = config.getConnectionStatus();
        if (status == null) {
            status = config.getAccessToken() != null
                    ? IntegrationConnectionStatus.CONNECTED
                    : IntegrationConnectionStatus.DISCONNECTED;
        }

        Instant expiresAt = config.getExpiresAt();
        if (expiresAt != null && Instant.now().isAfter(expiresAt)) {
            return IntegrationConnectionStatus.EXPIRED;
        }

        return status;
    }

    private boolean requiresAction(IntegrationConnectionStatus status) {
        return status == IntegrationConnectionStatus.ERROR
                || status == IntegrationConnectionStatus.EXPIRED
                || status == IntegrationConnectionStatus.DISCONNECTED;
    }


    public IntegrationService(IntegrationConfigRepository integrationConfigRepository,
                              EncryptionService encryptionService,
                              ClientRegistrationRepository clientRegistrationRepository,
                              LeadRepository leadRepository,
                              CampaignRepository campaignRepository,
                              IntegrationLogRepository integrationLogRepository, AutoAssignService autoAssignService) {
        this.integrationConfigRepository = integrationConfigRepository;
        this.encryptionService = encryptionService;
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.autoAssignService = autoAssignService;
        this.restTemplate = new RestTemplate();
        this.leadRepository = leadRepository;
        this.campaignRepository = campaignRepository;
        this.objectMapper = new ObjectMapper();
        this.integrationLogRepository = integrationLogRepository;
    }


    @Transactional
    public IntegrationConfig saveIntegrationConfig(UUID organizationId, IntegrationPlatform platform,
                                                   String accessToken, String refreshToken, Instant expiresAt,
                                                   String scope, String clientId, String clientSecret, UUID createdBy) {
        // Hassas verileri kaydetmeden önce şifrele
        String encryptedAccessToken = encryptionService.encrypt(accessToken);
        String encryptedRefreshToken = refreshToken != null ? encryptionService.encrypt(refreshToken) : null;
        String encryptedClientSecret = clientSecret != null ? encryptionService.encrypt(clientSecret) : null;

        IntegrationConfig config = integrationConfigRepository.findByOrganizationIdAndPlatform(organizationId, platform)
                .orElse(new IntegrationConfig());

        config.setOrganizationId(organizationId);
        config.setPlatform(platform);
        config.setAccessToken(encryptedAccessToken);
        config.setRefreshToken(encryptedRefreshToken);
        config.setExpiresAt(expiresAt);
        config.setScope(scope);
        config.setClientId(clientId);
        config.setClientSecret(encryptedClientSecret);
        config.setCreatedBy(createdBy);
        // platformPageId burada ayarlanacak, ancak handleOAuth2Callback'de belirlenecek.
        // Eğer bu metod doğrudan çağrılıyorsa ve pageId yoksa, null kalır.
        updateConnectionStatus(config, IntegrationConnectionStatus.CONNECTED,
                "OAuth yapılandırması manuel olarak kaydedildi.", null);

        return integrationConfigRepository.save(config);
    }

    public Optional<IntegrationConfig> getIntegrationConfig(UUID organizationId, IntegrationPlatform platform) {
        return integrationConfigRepository.findByOrganizationIdAndPlatform(organizationId, platform);
    }

    public List<IntegrationStatusResponse> getIntegrationStatuses(UUID organizationId) {
        Map<IntegrationPlatform, IntegrationConfig> byPlatform = integrationConfigRepository
                .findByOrganizationId(organizationId)
                .stream()
                .collect(java.util.stream.Collectors.toMap(IntegrationConfig::getPlatform, config -> config));

        List<IntegrationStatusResponse> statuses = new ArrayList<>();
        Arrays.stream(IntegrationPlatform.values())
                .sorted(Comparator.comparing(Enum::name))
                .forEach(platform -> {
                    IntegrationConfig config = byPlatform.get(platform);
                    IntegrationConnectionStatus status = resolveEffectiveStatus(config);

                    statuses.add(IntegrationStatusResponse.builder()
                            .platform(platform)
                            .connected(config != null)
                            .connectedAt(config != null ? config.getCreatedAt() : null)
                            .expiresAt(config != null ? config.getExpiresAt() : null)
                            .lastSyncedAt(config != null ? config.getLastSyncedAt() : null)
                            .platformPageId(config != null ? config.getPlatformPageId() : null)
                            .status(status)
                            .statusMessage(config != null ? config.getStatusMessage()
                                    : platform.name() + " entegrasyonu henüz yapılandırılmadı.")
                            .lastErrorAt(config != null ? config.getLastErrorAt() : null)
                            .lastErrorMessage(config != null ? config.getLastErrorMessage() : null)
                            .requiresAction(requiresAction(status))
                            .build());
                });
        return statuses;
    }

    @Transactional
    public void deleteIntegrationConfig(UUID organizationId, IntegrationPlatform platform) {
        integrationConfigRepository.findByOrganizationIdAndPlatform(organizationId, platform)
                .ifPresent(integrationConfigRepository::delete);
    }

    /**
     * OAuth2 yetkilendirme akışını başlatır.
     * Frontend'in kullanıcıyı yönlendirmesi gereken yetkilendirme URL'ini döndürür.
     */
    public String getAuthorizationUrl(String registrationId,
                                      UUID organizationId,
                                      UUID userId,
                                      String requestBaseUrl) {
        ClientRegistration clientRegistration = clientRegistrationRepository.findByRegistrationId(registrationId);
        if (clientRegistration == null) {
            throw new IllegalArgumentException("Geçersiz istemci kayıt ID'si: " + registrationId);
        }

        IntegrationPlatform platform = IntegrationPlatform.valueOf(registrationId.toUpperCase());
        integrationConfigRepository.findByOrganizationIdAndPlatform(organizationId, platform)
                .ifPresent(config -> {
                    updateConnectionStatus(config, IntegrationConnectionStatus.PENDING,
                            "OAuth yetkilendirme akışı başlatıldı.", null);
                    integrationConfigRepository.save(config);
                });

        String state = organizationId + "|" + userId;

        String redirectUri = resolveRedirectUri(clientRegistration.getRedirectUri(), requestBaseUrl);

        // Facebook için sadece sayfa lead’leri için gereken izinler
        List<String> scopes = Arrays.asList(
                "email",
                "public_profile",
                "pages_show_list",
                "pages_read_engagement",
                "leads_retrieval"
        );

        String scopeParam = String.join(",", scopes);
        String authorizationUri = clientRegistration.getProviderDetails().getAuthorizationUri();

        return UriComponentsBuilder.fromHttpUrl(authorizationUri)
                .queryParam("client_id", clientRegistration.getClientId())
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("state", state)
                .queryParam("scope", scopeParam)
                .build()
                .toUriString();
    }

    /**
     * OAuth2 callback'ini işler, kodları token'larla değiştirir ve yapılandırmayı kaydeder.
     * Bu metod, callback endpoint'iniz tarafından çağrılır.
     */
    @Transactional
    public void handleOAuth2Callback(String registrationId, String code, String state, String requestBaseUrl) {
        ClientRegistration clientRegistration = clientRegistrationRepository.findByRegistrationId(registrationId);
        if (clientRegistration == null) {
            logger.error("OAuth2 Callback Hatası: Geçersiz istemci kayıt ID'si: {}", registrationId);
            throw new IllegalArgumentException("Geçersiz istemci kayıt ID'si: " + registrationId);
        }

        String[] stateParts = state.split("\\|");
        if (stateParts.length != 2) {
            logger.error("OAuth2 Callback Hatası: Geçersiz state parametre formatı: {}", state);
            throw new IllegalArgumentException("Geçersiz state parametre formatı.");
        }
        UUID organizationId = UUID.fromString(stateParts[0]);
        UUID userId = UUID.fromString(stateParts[1]);

        IntegrationPlatform platform = IntegrationPlatform.valueOf(registrationId.toUpperCase());
        IntegrationConfig existingConfig = integrationConfigRepository
                .findByOrganizationIdAndPlatform(organizationId, platform)
                .orElse(null);

        IntegrationConfig config = existingConfig != null ? existingConfig : new IntegrationConfig();

        try {
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("grant_type", "authorization_code");
            params.add("client_id", clientRegistration.getClientId());
            params.add("client_secret", clientRegistration.getClientSecret());
            String redirectUri = resolveRedirectUri(clientRegistration.getRedirectUri(), requestBaseUrl);
            params.add("redirect_uri", redirectUri);
            params.add("code", code);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    clientRegistration.getProviderDetails().getTokenUri(),
                    HttpMethod.POST,
                    request,
                    Map.class
            );

            Map<String, Object> tokenResponse = response.getBody();
            if (tokenResponse == null || !tokenResponse.containsKey("access_token")) {
                throw new RuntimeException("Erişim token'ı alınamadı.");
            }

            String accessToken = (String) tokenResponse.get("access_token");
            String refreshToken = (String) tokenResponse.get("refresh_token");

            Long expiresInSeconds = null;
            Object expiresInObj = tokenResponse.get("expires_in");
            if (expiresInObj instanceof Number) {
                expiresInSeconds = ((Number) expiresInObj).longValue();
            } else if (expiresInObj instanceof String) {
                try { expiresInSeconds = Long.parseLong((String) expiresInObj); } catch (NumberFormatException ignored) {}
            }
            Instant expiresAt = (expiresInSeconds != null) ? Instant.now().plus(expiresInSeconds, ChronoUnit.SECONDS) : null;
            String scope = (String) tokenResponse.get("scope");

            config.setOrganizationId(organizationId);
            config.setPlatform(platform);
            config.setAccessToken(encryptionService.encrypt(accessToken));
            config.setRefreshToken(refreshToken != null ? encryptionService.encrypt(refreshToken) : null);
            config.setExpiresAt(expiresAt);
            config.setScope(scope);
            config.setClientId(clientRegistration.getClientId());
            config.setClientSecret(encryptionService.encrypt(clientRegistration.getClientSecret()));
            config.setCreatedBy(userId);

            // FACEBOOK: lead formu olan sayfayı bul
            if (registrationId.equalsIgnoreCase("facebook")) {
                try {
                    String pagesApiUrl = "https://graph.facebook.com/v18.0/me/accounts"
                            + "?fields=id,name,access_token&access_token=" + accessToken;

                    ResponseEntity<Map> pagesResponse = restTemplate.exchange(pagesApiUrl, HttpMethod.GET, null, Map.class);
                    List<Map<String, Object>> pagesData = (List<Map<String, Object>>) pagesResponse.getBody().get("data");

                    String selectedPageId = null;
                    if (pagesData != null) {
                        for (Map<String, Object> pageMap : pagesData) {
                            String pageId = (String) pageMap.get("id");
                            String pageAccessToken = (String) pageMap.get("access_token");
                            if (pageId == null || pageAccessToken == null) continue;

                            // Bu sayfanın en az bir lead formu var mı?
                            String testFormsUrl = "https://graph.facebook.com/v18.0/" + pageId
                                    + "/leadgen_forms?limit=1&access_token=" + pageAccessToken;

                            try {
                                ResponseEntity<Map> formsResp = restTemplate.exchange(testFormsUrl, HttpMethod.GET, null, Map.class);
                                List<Map<String, Object>> fdata = (List<Map<String, Object>>) formsResp.getBody().get("data");
                                if (fdata != null && !fdata.isEmpty()) {
                                    selectedPageId = pageId;
                                    logger.info("Lead formu olan Facebook sayfası bulundu: {} ({})", pageMap.get("name"), pageId);
                                    break;
                                }
                            } catch (Exception inner) {
                                logger.debug("Sayfa {} form kontrolünde hata/erişim yok: {}", pageId, inner.getMessage());
                            }
                        }
                    }

                    if (selectedPageId != null) {
                        config.setPlatformPageId(selectedPageId);
                    } else {
                        logger.warn("Organizasyon {} için lead formu olan bir Facebook sayfası bulunamadı.", organizationId);
                    }
                } catch (Exception e) {
                    logger.error("Facebook sayfaları çekilirken hata: {}", e.getMessage());
                }
            }

            updateConnectionStatus(config, IntegrationConnectionStatus.CONNECTED,
                    "OAuth bağlantısı başarıyla tamamlandı.", null);
            integrationConfigRepository.save(config);
            logger.info("OAuth2 entegrasyonu organizasyon {} için platform {} ile başarıyla tamamlandı.", organizationId, registrationId);
        } catch (Exception e) {
            if (existingConfig != null) {
                updateConnectionStatus(existingConfig, IntegrationConnectionStatus.ERROR,
                        "OAuth callback tamamlanamadı.", e.getMessage());
                integrationConfigRepository.save(existingConfig);
            }
            throw e;
        }
    }

    /**
     * Süresi dolmuş token'ları yenilemek ve aktif entegrasyonlara sahip tüm organizasyonlar için lead'leri çekmek için zamanlanmış görev.
     * Bu basit bir örnektir; gerçek bir uygulamada bunu daha sağlam bir şekilde yönetirsiniz (örn. organizasyon başına zamanlama).
     */

    @Transactional
    public String refreshAccessToken(UUID organizationId, IntegrationPlatform platform) {
        IntegrationConfig config = integrationConfigRepository.findByOrganizationIdAndPlatform(organizationId, platform)
                .orElseThrow(() -> new ResourceNotFoundException("Organizasyon " + organizationId + " ve platform " + platform + " için entegrasyon yapılandırması bulunamadı."));
        try {
            String decryptedRefreshToken = encryptionService.decrypt(config.getRefreshToken());
            String decryptedClientSecret = encryptionService.decrypt(config.getClientSecret());

            ClientRegistration clientRegistration = clientRegistrationRepository.findByRegistrationId(platform.name().toLowerCase());
            if (clientRegistration == null) {
                throw new IllegalArgumentException("Platform: " + platform.name() + " için geçersiz istemci kayıt ID'si.");
            }

            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("grant_type", "refresh_token");
            params.add("client_id", clientRegistration.getClientId());
            params.add("client_secret", decryptedClientSecret);
            params.add("refresh_token", decryptedRefreshToken);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    clientRegistration.getProviderDetails().getTokenUri(),
                    HttpMethod.POST,
                    request,
                    Map.class
            );

            Map<String, Object> tokenResponse = response.getBody();
            if (tokenResponse == null || !tokenResponse.containsKey("access_token")) {
                throw new RuntimeException("Erişim token'ı yenilenemedi.");
            }

            String newAccessToken = (String) tokenResponse.get("access_token");
            Long expiresInSeconds = ((Number) tokenResponse.get("expires_in")).longValue();
            Instant newExpiresAt = Instant.now().plus(expiresInSeconds, ChronoUnit.SECONDS);
            String newScope = (String) tokenResponse.get("scope"); // Scope yenilemede değişebilir

            config.setAccessToken(encryptionService.encrypt(newAccessToken));
            config.setExpiresAt(newExpiresAt);
            config.setScope(newScope);
            updateConnectionStatus(config, IntegrationConnectionStatus.CONNECTED,
                    "Erişim token'ı başarıyla yenilendi.", null);
            integrationConfigRepository.save(config);

            logger.info("Organizasyon {} için platform {} erişim token'ı yenilendi.", organizationId, platform);
            return newAccessToken;
        } catch (Exception e) {
            updateConnectionStatus(config, IntegrationConnectionStatus.ERROR,
                    "Token yenileme başarısız oldu.", e.getMessage());
            integrationConfigRepository.save(config);
            throw e;
        }
    }


    /**
     * Google Lead Ads API'den lead'leri çeker.
     * Bu metod, Google Ads API istemci kütüphanesiyle tam olarak implemente edilmelidir.
     */
    @Transactional
    public LeadSyncResult fetchGoogleLeads(UUID organizationId) {
        IntegrationLog runLog = IntegrationLog.builder()
                .organizationId(organizationId)
                .platform(IntegrationPlatform.GOOGLE)
                .startedAt(Instant.now())
                .build();

        int createdCount = 0;
        int updatedCount = 0;
        List<Lead> result = new ArrayList<>();

        IntegrationConfig config = null;
        try {
            logger.info("Organizasyon {} için Google Lead'leri çekilmeye çalışılıyor.", organizationId);
            config = integrationConfigRepository.findByOrganizationIdAndPlatform(
                            organizationId, IntegrationPlatform.GOOGLE)
                    .orElseThrow(() -> new ResourceNotFoundException("Google entegrasyonu bu organizasyon için yapılandırılmadı."));

            String accessToken = encryptionService.decrypt(config.getAccessToken());
            if (accessToken == null) {
                throw new SecurityException("Google erişim token'ı çözülemedi.");
            }

            String customerId = config.getPlatformPageId();
            if (customerId == null) {
                throw new IllegalArgumentException("Google müşteri ID'si (platformPageId) eksik.");
            }

            String url = "https://googleads.googleapis.com/v14/customers/" + customerId + "/googleAds:searchStream";

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> query = new HashMap<>();
            query.put("query",
                    "SELECT "
                            + "lead_form_submission_data.resource_name, "
                            + "lead_form_submission_data.lead_form_submission_fields, "
                            + "lead_form_submission_data.asset, "
                            + "lead_form_submission_data.campaign, "
                            + "lead_form_submission_data.ad_group_ad, "
                            + "lead_form_submission_data.submission_date_time "
                            + "FROM lead_form_submission_data "
                            + "WHERE segments.date DURING LAST_7_DAYS");

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(query, headers);

            ResponseEntity<List> response = restTemplate.exchange(url, HttpMethod.POST, entity, List.class);
            List<Map<String, Object>> rows = response.getBody();

            if (rows == null) {
                logger.info("Google API boş döndü.");
                runLog.setTotalFetched(0);
                return new LeadSyncResult(0, 0, 0);
            }

            for (Map<String, Object> row : rows) {
                Map leadForm = (Map) row.get("leadFormSubmissionData");
                if (leadForm == null) continue;

                String resourceName = asString(leadForm.get("resourceName"));
                String campaign = asString(leadForm.get("campaign"));
                String submissionTime = asString(leadForm.get("submissionDateTime"));
                Instant platformCreatedAt = parseInstant(submissionTime);
                String leadId = resourceName;

                Optional<Lead> existingOpt = leadRepository.findByOrganizationIdAndPlatformAndSourceLeadId(
                        organizationId, IntegrationPlatform.GOOGLE, leadId);

                Lead lead;
                if (existingOpt.isPresent()) {
                    lead = existingOpt.get();
                    logger.info("Güncelleniyor: {}", leadId);
                    updatedCount++;
                } else {
                    lead = new Lead();
                    lead.setOrganizationId(organizationId);
                    lead.setPlatform(IntegrationPlatform.GOOGLE);
                    lead.setSourceLeadId(leadId);
                    lead.setStatus(LeadStatus.UNCONTACTED);
                    createdCount++;
                }

                List<Map<String, Object>> fields = (List<Map<String, Object>>) leadForm.get("leadFormSubmissionFields");
                Map<String, String> extraFields = new LinkedHashMap<>();
                for (Map<String, Object> f : fields) {
                    String fieldName = asString(f.get("fieldType"));
                    String fieldValue = asString(f.get("fieldValue"));
                    if (fieldName != null && fieldValue != null) {
                        switch (fieldName.toLowerCase()) {
                            case "full_name": case "name": lead.setName(fieldValue); break;
                            case "email": lead.setEmail(fieldValue); break;
                            case "phone_number": lead.setPhone(fieldValue); break;
                            default: extraFields.put(fieldName, fieldValue);
                        }
                    }
                }

                if (!extraFields.isEmpty()) {
                    try {
                        lead.setExtraFieldsJson(objectMapper.writeValueAsString(extraFields));
                    } catch (Exception ignored) {}
                }

                lead.setNotes("Google Lead Form'dan geldi: " + campaign);
                lead.setPlatformCreatedAt(platformCreatedAt);
                leadRepository.save(lead);
                autoAssignService.assignLeadAutomatically(lead);
                result.add(lead);
            }

            runLog.setTotalFetched(result.size());
            runLog.setNewCreated(createdCount);
            runLog.setUpdated(updatedCount);

            if (config != null) {
                config.setLastSyncedAt(Instant.now());
                updateConnectionStatus(config, IntegrationConnectionStatus.CONNECTED,
                        String.format("Google lead senkronizasyonu tamamlandı (yeni: %d, güncellenen: %d)",
                                createdCount, updatedCount), null);
                integrationConfigRepository.save(config);
            }

            logger.info("Google entegrasyonu tamamlandı: fetched={}, created={}, updated={}",
                    result.size(), createdCount, updatedCount);

        } catch (Exception e) {
            logger.error("Google Lead çekme hatası: {}", e.getMessage(), e);
            runLog.setErrorMessage(e.getMessage());
            if (config != null) {
                updateConnectionStatus(config, IntegrationConnectionStatus.ERROR,
                        "Google lead senkronizasyonu başarısız oldu.", e.getMessage());
                integrationConfigRepository.save(config);
            }
        } finally {
            runLog.setFinishedAt(Instant.now());
            integrationLogRepository.save(runLog);
        }

        return new LeadSyncResult(result.size(), createdCount, updatedCount);
    }


    /**
     * Facebook Lead Ads API'den lead'leri çeker.
     * Bu metod, Facebook Graph API ile tam olarak implemente edilmelidir.
     */
    @Transactional
    public LeadSyncResult fetchFacebookLeads(UUID organizationId) {
        IntegrationLog runLog = IntegrationLog.builder()
                .organizationId(organizationId)
                .platform(IntegrationPlatform.FACEBOOK)
                .startedAt(Instant.now())
                .build();

        int createdCount = 0;
        int updatedCount = 0;
        List<Lead> result = new ArrayList<>();

        IntegrationConfig config = null;
        try {
            config = integrationConfigRepository
                    .findByOrganizationIdAndPlatform(organizationId, IntegrationPlatform.FACEBOOK)
                    .orElseThrow(() -> new ResourceNotFoundException("Facebook entegrasyonu yok."));

            String pageId = config.getPlatformPageId();
            if (pageId == null || pageId.isBlank()) {
                throw new IllegalArgumentException("Facebook Sayfa ID eksik.");
            }

            String userAccessToken = encryptionService.decrypt(config.getAccessToken());
            if (userAccessToken == null) throw new SecurityException("User access token çözülemedi.");

            Instant lastSyncedLeadCreatedAt = config.getLastLeadCreatedTime();
            Instant latestFetchedLeadCreatedAt = lastSyncedLeadCreatedAt;

            // Sayfa access token
            String pageAccessToken = config.getPageAccessToken();
            boolean tokenFromApi = false;
            try {
                Map pageResp = restTemplate.getForObject(
                        "https://graph.facebook.com/v18.0/{pageId}?fields=access_token&access_token={uat}",
                        Map.class, pageId, userAccessToken
                );
                if (pageResp != null) {
                    String freshToken = (String) pageResp.get("access_token");
                    if (freshToken != null && !freshToken.isBlank()) {
                        pageAccessToken = freshToken;
                        tokenFromApi = true;
                    }
                }
            } catch (Exception ex) {
                logger.warn("Facebook sayfa access token alınırken hata: {}", ex.getMessage());
            }

            if (pageAccessToken == null || pageAccessToken.isBlank()) {
                throw new RuntimeException("Page access token alınamadı.");
            }

            if (tokenFromApi) {
                config.setPageAccessToken(pageAccessToken);
                config.setPageTokenUpdatedAt(Instant.now());
            }

            final String LEAD_FIELDS = String.join(",",
                    "id",
                    "created_time",
                    "ad_id","ad_name",
                    "adset_id","adset_name",
                    "campaign_id","campaign_name",
                    "form_id",
                    "is_organic",
                    "field_data",
                    "custom_disclaimer_responses"
            );

            // 1) Formlar
            String formsUrl = "https://graph.facebook.com/v18.0/" + pageId
                    + "/leadgen_forms?fields=id,name&limit=50&access_token=" + pageAccessToken;

            while (formsUrl != null) {
                ResponseEntity<Map> formsResponse = restTemplate.exchange(formsUrl, HttpMethod.GET, null, Map.class);
                Map body = formsResponse.getBody();
                List<Map<String,Object>> forms = body != null ? (List<Map<String,Object>>) body.get("data") : null;

                if (forms != null) {
                    for (Map<String,Object> form : forms) {
                        String formId = (String) form.get("id");
                        String formName = (String) form.get("name");
                        if (formId == null) continue;

                        // 2) Lead’ler (sayfalama)
                        String leadsUrl = "https://graph.facebook.com/v18.0/" + formId
                                + "/leads?fields=" + LEAD_FIELDS + "&limit=100&access_token=" + pageAccessToken;

                        while (leadsUrl != null) {
                            ResponseEntity<Map> leadsResp = restTemplate.exchange(leadsUrl, HttpMethod.GET, null, Map.class);
                            Map lbody = leadsResp.getBody();
                            List<Map<String,Object>> leads = lbody != null ? (List<Map<String,Object>>) lbody.get("data") : null;

                            if (leads != null) {
                                for (Map<String,Object> fbLead : leads) {
                                    String fbLeadId = asString(fbLead.get("id"));
                                    if (fbLeadId == null) continue;

                                    Optional<Lead> existingOpt = leadRepository.findByOrganizationIdAndPlatformAndSourceLeadId(
                                            organizationId, IntegrationPlatform.FACEBOOK, fbLeadId);

                                    if (existingOpt.isPresent()) {
                                        Lead existing = existingOpt.get();
                                        updateLeadFields(existing, fbLead, formName, formId, pageId);
                                        leadRepository.save(existing);
                                        autoAssignService.assignLeadAutomatically(existing);
                                        logger.info("Updated existing Facebook lead {}", fbLeadId);
                                        result.add(existing);
                                        updatedCount++;
                                        Instant leadCreated = existing.getPlatformCreatedAt();
                                        if (leadCreated != null && (latestFetchedLeadCreatedAt == null || leadCreated.isAfter(latestFetchedLeadCreatedAt))) {
                                            latestFetchedLeadCreatedAt = leadCreated;
                                        }
                                        continue;
                                    }

                                    Instant leadCreatedAt = parseInstant(asString(fbLead.get("created_time")));
                                    if (lastSyncedLeadCreatedAt != null
                                            && leadCreatedAt != null
                                            && leadCreatedAt.isBefore(lastSyncedLeadCreatedAt)) {
                                        logger.debug("Facebook lead {} daha önce senkronize edilmiş, atlanıyor.", fbLeadId);
                                        continue;
                                    }

                                    Lead entity = new Lead();
                                    entity.setOrganizationId(organizationId);
                                    entity.setPlatform(IntegrationPlatform.FACEBOOK);
                                    entity.setSourceLeadId(fbLeadId);

                                    updateLeadFields(entity, fbLead, formName, formId, pageId);

                                    leadRepository.save(entity);
                                    autoAssignService.assignLeadAutomatically(entity);
                                    result.add(entity);
                                    createdCount++;
                                    logger.info("Created new Facebook lead {}", fbLeadId);

                                    Instant leadCreated = entity.getPlatformCreatedAt();
                                    if (leadCreated != null && (latestFetchedLeadCreatedAt == null || leadCreated.isAfter(latestFetchedLeadCreatedAt))) {
                                        latestFetchedLeadCreatedAt = leadCreated;
                                    }
                                }
                            }

                            leadsUrl = nextUrl(lbody);
                        }
                    }
                }

                formsUrl = nextUrl(body);
            }

            runLog.setTotalFetched(result.size());
            runLog.setNewCreated(createdCount);
            runLog.setUpdated(updatedCount);
            config.setLastSyncedAt(Instant.now());
            if (latestFetchedLeadCreatedAt != null
                    && (config.getLastLeadCreatedTime() == null || latestFetchedLeadCreatedAt.isAfter(config.getLastLeadCreatedTime()))) {
                config.setLastLeadCreatedTime(latestFetchedLeadCreatedAt);
            }

            updateConnectionStatus(config, IntegrationConnectionStatus.CONNECTED,
                    String.format("Facebook lead senkronizasyonu tamamlandı (yeni: %d, güncellenen: %d)",
                            createdCount, updatedCount), null);

            integrationConfigRepository.save(config);

            logger.info("Facebook entegrasyonu tamamlandı: fetched={}, created={}, updated={}",
                    result.size(), createdCount, updatedCount);
        } catch (Exception e) {
            logger.error("Facebook entegrasyonu hata verdi: {}", e.getMessage(), e);
            runLog.setErrorMessage(e.getMessage());
            if (config != null) {
                updateConnectionStatus(config, IntegrationConnectionStatus.ERROR,
                        "Facebook lead senkronizasyonu başarısız oldu.", e.getMessage());
                integrationConfigRepository.save(config);
            }
        } finally {
            runLog.setFinishedAt(Instant.now());
            integrationLogRepository.save(runLog);
        }

        return new LeadSyncResult(result.size(), createdCount, updatedCount);
    }



    /**
     * Süresi dolmuş token'ları yenilemek ve aktif entegrasyonlara sahip tüm organizasyonlar için lead'leri çekmek için zamanlanmış görev.
     * Bu basit bir örnektir; gerçek bir uygulamada bunu daha sağlam bir şekilde yönetirsiniz (örn. organizasyon başına zamanlama).
     */

    @Scheduled(fixedRate = 3600000) // Her saat çalıştır (3600000 ms)
    public void scheduledLeadSync() {
        logger.info("Tüm organizasyonlar için zamanlanmış lead senkronizasyonu başlatılıyor.");
        List<IntegrationConfig> configs = integrationConfigRepository.findAll(); // Tüm yapılandırmaları çeker

        for (IntegrationConfig config : configs) {
            try {
                // Token'ın yenilenmesi gerekip gerekmediğini kontrol et
                if (config.getExpiresAt() != null && Instant.now().isAfter(config.getExpiresAt().minus(5, ChronoUnit.MINUTES))) {
                    logger.info("Organizasyon {} platform {} için token yenileniyor.", config.getOrganizationId(), config.getPlatform());
                    refreshAccessToken(config.getOrganizationId(), config.getPlatform());
                }

                // Platforma göre lead'leri çek
                if (config.getPlatform() == IntegrationPlatform.GOOGLE) {
                    fetchGoogleLeads(config.getOrganizationId());
                } else if (config.getPlatform() == IntegrationPlatform.FACEBOOK) {
                    fetchFacebookLeads(config.getOrganizationId());
                }
            } catch (Exception e) {
                logger.error("Organizasyon {} platform {} için zamanlanmış lead senkronizasyonu sırasında hata oluştu: {}",
                        config.getOrganizationId(), config.getPlatform(), e.getMessage());
            }
        }
        logger.info("Zamanlanmış lead senkronizasyonu tamamlandı.");
    }

    private void updateLeadFields(Lead lead, Map<String,Object> fbLead, String formName, String formId, String pageId) {
        String adId    = asString(fbLead.get("ad_id"));
        String adName  = asString(fbLead.get("ad_name"));
        String adsetId = asString(fbLead.get("adset_id"));
        String adsetNm = asString(fbLead.get("adset_name"));
        String campId  = asString(fbLead.get("campaign_id"));
        String campNm  = asString(fbLead.get("campaign_name"));
        Boolean organic = asBoolean(fbLead.get("is_organic"));

        String createdTimeStr = asString(fbLead.get("created_time"));
        Instant platformCreatedAt = parseInstant(createdTimeStr);

        boolean hasExistingName = !isBlank(lead.getName());
        String name = null;
        String email = null;
        String phone = null;
        String firstName = null;
        String lastName = null;
        Map<String, String> extraFields = new LinkedHashMap<>();
        Object fdObj = fbLead.get("field_data");
        if (fdObj instanceof List) {
            for (Map<String, Object> f : (List<Map<String, Object>>) fdObj) {
                String fname = asString(f.get("name"));
                String fval = firstValue(f.get("values"));
                if (fname == null) {
                    continue;
                }
                String normalized = normalizeFieldKey(fname);

                if (!isBlank(fval) && isNameFieldKey(normalized)) {
                    name = fval;
                    continue;
                }
                if (!isBlank(fval) && isFirstNameFieldKey(normalized)) {
                    firstName = fval;
                    continue;
                }
                if (!isBlank(fval) && isLastNameFieldKey(normalized)) {
                    lastName = fval;
                    continue;
                }
                if (!isBlank(fval) && isEmailFieldKey(normalized)) {
                    email = fval;
                    continue;
                }
                if (!isBlank(fval) && isPhoneFieldKey(normalized)) {
                    phone = fval;
                    continue;
                }

                if (fval != null) {
                    extraFields.put(fname, fval);
                }
            }
        }

        if (isBlank(name) && (!isBlank(firstName) || !isBlank(lastName))) {
            StringBuilder sb = new StringBuilder();
            if (!isBlank(firstName)) {
                sb.append(firstName.trim());
            }
            if (!isBlank(lastName)) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(lastName.trim());
            }
            name = sb.toString().trim();
        }

        if (isBlank(name)) {
            for (Map.Entry<String, String> entry : extraFields.entrySet()) {
                String normalized = normalizeFieldKey(entry.getKey());
                if (isNameFieldKey(normalized) && !isBlank(entry.getValue())) {
                    name = entry.getValue();
                    break;
                }
            }
        }

        if (isBlank(name) && !hasExistingName) {
            if (!isBlank(email)) {
                name = email;
            } else if (!isBlank(phone)) {
                name = phone;
            } else {
                String sourceId = lead.getSourceLeadId();
                name = sourceId != null ? "Facebook Lead " + sourceId : "Facebook Lead";
            }
        }

        if (!isBlank(name)) {
            lead.setName(name);
        }

        if (!isBlank(email)) {
            lead.setEmail(email);
        }
        if (!isBlank(phone)) {
            lead.setPhone(phone);
        }

        if (!extraFields.isEmpty()) {
            try {
                lead.setExtraFieldsJson(objectMapper.writeValueAsString(extraFields));
            } catch (Exception e) {
                logger.warn("Facebook lead extra alanları JSON'a dönüştürülemedi: {}", e.getMessage());
            }
        }

        Object disclaimerResponses = fbLead.get("custom_disclaimer_responses");
        if (disclaimerResponses != null) {
            try {
                lead.setDisclaimerResponsesJson(objectMapper.writeValueAsString(disclaimerResponses));
            } catch (Exception e) {
                logger.warn("Facebook lead disclaimer yanıtları JSON'a dönüştürülemedi: {}", e.getMessage());
            }
        }

        lead.setFormId(formId);
        lead.setFormName(formName);
        lead.setPageId(pageId);
        lead.setAdId(adId);
        lead.setAdName(adName);
        lead.setAdsetId(adsetId);
        lead.setAdsetName(adsetNm);
        lead.setFbCampaignId(campId);
        lead.setFbCampaignName(campNm);
        lead.setOrganic(organic);
        lead.setPlatformCreatedAt(platformCreatedAt);

        if ((lead.getNotes() == null || lead.getNotes().isBlank()) && formName != null) {
            lead.setNotes("Facebook formu: " + formName);
        }

        if (lead.getLanguage() == null && lead.getNotes() != null) {
            try {
                lead.setLanguage(detectLanguageFromText(lead.getNotes()));
            } catch (Exception ignored) {
            }
        }

        // Status sadece ilk kayıt sırasında NEW atanmalı, update sırasında dokunma
        if (lead.getStatus() == null) {
            lead.setStatus(LeadStatus.UNCONTACTED);
        }
    }

}
