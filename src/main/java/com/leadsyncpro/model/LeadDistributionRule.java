package com.leadsyncpro.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lead_distribution_rules", uniqueConstraints = {
        @UniqueConstraint(name = "uq_lead_distribution_rule",
                columnNames = {"organization_id", "platform", "page_id", "campaign_id", "adset_id", "ad_id"})
}, indexes = {
        @Index(name = "idx_lead_distribution_rule_org_platform", columnList = "organization_id, platform")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeadDistributionRule {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 50)
    private IntegrationPlatform platform;

    @Column(name = "page_id", nullable = false, length = 100)
    private String pageId;

    @Column(name = "page_name", length = 255)
    private String pageName;

    @Column(name = "campaign_id", nullable = false, length = 100)
    private String campaignId;

    @Column(name = "campaign_name", length = 255)
    private String campaignName;

    @Column(name = "adset_id", nullable = false, length = 100)
    private String adsetId;

    @Column(name = "adset_name", length = 255)
    private String adsetName;

    @Column(name = "ad_id", nullable = false, length = 100)
    private String adId;

    @Column(name = "ad_name", length = 255)
    private String adName;

    @Column(name = "current_index")
    private Integer currentIndex;

    @Column(name = "current_count")
    private Integer currentCount;

    @Version
    private Long version;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (currentIndex == null) {
            currentIndex = 0;
        }
        if (currentCount == null) {
            currentCount = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}

