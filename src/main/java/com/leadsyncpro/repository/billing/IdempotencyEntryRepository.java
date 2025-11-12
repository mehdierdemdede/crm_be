package com.leadsyncpro.repository.billing;

import com.leadsyncpro.model.billing.IdempotencyEntry;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IdempotencyEntryRepository extends JpaRepository<IdempotencyEntry, UUID> {

    Optional<IdempotencyEntry> findByIdempotencyKey(String idempotencyKey);

    Optional<IdempotencyEntry> findByIdempotencyKeyAndExpiresAtAfter(String idempotencyKey, Instant threshold);
}
