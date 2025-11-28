package com.leadsyncpro.controller;

import com.leadsyncpro.dto.LanguageOptionResponse;
import com.leadsyncpro.service.LanguageService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/languages")
@RequiredArgsConstructor
public class PublicLanguageController {

    private final LanguageService languageService;

    @GetMapping
    public ResponseEntity<List<LanguageOptionResponse>> listActiveLanguages() {
        return ResponseEntity.ok(languageService.findActiveLanguages());
    }
}
