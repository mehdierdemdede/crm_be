package com.leadsyncpro.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadsyncpro.dto.SaleRequest;
import com.leadsyncpro.dto.SaleResponse;
import com.leadsyncpro.exception.ResourceNotFoundException;
import com.leadsyncpro.model.Lead;
import com.leadsyncpro.model.Sale;
import com.leadsyncpro.repository.LeadRepository;
import com.leadsyncpro.repository.SalesRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SalesService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SalesService.class);

    private final LeadRepository leadRepository;
    private final SalesRepository salesRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public SaleResponse createSale(SaleRequest req, MultipartFile file, UUID orgId, UUID userId) {
        Lead lead = leadRepository.findById(UUID.fromString(req.getLeadId()))
                .filter(l -> l.getOrganizationId().equals(orgId))
                .orElseThrow(() -> new ResourceNotFoundException("Lead not found"));

        Sale sale = new Sale();
        sale.setLead(lead);
        sale.setUserId(userId);
        sale.setOrganizationId(orgId);
        sale.setOperationType(req.getOperationType());
        sale.setPrice(req.getPrice());
        sale.setCurrency(req.getCurrency());
        sale.setHotel(req.getHotel());
        sale.setNights(req.getNights());
        sale.setTransferJson(writeJson(req));

        if (req.getOperationDate() != null && !req.getOperationDate().isBlank()) {
            LocalDate opDate = LocalDate.parse(req.getOperationDate());
            sale.setOperationDate(opDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        } else {
            sale.setOperationDate(Instant.now());
        }

        sale.setCreatedAt(Instant.now());

        // 🔹 Dosya varsa kaydet
        if (file != null && !file.isEmpty()) {
            try {
                String dirPath = "";
                File dir = new File(dirPath);
                if (!dir.exists())
                    dir.mkdirs();

                String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
                File dest = new File(dir, filename);
                file.transferTo(dest);

                sale.setDocumentPath(dest.getAbsolutePath());
                log.info("📁 Document saved to {}", dest.getAbsolutePath());
            } catch (Exception e) {
                log.error("❌ File upload failed: {}", e.getMessage());
            }
        }

        Sale saved = salesRepository.save(sale);

        return mapToSaleResponse(saved);
    }

    public SaleResponse getSaleById(UUID saleId, UUID organizationId) {
        Sale sale = salesRepository.findByIdAndOrganizationId(saleId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Sale not found"));

        return mapToSaleResponse(sale);
    }

    public org.springframework.data.domain.Page<SaleResponse> getAllSales(UUID organizationId,
            org.springframework.data.domain.Pageable pageable) {
        return salesRepository.findAllByOrganizationId(organizationId, pageable)
                .map(this::mapToSaleResponse);
    }

    public java.util.List<SaleResponse> getSalesByLead(UUID leadId, UUID organizationId) {
        return salesRepository.findAllByLead_IdAndOrganizationIdOrderByOperationDateDesc(leadId, organizationId)
                .stream()
                .map(this::mapToSaleResponse)
                .toList();
    }

    public java.util.List<SaleResponse> getSalesByDateRange(UUID organizationId, Instant startDate, Instant endDate) {
        return salesRepository
                .findAllByOrganizationIdAndOperationDateBetweenOrderByOperationDateAsc(organizationId, startDate,
                        endDate)
                .stream()
                .map(this::mapToSaleResponse)
                .toList();
    }

    private String writeJson(SaleRequest req) {
        try {
            return objectMapper.writeValueAsString(req.getTransfer());
        } catch (Exception e) {
            log.warn("Transfer JSON serialization failed: {}", e.getMessage());
            return "[]";
        }
    }

    private SaleResponse mapToSaleResponse(Sale sale) {
        java.util.List<String> transferList = new java.util.ArrayList<>();
        if (sale.getTransferJson() != null && !sale.getTransferJson().isBlank()) {
            try {
                transferList = objectMapper.readValue(sale.getTransferJson(),
                        new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>() {
                        });
            } catch (Exception e) {
                // fallback simple parser or ignore
                String json = sale.getTransferJson().trim();
                if (json.startsWith("[") && json.endsWith("]")) {
                    String content = json.substring(1, json.length() - 1);
                    for (String part : content.split(",")) {
                        String clean = part.trim().replace("\"", "");
                        if (!clean.isEmpty())
                            transferList.add(clean);
                    }
                }
            }
        }

        return SaleResponse.builder()
                .id(sale.getId())
                .leadId(sale.getLead() != null ? sale.getLead().getId() : null)
                .operationType(sale.getOperationType())
                .price(sale.getPrice())
                .currency(sale.getCurrency())
                .hotel(sale.getHotel())
                .nights(sale.getNights())
                .transfer(transferList)
                .documentPath(sale.getDocumentPath())
                .operationDate(sale.getOperationDate())
                .createdAt(sale.getCreatedAt())
                .build();
    }
}
