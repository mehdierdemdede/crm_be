package com.leadsyncpro.repository;

import com.leadsyncpro.model.TransferRoute;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface TransferRouteRepository extends JpaRepository<TransferRoute, UUID> {
}