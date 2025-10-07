package com.leadsyncpro.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleRequest {
    private String leadId;
    private String operationDate;
    private String operationType;
    private Double price;
    private String currency;
    private String hotel;
    private Integer nights;
    private List<String> transfer;
}
