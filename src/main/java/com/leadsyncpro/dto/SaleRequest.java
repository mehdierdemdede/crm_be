package com.leadsyncpro.dto;

import lombok.Data;

import java.util.List;

@Data
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
