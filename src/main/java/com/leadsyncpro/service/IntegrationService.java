// src/main/java/com/leadsyncpro/service/IntegrationService.java
package com.leadsyncpro.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pemistahl.lingua.api.Language;
import com.github.pemistahl.lingua.api.LanguageDetector;
import com.github.pemistahl.lingua.api.LanguageDetectorBuilder;
import com.leadsyncpro.exception.ResourceNotFoundException;
import com.leadsyncpro.model.IntegrationConfig;
import com.leadsyncpro.model.IntegrationPlatform;
import com.leadsyncpro.model.Lead;
import com.leadsyncpro.model.LeadStatus;
import com.leadsyncpro.repository.CampaignRepository;
import com.leadsyncpro.repository.IntegrationConfigRepository;
import com.leadsyncpro.repository.LeadRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;


@Service
public class IntegrationService {

    private static final Logger logger = LoggerFactory.getLogger(IntegrationService.class);

    private final IntegrationConfigRepository integrationConfigRepository;
    private final EncryptionService encryptionService;
    private final ClientRegistrationRepository clientRegistrationRepository;
    private final RestTemplate restTemplate;
    private final LeadRepository leadRepository;
    private final CampaignRepository campaignRepository;
    private final ObjectMapper objectMapper;


    @Value("${app.encryption.key}")
    private String encryptionKey;

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


    public IntegrationService(IntegrationConfigRepository integrationConfigRepository,
                              EncryptionService encryptionService,
                              ClientRegistrationRepository clientRegistrationRepository,
                              LeadRepository leadRepository,
                              CampaignRepository campaignRepository) {
        this.integrationConfigRepository = integrationConfigRepository;
        this.encryptionService = encryptionService;
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.restTemplate = new RestTemplate();
        this.leadRepository = leadRepository;
        this.campaignRepository = campaignRepository;
        this.objectMapper = new ObjectMapper();
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

        return integrationConfigRepository.save(config);
    }

    public Optional<IntegrationConfig> getIntegrationConfig(UUID organizationId, IntegrationPlatform platform) {
        return integrationConfigRepository.findByOrganizationIdAndPlatform(organizationId, platform);
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
    public String getAuthorizationUrl(String registrationId, UUID organizationId, UUID userId) {
        ClientRegistration clientRegistration = clientRegistrationRepository.findByRegistrationId(registrationId);
        if (clientRegistration == null) {
            throw new IllegalArgumentException("Geçersiz istemci kayıt ID'si: " + registrationId);
        }

        String state = organizationId + "|" + userId;

        String redirectUri = clientRegistration.getRedirectUri();
        if (redirectUri.contains("{baseUrl}")) {
            redirectUri = redirectUri.replace("{baseUrl}", "http://localhost:8080");
        }

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
    public void handleOAuth2Callback(String registrationId, String code, String state) {
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

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", clientRegistration.getClientId());
        params.add("client_secret", clientRegistration.getClientSecret());
        String redirectUri = clientRegistration.getRedirectUri();
        if (redirectUri.contains("{baseUrl}")) {
            redirectUri = redirectUri.replace("{baseUrl}", "http://localhost:8080");
        }
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

        IntegrationConfig config = integrationConfigRepository.findByOrganizationIdAndPlatform(
                        organizationId, IntegrationPlatform.valueOf(registrationId.toUpperCase()))
                .orElse(new IntegrationConfig());

        config.setOrganizationId(organizationId);
        config.setPlatform(IntegrationPlatform.valueOf(registrationId.toUpperCase()));
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

        integrationConfigRepository.save(config);
        logger.info("OAuth2 entegrasyonu organizasyon {} için platform {} ile başarıyla tamamlandı.", organizationId, registrationId);
    }

    /**
     * Süresi dolmuş token'ları yenilemek ve aktif entegrasyonlara sahip tüm organizasyonlar için lead'leri çekmek için zamanlanmış görev.
     * Bu basit bir örnektir; gerçek bir uygulamada bunu daha sağlam bir şekilde yönetirsiniz (örn. organizasyon başına zamanlama).
     */

    @Transactional
    public String refreshAccessToken(UUID organizationId, IntegrationPlatform platform) {
        IntegrationConfig config = integrationConfigRepository.findByOrganizationIdAndPlatform(organizationId, platform)
                .orElseThrow(() -> new ResourceNotFoundException("Organizasyon " + organizationId + " ve platform " + platform + " için entegrasyon yapılandırması bulunamadı."));

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

        // Saklanan yapılandırmayı güncelle
        config.setAccessToken(encryptionService.encrypt(newAccessToken));
        config.setExpiresAt(newExpiresAt);
        config.setScope(newScope);
        integrationConfigRepository.save(config);

        logger.info("Organizasyon {} için platform {} erişim token'ı yenilendi.", organizationId, platform);
        return newAccessToken;
    }


    /**
     * Google Lead Ads API'den lead'leri çeker.
     * Bu metod, Google Ads API istemci kütüphanesiyle tam olarak implemente edilmelidir.
     */
    public List<Lead> fetchGoogleLeads(UUID organizationId) {
        logger.info("Organizasyon {} için Google Lead'leri çekilmeye çalışılıyor.", organizationId);
        IntegrationConfig config = integrationConfigRepository.findByOrganizationIdAndPlatform(organizationId, IntegrationPlatform.GOOGLE)
                .orElseThrow(() -> new ResourceNotFoundException("Google entegrasyonu bu organizasyon için yapılandırılmadı."));

        String accessToken = encryptionService.decrypt(config.getAccessToken());
        if (accessToken == null) {
            throw new SecurityException("Google erişim token'ı çözülemedi.");
        }

        // TODO: Buraya gerçek Google Ads API çağrıları implemente edilmelidir.
        // Bu kısım Google Ads API'nin detaylı kullanımına göre yazılmalıdır.
        // Müşteri ID'nizi ve sorgunuzu doğru şekilde ayarlamanız gerekir.
        // config.getPlatformPageId() Google müşteri ID'si olarak kullanılabilir.
        logger.warn("Google Lead çekme işlemi yer tutucudur. Gerçek Google Ads API entegrasyonu gereklidir.");
        return Collections.emptyList(); // Yer tutucu için boş liste döndür
    }

    /**
     * Facebook Lead Ads API'den lead'leri çeker.
     * Bu metod, Facebook Graph API ile tam olarak implemente edilmelidir.
     */
    public List<Lead> fetchFacebookLeads(UUID organizationId) {
        IntegrationConfig config = integrationConfigRepository
                .findByOrganizationIdAndPlatform(organizationId, IntegrationPlatform.FACEBOOK)
                .orElseThrow(() -> new ResourceNotFoundException("Facebook entegrasyonu yok."));

        String pageId = config.getPlatformPageId();
        if (pageId == null || pageId.isBlank()) {
            throw new IllegalArgumentException("Facebook Sayfa ID eksik.");
        }


        String userAccessToken = encryptionService.decrypt(config.getAccessToken());
        if (userAccessToken == null) throw new SecurityException("User access token çözülemedi.");

        // Sayfa access token
        String pageAccessToken = null;
        try {
            Map pageResp = restTemplate.getForObject(
                    "https://graph.facebook.com/v18.0/{pageId}?fields=access_token&access_token={uat}",
                    Map.class, pageId, userAccessToken
            );
            if (pageResp != null) pageAccessToken = (String) pageResp.get("access_token");
        } catch (Exception ignored) {}
        if (pageAccessToken == null) throw new RuntimeException("Page access token alınamadı.");

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

        List<Lead> result = new ArrayList<>();

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
                                // --- Idempotency
                                String fbLeadId = asString(fbLead.get("id"));
                                if (fbLeadId != null && leadRepository.existsByOrganizationIdAndPlatformAndSourceLeadId(
                                        organizationId, IntegrationPlatform.FACEBOOK, fbLeadId)) {
                                    continue;
                                }

                                // --- Temel alanlar
                                String createdTimeStr = asString(fbLead.get("created_time"));
                                Instant platformCreatedAt = parseInstant(createdTimeStr);

                                String adId    = asString(fbLead.get("ad_id"));
                                String adName  = asString(fbLead.get("ad_name"));
                                String adsetId = asString(fbLead.get("adset_id"));
                                String adsetNm = asString(fbLead.get("adset_name"));
                                String campId  = asString(fbLead.get("campaign_id"));
                                String campNm  = asString(fbLead.get("campaign_name"));
                                Boolean organic = asBoolean(fbLead.get("is_organic"));

                                // --- field_data: name/email/phone + kalanları extras’a
                                String name = null, email = null, phone = null, firstName = null, lastName = null;
                                Map<String,Object> extras = new LinkedHashMap<>();

                                Object fdObj = fbLead.get("field_data");
                                if (fdObj instanceof List) {
                                    List<Map<String,Object>> fieldData = (List<Map<String,Object>>) fdObj;
                                    for (Map<String,Object> f : fieldData) {
                                        String fname = asString(f.get("name"));
                                        String fval  = firstValue(f.get("values"));
                                        if (fname == null) continue;

                                        switch (fname.toLowerCase()) {
                                            case "full_name":
                                            case "name":        if (isBlank(name)) name = fval; break;
                                            case "first_name":  firstName = fval; break;
                                            case "last_name":   lastName  = fval; break;
                                            case "email":       email     = fval; break;
                                            case "phone":
                                            case "phone_number":phone     = fval; break;
                                            case "language":    /* dil alanın varsa */ ; break;
                                            default:
                                                // custom alanları kaçırma
                                                if (fval != null) extras.put(fname, fval);
                                        }
                                    }
                                }
                                if (isBlank(name) && (!isBlank(firstName) || !isBlank(lastName))) {
                                    name = (safe(firstName) + " " + safe(lastName)).trim();
                                }
                                if (isBlank(name)) {
                                    name = !isBlank(email) ? email :
                                            !isBlank(phone) ? phone :
                                                    "Facebook Lead" + (isBlank(formName) ? "" : " - " + formName)
                                                            + (fbLeadId != null ? " (" + fbLeadId + ")" : "");
                                }

                                // --- disclaimer_responses JSON
                                String disclaimerJson = null;
                                try {
                                    Object dr = fbLead.get("custom_disclaimer_responses");
                                    if (dr != null) disclaimerJson = objectMapper.writeValueAsString(dr);
                                } catch (Exception ignored) {}

                                // --- extras JSON
                                String extrasJson = null;
                                try {
                                    if (!extras.isEmpty()) extrasJson = objectMapper.writeValueAsString(extras);
                                } catch (Exception ignored) {}
                                String langCode = detectLanguageFromText(formName);

                                // --- Persist
                                Lead entity = new Lead();
                                if (langCode != null) {
                                    entity.setLanguage(langCode);
                                }
                                entity.setOrganizationId(organizationId);
                                entity.setPlatform(IntegrationPlatform.FACEBOOK);
                                entity.setSourceLeadId(fbLeadId);
                                entity.setName(name);
                                entity.setEmail(email);
                                entity.setPhone(phone);
                                entity.setNotes("Facebook Lead formdan: " + safe(formName) + " - Form ID: " + formId);
                                entity.setStatus(LeadStatus.NEW);

                                // geniş alanlar (entity’nde varsa)
                                entity.setPlatformCreatedAt(platformCreatedAt);
                                entity.setPageId(pageId);
                                entity.setFormId(formId);
                                entity.setFormName(formName);
                                entity.setOrganic(organic);
                                entity.setAdId(adId);
                                entity.setAdName(adName);
                                entity.setAdsetId(adsetId);
                                entity.setAdsetName(adsetNm);
                                entity.setFbCampaignId(campId);
                                entity.setFbCampaignName(campNm);
                                entity.setDisclaimerResponsesJson(disclaimerJson);
                                entity.setExtraFieldsJson(extrasJson);

                                leadRepository.save(entity);
                                result.add(entity);
                            }
                        }

                        // leads paging
                        leadsUrl = nextUrl(lbody);
                    }
                }
            }

            // forms paging
            formsUrl = nextUrl(body);
        }

        return result;
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
}
