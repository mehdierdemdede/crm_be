package com.leadsyncpro.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.Entity; // Changed from javax.persistence
import jakarta.persistence.Table; // Changed from javax.persistence
import jakarta.persistence.Id; // Changed from javax.persistence
import jakarta.persistence.GeneratedValue; // Changed from javax.persistence
import jakarta.persistence.Column; // Changed from javax.persistence
import jakarta.persistence.UniqueConstraint; // Changed from javax.persistence
import jakarta.persistence.Enumerated; // Changed from javax.persistence
import jakarta.persistence.EnumType; // Changed from javax.persistence
import jakarta.persistence.PrePersist; // Changed from javax.persistence
import jakarta.persistence.PreUpdate; // Changed from javax.persistence
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"organization_id", "email"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
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
    private boolean isActive = true;

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