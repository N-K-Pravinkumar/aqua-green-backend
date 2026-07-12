package com.aquagreen.controller;

import com.aquagreen.dto.ApiResponse;
import com.aquagreen.model.BrandPartner;
import com.aquagreen.repository.BrandPartnerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/brands")
@RequiredArgsConstructor
public class BrandPartnerController {

    private final BrandPartnerRepository repo;

    @GetMapping
    public ResponseEntity<ApiResponse<List<BrandPartner>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success("OK", repo.findByActiveTrueOrderByDisplayOrderAsc()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BrandPartner>> create(@RequestBody BrandPartner b) {
        b.setId(null);
        return ResponseEntity.ok(ApiResponse.success("Created", repo.save(b)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<BrandPartner>> update(@PathVariable Long id, @RequestBody BrandPartner b) {
        b.setId(id);
        return ResponseEntity.ok(ApiResponse.success("Updated", repo.save(b)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        repo.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }
}
