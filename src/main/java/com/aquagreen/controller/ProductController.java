package com.aquagreen.controller;
import com.aquagreen.dto.ApiResponse;
import com.aquagreen.model.Product;
import com.aquagreen.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;
@RestController @RequestMapping("/api/products") @RequiredArgsConstructor
public class ProductController {
    private final ProductRepository repo;
    @GetMapping public ResponseEntity<ApiResponse<List<Product>>> getAll() { return ResponseEntity.ok(ApiResponse.success("OK",repo.findByActiveTrueOrderByDisplayOrderAsc())); }
    @GetMapping("/featured") public ResponseEntity<ApiResponse<List<Product>>> getFeatured() { return ResponseEntity.ok(ApiResponse.success("OK",repo.findTop5ByActiveTrueOrderByDisplayOrderAsc())); }
    @GetMapping("/all-admin") public ResponseEntity<ApiResponse<List<Product>>> getAllAdmin() { return ResponseEntity.ok(ApiResponse.success("OK",repo.findAll())); }
    @GetMapping("/category/{cat}") public ResponseEntity<ApiResponse<List<Product>>> getByCategory(@PathVariable String cat) { return ResponseEntity.ok(ApiResponse.success("OK",repo.findByCategoryAndActiveTrueOrderByDisplayOrderAsc(cat.toUpperCase()))); }
    @GetMapping("/{id}") public ResponseEntity<ApiResponse<Product>> getById(@PathVariable Long id) { return repo.findById(id).map(p->ResponseEntity.ok(ApiResponse.success("OK",p))).orElse(ResponseEntity.notFound().build()); }
    @PostMapping public ResponseEntity<ApiResponse<Product>> create(@RequestBody Product p) { p.setId(null); return ResponseEntity.ok(ApiResponse.success("Product created",repo.save(p))); }
    @PutMapping("/{id}") public ResponseEntity<ApiResponse<Product>> update(@PathVariable Long id,@RequestBody Product p) { p.setId(id); p.setUpdatedAt(LocalDateTime.now()); return ResponseEntity.ok(ApiResponse.success("Product updated",repo.save(p))); }
    @DeleteMapping("/{id}") public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) { repo.findById(id).ifPresent(p->{p.setActive(false);repo.save(p);}); return ResponseEntity.ok(ApiResponse.success("Deleted",null)); }
}
