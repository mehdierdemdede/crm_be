package com.leadsyncpro.repository;

import com.leadsyncpro.model.IntegrationLog;
import com.leadsyncpro.model.IntegrationPlatform;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface IntegrationLogRepository extends JpaRepository<IntegrationLog, UUID> {

    @Query("SELECT log FROM IntegrationLog log " +
            "WHERE log.organizationId = :organizationId " +
            "AND (:platform IS NULL OR log.platform = :platform)")
    List<IntegrationLog> findAllByOrganizationIdAndOptionalPlatform(
            @Param("organizationId") UUID organizationId,
            @Param("platform") IntegrationPlatform platform,
            Sort sort);

    @Query("SELECT log FROM IntegrationLog log " +
            "WHERE log.organizationId = :organizationId " +
            "AND (:platform IS NULL OR log.platform = :platform)")
    Page<IntegrationLog> findAllByOrganizationIdAndOptionalPlatform(
            @Param("organizationId") UUID organizationId,
            @Param("platform") IntegrationPlatform platform,
            Pageable pageable);
}
