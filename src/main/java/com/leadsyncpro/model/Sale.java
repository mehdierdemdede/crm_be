package com.leadsyncpro.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sales")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Sale {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id", nullable = false)
    private Lead lead;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 100)
    private String operationType;

    @Column(nullable = false)
    private Double price;

    @Column(length = 10, nullable = false)
    private String currency;

    @Column(length = 100)
    private String hotel;

    @Column(nullable = false)
    private Integer nights;

    @Column(name = "transfer_json", columnDefinition = "TEXT")
    private String transferJson;

    @Column(name = "document_path", length = 255)
    private String documentPath;

    @Column(nullable = false)
    private Instant operationDate;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
