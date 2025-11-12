package com.leadsyncpro.billing.api.idempotency;

import com.leadsyncpro.model.billing.IdempotencyEntry;
import com.leadsyncpro.repository.billing.IdempotencyEntryRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class JpaIdempotencyStorage implements IdempotencyStorage {

    private final IdempotencyEntryRepository repository;

    @Override
    @Transactional(readOnly = true)
    public Optional<IdempotencyEntry> findActiveEntry(String key, Instant now) {
        return repository.findByIdempotencyKeyAndExpiresAtAfter(key, now);
    }

    @Override
    @Transactional
    public IdempotencyEntry createOrGet(String key, String requestHash, Instant now, Instant expiresAt) {
        Optional<IdempotencyEntry> existing = repository.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            IdempotencyEntry entry = existing.get();
            if (entry.getExpiresAt() != null && entry.getExpiresAt().isBefore(now)) {
                repository.delete(entry);
            } else {
                boolean dirty = false;
                if (entry.getExpiresAt() == null || expiresAt.isAfter(entry.getExpiresAt())) {
                    entry.setExpiresAt(expiresAt);
                    dirty = true;
                }
                if (entry.getRequestHash() == null) {
                    entry.setRequestHash(requestHash);
                    dirty = true;
                }
                if (dirty) {
                    repository.save(entry);
                }
                return entry;
            }
        }

        IdempotencyEntry entry = IdempotencyEntry.builder()
                .idempotencyKey(key)
                .requestHash(requestHash)
                .expiresAt(expiresAt)
                .build();
        try {
            return repository.save(entry);
        } catch (DataIntegrityViolationException ex) {
            log.debug("Concurrent idempotency attempt for key={}", key, ex);
            IdempotencyEntry concurrent = repository
                    .findByIdempotencyKey(key)
                    .orElseThrow(() -> ex);
            boolean dirty = false;
            if (concurrent.getRequestHash() == null) {
                concurrent.setRequestHash(requestHash);
                dirty = true;
            }
            if (concurrent.getExpiresAt() == null || expiresAt.isAfter(concurrent.getExpiresAt())) {
                concurrent.setExpiresAt(expiresAt);
                dirty = true;
            }
            if (dirty) {
                repository.save(concurrent);
            }
            return concurrent;
        }
    }

    @Override
    @Transactional
    public void persistResponse(UUID entryId, String responseBody, int statusCode, Instant expiresAt) {
        repository
                .findById(entryId)
                .ifPresent(entry -> {
                    entry.setResponseBody(responseBody);
                    entry.setResponseStatus(statusCode);
                    Instant currentExpiry = entry.getExpiresAt();
                    if (expiresAt != null
                            && (currentExpiry == null || expiresAt.isAfter(currentExpiry))) {
                        entry.setExpiresAt(expiresAt);
                    }
                    repository.save(entry);
                });
    }
}
