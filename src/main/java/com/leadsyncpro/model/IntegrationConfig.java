package com.leadsyncpro.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "integration_configs",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"organization_id", "platform"})
        },
        indexes = {
                @Index(name = "idx_intconfig_org_platform", columnList = "organization_id, platform"),
                @Index(name = "idx_intconfig_last_synced", columnList = "last_synced_at")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntegrationConfig {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private IntegrationPlatform platform; // GOOGLE, FACEBOOK

    @Column(name = "access_token", nullable = false, columnDefinition = "TEXT")
    private String accessToken;

    @Column(name = "refresh_token", columnDefinition = "TEXT")
    private String refreshToken;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(columnDefinition = "TEXT")
    private String scope;

    @Column(name = "client_id", columnDefinition = "TEXT")
    private String clientId;

    @Column(name = "client_secret", columnDefinition = "TEXT")
    private String clientSecret;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "platform_page_id", columnDefinition = "TEXT")
    private String platformPageId;

    @Column(name = "page_access_token", columnDefinition = "TEXT")
    private String pageAccessToken;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "last_lead_created_time")
    private Instant lastLeadCreatedTime;

    @Column(name = "page_token_updated_at")
    private Instant pageTokenUpdatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_status", nullable = false, length = 40)
    @Builder.Default
    private IntegrationConnectionStatus connectionStatus = IntegrationConnectionStatus.DISCONNECTED;

    @Column(name = "status_message", columnDefinition = "TEXT")
    private String statusMessage;

    @Column(name = "last_error_at")
    private Instant lastErrorAt;

    @Column(name = "last_error_message", columnDefinition = "TEXT")
    private String lastErrorMessage;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (connectionStatus == null) {
            connectionStatus = IntegrationConnectionStatus.DISCONNECTED;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
