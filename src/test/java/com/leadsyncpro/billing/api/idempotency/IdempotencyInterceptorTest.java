package com.leadsyncpro.billing.api.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.leadsyncpro.model.billing.IdempotencyEntry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

class IdempotencyInterceptorTest {

    private final Clock clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);
    private IdempotencyInterceptor interceptor;
    private InMemoryStorage storage;

    @BeforeEach
    void setup() {
        storage = new InMemoryStorage();
        interceptor = new IdempotencyInterceptor(storage, clock, Duration.ofMinutes(5));
    }

    @Test
    void replaysStoredResponseWhenHashesMatch() throws Exception {
        String body = "{\"customerId\":\"1\"}";
        String hash = hash("POST", "/api/billing/subscriptions", body.getBytes(StandardCharsets.UTF_8));
        IdempotencyEntry entry = IdempotencyEntry.builder()
                .id(UUID.randomUUID())
                .idempotencyKey("key-1")
                .requestHash(hash)
                .responseStatus(201)
                .responseBody("{\"status\":\"ok\"}")
                .expiresAt(Instant.now(clock).plusSeconds(30))
                .build();
        storage.store(entry);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/billing/subscriptions");
        request.addHeader("Idempotency-Key", "key-1");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(cachedRequest, response, handlerMethod());

        assertThat(proceed).isFalse();
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getContentAsString()).contains("ok");
        assertThat(IdempotencyInterceptor.isReplayed(cachedRequest)).isTrue();
    }

    @Test
    void throwsConflictWhenHashesDiffer() throws Exception {
        String body = "{\"customerId\":\"1\"}";
        String differentBody = "{\"customerId\":\"2\"}";
        String hash = hash("POST", "/api/billing/subscriptions", body.getBytes(StandardCharsets.UTF_8));
        IdempotencyEntry entry = IdempotencyEntry.builder()
                .id(UUID.randomUUID())
                .idempotencyKey("key-2")
                .requestHash(hash)
                .expiresAt(Instant.now(clock).plusSeconds(30))
                .build();
        storage.store(entry);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/billing/subscriptions");
        request.addHeader("Idempotency-Key", "key-2");
        request.setContent(differentBody.getBytes(StandardCharsets.UTF_8));
        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);

        assertThatThrownBy(() -> interceptor.preHandle(cachedRequest, new MockHttpServletResponse(), handlerMethod()))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void registersNewEntryWhenNotPresent() throws Exception {
        String body = "{\"customerId\":\"3\"}";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/billing/subscriptions");
        request.addHeader("Idempotency-Key", "key-3");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(cachedRequest, response, handlerMethod());

        assertThat(proceed).isTrue();
        Optional<UUID> entryId = IdempotencyInterceptor.resolveEntryId(cachedRequest);
        assertThat(entryId).isPresent();
    }

    private HandlerMethod handlerMethod() throws NoSuchMethodException {
        return new HandlerMethod(new TestController(), TestController.class.getMethod("handle"));
    }

    private String hash(String method, String uri, byte[] body) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(method.getBytes(StandardCharsets.UTF_8));
        digest.update(uri.getBytes(StandardCharsets.UTF_8));
        digest.update(body);
        return Base64.getEncoder().encodeToString(digest.digest());
    }

    private static class TestController {
        @IdempotentEndpoint
        public void handle() {}
    }

    private static class InMemoryStorage implements IdempotencyStorage {
        private final Map<String, IdempotencyEntry> entries = new HashMap<>();

        @Override
        public Optional<IdempotencyEntry> findActiveEntry(String key, Instant now) {
            IdempotencyEntry entry = entries.get(key);
            if (entry == null || entry.getExpiresAt().isBefore(now)) {
                return Optional.empty();
            }
            return Optional.of(entry);
        }

        @Override
        public IdempotencyEntry createOrGet(String key, String requestHash, Instant now, Instant expiresAt) {
            IdempotencyEntry existing = entries.get(key);
            if (existing != null && existing.getExpiresAt().isAfter(now)) {
                return existing;
            }
            IdempotencyEntry entry = IdempotencyEntry.builder()
                    .id(UUID.randomUUID())
                    .idempotencyKey(key)
                    .requestHash(requestHash)
                    .expiresAt(expiresAt)
                    .build();
            entries.put(key, entry);
            return entry;
        }

        @Override
        public void persistResponse(UUID entryId, String responseBody, int statusCode, Instant expiresAt) {
            // no-op for interceptor tests
        }

        void store(IdempotencyEntry entry) {
            entries.put(entry.getIdempotencyKey(), entry);
        }
    }
}
