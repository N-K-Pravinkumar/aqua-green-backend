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
import java.util.stream.Collectors;

@RestController @RequestMapping("/api/customers") @RequiredArgsConstructor
public class CustomerController {
    private final CustomerRepository repo;
    private final LeadRepository leadRepo;
    private final EnquiryRepository enquiryRepo;
    private final ServiceRequestRepository serviceRequestRepo;
    private final SaleRepository saleRepo;
    private final QuotationRepository quotationRepo;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<Customer>>> getAll(
            @RequestParam(defaultValue="0")  int page,
            @RequestParam(defaultValue="20") int size) {
        Pageable p = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.success("OK", repo.findByActiveTrueOrderByCreatedAtDesc(p)));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<Customer>>> search(@RequestParam String q) {
        return ResponseEntity.ok(ApiResponse.success("OK",
            repo.findByNameContainingIgnoreCaseOrMobileContaining(q, q)));
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
        repo.findById(id).ifPresent(c -> { c.setActive(false); repo.save(c); });
        return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }

    /** Full customer timeline — leads, enquiries, service requests, sales, quotations */
    @GetMapping("/{id}/timeline")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTimeline(@PathVariable Long id) {
        Customer c = repo.findById(id)
            .orElseThrow(() -> new RuntimeException("Customer not found"));

        String mobile = com.aquagreen.util.MobileUtil.normalize(c.getMobile());
        if (mobile == null) mobile = "";
        String name   = c.getName()   != null ? c.getName().toLowerCase() : "";

        Map<String, Object> timeline = new LinkedHashMap<>();

        timeline.put("leads", leadRepo.findAllByOrderByCreatedAtDesc().stream()
            .filter(l -> !mobile.isEmpty() && mobile.equals(com.aquagreen.util.MobileUtil.normalize(l.getMobile()))
                      || name.equals(l.getName() != null ? l.getName().toLowerCase() : ""))
            .collect(Collectors.toList()));

        timeline.put("enquiries", enquiryRepo.findAllByOrderByCreatedAtDesc().stream()
            .filter(e -> !mobile.isEmpty() && mobile.equals(com.aquagreen.util.MobileUtil.normalize(e.getMobile()))
                      || name.equals(e.getCustomerName() != null ? e.getCustomerName().toLowerCase() : ""))
            .collect(Collectors.toList()));

        timeline.put("serviceRequests", serviceRequestRepo.findAllByOrderByCreatedAtDesc().stream()
            .filter(s -> (s.getCustomer() != null && id.equals(s.getCustomer().getId()))
                      || (!mobile.isEmpty() && mobile.equals(com.aquagreen.util.MobileUtil.normalize(s.getCustomerMobile()))))
            .collect(Collectors.toList()));

        timeline.put("sales", saleRepo.findAllByOrderByCreatedAtDesc().stream()
            .filter(s -> (s.getCustomer() != null && id.equals(s.getCustomer().getId()))
                      || (!mobile.isEmpty() && mobile.equals(com.aquagreen.util.MobileUtil.normalize(s.getCustomerMobile()))))
            .collect(Collectors.toList()));

        timeline.put("quotations", quotationRepo.findAllByOrderByCreatedAtDesc().stream()
            .filter(q -> (q.getCustomer() != null && id.equals(q.getCustomer().getId()))
                      || (!mobile.isEmpty() && mobile.equals(com.aquagreen.util.MobileUtil.normalize(q.getCustomerMobile()))))
            .collect(Collectors.toList()));

        return ResponseEntity.ok(ApiResponse.success("OK", timeline));
    }
}
