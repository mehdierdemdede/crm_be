package com.leadsyncpro.billing.api.idempotency;

import com.leadsyncpro.model.billing.IdempotencyEntry;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyStorage {

    Optional<IdempotencyEntry> findActiveEntry(String key, Instant now);

    IdempotencyEntry createOrGet(String key, String requestHash, Instant now, Instant expiresAt);

    void persistResponse(UUID entryId, String responseBody, int statusCode, Instant expiresAt);
}
