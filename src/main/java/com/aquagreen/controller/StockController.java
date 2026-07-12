package com.aquagreen.controller;
import com.aquagreen.dto.ApiResponse;
import com.aquagreen.model.StockItem;
import com.aquagreen.repository.StockItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/stock") @RequiredArgsConstructor
public class StockController {
    private final StockItemRepository repo;
    @GetMapping public ResponseEntity<ApiResponse<List<StockItem>>> getAll() { return ResponseEntity.ok(ApiResponse.success("OK",repo.findByActiveTrueOrderByNameAsc())); }
    @GetMapping("/low") public ResponseEntity<ApiResponse<List<StockItem>>> getLow() { return ResponseEntity.ok(ApiResponse.success("OK",repo.findLowStockItems())); }
    @GetMapping("/{id}") public ResponseEntity<ApiResponse<StockItem>> getById(@PathVariable Long id) { return repo.findById(id).map(s->ResponseEntity.ok(ApiResponse.success("OK",s))).orElse(ResponseEntity.notFound().build()); }
    @PostMapping public ResponseEntity<ApiResponse<StockItem>> create(@RequestBody StockItem s) { s.setId(null); return ResponseEntity.ok(ApiResponse.success("Stock item created",repo.save(s))); }
    @PutMapping("/{id}") public ResponseEntity<ApiResponse<StockItem>> update(@PathVariable Long id,@RequestBody StockItem s) { s.setId(id); return ResponseEntity.ok(ApiResponse.success("Updated",repo.save(s))); }
    @PatchMapping("/{id}/stock") public ResponseEntity<ApiResponse<StockItem>> updateStock(@PathVariable Long id,@RequestParam int qty,@RequestParam(defaultValue="ADD") String type) {
        StockItem s = repo.findById(id).orElseThrow(()->new RuntimeException("Not found"));
        if("ADD".equals(type)) s.setCurrentStock(s.getCurrentStock()+qty);
        else s.setCurrentStock(Math.max(0,s.getCurrentStock()-qty));
        return ResponseEntity.ok(ApiResponse.success("Stock updated",repo.save(s)));
    }
    @DeleteMapping("/{id}") public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) { repo.findById(id).ifPresent(s->{s.setActive(false);repo.save(s);}); return ResponseEntity.ok(ApiResponse.success("Deleted",null)); }
}
