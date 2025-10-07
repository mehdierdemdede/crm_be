package com.leadsyncpro.repository;

import com.leadsyncpro.model.Sale;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SalesRepository extends JpaRepository<Sale, UUID> {}

