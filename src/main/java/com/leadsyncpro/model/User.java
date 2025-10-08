package com.leadsyncpro.model;

import lombok.*;
import org.hibernate.annotations.GenericGenerator;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import java.util.Set;

@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"organization_id", "email"})
        },
        indexes = {
                @Index(name = "idx_user_org_role", columnList = "organization_id, role"),
                @Index(name = "idx_user_org_active", columnList = "organization_id, is_active")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "is_active")
    @Builder.Default
    private boolean isActive = true;

    @ElementCollection(targetClass = SupportedLanguages.class)
    @Enumerated(EnumType.STRING)
    @Column(name = "supported_languages")
    private Set<SupportedLanguages> supportedLanguages;

    @Column(name = "daily_capacity")
    private Integer dailyCapacity;

    @Column(name = "auto_assign_enabled")
    @Builder.Default
    private boolean autoAssignEnabled = false;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

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
