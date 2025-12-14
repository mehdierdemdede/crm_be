package com.leadsyncpro.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleResponse {
    private UUID id;
    private UUID leadId;
    private String operationType;
    private Double price;
    private String currency;
    private String hotel;
    private Integer nights;
    private java.util.List<String> transfer;
    private String documentPath;
    private java.time.Instant operationDate;
    private java.time.Instant createdAt;
}
