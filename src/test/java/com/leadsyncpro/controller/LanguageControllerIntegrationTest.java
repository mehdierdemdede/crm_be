package com.leadsyncpro.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadsyncpro.dto.LanguageRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LanguageControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Admins can execute full CRUD flow for languages")
    @WithMockUser(authorities = {"ADMIN"})
    void fullCrudFlow() throws Exception {
        LanguageRequest createRequest = new LanguageRequest("en", "English", "\uD83C\uDDEC\uD83C\uDDE7", true);

        MvcResult createResult = mockMvc.perform(post("/api/languages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("en"))
                .andExpect(jsonPath("$.name").value("English"))
                .andReturn();

        JsonNode createdNode = objectMapper.readTree(createResult.getResponse().getContentAsString());
        UUID languageId = UUID.fromString(createdNode.get("id").asText());
        assertThat(languageId).isNotNull();

        mockMvc.perform(get("/api/languages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(languageId.toString()))
                .andExpect(jsonPath("$[0].code").value("en"));

        LanguageRequest updateRequest = new LanguageRequest("en", "British English", "\uD83C\uDDEC\uD83C\uDDE7", false);

        mockMvc.perform(put("/api/languages/" + languageId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("British English"))
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(delete("/api/languages/" + languageId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/languages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    @DisplayName("Duplicate language codes are rejected")
    @WithMockUser(authorities = {"SUPER_ADMIN"})
    void duplicateCodeFails() throws Exception {
        LanguageRequest request = new LanguageRequest("tr", "Turkish", "\uD83C\uDDF9\uD83C\uDDF7", true);

        mockMvc.perform(post("/api/languages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/languages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Language code must be unique."));
    }

    @Test
    @DisplayName("Validation errors are returned when required fields are blank")
    @WithMockUser(authorities = {"ADMIN"})
    void validationErrorsReturnedForBlankFields() throws Exception {
        LanguageRequest invalidRequest = new LanguageRequest(" ", "", null, null);

        mockMvc.perform(post("/api/languages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("Code must not be blank."))
                .andExpect(jsonPath("$.name").value("Name must not be blank."));
    }

    @Test
    @DisplayName("Requests without authentication are rejected")
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/languages"))
                .andExpect(status().isUnauthorized());
    }
}
