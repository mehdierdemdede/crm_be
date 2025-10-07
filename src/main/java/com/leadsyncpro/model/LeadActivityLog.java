package com.leadsyncpro.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lead_activity_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeadActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id")
    private Lead lead;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "action", length = 100)
    @Enumerated(EnumType.STRING)
    private ActionType action;     // "CALL" | "WHATSAPP" | "NOTE" vs.

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;    // serbest metin

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
