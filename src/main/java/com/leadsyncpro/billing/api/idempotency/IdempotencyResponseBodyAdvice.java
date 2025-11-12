package com.leadsyncpro.billing.api.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@Slf4j
@ControllerAdvice(assignableTypes = com.leadsyncpro.billing.api.BillingController.class)
@RequiredArgsConstructor
public class IdempotencyResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    private final IdempotencyStorage storage;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return body;
        }
        HttpServletRequest httpServletRequest = servletRequest.getServletRequest();
        if (IdempotencyInterceptor.isReplayed(httpServletRequest)) {
            return body;
        }
        Optional<UUID> entryId = IdempotencyInterceptor.resolveEntryId(httpServletRequest);
        if (entryId.isEmpty()) {
            return body;
        }
        String serialized = serialize(body);
        int status = ((ServletServerHttpResponse) response).getServletResponse().getStatus();
        Instant expiresAt = Instant.now(clock).plusSeconds(300);
        storage.persistResponse(entryId.get(), serialized, status, expiresAt);
        return body;
    }

    private String serialize(Object body) {
        if (body == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialise idempotent response body", ex);
            return null;
        }
    }
}
