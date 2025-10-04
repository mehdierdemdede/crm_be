package com.leadsyncpro.repository;

import com.leadsyncpro.model.IntegrationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IntegrationLogRepository extends JpaRepository<IntegrationLog, UUID> {
}