package com.leadsyncpro.controller;

import com.leadsyncpro.model.Hotel;
import com.leadsyncpro.repository.HotelRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/hotels")
@RequiredArgsConstructor
public class HotelController {

    private final HotelRepository hotelRepository;

    @GetMapping
    public ResponseEntity<List<Hotel>> getAllHotels() {
        return ResponseEntity.ok(hotelRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<Hotel> createHotel(@Valid @RequestBody Hotel hotel) {
        return ResponseEntity.status(HttpStatus.CREATED).body(hotelRepository.save(hotel));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Hotel> updateHotel(@PathVariable UUID id, @Valid @RequestBody Hotel updated) {
        return hotelRepository.findById(id)
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
    public ResponseEntity<Void> deleteHotel(@PathVariable UUID id) {
        hotelRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
