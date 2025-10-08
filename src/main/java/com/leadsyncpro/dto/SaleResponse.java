package com.leadsyncpro.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleResponse {
    private UUID id;
    private UUID leadId;
    private String productName;
    private Double amount;
    private String currency;
    private Instant createdAt;
}
