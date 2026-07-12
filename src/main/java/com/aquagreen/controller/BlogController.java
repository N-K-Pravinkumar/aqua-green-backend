package com.aquagreen.controller;
import com.aquagreen.dto.ApiResponse;
import com.aquagreen.model.Blog;
import com.aquagreen.repository.BlogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;
@RestController @RequestMapping("/api/blogs") @RequiredArgsConstructor
public class BlogController {
    private final BlogRepository repo;
    @GetMapping public ResponseEntity<ApiResponse<List<Blog>>> getAll(@RequestParam(required=false) String status) {
        return ResponseEntity.ok(ApiResponse.success("OK",status!=null?repo.findByStatusOrderByCreatedAtDesc(status):repo.findAllByOrderByCreatedAtDesc()));
    }
    @GetMapping("/published") public ResponseEntity<ApiResponse<List<Blog>>> getPublished() { return ResponseEntity.ok(ApiResponse.success("OK",repo.findByStatusOrderByCreatedAtDesc("PUBLISHED"))); }
    @GetMapping("/latest") public ResponseEntity<ApiResponse<List<Blog>>> getLatest() { return ResponseEntity.ok(ApiResponse.success("OK",repo.findTop3ByStatusOrderByPublishedAtDesc("PUBLISHED"))); }
    @GetMapping("/{id}") public ResponseEntity<ApiResponse<Blog>> getById(@PathVariable Long id) { return repo.findById(id).map(b->ResponseEntity.ok(ApiResponse.success("OK",b))).orElse(ResponseEntity.notFound().build()); }
    @GetMapping("/slug/{slug}") public ResponseEntity<ApiResponse<Blog>> getBySlug(@PathVariable String slug) { return repo.findBySlug(slug).map(b->ResponseEntity.ok(ApiResponse.success("OK",b))).orElse(ResponseEntity.notFound().build()); }
    @PostMapping public ResponseEntity<ApiResponse<Blog>> create(@RequestBody Blog b) {
        b.setId(null);
        if("PUBLISHED".equals(b.getStatus()) && b.getPublishedAt()==null) b.setPublishedAt(LocalDateTime.now());
        return ResponseEntity.ok(ApiResponse.success("Blog created",repo.save(b)));
    }
    @PutMapping("/{id}") public ResponseEntity<ApiResponse<Blog>> update(@PathVariable Long id,@RequestBody Blog b) {
        b.setId(id);
        if("PUBLISHED".equals(b.getStatus()) && b.getPublishedAt()==null) b.setPublishedAt(LocalDateTime.now());
        return ResponseEntity.ok(ApiResponse.success("Updated",repo.save(b)));
    }
    @DeleteMapping("/{id}") public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) { repo.deleteById(id); return ResponseEntity.ok(ApiResponse.success("Deleted",null)); }
}
