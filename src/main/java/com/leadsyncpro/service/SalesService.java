package com.leadsyncpro.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadsyncpro.dto.SaleRequest;
import com.leadsyncpro.dto.SaleResponse;
import com.leadsyncpro.exception.ResourceNotFoundException;
import com.leadsyncpro.model.Lead;
import com.leadsyncpro.model.Sale;
import com.leadsyncpro.repository.LeadRepository;
import com.leadsyncpro.repository.SalesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SalesService {
    private final LeadRepository leadRepository;
    private final SalesRepository salesRepository;

    @Transactional
    public SaleResponse createSale(SaleRequest req, UUID organizationId, UUID userId) {
        Lead lead = leadRepository.findById(UUID.fromString(req.getLeadId()))
                .filter(l -> l.getOrganizationId().equals(organizationId))
                .orElseThrow(() -> new ResourceNotFoundException("Lead not found"));

        Sale sale = new Sale();
        sale.setLead(lead);
        sale.setUserId(userId);
        sale.setOperationType(req.getOperationType());
        sale.setPrice(req.getPrice());
        sale.setCurrency(req.getCurrency());
        sale.setHotel(req.getHotel());
        sale.setNights(req.getNights());
        try {
            sale.setTransferJson(new ObjectMapper().writeValueAsString(req.getTransfer()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        sale.setCreatedAt(Instant.now());

        sale = salesRepository.save(sale);

        return SaleResponse.builder()
                .id(sale.getId())
                .operationType(sale.getOperationType())
                .price(sale.getPrice())
                .currency(sale.getCurrency())
                .hotel(sale.getHotel())
                .createdAt(sale.getCreatedAt())
                .build();
    }
}

