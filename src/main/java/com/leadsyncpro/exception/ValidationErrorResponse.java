package com.leadsyncpro.exception;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
@AllArgsConstructor
public class ValidationErrorResponse {
    private Instant timestamp;
    private String message;
    private String path;
    private Map<String, String> errors;
}
