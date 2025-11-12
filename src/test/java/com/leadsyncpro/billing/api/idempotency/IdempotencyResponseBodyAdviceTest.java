package com.leadsyncpro.billing.api.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

class IdempotencyResponseBodyAdviceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RecordingStorage storage = new RecordingStorage();
    private final Clock clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final IdempotencyResponseBodyAdvice advice =
            new IdempotencyResponseBodyAdvice(storage, objectMapper, clock);

    @Test
    void persistsResponseSnapshot() {
        UUID entryId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(IdempotencyInterceptor.ENTRY_ID_ATTRIBUTE, entryId);
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        servletResponse.setStatus(201);

        advice.beforeBodyWrite(
                new ResponsePayload("created"),
                null,
                MediaType.APPLICATION_JSON,
                MappingJackson2HttpMessageConverter.class,
                new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(servletResponse));

        assertThat(storage.lastEntryId).contains(entryId);
        assertThat(storage.lastStatus).isEqualTo(201);
        assertThat(storage.lastBody).isEqualTo("{\"status\":\"created\"}");
    }

    private record ResponsePayload(String status) {}

    private static class RecordingStorage implements IdempotencyStorage {
        private Optional<UUID> lastEntryId = Optional.empty();
        private String lastBody;
        private Integer lastStatus;

        @Override
        public Optional<com.leadsyncpro.model.billing.IdempotencyEntry> findActiveEntry(String key, Instant now) {
            return Optional.empty();
        }

        @Override
        public com.leadsyncpro.model.billing.IdempotencyEntry createOrGet(
                String key, String requestHash, Instant now, Instant expiresAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void persistResponse(UUID entryId, String responseBody, int statusCode, Instant expiresAt) {
            this.lastEntryId = Optional.of(entryId);
            this.lastBody = responseBody;
            this.lastStatus = statusCode;
        }
    }
}
