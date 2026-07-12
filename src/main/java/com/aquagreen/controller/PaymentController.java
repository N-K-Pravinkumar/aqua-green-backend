package com.aquagreen.controller;

import com.aquagreen.dto.ApiResponse;
import com.aquagreen.model.Payment;
import com.aquagreen.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentRepository repo;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Payment>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success("OK", repo.findAllByOrderByCreatedAtDesc()));
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<ApiResponse<List<Payment>>> getByCustomer(@PathVariable Long customerId) {
        return ResponseEntity.ok(ApiResponse.success("OK", repo.findByCustomerIdOrderByCreatedAtDesc(customerId)));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats() {
        return ResponseEntity.ok(ApiResponse.success("OK", Map.of(
                "totalRevenue", repo.sumTotalRevenue(),
                "monthlyRevenue", repo.sumMonthlyRevenue(),
                "totalPayments", repo.count()
        )));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Payment>> getById(@PathVariable Long id) {
        return repo.findById(id).map(p -> ResponseEntity.ok(ApiResponse.success("OK", p)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Payment>> create(@RequestBody Payment p) {
        p.setId(null);
        if (p.getPaymentNumber() == null) {
            p.setPaymentNumber("AQG-PAY-" + System.currentTimeMillis());
        }
        return ResponseEntity.ok(ApiResponse.success("Payment recorded", repo.save(p)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Payment>> update(@PathVariable Long id, @RequestBody Payment p) {
        p.setId(id);
        return ResponseEntity.ok(ApiResponse.success("Updated", repo.save(p)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        repo.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }
}
