package com.leadsyncpro.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "integration_logs",
        indexes = {
                @Index(name = "idx_intlog_org_platform", columnList = "organization_id, platform"),
                @Index(name = "idx_intlog_started", columnList = "started_at")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntegrationLog {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private IntegrationPlatform platform;

    @Column(name = "total_fetched")
    private int totalFetched;

    @Column(name = "new_created")
    private int newCreated;

    @Column(name = "updated")
    private int updated;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @PrePersist
    protected void onCreate() {
        if (this.startedAt == null)
            this.startedAt = Instant.now();
    }
}
