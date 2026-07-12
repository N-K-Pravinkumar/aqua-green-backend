package com.aquagreen.controller;
import com.aquagreen.dto.ApiResponse;
import com.aquagreen.model.Lead;
import com.aquagreen.repository.LeadRepository;
import com.aquagreen.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequestMapping("/api/leads") @RequiredArgsConstructor
public class LeadController {
    private final LeadRepository repo;
    private final CustomerService customerService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<Lead>>> getAll(
            @RequestParam(required=false) String status,
            @RequestParam(defaultValue="0")  int page,
            @RequestParam(defaultValue="20") int size) {
        Pageable p = PageRequest.of(page, size);
        Page<Lead> result = status != null && !status.isBlank()
            ? repo.findByStatusOrderByCreatedAtDesc(status, p)
            : repo.findAllByOrderByCreatedAtDesc(p);
        return ResponseEntity.ok(ApiResponse.success("OK", result));
    }

    @GetMapping("/counts")
    public ResponseEntity<ApiResponse<Map<String,Long>>> counts() {
        return ResponseEntity.ok(ApiResponse.success("OK", Map.of(
            "NEW",             repo.countByStatus("NEW"),
            "CONTACTED",       repo.countByStatus("CONTACTED"),
            "FOLLOW_UP",       repo.countByStatus("FOLLOW_UP"),
            "QUOTATION_SENT",  repo.countByStatus("QUOTATION_SENT"),
            "CONVERTED",       repo.countByStatus("CONVERTED"),
            "LOST",            repo.countByStatus("LOST"),
            "TOTAL",           repo.count())));
    }

    @GetMapping("/{id}") public ResponseEntity<ApiResponse<Lead>> getById(@PathVariable Long id) {
        return repo.findById(id).map(l->ResponseEntity.ok(ApiResponse.success("OK",l))).orElse(ResponseEntity.notFound().build());
    }
    @PostMapping public ResponseEntity<ApiResponse<Lead>> create(@RequestBody Lead lead) {
        lead.setId(null);
        Lead saved = repo.save(lead);
        // Auto-create or enrich customer record from lead
        customerService.findOrCreate(saved.getName(), saved.getMobile(),
            saved.getEmail(), null, saved.getCity(), "LEAD");
        return ResponseEntity.ok(ApiResponse.success("Lead created", saved));
    }
    @PutMapping("/{id}") public ResponseEntity<ApiResponse<Lead>> update(@PathVariable Long id, @RequestBody Lead lead) {
        lead.setId(id); return ResponseEntity.ok(ApiResponse.success("Updated", repo.save(lead)));
    }
    @PatchMapping("/{id}/status") public ResponseEntity<ApiResponse<Lead>> updateStatus(@PathVariable Long id, @RequestParam String status) {
        Lead l = repo.findById(id).orElseThrow(()->new RuntimeException("Not found"));
        l.setStatus(status); return ResponseEntity.ok(ApiResponse.success("Status updated", repo.save(l)));
    }
    @PatchMapping("/{id}/notes") public ResponseEntity<ApiResponse<Lead>> addNote(@PathVariable Long id, @RequestParam String notes) {
        Lead l = repo.findById(id).orElseThrow(()->new RuntimeException("Not found"));
        l.setNotes(notes); return ResponseEntity.ok(ApiResponse.success("Notes saved", repo.save(l)));
    }
    @DeleteMapping("/{id}") public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        repo.deleteById(id); return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }
}
