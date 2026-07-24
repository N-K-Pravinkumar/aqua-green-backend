package com.aquagreen.controller;

import com.aquagreen.dto.ApiResponse;
import com.aquagreen.model.Customer;
import com.aquagreen.model.ServiceRequest;
import com.aquagreen.repository.ServiceRequestRepository;
import com.aquagreen.service.CustomerService;
import com.aquagreen.util.MobileUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * One-off bulk import for historical service bills, pasted straight out of
 * a spreadsheet (tab-separated). Each line looks like:
 *
 * Naresh <TAB> Ondipudur,cbe <TAB> 9344960687 <TAB> 12-10-20 <TAB> <TAB> 3000
 * <TAB> Get Volve / 550 <TAB> Carbon Set / 1500 <TAB> Out Filter / 200 ...
 *
 * i.e. name, address, mobile, then anywhere in the remaining columns: a
 * date, a plain total amount, and any number of "Item Name / Price" cells.
 * Column position isn't assumed beyond the first three — blank columns and
 * inconsistent layouts are tolerated by scanning for shape (date-like,
 * number-like, "X / Y"-like) rather than a fixed index.
 *
 * Each line becomes one COMPLETED ServiceRequest with sparePartsJson
 * populated, so it shows up correctly in the Customer History timeline and
 * is available to the Maintenance Reminders part-search. Historical parts
 * are NOT deducted from current stock (stockDeducted is pre-set to true).
 */
@RestController
@RequestMapping("/api/service-requests/import")
@RequiredArgsConstructor
@Slf4j
public class LegacyImportController {

    private final ServiceRequestRepository repo;
    private final CustomerService customerService;
    private final ObjectMapper mapper;

    @PostMapping("/legacy-bills")
    public ResponseEntity<ApiResponse<Map<String, Object>>> importLegacy(@RequestBody Map<String, String> body) {
        String text = body.getOrDefault("text", "");
        String[] lines = text.split("\r?\n");

        int created = 0, skipped = 0;
        List<String> errors = new ArrayList<>();

        for (String rawLine : lines) {
            if (rawLine.trim().isEmpty())
                continue;
            try {
                String[] cols = rawLine.split("\t");
                if (cols.length < 3) {
                    skipped++;
                    errors.add("Skipped (need at least name/address/mobile): " + preview(rawLine));
                    continue;
                }

                String name = cols[0].trim();
                String address = cols[1].trim();
                String mobile = MobileUtil.normalize(cols[2].trim());
                if (name.isEmpty() || mobile == null || mobile.isEmpty()) {
                    skipped++;
                    errors.add("Skipped (missing name or mobile): " + preview(rawLine));
                    continue;
                }

                LocalDateTime completedAt = null;
                BigDecimal total = null;
                List<Map<String, Object>> parts = new ArrayList<>();
                BigDecimal partsSum = BigDecimal.ZERO;

                for (int i = 3; i < cols.length; i++) {
                    String cell = cols[i].trim();
                    if (cell.isEmpty())
                        continue;

                    if (completedAt == null && cell.matches("\\d{1,2}-\\d{1,2}-\\d{2,4}")) {
                        completedAt = parseLegacyDate(cell);
                        continue;
                    }
                    if (total == null && cell.matches("\\d+(\\.\\d+)?")) {
                        total = new BigDecimal(cell);
                        continue;
                    }
                    if (cell.contains("/")) {
                        int idx = cell.lastIndexOf('/');
                        String pname = cell.substring(0, idx).trim();
                        String priceStr = cell.substring(idx + 1).trim();
                        try {
                            BigDecimal price = new BigDecimal(priceStr);
                            if (!pname.isEmpty()) {
                                Map<String, Object> part = new LinkedHashMap<>();
                                part.put("name", pname);
                                part.put("qty", 1);
                                part.put("unitPrice", price);
                                part.put("lineTotal", price);
                                parts.add(part);
                                partsSum = partsSum.add(price);
                            }
                        } catch (NumberFormatException ignore) {
                            /* not a "name / price" cell — skip it */ }
                    }
                }

                if (completedAt == null)
                    completedAt = LocalDateTime.now();
                BigDecimal totalBill = total != null ? total : partsSum;
                BigDecimal serviceCharge = total != null ? total.subtract(partsSum).max(BigDecimal.ZERO)
                        : BigDecimal.ZERO;

                Customer cust = customerService.findOrCreate(name, mobile, null, address, null, "LEGACY_IMPORT");

                ServiceRequest sr = ServiceRequest.builder()
                        .ticketNumber("LEGACY-" + System.nanoTime())
                        .customer(cust)
                        .customerName(name)
                        .customerMobile(mobile)
                        .customerAddress(address)
                        .status("COMPLETED")
                        .priority("MEDIUM")
                        .serviceCharge(serviceCharge)
                        .sparePartsJson(parts.isEmpty() ? null : mapper.writeValueAsString(parts))
                        .sparePartsTotal(partsSum)
                        .totalBillAmount(totalBill)
                        .paymentStatus("PAID")
                        .completedAt(completedAt)
                        .stockDeducted(true) // historical parts — never deduct from current stock
                        .build();

                repo.save(sr);
                created++;
            } catch (Exception e) {
                skipped++;
                errors.add(preview(rawLine) + " → " + e.getMessage());
                log.warn("Legacy import line failed: {}", e.getMessage());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("created", created);
        result.put("skipped", skipped);
        result.put("errors", errors);
        return ResponseEntity.ok(ApiResponse.success(created + " bill(s) imported, " + skipped + " skipped", result));
    }

    private String preview(String line) {
        return line.length() > 50 ? line.substring(0, 50) + "…" : line;
    }

    /** Accepts DD-MM-YY or DD-MM-YYYY, defaulting 2-digit years to 20xx. */
    private LocalDateTime parseLegacyDate(String s) {
        String[] p = s.split("-");
        int day = Integer.parseInt(p[0]);
        int month = Integer.parseInt(p[1]);
        int year = Integer.parseInt(p[2]);
        if (year < 100)
            year += 2000;
        return LocalDateTime.of(year, month, day, 12, 0);
    }
}
