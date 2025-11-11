package com.leadsyncpro.repository.billing;

import com.leadsyncpro.model.billing.WebhookEvent;
import com.leadsyncpro.model.billing.WebhookEventStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {
    Optional<WebhookEvent> findByProviderEventId(String providerEventId);

    long countByStatus(WebhookEventStatus status);
}
