// src/main/java/com/leadsyncpro/service/IntegrationService.java
package com.leadsyncpro.service;

import com.leadsyncpro.exception.ResourceNotFoundException;
import com.leadsyncpro.model.IntegrationConfig;
import com.leadsyncpro.model.IntegrationPlatform;
import com.leadsyncpro.model.Lead; // Kendi Lead sınıfımız
import com.leadsyncpro.model.LeadStatus;
import com.leadsyncpro.repository.IntegrationConfigRepository;
import com.leadsyncpro.repository.LeadRepository;
import com.leadsyncpro.repository.CampaignRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// RestFB importları
import com.restfb.DefaultFacebookClient;
import com.restfb.FacebookClient;
import com.restfb.Version;
import com.restfb.types.ads.LeadgenForm;
import com.restfb.Connection;
import com.restfb.types.Page;
import com.restfb.Parameter;



// Jackson for JSON parsing (RestTemplate ile Map dönüşümü için gerekli)
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;


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

        // organizationId ve userId'yi güvenli bir şekilde aktarmak için state parametresi kullan
        String state = organizationId.toString() + "|" + userId.toString();

        // redirectUri'deki {baseUrl} placeholder'ını manuel olarak değiştir
        String redirectUri = clientRegistration.getRedirectUri();
        if (redirectUri.contains("{baseUrl}")) {
            redirectUri = redirectUri.replace("{baseUrl}", "http://localhost:8080");
        }

        // Scope'ları virgülle ayrılmış String'e dönüştür ve küçük harfe çevir
        String scopes = clientRegistration.getScopes().stream()
                .map(String::toLowerCase)
                .collect(Collectors.joining(","));


        OAuth2AuthorizationRequest authorizationRequest = OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri(clientRegistration.getProviderDetails().getAuthorizationUri())
                .clientId(clientRegistration.getClientId())
                .redirectUri(redirectUri)
                .scope(scopes)
                .state(state)
                .build();

        return authorizationRequest.getAuthorizationRequestUri();
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

        // State'ten organizationId ve userId'yi çıkar
        String[] stateParts = state.split("\\|");
        if (stateParts.length != 2) {
            logger.error("OAuth2 Callback Hatası: Geçersiz state parametre formatı: {}", state);
            throw new IllegalArgumentException("Geçersiz state parametre formatı.");
        }
        UUID organizationId = UUID.fromString(stateParts[0]);
        UUID userId = UUID.fromString(stateParts[1]);

        logger.info("Platform: {} için organizasyon: {} için OAuth2 Callback alındı", registrationId, organizationId);

        // Yetkilendirme kodunu token'larla değiştirmek için istek hazırla
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
        if (tokenResponse == null) {
            logger.error("OAuth2 Callback Hatası: {} platformu için token yanıt gövdesi boş.", registrationId);
            throw new RuntimeException("Erişim token'ı alınamadı: Boş yanıt.");
        }
        if (!tokenResponse.containsKey("access_token")) {
            logger.error("OAuth2 Callback Hatası: {} platformu için yanıtta erişim token'ı bulunamadı. Yanıt: {}", registrationId, tokenResponse);
            throw new RuntimeException("Erişim token'ı alınamadı: Yanıtta access_token yok.");
        }

        String accessToken = (String) tokenResponse.get("access_token");
        String refreshToken = (String) tokenResponse.get("refresh_token");

        // expires_in'i daha güvenli bir şekilde al
        Long expiresInSeconds = null;
        Object expiresInObj = tokenResponse.get("expires_in");
        if (expiresInObj instanceof Number) {
            expiresInSeconds = ((Number) expiresInObj).longValue();
        } else if (expiresInObj instanceof String) {
            try {
                expiresInSeconds = Long.parseLong((String) expiresInObj);
            } catch (NumberFormatException e) {
                logger.warn("OAuth2 Callback Uyarısı: '{}' platformu için 'expires_in' string ama geçerli bir sayı değil: {}", registrationId, expiresInObj);
            }
        } else {
            logger.warn("OAuth2 Callback Uyarısı: '{}' platformu için 'expires_in' beklenmeyen tipte: {}", registrationId, expiresInObj != null ? expiresInObj.getClass().getName() : "null");
        }

        Instant expiresAt = (expiresInSeconds != null) ? Instant.now().plus(expiresInSeconds, ChronoUnit.SECONDS) : null;
        String scope = (String) tokenResponse.get("scope");

        // Entegrasyon yapılandırmasını al veya oluştur
        IntegrationConfig config = integrationConfigRepository.findByOrganizationIdAndPlatform(organizationId, IntegrationPlatform.valueOf(registrationId.toUpperCase()))
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

        // Facebook için sayfa ID'sini çek ve kaydet
        if (registrationId.equalsIgnoreCase("facebook")) {
            try {
                // DÜZELTİLEN KISIM: RestFB yerine doğrudan RestTemplate ile me/accounts çağrısı
                // 'perms' alanı doğrudan sorgulanmadığı için hata vermeyecek.
                String pagesApiUrl = "https://graph.facebook.com/v18.0/me/accounts?access_token=" + accessToken + "&fields=id,name,access_token"; // 'perms' alanı kaldırıldı, access_token eklendi
                ResponseEntity<Map> pagesResponse = restTemplate.exchange(pagesApiUrl, HttpMethod.GET, null, Map.class);
                List<Map<String, Object>> pagesData = (List<Map<String, Object>>) pagesResponse.getBody().get("data");

                String selectedPageId = null;
                if (pagesData != null) {
                    for (Map<String, Object> pageMap : pagesData) {
                        String pageAccessToken = (String) pageMap.get("access_token");
                        if (pageAccessToken != null) {
                            // Debug token endpoint'ini kullanarak sayfa token'ının izinlerini kontrol et
                            String debugTokenUrl = "https://graph.facebook.com/debug_token?input_token=" + pageAccessToken + "&access_token=" + accessToken; // Kullanıcının ana access_token'ı debug_token için gerekli
                            ResponseEntity<Map> debugResponse = restTemplate.exchange(debugTokenUrl, HttpMethod.GET, null, Map.class);
                            Map<String, Object> debugData = (Map<String, Object>) debugResponse.getBody().get("data");

                            if (debugData != null && debugData.containsKey("scopes")) {
                                List<String> scopes = (List<String>) debugData.get("scopes");
                                if (scopes != null && scopes.contains("manage_leads")) { // Facebook API'de 'manage_leads' küçük harf
                                    selectedPageId = (String) pageMap.get("id");
                                    logger.info("MANAGE_LEADS iznine sahip Facebook sayfası bulundu: {} ({})", pageMap.get("name"), pageMap.get("id"));
                                    break;
                                }
                            }
                        }
                    }
                }
                if (selectedPageId != null) {
                    config.setPlatformPageId(selectedPageId);
                    logger.info("Organizasyon {} için Facebook Sayfa ID'si kaydedildi: {}", organizationId, selectedPageId);
                } else {
                    logger.warn("Organizasyon {} için MANAGE_LEADS iznine sahip Facebook sayfası bulunamadı. Lead'ler çekilemiyor.", organizationId);
                }

            } catch (Exception e) {
                logger.error("Organizasyon {} için Facebook sayfaları çekilirken hata oluştu: {}", organizationId, e.getMessage());
            }
        }
        // Google için de benzer şekilde müşteri ID'si çekilebilir (Google Ads API'ye özgü)

        integrationConfigRepository.save(config);

        logger.info("OAuth2 entegrasyonu organizasyon {} için platform {} ile başarılı oldu.", organizationId, registrationId);
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



        logger.info("Organizasyon {} için Facebook Lead'leri çekilmeye çalışılıyor.", organizationId);
        IntegrationConfig config = integrationConfigRepository.findByOrganizationIdAndPlatform(organizationId, IntegrationPlatform.FACEBOOK)
                .orElseThrow(() -> new ResourceNotFoundException("Facebook entegrasyonu bu organizasyon için yapılandırılmadı."));

        String accessToken = encryptionService.decrypt(config.getAccessToken());
        if (accessToken == null) {
            throw new SecurityException("Facebook erişim token'ı çözülemedi.");
        }
        // Sayfa ID'sini config'den al
        String facebookPageId = config.getPlatformPageId();
        facebookPageId = "2018242931803116";
        if (facebookPageId == null || facebookPageId.isEmpty()) {
            logger.error("Organizasyon {} için Facebook Sayfa ID'si yapılandırılmadı. Lead'ler çekilemiyor.", organizationId);
            throw new IllegalArgumentException("Entegrasyon yapılandırmasında Facebook Sayfa ID'si eksik. OAuth sırasında MANAGE_LEADS iznine sahip bir sayfa seçtiğinizden emin olun.");
        }


        try {
            // DÜZELTİLEN KISIM: RestFB yerine doğrudan RestTemplate ile leadgen_forms ve leads çekme
            // LeadgenForm ve Lead (RestFB tipleri) yerine doğrudan Map olarak işleme
            String leadgenFormsApiUrl = "https://graph.facebook.com/v18.0/" + facebookPageId + "/leadgen_forms?access_token=" + accessToken + "&fields=id,name,leads";
            ResponseEntity<Map> formsResponse = restTemplate.exchange(leadgenFormsApiUrl, HttpMethod.GET, null, Map.class);
            List<Map<String, Object>> formsData = (List<Map<String, Object>>) formsResponse.getBody().get("data");

            List<Lead> fetchedLeads = new java.util.ArrayList<>();
            if (formsData != null) {
                for (Map<String, Object> formMap : formsData) {
                    String formId = (String) formMap.get("id");
                    String formName = (String) formMap.get("name");
                    logger.info("Facebook Leadgen Form işleniyor: {} ({})", formName, formId);

                    // Her formun lead'lerini çek
                    Map<String, Object> leadsConnection = (Map<String, Object>) formMap.get("leads");
                    if (leadsConnection != null && leadsConnection.containsKey("data")) {
                        List<Map<String, Object>> facebookLeadsData = (List<Map<String, Object>>) leadsConnection.get("data");
                        for (Map<String, Object> fbLeadData : facebookLeadsData) {
                            Lead newLead = new Lead();
                            newLead.setOrganizationId(organizationId);

                            // Facebook lead alanlarını ayrıştır
                            if (fbLeadData.containsKey("field_data")) {
                                List<Map<String, String>> fieldDataList = (List<Map<String, String>>) fbLeadData.get("field_data");
                                if (fieldDataList != null) {
                                    for (Map<String, String> field : fieldDataList) {
                                        String fieldName = field.get("name");
                                        // DÜZELTİLEN KISIM: field.get("values")'tan gelen değeri daha güvenli al
                                        Object valuesObj = field.get("values");
                                        String fieldValue = null;
                                        if (valuesObj instanceof List && !((List<?>)valuesObj).isEmpty()) {
                                            fieldValue = ((List<?>)valuesObj).get(0).toString();
                                        } else if (valuesObj instanceof String) {
                                            fieldValue = (String) valuesObj;
                                        }

                                        if ("full_name".equalsIgnoreCase(fieldName) || "name".equalsIgnoreCase(fieldName)) {
                                            newLead.setName(fieldValue);
                                        } else if ("email".equalsIgnoreCase(fieldName)) {
                                            newLead.setEmail(fieldValue);
                                        } else if ("phone_number".equalsIgnoreCase(fieldName) || "phone".equalsIgnoreCase(fieldName)) {
                                            newLead.setPhone(fieldValue);
                                        }
                                        // Diğer alanları da burada işleyebilirsiniz
                                    }
                                }
                            }

                            newLead.setNotes("Facebook Lead formdan: " + formName + " - Form ID: " + formId);
                            newLead.setStatus(LeadStatus.NEW); // Varsayılan durum
                            // created_time'ı Instant'a dönüştürme (eğer çekiliyorsa)
                            // String createdTimeString = (String) fbLeadData.get("created_time");
                            // if (createdTimeString != null) {
                            //     try {
                            //         newLead.setCreatedAt(Instant.parse(createdTimeString));
                            //     } catch (Exception e) {
                            //         logger.warn("Could not parse created_time: {}", createdTimeString, e);
                            //     }
                            // }

                            leadRepository.save(newLead);
                            fetchedLeads.add(newLead);
                            logger.info("Facebook Lead kaydedildi: {}", newLead.getName());
                        }
                    }
                }
            }
            return fetchedLeads;

        } catch (Exception e) {
            logger.error("Organizasyon {} için Facebook Lead'leri çekilirken hata oluştu: {}", organizationId, e.getMessage());
            throw new RuntimeException("Facebook Lead'leri çekilirken hata oluştu: " + e.getMessage(), e);
        }
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
