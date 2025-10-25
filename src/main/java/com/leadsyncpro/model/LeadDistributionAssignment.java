package com.leadsyncpro.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lead_distribution_assignments", uniqueConstraints = {
        @UniqueConstraint(name = "uq_lead_distribution_assignment", columnNames = {"rule_id", "user_id"})
}, indexes = {
        @Index(name = "idx_lead_distribution_assignment_rule", columnList = "rule_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeadDistributionAssignment {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rule_id", nullable = false)
    private LeadDistributionRule rule;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "frequency", nullable = false)
    private Integer frequency;

    @Column(name = "position", nullable = false)
    private Integer position;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (frequency == null) {
            frequency = 0;
        }
        if (position == null) {
            position = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}

