package com.leadsyncpro.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "leads",
        indexes = {
                @Index(name = "idx_leads_org_platform_sourceid", columnList = "organization_id,platform,source_lead_id")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
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

    @ManyToOne
    @JoinColumn(name = "campaign_id")
    private Campaign campaign;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private LeadStatus status;

    @ManyToOne
    @JoinColumn(name = "assigned_to_user_id")
    private User assignedToUser;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    // ------------------- Facebook Graph API ek alanlar -------------------

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", length = 50, nullable = false)
    private IntegrationPlatform platform; // FACEBOOK | GOOGLE

    @Column(name = "source_lead_id", length = 100)
    private String sourceLeadId; // FB lead id

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
