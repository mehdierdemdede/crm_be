package com.leadsyncpro.dto;

import com.leadsyncpro.model.LeadStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeadResponse {
    private UUID id;
    private String name;
    private LeadStatus status;
    private String phone;
    private String messengerPageId;
    private UUID lastSaleId;
    private SaleResponse lastSale;
}
