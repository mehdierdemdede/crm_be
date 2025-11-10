package com.leadsyncpro.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "hotels")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Hotel {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 255)
    @NotBlank(message = "Hotel name is required")
    private String name;

    @Column(nullable = false, length = 255)
    @NotBlank(message = "Hotel address is required")
    private String address;

    @Column(name = "star_rating", nullable = false)
    @NotNull(message = "Star rating is required")
    @Min(value = 1, message = "Star rating must be at least 1")
    @Max(value = 5, message = "Star rating cannot exceed 5")
    private Integer starRating;

    @Column(name = "nightly_rate", nullable = false)
    @NotNull(message = "Nightly rate is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Nightly rate must be positive")
    private Double nightlyRate;

    @Column(length = 3, nullable = false)
    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a valid ISO 4217 code")
    @Builder.Default
    private String currency = "EUR";

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        applyDefaults();
    }

    @PreUpdate
    protected void onUpdate() {
        applyDefaults();
    }

    private void applyDefaults() {
        setCurrency(currency);
    }

    public void setCurrency(String currency) {
        this.currency = (currency == null || currency.isBlank())
                ? "EUR"
                : currency.trim().toUpperCase();
    }
}
