package com.aquagreen.controller;

import com.aquagreen.dto.ApiResponse;
import com.aquagreen.model.*;
import com.aquagreen.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * ImportController — bulk import of historical service records and sales data.
 *
 * POST /api/import/service-records   — import service table rows
 * POST /api/import/sales             — import sales + customer rows
 * POST /api/import/customers         — import customers only
 * GET  /api/import/summary           — counts of imported records
 */
@RestController
@RequestMapping("/api/import")
@RequiredArgsConstructor
@Slf4j
public class ImportController {

    private final CustomerRepository  customerRepo;
    private final ServiceRequestRepository serviceRepo;
    private final SaleRepository saleRepo;
    private final ObjectMapper mapper;

    // ─────────────────────────────────────────────────────────
    // DTOs
    // ─────────────────────────────────────────────────────────

    @Data
    public static class ServiceRowRequest {
        private String name;          // Customer name
        private String address;
        private String phone;
        private String date;          // "4-9-20" / "11/10/20" / "23-9-20"
        private String serviceFee;    // numeric string or empty
        private String total;
        private List<String> services; // ["Level Controller Float Model / 1250", …]
    }

    @Data
    public static class SaleRowRequest {
        private String saleCode;       // SAL001
        private String billingName;    // Customer name
        private String customerCode;   // AGA001
        private String product;
        private String saleDate;       // "03-02-2012"
        private String amount;
    }

    @Data
    public static class CustomerRowRequest {
        private String customerCode;
        private String name;
        private String address;
        private List<String> phones;
    }

    @Data
    public static class ImportRequest<T> {
        private List<T> rows;
    }

    @Data
    public static class ImportResult {
        private int imported;
        private int skipped;
        private int errors;
        private List<String> errorDetails = new ArrayList<>();
    }

    // ─────────────────────────────────────────────────────────
    // Import service records
    // ─────────────────────────────────────────────────────────

    @PostMapping("/service-records")
    public ResponseEntity<ApiResponse<ImportResult>> importServiceRecords(
            @RequestBody Map<String, List<ServiceRowRequest>> body) {

        List<ServiceRowRequest> rows = body.getOrDefault("rows", List.of());
        ImportResult result = new ImportResult();

        for (ServiceRowRequest row : rows) {
            try {
                // Find or create customer
                Customer customer = findOrCreateCustomer(
                    row.getName(), row.getPhone(), row.getAddress());

                // Parse date
                LocalDateTime date = parseDate(row.getDate());

                // Build spare parts JSON from services list
                List<Map<String,Object>> parts = new ArrayList<>();
                BigDecimal spareTotal = BigDecimal.ZERO;
                if (row.getServices() != null) {
                    for (String svc : row.getServices()) {
                        if (svc == null || svc.isBlank()) continue;
                        // Format: "Description / amount" or just "Description"
                        String[] split = svc.split("/");
                        String desc  = split[0].trim();
                        BigDecimal amt = BigDecimal.ZERO;
                        if (split.length > 1) {
                            try { amt = new BigDecimal(split[1].trim()); } catch (Exception e) {}
                        }
                        Map<String,Object> p = new LinkedHashMap<>();
                        p.put("name", desc);
                        p.put("qty", 1);
                        p.put("unitPrice", amt);
                        p.put("lineTotal", amt);
                        parts.add(p);
                        spareTotal = spareTotal.add(amt);
                    }
                }

                BigDecimal serviceFee = parseMoney(row.getServiceFee());
                BigDecimal totalAmt   = parseMoney(row.getTotal());
                if (totalAmt.compareTo(BigDecimal.ZERO) == 0) {
                    totalAmt = serviceFee.add(spareTotal);
                }

                String spareJson = mapper.writeValueAsString(parts);
                String invoiceNo = "IMP-SVC-" + System.nanoTime() % 1000000;

                ServiceRequest sr = ServiceRequest.builder()
                    .customer(customer)
                    .customerName(customer.getName())
                    .customerMobile(customer.getMobile())
                    .customerAddress(customer.getAddress())
                    .issueDescription(parts.isEmpty() ? "Service visit" :
                        parts.stream().map(p -> (String)p.get("name")).reduce("", (a,b) -> a.isBlank()?b:a+", "+b))
                    .serviceCharge(serviceFee)
                    .sparePartsJson(spareJson)
                    .sparePartsTotal(spareTotal)
                    .totalBillAmount(totalAmt)
                    .paymentStatus("PAID")
                    .paymentMethod("CASH")
                    .status("COMPLETED")
                    .invoiceNumber(invoiceNo)
                    .ticketNumber("TKT-IMP-" + System.nanoTime() % 1000000)
                    .stockDeducted(false)
                    .createdAt(date)
                    .completedAt(date)
                    .build();

                serviceRepo.save(sr);
                result.setImported(result.getImported() + 1);

            } catch (Exception e) {
                result.setErrors(result.getErrors() + 1);
                result.getErrorDetails().add("Row [" + row.getName() + "]: " + e.getMessage());
                log.warn("Import service row failed: {}", e.getMessage());
            }
        }
        log.info("Service import: imported={} errors={}", result.getImported(), result.getErrors());
        return ResponseEntity.ok(ApiResponse.success("Import complete", result));
    }

    // ─────────────────────────────────────────────────────────
    // Import customers
    // ─────────────────────────────────────────────────────────

    @PostMapping("/customers")
    public ResponseEntity<ApiResponse<ImportResult>> importCustomers(
            @RequestBody Map<String, List<CustomerRowRequest>> body) {

        List<CustomerRowRequest> rows = body.getOrDefault("rows", List.of());
        ImportResult result = new ImportResult();

        for (CustomerRowRequest row : rows) {
            try {
                String primaryPhone = (row.getPhones() != null && !row.getPhones().isEmpty())
                    ? row.getPhones().get(0) : "0000000000";
                // Skip if already exists by code or name+phone
                boolean exists = customerRepo.findByMobile(primaryPhone).isPresent();
                if (exists) { result.setSkipped(result.getSkipped() + 1); continue; }

                Customer c = Customer.builder()
                    .name(row.getName())
                    .mobile(primaryPhone)
                    .address(row.getAddress())
                    .customerType(row.getCustomerCode())
                    .active(true)
                    .build();
                customerRepo.save(c);
                result.setImported(result.getImported() + 1);
            } catch (Exception e) {
                result.setErrors(result.getErrors() + 1);
                result.getErrorDetails().add("Customer [" + row.getName() + "]: " + e.getMessage());
            }
        }
        return ResponseEntity.ok(ApiResponse.success("Customers imported", result));
    }

    // ─────────────────────────────────────────────────────────
    // Import sales
    // ─────────────────────────────────────────────────────────

    @PostMapping("/sales")
    public ResponseEntity<ApiResponse<ImportResult>> importSales(
            @RequestBody Map<String, Object> body) {

        @SuppressWarnings("unchecked")
        List<Map<String,Object>> saleRows = (List<Map<String,Object>>) body.getOrDefault("sales", List.of());
        @SuppressWarnings("unchecked")
        List<Map<String,Object>> custRows = (List<Map<String,Object>>) body.getOrDefault("customers", List.of());
        @SuppressWarnings("unchecked")
        List<Map<String,Object>> phoneRows = (List<Map<String,Object>>) body.getOrDefault("phones", List.of());

        ImportResult result = new ImportResult();

        // Build customer code → phones map
        Map<String, List<String>> codePhones = new HashMap<>();
        for (Map<String,Object> pr : phoneRows) {
            String code = str(pr.get("customerCode"));
            String phone = str(pr.get("phone"));
            codePhones.computeIfAbsent(code, k -> new ArrayList<>()).add(phone);
        }

        // Build customer code → customer map, upsert by phone
        Map<String, Customer> codeToCustomer = new HashMap<>();
        for (Map<String,Object> cr : custRows) {
            try {
                String code    = str(cr.get("customerCode"));
                String name    = str(cr.get("customerName"));
                String address = str(cr.get("address"));
                List<String> phones = codePhones.getOrDefault(code, List.of("0000000000"));
                String primaryPhone = phones.get(0);

                Customer existing = customerRepo.findByMobile(primaryPhone).orElse(null);
                if (existing != null) {
                    codeToCustomer.put(code, existing);
                    result.setSkipped(result.getSkipped() + 1);
                } else {
                    Customer c = Customer.builder()
                        .name(name).mobile(primaryPhone).address(address)
                        .customerType(code).active(true).build();
                    codeToCustomer.put(code, customerRepo.save(c));
                    result.setImported(result.getImported() + 1);
                }
            } catch (Exception e) {
                result.setErrors(result.getErrors() + 1);
                result.getErrorDetails().add("Customer: " + e.getMessage());
            }
        }

        // Import sales rows
        int salesImported = 0;
        for (Map<String,Object> sr : saleRows) {
            try {
                String code    = str(sr.get("customerCode"));
                String product = str(sr.get("product"));
                String amount  = str(sr.get("amount"));
                String date    = str(sr.get("saleDate"));
                String saleCode= str(sr.get("saleCode"));

                Customer customer = codeToCustomer.get(code);
                LocalDateTime saleDate = parseDate(date);
                BigDecimal amt = parseMoney(amount);

                Sale sale = Sale.builder()
                    .customer(customer)
                    .customerName(customer != null ? customer.getName() : str(sr.get("billingName")))
                    .customerMobile(customer != null ? customer.getMobile() : "")
                    .productName(product)
                    .quantity(1)
                    .unitPrice(amt)
                    .totalAmount(amt)
                    .paymentStatus("PAID")
                    .paymentMethod("CASH")
                    .invoiceNumber(saleCode)
                    .stockDeducted(false)
                    .createdAt(saleDate)
                    .notes("Imported from legacy records")
                    .build();

                saleRepo.save(sale);
                salesImported++;
            } catch (Exception e) {
                result.setErrors(result.getErrors() + 1);
                result.getErrorDetails().add("Sale [" + sr.get("saleCode") + "]: " + e.getMessage());
            }
        }

        result.setImported(result.getImported() + salesImported);
        log.info("Sales import: customers={} sales={} errors={}",
            codeToCustomer.size(), salesImported, result.getErrors());
        return ResponseEntity.ok(ApiResponse.success("Sales import complete", result));
    }

    // ─────────────────────────────────────────────────────────
    // Summary
    // ─────────────────────────────────────────────────────────

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String,Long>>> summary() {
        return ResponseEntity.ok(ApiResponse.success("OK", Map.of(
            "customers",       customerRepo.count(),
            "serviceRecords",  serviceRepo.count(),
            "sales",           saleRepo.count()
        )));
    }

    // ─────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────

    private Customer findOrCreateCustomer(String name, String phone, String address) {
        String mobile = (phone == null || phone.isBlank()) ? "0000000000" : phone.trim().replaceAll("[^0-9]", "");
        if (mobile.length() > 10) mobile = mobile.substring(mobile.length() - 10);
        if (mobile.length() < 10) mobile = mobile + "0".repeat(10 - mobile.length());

        Optional<Customer> existing = customerRepo.findByMobile(mobile);
        if (existing.isPresent()) return existing.get();

        Customer c = Customer.builder()
            .name(name != null ? name.trim() : "Unknown")
            .mobile(mobile)
            .address(address != null ? address.trim() : "")
            .active(true)
            .build();
        return customerRepo.save(c);
    }

    /** Parse various Indian date formats: d-M-yy, d/M/yy, dd-MM-yyyy, dd/MM/yyyy */
    private LocalDateTime parseDate(String raw) {
        if (raw == null || raw.isBlank()) return LocalDateTime.now();
        String d = raw.trim();
        String[] formats = {
            "d-M-yy","d/M/yy","dd-MM-yy","dd/MM/yy",
            "d-M-yyyy","d/M/yyyy","dd-MM-yyyy","dd/MM/yyyy",
            "MM-dd-yyyy","yyyy-MM-dd"
        };
        for (String fmt : formats) {
            try {
                return java.time.LocalDate.parse(d, DateTimeFormatter.ofPattern(fmt)).atStartOfDay();
            } catch (DateTimeParseException ignored) {}
        }
        log.warn("Could not parse date '{}', using now", d);
        return LocalDateTime.now();
    }

    private BigDecimal parseMoney(String v) {
        if (v == null || v.isBlank()) return BigDecimal.ZERO;
        try { return new BigDecimal(v.trim().replaceAll("[^0-9.]", "")); }
        catch (Exception e) { return BigDecimal.ZERO; }
    }

    private String str(Object o) { return o != null ? o.toString().trim() : ""; }
}
