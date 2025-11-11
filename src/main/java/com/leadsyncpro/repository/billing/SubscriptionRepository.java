package com.leadsyncpro.repository.billing;

import com.leadsyncpro.model.billing.Customer;
import com.leadsyncpro.model.billing.Subscription;
import com.leadsyncpro.model.billing.SubscriptionStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
    List<Subscription> findByCustomer(Customer customer);

    Optional<Subscription> findByExternalSubscriptionId(String externalSubscriptionId);

    List<Subscription> findByStatus(SubscriptionStatus status);
}
