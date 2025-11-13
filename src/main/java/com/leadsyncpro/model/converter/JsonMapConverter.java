package com.leadsyncpro.model.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Converter
public class JsonMapConverter implements AttributeConverter<Map<String, Object>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> TYPE_REF = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(Map<String, Object> attribute) {
        Map<String, Object> safeMap = attribute == null ? Collections.emptyMap() : attribute;
        try {
            return OBJECT_MAPPER.writeValueAsString(safeMap);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialize metadata", e);
        }
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new HashMap<>();
        }
        try {
            return OBJECT_MAPPER.readValue(dbData, TYPE_REF);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to deserialize metadata", e);
        }
    }
}
