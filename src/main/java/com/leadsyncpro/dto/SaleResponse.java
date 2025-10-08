package com.leadsyncpro.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Builder
@Data
public class SaleResponse {
    private UUID id;
    private String operationType;
    private Double price;
    private String currency;
    private String hotel;
    private Integer nights;
    private String documentPath;
    private Instant createdAt;
}

