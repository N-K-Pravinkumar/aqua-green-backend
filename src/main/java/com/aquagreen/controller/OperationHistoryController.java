package com.aquagreen.controller;

import com.aquagreen.dto.ApiResponse;
import com.aquagreen.model.OperationHistory;
import com.aquagreen.repository.OperationHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class OperationHistoryController {

    private final OperationHistoryRepository repo;

    @GetMapping
    public ResponseEntity<ApiResponse<List<OperationHistory>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success("OK", repo.findTop50ByOrderByCreatedAtDesc()));
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<ApiResponse<List<OperationHistory>>> getByCustomer(@PathVariable Long customerId) {
        return ResponseEntity.ok(ApiResponse.success("OK", repo.findByCustomerIdOrderByCreatedAtDesc(customerId)));
    }

    @GetMapping("/entity/{type}/{id}")
    public ResponseEntity<ApiResponse<List<OperationHistory>>> getByEntity(
            @PathVariable String type, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("OK",
                repo.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(type, id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OperationHistory>> create(@RequestBody OperationHistory h) {
        h.setId(null);
        return ResponseEntity.ok(ApiResponse.success("Logged", repo.save(h)));
    }
}
