package com.leadsyncpro.controller;

import com.leadsyncpro.dto.LanguageCatalogResponse;
import com.leadsyncpro.dto.LanguageRequest;
import com.leadsyncpro.dto.LanguageResponse;
import com.leadsyncpro.service.LanguageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/languages")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('SUPER_ADMIN','ADMIN')")
public class LanguageController {

    private final LanguageService languageService;

    @GetMapping
    public ResponseEntity<List<LanguageResponse>> getLanguages() {
        return ResponseEntity.ok(languageService.findAll());
    }

    @GetMapping("/catalog")
    public ResponseEntity<List<LanguageCatalogResponse>> searchLanguages(@RequestParam("query") String query) {
        return ResponseEntity.ok(languageService.searchCatalog(query));
    }

    @PostMapping
    public ResponseEntity<LanguageResponse> createLanguage(@Valid @RequestBody LanguageRequest request) {
        LanguageResponse response = languageService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<LanguageResponse> updateLanguage(@PathVariable UUID id,
                                                            @Valid @RequestBody LanguageRequest request) {
        return ResponseEntity.ok(languageService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLanguage(@PathVariable UUID id) {
        languageService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
