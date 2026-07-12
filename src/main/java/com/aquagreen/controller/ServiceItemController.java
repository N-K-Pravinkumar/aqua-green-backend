package com.aquagreen.controller;
import com.aquagreen.dto.ApiResponse;
import com.aquagreen.model.ServiceItem;
import com.aquagreen.repository.ServiceItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/services") @RequiredArgsConstructor
public class ServiceItemController {
    private final ServiceItemRepository repo;
    @GetMapping public ResponseEntity<ApiResponse<List<ServiceItem>>> getAll() { return ResponseEntity.ok(ApiResponse.success("OK",repo.findByActiveTrueOrderByDisplayOrderAsc())); }
    @GetMapping("/featured") public ResponseEntity<ApiResponse<List<ServiceItem>>> getFeatured() { return ResponseEntity.ok(ApiResponse.success("OK",repo.findTop5ByActiveTrueOrderByDisplayOrderAsc())); }
    @GetMapping("/all-admin") public ResponseEntity<ApiResponse<List<ServiceItem>>> getAllAdmin() { return ResponseEntity.ok(ApiResponse.success("OK",repo.findAll())); }
    @GetMapping("/{id}") public ResponseEntity<ApiResponse<ServiceItem>> getById(@PathVariable Long id) { return repo.findById(id).map(s->ResponseEntity.ok(ApiResponse.success("OK",s))).orElse(ResponseEntity.notFound().build()); }
    @PostMapping public ResponseEntity<ApiResponse<ServiceItem>> create(@RequestBody ServiceItem s) { s.setId(null); return ResponseEntity.ok(ApiResponse.success("Created",repo.save(s))); }
    @PutMapping("/{id}") public ResponseEntity<ApiResponse<ServiceItem>> update(@PathVariable Long id,@RequestBody ServiceItem s) { s.setId(id); return ResponseEntity.ok(ApiResponse.success("Updated",repo.save(s))); }
    @DeleteMapping("/{id}") public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) { repo.findById(id).ifPresent(s->{s.setActive(false);repo.save(s);}); return ResponseEntity.ok(ApiResponse.success("Deleted",null)); }
}
