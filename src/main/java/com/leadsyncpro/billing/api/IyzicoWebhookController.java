package com.leadsyncpro.billing.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadsyncpro.billing.integration.iyzico.IyzicoClient;
import com.leadsyncpro.billing.worker.WebhookProcessor;
import com.leadsyncpro.model.billing.WebhookEvent;
import com.leadsyncpro.model.billing.WebhookEventStatus;
import com.leadsyncpro.repository.billing.WebhookEventRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/webhooks", consumes = MediaType.APPLICATION_JSON_VALUE)
public class IyzicoWebhookController {

    private final IyzicoClient iyzicoClient;
    private final WebhookEventRepository webhookEventRepository;
    private final WebhookProcessor webhookProcessor;
    private final ObjectMapper objectMapper;

    @PostMapping("/iyzico")
    public ResponseEntity<Void> handleWebhook(
            @RequestHeader(value = "X-Iyzi-Signature", required = false) String signature,
            @RequestBody String payload) {
        if (!iyzicoClient.verifyWebhook(signature, payload)) {
            log.warn("Rejected Iyzico webhook due to invalid signature");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid webhook signature");
        }

        JsonNode rootNode;
        try {
            rootNode = objectMapper.readTree(payload);
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid JSON payload", ex);
        }

        String providerEventId = extractText(rootNode, "provider_event_id", "eventId", "event_id", "id")
                .filter(StringUtils::hasText)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing provider_event_id"));

        Optional<WebhookEvent> existing = webhookEventRepository.findByProviderEventId(providerEventId);
        if (existing.isPresent()) {
            log.info("Ignoring duplicate Iyzico webhook event with id={}", providerEventId);
            return ResponseEntity.ok().build();
        }

        String eventType = extractText(rootNode, "eventType", "type", "event")
                .filter(StringUtils::hasText)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing event type"));

        WebhookEvent event = WebhookEvent.builder()
                .provider("iyzico")
                .eventType(eventType)
                .payload(payload)
                .signature(signature)
                .status(WebhookEventStatus.PENDING)
                .providerEventId(providerEventId)
                .build();

        try {
            event = webhookEventRepository.save(event);
            webhookProcessor.process(event.getId());
            return ResponseEntity.accepted().build();
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to handle webhook", ex);
        }
    }

    private Optional<String> extractText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            if (node.has(fieldName) && node.get(fieldName).isTextual()) {
                return Optional.of(node.get(fieldName).asText());
            }
        }
        if (node.has("data")) {
            return extractText(node.get("data"), fieldNames);
        }
        return Optional.empty();
    }
}
