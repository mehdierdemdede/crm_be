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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SalesService {

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
        sale.setCreatedAt(Instant.now());

        // 🔹 Dosya varsa kaydet
        if (file != null && !file.isEmpty()) {
            try {
                String dirPath = "uploads/documents";
                File dir = new File(dirPath);
                if (!dir.exists()) dir.mkdirs();

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

        return SaleResponse.builder()
                .id(saved.getId())
                .operationType(saved.getOperationType())
                .price(saved.getPrice())
                .currency(saved.getCurrency())
                .hotel(saved.getHotel())
                .nights(saved.getNights())
                .documentPath(saved.getDocumentPath())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    private String writeJson(SaleRequest req) {
        try {
            return objectMapper.writeValueAsString(req.getTransfer());
        } catch (Exception e) {
            log.warn("Transfer JSON serialization failed: {}", e.getMessage());
            return "[]";
        }
    }
}
