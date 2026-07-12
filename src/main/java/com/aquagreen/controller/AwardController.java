package com.aquagreen.controller;
import com.aquagreen.dto.ApiResponse;
import com.aquagreen.model.Award;
import com.aquagreen.repository.AwardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/awards") @RequiredArgsConstructor
public class AwardController {
    private final AwardRepository repo;
    @GetMapping public ResponseEntity<ApiResponse<List<Award>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success("OK", repo.findByActiveTrueOrderByDisplayOrderAsc()));
    }
    @PostMapping public ResponseEntity<ApiResponse<Award>> create(@RequestBody Award a) {
        a.setId(null); return ResponseEntity.ok(ApiResponse.success("Created", repo.save(a)));
    }
    @PutMapping("/{id}") public ResponseEntity<ApiResponse<Award>> update(@PathVariable Long id, @RequestBody Award a) {
        a.setId(id); return ResponseEntity.ok(ApiResponse.success("Updated", repo.save(a)));
    }
    @DeleteMapping("/{id}") public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        repo.deleteById(id); return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }
}
