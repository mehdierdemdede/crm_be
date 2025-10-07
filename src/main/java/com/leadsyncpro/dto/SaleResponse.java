package com.leadsyncpro.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleResponse {
    private UUID id;
    private String operationType;
    private Double price;
    private String currency;
    private String hotel;
    private Instant createdAt;
}

