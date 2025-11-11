package com.leadsyncpro.repository.billing;

import com.leadsyncpro.model.billing.SeatAllocation;
import com.leadsyncpro.model.billing.Subscription;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatAllocationRepository extends JpaRepository<SeatAllocation, UUID> {
    List<SeatAllocation> findBySubscriptionOrderByEffectiveFromDesc(Subscription subscription);
}
