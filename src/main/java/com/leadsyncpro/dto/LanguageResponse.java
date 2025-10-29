package com.leadsyncpro.dto;

import java.time.Instant;
import java.util.UUID;

public record LanguageResponse(
        UUID id,
        String code,
        String name,
        String flagEmoji,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
