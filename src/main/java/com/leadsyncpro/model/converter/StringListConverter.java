package com.leadsyncpro.model.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Converter
public class StringListConverter implements AttributeConverter<List<String>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> TYPE_REF = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        List<String> safeList = attribute == null ? Collections.emptyList() : attribute;
        try {
            return OBJECT_MAPPER.writeValueAsString(safeList);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialize stops list", e);
        }
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return OBJECT_MAPPER.readValue(dbData, TYPE_REF);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to deserialize stops list", e);
        }
    }
}
