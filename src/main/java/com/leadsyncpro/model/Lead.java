package com.leadsyncpro.model;

import lombok.*;
import org.hibernate.annotations.GenericGenerator;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "leads",
        indexes = {
                // 🔹 Entegrasyon duplicate kontrolü (Facebook/Google)
                @Index(name = "idx_leads_org_platform_sourceid", columnList = "organization_id, platform, source_lead_id"),
                // 🔹 Organizasyon + status sorguları (dashboard / filtre)
                @Index(name = "idx_leads_org_status", columnList = "organization_id, status"),
                // 🔹 Kullanıcı bazlı lead listesi (assign ve kullanıcı dashboard)
                @Index(name = "idx_leads_assigned_user", columnList = "assigned_to_user_id")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Lead {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private String name;

    @Column(length = 50)
    private String phone;

    @Column(length = 255)
    private String email;

    @Column(length = 50)
    private String language;

    @Column(columnDefinition = "TEXT")
    private String notes;

    // 🔹 İlişkilendirilmiş kampanya (nullable olabilir)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id")
    private Campaign campaign;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private LeadStatus status;

    // 🔹 Lead hangi kullanıcıya atanmış
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_user_id")
    private User assignedToUser;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "first_action_at")
    private Instant firstActionAt;

    // ------------------- Facebook / Google API alanları -------------------

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", length = 50, nullable = false)
    private IntegrationPlatform platform; // FACEBOOK | GOOGLE

    @Column(name = "source_lead_id", length = 100)
    private String sourceLeadId; // platform lead id

    @Column(name = "platform_created_at")
    private Instant platformCreatedAt; // created_time

    @Column(name = "page_id", length = 100)
    private String pageId;

    @Column(name = "form_id", length = 100)
    private String formId;

    @Column(name = "form_name", length = 255)
    private String formName;

    @Column(name = "is_organic")
    private Boolean organic;

    @Column(name = "ad_id", length = 100)
    private String adId;

    @Column(name = "ad_name", length = 255)
    private String adName;

    @Column(name = "adset_id", length = 100)
    private String adsetId;

    @Column(name = "adset_name", length = 255)
    private String adsetName;

    @Column(name = "fb_campaign_id", length = 100)
    private String fbCampaignId;

    @Column(name = "fb_campaign_name", length = 255)
    private String fbCampaignName;

    @Column(name = "extra_fields_json", columnDefinition = "TEXT")
    private String extraFieldsJson;

    @Column(name = "disclaimer_responses_json", columnDefinition = "TEXT")
    private String disclaimerResponsesJson;

    // ------------------- Otomatik timestamp -------------------

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
