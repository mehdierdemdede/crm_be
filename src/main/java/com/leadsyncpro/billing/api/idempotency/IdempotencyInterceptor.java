package com.leadsyncpro.billing.api.idempotency;

import com.leadsyncpro.model.billing.IdempotencyEntry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
public class IdempotencyInterceptor implements HandlerInterceptor {

    public static final String ENTRY_ID_ATTRIBUTE = IdempotencyInterceptor.class.getName() + ".ENTRY_ID";
    private static final String REPLAYED_ATTRIBUTE = IdempotencyInterceptor.class.getName() + ".REPLAYED";

    private final IdempotencyStorage storage;
    private final Clock clock;
    private final Duration ttl;

    public IdempotencyInterceptor(IdempotencyStorage storage, Clock clock, Duration ttl) {
        this.storage = storage;
        this.clock = clock;
        this.ttl = ttl;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        if (handlerMethod.getMethodAnnotation(IdempotentEndpoint.class) == null) {
            return true;
        }

        String key = request.getHeader("Idempotency-Key");
        if (!StringUtils.hasText(key)) {
            throw new IdempotencyKeyMissingException();
        }

        CachedBodyHttpServletRequest cachedRequest =
                request instanceof CachedBodyHttpServletRequest cached
                        ? cached
                        : new CachedBodyHttpServletRequest(request);
        byte[] body = cachedRequest.getCachedBody();
        String requestHash = computeHash(request.getMethod(), request.getRequestURI(), body);
        Instant now = Instant.now(clock);

        Optional<IdempotencyEntry> existing = storage.findActiveEntry(key, now);
        if (existing.isPresent()) {
            IdempotencyEntry entry = existing.get();
            ensureMatchingHash(entry, requestHash);
            if (entry.getResponseStatus() != null) {
                replayResponse(response, entry);
                request.setAttribute(REPLAYED_ATTRIBUTE, Boolean.TRUE);
                return false;
            }
            request.setAttribute(ENTRY_ID_ATTRIBUTE, entry.getId());
            return true;
        }

        Instant expiresAt = now.plus(ttl);
        IdempotencyEntry created = storage.createOrGet(key, requestHash, now, expiresAt);
        ensureMatchingHash(created, requestHash);
        if (created.getResponseStatus() != null) {
            replayResponse(response, created);
            request.setAttribute(REPLAYED_ATTRIBUTE, Boolean.TRUE);
            return false;
        }
        request.setAttribute(ENTRY_ID_ATTRIBUTE, created.getId());
        return true;
    }

    private void ensureMatchingHash(IdempotencyEntry entry, String requestHash) {
        if (!requestHash.equals(entry.getRequestHash())) {
            throw new IdempotencyConflictException();
        }
    }

    private void replayResponse(HttpServletResponse response, IdempotencyEntry entry) throws IOException {
        response.setStatus(entry.getResponseStatus());
        String body = entry.getResponseBody();
        if (body != null && !body.isBlank()) {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(body);
        }
        log.debug("Replayed idempotent response for key={}", entry.getIdempotencyKey());
    }

    private String computeHash(String method, String uri, byte[] body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(method.getBytes(StandardCharsets.UTF_8));
            digest.update(uri.getBytes(StandardCharsets.UTF_8));
            if (body != null) {
                digest.update(body);
            }
            return Base64.getEncoder().encodeToString(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest algorithm not available", ex);
        }
    }

    public static boolean isReplayed(HttpServletRequest request) {
        Object value = request.getAttribute(REPLAYED_ATTRIBUTE);
        return value instanceof Boolean bool && bool;
    }

    public static Optional<UUID> resolveEntryId(HttpServletRequest request) {
        Object value = request.getAttribute(ENTRY_ID_ATTRIBUTE);
        if (value instanceof UUID uuid) {
            return Optional.of(uuid);
        }
        return Optional.empty();
    }
}
