package com.aquagreen.controller;
import com.aquagreen.dto.ApiResponse;
import com.aquagreen.model.Enquiry;
import com.aquagreen.model.Product;
import com.aquagreen.repository.EnquiryRepository;
import com.aquagreen.repository.ProductRepository;
import com.aquagreen.service.CustomerService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequestMapping("/api/enquiries") @RequiredArgsConstructor
public class EnquiryController {
    private final EnquiryRepository repo;
    private final ProductRepository productRepo;
    private final com.aquagreen.service.SmsService smsService;
    private final CustomerService customerService;

    @Data static class EnquiryRequest {
        private String customerName, mobile, email, address, serviceRequired, message, source;
        private Long productId;
        private String productName;
    }

    @PostMapping public ResponseEntity<ApiResponse<Enquiry>> submit(@RequestBody EnquiryRequest req) {
        Product product = req.getProductId()!=null ? productRepo.findById(req.getProductId()).orElse(null) : null;
        String pName = req.getProductName()!=null ? req.getProductName() : (product!=null?product.getName():null);
        Enquiry e = Enquiry.builder()
            .customerName(req.getCustomerName()).mobile(req.getMobile()).email(req.getEmail())
            .address(req.getAddress()).product(product).productName(pName)
            .serviceRequired(req.getServiceRequired()).message(req.getMessage())
            .source(req.getSource()!=null?req.getSource():"WEBSITE").build();
        Enquiry saved = repo.save(e);
        // Auto-create or enrich customer record from enquiry
        customerService.findOrCreate(saved.getCustomerName(), saved.getMobile(),
            saved.getEmail(), saved.getAddress(), null, "ENQUIRY");
        // Notify customer by SMS
        smsService.sendEnquiryReceived(saved.getMobile(), saved.getCustomerName(), saved.getProductName());
        return ResponseEntity.ok(ApiResponse.success("Thank you! We will contact you within 45 minutes.", saved));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<Enquiry>>> getAll(
            @RequestParam(required=false) String status,
            @RequestParam(defaultValue="0")  int page,
            @RequestParam(defaultValue="20") int size) {
        Pageable p = PageRequest.of(page, size);
        Page<Enquiry> result = status!=null && !status.isBlank()
            ? repo.findByStatusOrderByCreatedAtDesc(status, p)
            : repo.findAllByOrderByCreatedAtDesc(p);
        return ResponseEntity.ok(ApiResponse.success("OK", result));
    }

    @GetMapping("/counts") public ResponseEntity<ApiResponse<Map<String,Long>>> counts() {
        return ResponseEntity.ok(ApiResponse.success("OK", Map.of(
            "NEW",       repo.countByStatus("NEW"),
            "CONTACTED", repo.countByStatus("CONTACTED"),
            "CONVERTED", repo.countByStatus("CONVERTED"),
            "CLOSED",    repo.countByStatus("CLOSED"),
            "TOTAL",     repo.count())));
    }
    @PatchMapping("/{id}/status") public ResponseEntity<ApiResponse<Enquiry>> updateStatus(@PathVariable Long id, @RequestParam String status) {
        Enquiry e = repo.findById(id).orElseThrow(()->new RuntimeException("Not found"));
        e.setStatus(status); return ResponseEntity.ok(ApiResponse.success("Updated", repo.save(e)));
    }
    @DeleteMapping("/{id}") public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        repo.deleteById(id); return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }
}
