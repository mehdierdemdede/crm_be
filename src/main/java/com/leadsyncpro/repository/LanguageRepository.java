package com.leadsyncpro.repository;

import com.leadsyncpro.model.Language;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LanguageRepository extends JpaRepository<Language, UUID> {
    Optional<Language> findByCodeIgnoreCase(String code);
}
