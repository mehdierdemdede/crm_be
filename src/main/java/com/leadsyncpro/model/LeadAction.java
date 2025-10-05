package com.leadsyncpro.model;

import lombok.*;
import org.hibernate.annotations.GenericGenerator;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;


@Entity
@Table(name = "lead_actions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeadAction {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id")
    private Lead lead;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "action_type", length = 50)
    private String actionType;

    @Column(columnDefinition = "TEXT")
    private String message;

    private Instant createdAt;
}
