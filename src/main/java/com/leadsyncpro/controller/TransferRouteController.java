package com.leadsyncpro.controller;

import com.leadsyncpro.model.TransferRoute;
import com.leadsyncpro.repository.TransferRouteRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/transfer-routes")
@RequiredArgsConstructor
public class TransferRouteController {

    private final TransferRouteRepository repo;

    @GetMapping
    public ResponseEntity<List<TransferRoute>> getAll() {
        return ResponseEntity.ok(repo.findAll());
    }

    @PostMapping
    public ResponseEntity<TransferRoute> create(@Valid @RequestBody TransferRoute route) {
        return ResponseEntity.status(HttpStatus.CREATED).body(repo.save(route));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TransferRoute> update(@PathVariable UUID id, @Valid @RequestBody TransferRoute updated) {
        return repo.findById(id)
                .map(t -> {
                    t.setStart(updated.getStart());
                    t.setFinalDestination(updated.getFinalDestination());
                    t.setStops(updated.getStops());
                    t.setPrice(updated.getPrice());
                    t.setCurrency(updated.getCurrency());
                    return ResponseEntity.ok(repo.save(t));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}