package com.leadsyncpro.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadsyncpro.model.Hotel;
import com.leadsyncpro.repository.HotelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class HotelControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private HotelRepository hotelRepository;

    @BeforeEach
    void setUp() {
        hotelRepository.deleteAll();
    }

    @Test
    @WithMockUser
    void createHotel_whenPayloadIsValid_returnsCreatedHotel() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "Test Hotel");
        payload.put("address", "123 Test Street");
        payload.put("starRating", 4);
        payload.put("nightlyRate", 189.99);
        payload.put("currency", "eur");

        mockMvc.perform(post("/hotels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Test Hotel"))
                .andExpect(jsonPath("$.currency").value("EUR"));

        assertThat(hotelRepository.findAll()).hasSize(1);
    }

    @Test
    @WithMockUser
    void createHotel_whenStarRatingIsInvalid_returnsValidationError() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "Invalid Hotel");
        payload.put("address", "123 Test Street");
        payload.put("starRating", 6);
        payload.put("nightlyRate", 189.99);
        payload.put("currency", "EUR");

        mockMvc.perform(post("/hotels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors.starRating").value("Star rating cannot exceed 5"));
    }

    @Test
    @WithMockUser
    void getHotels_returnsPersistedHotels() throws Exception {
        Hotel hotel = Hotel.builder()
                .name("Sample Hotel")
                .address("456 Sample Avenue")
                .starRating(3)
                .nightlyRate(120.0)
                .build();
        hotel.setCurrency("EUR");
        hotelRepository.save(hotel);

        mockMvc.perform(get("/hotels")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Sample Hotel"))
                .andExpect(jsonPath("$[0].address").value("456 Sample Avenue"))
                .andExpect(jsonPath("$[0].currency").value("EUR"));
    }
}
