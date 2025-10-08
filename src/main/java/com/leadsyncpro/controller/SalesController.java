package com.leadsyncpro.controller;

import com.leadsyncpro.dto.SaleRequest;
import com.leadsyncpro.dto.SaleResponse;
import com.leadsyncpro.model.Sale;
import com.leadsyncpro.repository.SalesRepository;
import com.leadsyncpro.security.UserPrincipal;
import com.leadsyncpro.service.SalesService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
public class SalesController {

    private final SalesService salesService;
    private final SalesRepository salesRepository;

    /**
     * 💾 Satış kaydı oluştur (belge yükleme opsiyonel)
     */
    @PostMapping(consumes = {"multipart/form-data"})
    @PreAuthorize("hasAnyAuthority('ADMIN','USER','SUPER_ADMIN')")
    public ResponseEntity<SaleResponse> createSale(
            @RequestPart("data") SaleRequest req,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        SaleResponse created = salesService.createSale(
                req, file, currentUser.getOrganizationId(), currentUser.getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{saleId}")
    @PreAuthorize("hasAnyAuthority('ADMIN','USER','SUPER_ADMIN')")
    public ResponseEntity<SaleResponse> getSaleById(@PathVariable UUID saleId,
                                                   @AuthenticationPrincipal UserPrincipal currentUser) {
        SaleResponse response = salesService.getSaleById(saleId, currentUser.getOrganizationId());
        return ResponseEntity.ok(response);
    }

    /**
     * 📁 Belge indirme veya görüntüleme
     * - PDF, JPG, PNG gibi dosyalar tarayıcıda açılır.
     * - Diğer türler otomatik olarak indirilir.
     */
    @GetMapping("/document/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','USER','SUPER_ADMIN')")
    public ResponseEntity<Resource> downloadDocument(@PathVariable UUID id) throws IOException {
        Sale sale = salesRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sale not found"));

        if (sale.getDocumentPath() == null) {
            return ResponseEntity.notFound().build();
        }

        File file = new File(sale.getDocumentPath());
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        Path path = file.toPath();
        Resource resource = new UrlResource(path.toUri());

        // İçerik türünü otomatik belirle
        String mimeType = Files.probeContentType(path);
        if (mimeType == null) {
            mimeType = "application/octet-stream";
        }

        boolean inlineView =
                mimeType.startsWith("image/") ||
                        mimeType.equals("application/pdf");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        (inlineView ? "inline" : "attachment") + "; filename=\"" + file.getName() + "\"")
                .contentType(MediaType.parseMediaType(mimeType))
                .body(resource);
    }
}
