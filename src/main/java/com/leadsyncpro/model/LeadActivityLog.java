package com.leadsyncpro.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lead_activity_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeadActivityLog {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id")
    private Lead lead;

    @Column(name = "user_id")
    private UUID userId;

    @Column(length = 100)
    private String action;

    @Column(columnDefinition = "TEXT")
    private String details;

    private Instant createdAt;
}
