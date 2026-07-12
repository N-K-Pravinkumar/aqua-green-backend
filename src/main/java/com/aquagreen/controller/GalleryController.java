package com.aquagreen.controller;
import com.aquagreen.dto.ApiResponse;
import com.aquagreen.model.GalleryItem;
import com.aquagreen.repository.GalleryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/gallery") @RequiredArgsConstructor
public class GalleryController {
    private final GalleryRepository repo;
    @GetMapping public ResponseEntity<ApiResponse<List<GalleryItem>>> getAll(@RequestParam(required=false) String category) {
        List<GalleryItem> items = category!=null ? repo.findByCategoryAndActiveTrueOrderByDisplayOrderAsc(category) : repo.findByActiveTrueOrderByDisplayOrderAsc();
        return ResponseEntity.ok(ApiResponse.success("OK",items));
    }
    @GetMapping("/preview") public ResponseEntity<ApiResponse<List<GalleryItem>>> getPreview() { return ResponseEntity.ok(ApiResponse.success("OK",repo.findTop6ByActiveTrueOrderByDisplayOrderAsc())); }
    @GetMapping("/{id}") public ResponseEntity<ApiResponse<GalleryItem>> getById(@PathVariable Long id) { return repo.findById(id).map(g->ResponseEntity.ok(ApiResponse.success("OK",g))).orElse(ResponseEntity.notFound().build()); }
    @PostMapping public ResponseEntity<ApiResponse<GalleryItem>> create(@RequestBody GalleryItem g) { g.setId(null); return ResponseEntity.ok(ApiResponse.success("Created",repo.save(g))); }
    @PutMapping("/{id}") public ResponseEntity<ApiResponse<GalleryItem>> update(@PathVariable Long id,@RequestBody GalleryItem g) { g.setId(id); return ResponseEntity.ok(ApiResponse.success("Updated",repo.save(g))); }
    @DeleteMapping("/{id}") public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) { repo.findById(id).ifPresent(g->{g.setActive(false);repo.save(g);}); return ResponseEntity.ok(ApiResponse.success("Deleted",null)); }
}
