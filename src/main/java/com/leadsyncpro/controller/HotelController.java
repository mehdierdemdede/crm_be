package com.leadsyncpro.controller;

import com.leadsyncpro.model.Hotel;
import com.leadsyncpro.repository.HotelRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.leadsyncpro.security.UserPrincipal;
import org.springframework.security.core.Authentication;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({ "/hotels", "/api/hotels" })
@RequiredArgsConstructor
public class HotelController {

    private final HotelRepository hotelRepository;

    @GetMapping
    public ResponseEntity<List<Hotel>> getAllHotels(Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(hotelRepository.findAllByOrganizationId(userPrincipal.getOrganizationId()));
    }

    @PostMapping
    public ResponseEntity<Hotel> createHotel(@Valid @RequestBody Hotel hotel, Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        hotel.setOrganizationId(userPrincipal.getOrganizationId());
        return ResponseEntity.status(HttpStatus.CREATED).body(hotelRepository.save(hotel));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Hotel> updateHotel(@PathVariable UUID id, @Valid @RequestBody Hotel updated,
            Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        return hotelRepository.findById(id)
                .filter(h -> h.getOrganizationId().equals(userPrincipal.getOrganizationId()))
                .map(h -> {
                    h.setName(updated.getName());
                    h.setAddress(updated.getAddress());
                    h.setStarRating(updated.getStarRating());
                    h.setNightlyRate(updated.getNightlyRate());
                    h.setCurrency(updated.getCurrency());
                    return ResponseEntity.ok(hotelRepository.save(h));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteHotel(@PathVariable UUID id, Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        hotelRepository.findById(id)
                .filter(h -> h.getOrganizationId().equals(userPrincipal.getOrganizationId()))
                .ifPresent(h -> hotelRepository.deleteById(id));

        return ResponseEntity.noContent().build();
    }
}
