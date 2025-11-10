package com.leadsyncpro.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.leadsyncpro.model.converter.StringListConverter;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "transfer_routes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferRoute {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String name;

    @Column(name = "start_location", nullable = false)
    @NotBlank(message = "Start location is required")
    private String start;

    @Column(name = "final_destination", nullable = false)
    @NotBlank(message = "Final destination is required")
    @JsonProperty("final")
    private String finalDestination;

    @Column(name = "stops", columnDefinition = "TEXT")
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    @Size(max = 3, message = "A route can have at most 3 stops")
    @Convert(converter = StringListConverter.class)
    @Builder.Default
    private List<String> stops = new ArrayList<>();

    @Column(nullable = false)
    @NotNull(message = "Transfer price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Transfer price must be positive")
    private Double price;

    @Column(length = 3, nullable = false)
    @Builder.Default
    private String currency = "EUR";

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        prepareForPersist();
    }

    @PreUpdate
    protected void onUpdate() {
        prepareForPersist();
    }

    public void setStops(List<String> stops) {
        this.stops = stops == null ? new ArrayList<>() : new ArrayList<>(stops);
    }

    private void prepareForPersist() {
        setCurrency(currency);
        if (stops == null) {
            stops = new ArrayList<>();
        }
        start = start != null ? start.trim() : null;
        finalDestination = finalDestination != null ? finalDestination.trim() : null;
        stops = stops.stream()
                .filter(stop -> stop != null && !stop.isBlank())
                .map(String::trim)
                .collect(Collectors.toList());
        if (stops.size() > 3) {
            throw new IllegalArgumentException("A route can have at most 3 stops");
        }
        name = buildRouteName();
    }

    public void setCurrency(String currency) {
        this.currency = (currency == null || currency.isBlank())
                ? "EUR"
                : currency.trim().toUpperCase();
    }

    private String buildRouteName() {
        List<String> segments = new ArrayList<>();
        if (start != null && !start.isBlank()) {
            segments.add(start);
        }
        if (stops != null) {
            segments.addAll(stops);
        }
        if (finalDestination != null && !finalDestination.isBlank()) {
            segments.add(finalDestination);
        }
        return String.join(" \u2192 ", segments);
    }
}
