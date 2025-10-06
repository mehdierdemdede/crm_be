package com.leadsyncpro.repository;

import com.leadsyncpro.model.Hotel;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface HotelRepository extends JpaRepository<Hotel, UUID> {
}
