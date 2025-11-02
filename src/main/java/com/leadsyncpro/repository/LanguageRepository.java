package com.leadsyncpro.repository;

import com.leadsyncpro.model.Language;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LanguageRepository extends JpaRepository<Language, UUID> {
    Optional<Language> findByCodeIgnoreCase(String code);

    @Query("SELECT l FROM Language l " +
            "WHERE l.active = true AND (" +
            "LOWER(l.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(l.code) LIKE LOWER(CONCAT('%', :query, '%'))" +
            ") ORDER BY LOWER(l.name) ASC")
    List<Language> searchActiveCatalog(@Param("query") String query, Pageable pageable);
}
