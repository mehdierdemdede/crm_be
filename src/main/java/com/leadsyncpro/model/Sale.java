package com.leadsyncpro.model;


import lombok.*;
import org.hibernate.annotations.GenericGenerator;
import jakarta.persistence.*;
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
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id", nullable = false)
    private Lead lead;

    @Column(nullable = false)
    private UUID userId;

    private String operationType;
    private Double price;
    private String currency;
    private String hotel;
    private Integer nights;

    @Column(columnDefinition = "TEXT")
    private String transferJson;

    private Instant createdAt;
}
