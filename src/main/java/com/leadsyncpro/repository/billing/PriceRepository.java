package com.leadsyncpro.repository.billing;

import com.leadsyncpro.model.billing.BillingPeriod;
import com.leadsyncpro.model.billing.Plan;
import com.leadsyncpro.model.billing.Price;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceRepository extends JpaRepository<Price, UUID> {
    List<Price> findByPlan(Plan plan);

    Optional<Price> findByPlanAndBillingPeriod(Plan plan, BillingPeriod billingPeriod);
}
