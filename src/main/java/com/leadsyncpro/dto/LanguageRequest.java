package com.leadsyncpro.dto;

import jakarta.validation.constraints.NotBlank;

public record LanguageRequest(
        @NotBlank(message = "Code must not be blank.") String code,
        @NotBlank(message = "Name must not be blank.") String name,
        String flagEmoji,
        Boolean active
) {
}
