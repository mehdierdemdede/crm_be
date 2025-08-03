// src/main/java/com/leadsyncpro/model/IntegrationConfig.java
package com.leadsyncpro.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Column;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "integration_configs", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"organization_id", "platform"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
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
    private String accessToken; // Encrypted User Access Token

    @Column(name = "refresh_token", columnDefinition = "TEXT")
    private String refreshToken; // Encrypted refresh token (if available)

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(columnDefinition = "TEXT")
    private String scope;

    @Column(name = "client_id", columnDefinition = "TEXT")
    private String clientId; // For reference/verification

    @Column(name = "client_secret", columnDefinition = "TEXT")
    private String clientSecret; // Encrypted, for refresh token usage

    @Column(name = "created_by")
    private UUID createdBy; // User who initiated this integration

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "platform_page_id", columnDefinition = "TEXT")
    private String platformPageId; // Facebook için seçilen sayfa ID'si

    // YENİ EKLENEN KISIM: Sayfa Erişim Token'ı
    @Column(name = "page_access_token", columnDefinition = "TEXT")
    private String pageAccessToken; // Encrypted Page Access Token

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