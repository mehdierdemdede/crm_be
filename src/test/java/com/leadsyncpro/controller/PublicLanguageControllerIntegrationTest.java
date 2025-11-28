package com.leadsyncpro.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.leadsyncpro.model.Language;
import com.leadsyncpro.repository.LanguageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicLanguageControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LanguageRepository languageRepository;

    @BeforeEach
    void setUp() {
        languageRepository.deleteAll();
    }

    @Test
    @DisplayName("Active languages are available via the public endpoint")
    void activeLanguagesAreExposedWithoutAuthentication() throws Exception {
        languageRepository.save(Language.builder()
                .code("tr")
                .name("Turkish")
                .flagEmoji("\uD83C\uDDF9\uD83C\uDDF7")
                .active(true)
                .build());
        languageRepository.save(Language.builder()
                .code("en")
                .name("English")
                .flagEmoji("\uD83C\uDDEC\uD83C\uDDE7")
                .active(true)
                .build());
        languageRepository.save(Language.builder()
                .code("de")
                .name("German")
                .flagEmoji("\uD83C\uDDE9\uD83C\uDDEA")
                .active(false)
                .build());

        mockMvc.perform(get("/api/public/languages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].code").value("en"))
                .andExpect(jsonPath("$[0].name").value("English"))
                .andExpect(jsonPath("$[1].code").value("tr"))
                .andExpect(jsonPath("$[1].name").value("Turkish"));
    }
}
