package com.aquagreen.service;

import com.aquagreen.model.Customer;
import com.aquagreen.model.OperationHistory;
import com.aquagreen.repository.CustomerRepository;
import com.aquagreen.repository.OperationHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Shared service — creates or updates a Customer record from any
 * inbound source (Lead, Enquiry, manual).
 *
 * Logic:
 *   1. Normalise the mobile number.
 *   2. Look up existing customer by mobile.
 *   3. If found → enrich any blank fields (email, address, city) and return.
 *   4. If not found → create a new Customer, log CUSTOMER_CREATED, and return.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerService {

    private final CustomerRepository customerRepo;
    private final OperationHistoryRepository historyRepo;

    public Customer findOrCreate(String name, String mobile, String email,
                                 String address, String city, String source) {
        if (mobile == null || mobile.isBlank()) return null;

        // Normalise to at most 10 digits — must be a final variable for lambdas
        String raw = mobile.replaceAll("[^0-9]", "");
        final String normMobile = raw.length() > 10
            ? raw.substring(raw.length() - 10)
            : raw;

        // ── Existing customer — enrich blank fields ─────────────
        java.util.Optional<Customer> existing = customerRepo.findByMobile(normMobile);
        if (existing.isPresent()) {
            Customer c = existing.get();
            boolean changed = false;
            if (isBlank(c.getEmail())   && !isBlank(email))   { c.setEmail(email);     changed = true; }
            if (isBlank(c.getAddress()) && !isBlank(address)) { c.setAddress(address); changed = true; }
            if (isBlank(c.getCity())    && !isBlank(city))    { c.setCity(city);       changed = true; }
            if (changed) customerRepo.save(c);
            log.info("[CustomerService] Existing customer {} ({}) matched for source={}",
                c.getName(), normMobile, source);
            return c;
        }

        // ── New customer ─────────────────────────────────────────
        Customer c = Customer.builder()
            .name(name != null ? name.trim() : "Unknown")
            .mobile(normMobile)
            .email(email)
            .address(address)
            .city(city)
            .customerType("RESIDENTIAL")
            .active(true)
            .build();
        Customer saved = customerRepo.save(c);

        historyRepo.save(OperationHistory.builder()
            .action("CUSTOMER_CREATED")
            .entityType("Customer")
            .entityId(saved.getId())
            .entityName(saved.getName())
            .customer(saved)
            .performedBy("SYSTEM")
            .remarks("Auto-created from " + source)
            .build());

        log.info("[CustomerService] New customer created: {} ({}) from source={}",
            saved.getName(), normMobile, source);
        return saved;
    }

    private boolean isBlank(String s) { return s == null || s.isBlank(); }
}
