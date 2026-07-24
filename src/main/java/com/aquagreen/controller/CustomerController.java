package com.aquagreen.controller;

import com.aquagreen.dto.ApiResponse;
import com.aquagreen.model.Customer;
import com.aquagreen.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {
    private final CustomerRepository repo;
    private final LeadRepository leadRepo;
    private final EnquiryRepository enquiryRepo;
    private final ServiceRequestRepository serviceRequestRepo;
    private final SaleRepository saleRepo;
    private final QuotationRepository quotationRepo;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<Customer>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable p = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.success("OK", repo.findByActiveTrueOrderByCreatedAtDesc(p)));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<Customer>>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable p = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.success("OK",
                repo.findByNameContainingIgnoreCaseOrMobileContaining(q, q, p)));
    }

    @GetMapping("/lookup")
    public ResponseEntity<ApiResponse<Customer>> lookupByMobile(@RequestParam String mobile) {
        Customer c = repo.findByMobile(mobile.trim()).orElse(null);
        return ResponseEntity.ok(ApiResponse.success(c != null ? "Found" : "Not found", c));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Customer>> getById(@PathVariable Long id) {
        return repo.findById(id)
                .map(c -> ResponseEntity.ok(ApiResponse.success("OK", c)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Customer>> create(@RequestBody Customer c) {
        c.setId(null);
        return ResponseEntity.ok(ApiResponse.success("Customer created", repo.save(c)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Customer>> update(@PathVariable Long id, @RequestBody Customer c) {
        c.setId(id);
        return ResponseEntity.ok(ApiResponse.success("Updated", repo.save(c)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        repo.findById(id).ifPresent(c -> {
            c.setActive(false);
            repo.save(c);
        });
        return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }

    /**
     * Full customer timeline — leads, enquiries, service requests, sales,
     * quotations.
     *
     * IMPORTANT: this must filter with a WHERE clause at the database level,
     * not load entire tables and filter in Java. This endpoint previously did
     * findAllByOrderByCreatedAtDesc() (no filter) for all five tables, then
     * filtered in memory — which (a) pulls every row over the network from
     * Supabase on every single call, and (b) triggers Hibernate's eager
     * fetch of each row's linked Customer one-by-one (N+1), multiplying into
     * hundreds of extra round-trips. Combined with Supabase being in a
     * different region than the Railway app, that added up to 14+ second
     * responses and client-side timeouts. Targeted findByMobile queries
     * avoid both problems.
     */
    @GetMapping("/{id}/timeline")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTimeline(@PathVariable Long id) {
        Customer c = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Customer not found"));

        String mobileNorm = com.aquagreen.util.MobileUtil.normalize(c.getMobile());
        final String mobile = mobileNorm != null ? mobileNorm : "";

        Map<String, Object> timeline = new LinkedHashMap<>();
        timeline.put("leads", mobile.isEmpty() ? List.of() : leadRepo.findByMobileOrderByCreatedAtDesc(mobile));
        timeline.put("enquiries", mobile.isEmpty() ? List.of() : enquiryRepo.findByMobileOrderByCreatedAtDesc(mobile));
        timeline.put("serviceRequests",
                mobile.isEmpty() ? List.of() : serviceRequestRepo.findByCustomerMobileOrderByCreatedAtDesc(mobile));
        timeline.put("sales", mobile.isEmpty() ? List.of() : saleRepo.findByCustomerMobileOrderByCreatedAtDesc(mobile));
        timeline.put("quotations",
                mobile.isEmpty() ? List.of() : quotationRepo.findByCustomerMobileOrderByCreatedAtDesc(mobile));

        return ResponseEntity.ok(ApiResponse.success("OK", timeline));
    }
}
