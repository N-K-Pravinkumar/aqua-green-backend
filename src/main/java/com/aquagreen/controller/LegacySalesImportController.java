package com.aquagreen.controller;

import com.aquagreen.dto.ApiResponse;
import com.aquagreen.model.Customer;
import com.aquagreen.model.CustomerPhone;
import com.aquagreen.model.Sale;
import com.aquagreen.repository.CustomerPhoneRepository;
import com.aquagreen.repository.SaleRepository;
import com.aquagreen.service.CustomerService;
import com.aquagreen.util.MobileUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Bulk import for historical sales, one row per sale, tab-separated:
 *
 *   Customer Name <TAB> Address <TAB> Phone1/Phone2/... <TAB> Product <TAB> Date (DD-MM-YYYY) <TAB> Amount
 *
 * The phone column supports multiple numbers separated by "/" (matching
 * AGA_Sales.xlsx's own "Phone Numbers" column) — the first becomes the
 * customer's primary mobile, any additional ones are stored as extra
 * CustomerPhone records so the same person is never created twice just
 * because a bill used their second number.
 */
@RestController @RequestMapping("/api/sales/import") @RequiredArgsConstructor @Slf4j
public class LegacySalesImportController {

    private final SaleRepository saleRepo;
    private final CustomerPhoneRepository phoneRepo;
    private final CustomerService customerService;

    @PostMapping("/legacy-sales")
    public ResponseEntity<ApiResponse<Map<String,Object>>> importLegacy(@RequestBody Map<String,String> body) {
        String text = body.getOrDefault("text", "");
        String[] lines = text.split("\r?\n");

        int created = 0, skipped = 0, phonesLinked = 0;
        List<String> errors = new ArrayList<>();
        List<String> existingCodes = new ArrayList<>(saleRepo.findAllSaleCodes());

        for (String rawLine : lines) {
            if (rawLine.trim().isEmpty()) continue;
            try {
                String[] cols = rawLine.split("\t");
                if (cols.length < 3) {
                    skipped++;
                    errors.add("Skipped (need at least name/address/phone): " + preview(rawLine));
                    continue;
                }

                String name    = cols[0].trim();
                String address = cols.length > 1 ? cols[1].trim() : "";
                String phonesCell = cols.length > 2 ? cols[2].trim() : "";
                String product = cols.length > 3 ? cols[3].trim() : "";
                String dateCell = cols.length > 4 ? cols[4].trim() : "";
                String amountCell = cols.length > 5 ? cols[5].trim().replaceAll("[^0-9.]", "") : "";

                List<String> phones = new ArrayList<>();
                for (String p : phonesCell.split("/")) {
                    String n = MobileUtil.normalize(p.trim());
                    if (n != null && n.length() == 10) phones.add(n);
                }
                if (name.isEmpty() || phones.isEmpty()) {
                    skipped++;
                    errors.add("Skipped (missing name or valid phone): " + preview(rawLine));
                    continue;
                }

                String primaryPhone = phones.get(0);
                Customer cust = customerService.findOrCreate(name, primaryPhone, null, address, null, "LEGACY_SALES_IMPORT");

                // Register any extra phone numbers, skipping ones already used anywhere
                for (int i = 1; i < phones.size(); i++) {
                    String extra = phones.get(i);
                    boolean taken = phoneRepo.existsByPhone(extra) || extra.equals(primaryPhone);
                    if (!taken) {
                        try {
                            phoneRepo.save(CustomerPhone.builder().customer(cust).phone(extra).label("From import").build());
                            phonesLinked++;
                        } catch (Exception dupe) { /* race/unique-violation — fine to skip */ }
                    }
                }

                LocalDateTime saleDate = dateCell.isEmpty() ? LocalDateTime.now() : parseDate(dateCell);
                BigDecimal amount = amountCell.isEmpty() ? BigDecimal.ZERO : new BigDecimal(amountCell);

                String saleCode = com.aquagreen.util.CodeGenerator.next("SALE", existingCodes, 3);
                existingCodes.add(saleCode);

                Sale sale = Sale.builder()
                    .saleCode(saleCode)
                    .customer(cust)
                    .customerName(name)
                    .customerMobile(primaryPhone)
                    .customerAddress(address)
                    .productName(product)
                    .quantity(1)
                    .unitPrice(amount)
                    .totalAmount(amount)
                    .paymentStatus("PAID")
                    .invoiceNumber("LEGACY-SALE-" + System.nanoTime())
                    .stockDeducted(true) // historical — never deduct from current stock
                    .createdAt(saleDate)
                    .build();

                saleRepo.save(sale);
                created++;
            } catch (Exception e) {
                skipped++;
                errors.add(preview(rawLine) + " → " + e.getMessage());
                log.warn("Legacy sale import line failed: {}", e.getMessage());
            }
        }

        Map<String,Object> result = new LinkedHashMap<>();
        result.put("created", created);
        result.put("skipped", skipped);
        result.put("phonesLinked", phonesLinked);
        result.put("errors", errors);
        return ResponseEntity.ok(ApiResponse.success(created + " sale(s) imported, " + skipped + " skipped", result));
    }

    private String preview(String line) {
        return line.length() > 60 ? line.substring(0, 60) + "…" : line;
    }

    /** Accepts DD-MM-YYYY or DD-MM-YY. */
    private LocalDateTime parseDate(String s) {
        String[] p = s.split("-");
        int day = Integer.parseInt(p[0]);
        int month = Integer.parseInt(p[1]);
        int year = Integer.parseInt(p[2]);
        if (year < 100) year += 2000;
        return LocalDateTime.of(year, month, day, 12, 0);
    }
}
