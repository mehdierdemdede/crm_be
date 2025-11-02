package com.leadsyncpro.service;

import com.leadsyncpro.dto.LanguageCatalogResponse;
import com.leadsyncpro.dto.LanguageRequest;
import com.leadsyncpro.dto.LanguageResponse;
import com.leadsyncpro.exception.ResourceNotFoundException;
import com.leadsyncpro.model.Language;
import com.leadsyncpro.repository.LanguageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class LanguageService {

    private final LanguageRepository languageRepository;

    @Transactional(readOnly = true)
    public List<LanguageResponse> findAll() {
        return languageRepository.findAll(Sort.by(Sort.Direction.ASC, "name"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LanguageCatalogResponse> searchCatalog(String query) {
        String sanitizedQuery = query == null ? "" : query.trim();
        if (sanitizedQuery.isEmpty()) {
            return List.of();
        }

        return languageRepository.searchActiveCatalog(sanitizedQuery, PageRequest.of(0, 10))
                .stream()
                .map(language -> new LanguageCatalogResponse(
                        language.getId(),
                        language.getCode(),
                        language.getName(),
                        language.getFlagEmoji()
                ))
                .toList();
    }

    public LanguageResponse create(LanguageRequest request) {
        String code = sanitizeCode(request.code());
        String name = sanitizeName(request.name());

        languageRepository.findByCodeIgnoreCase(code)
                .ifPresent(language -> {
                    throw new IllegalArgumentException("Language code must be unique.");
                });

        Language language = Language.builder()
                .code(code)
                .name(name)
                .flagEmoji(request.flagEmoji())
                .active(request.active() == null || request.active())
                .build();

        Language saved = languageRepository.save(language);
        return toResponse(saved);
    }

    public LanguageResponse update(UUID id, LanguageRequest request) {
        Language existing = languageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Language not found with id: " + id));

        String code = sanitizeCode(request.code());
        String name = sanitizeName(request.name());

        languageRepository.findByCodeIgnoreCase(code)
                .filter(language -> !language.getId().equals(id))
                .ifPresent(language -> {
                    throw new IllegalArgumentException("Language code must be unique.");
                });

        existing.setCode(code);
        existing.setName(name);
        existing.setFlagEmoji(request.flagEmoji());
        if (request.active() != null) {
            existing.setActive(request.active());
        }

        Language updated = languageRepository.save(existing);
        return toResponse(updated);
    }

    public void delete(UUID id) {
        Language existing = languageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Language not found with id: " + id));
        languageRepository.delete(existing);
    }

    private String sanitizeCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("Code must not be blank.");
        }
        return code.trim();
    }

    private String sanitizeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Name must not be blank.");
        }
        return name.trim();
    }

    private LanguageResponse toResponse(Language language) {
        return new LanguageResponse(
                language.getId(),
                language.getCode(),
                language.getName(),
                language.getFlagEmoji(),
                language.isActive(),
                language.getCreatedAt(),
                language.getUpdatedAt()
        );
    }
}
