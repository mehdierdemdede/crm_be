package com.leadsyncpro.dto;

import java.util.UUID;

public record LanguageCatalogResponse(
        UUID id,
        String code,
        String name,
        String flagEmoji
) {
}
